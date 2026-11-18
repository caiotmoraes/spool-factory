package br.com.caiot.u1spool;
import org.json.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Bug 2026-11-18: vínculo extrusor → rolo (SET_SPOOL_ID + local) e rolo anterior para o Armazém. */
public class LinkTest {
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 public static void main(String[] args)throws Exception{
  L10n.load(Files.newInputStream(Path.of("assets/strings.json")));L10n.set("pt","pt");
  ok(Api.setSpoolScript(1,15).equals("SET_SPOOL_ID CHANNEL=1 SPOOL_ID=15"),"script T2, canal 0-based");
  ok(Api.setSpoolPath(3,12).equals("/printer/gcode/script?script=SET_SPOOL_ID%20CHANNEL%3D3%20SPOOL_ID%3D12"),"query igual ao display");
  ok(Api.urlEncode("a&b?c#d+é").equals("a%26b%3Fc%23d%2B%C3%A9"),"percent-encoding RFC 3986");
  ok(Api.locationQuery(1).equals("/v1/spool?location=Snapmaker%20U1%20-%20Extrusor%202"),"busca do local");
  for(int[] bad:new int[][]{{4,15},{-1,15},{1,0}})try{Api.setSpoolScript(bad[0],bad[1]);throw new AssertionError("inválido aceito");}catch(java.io.IOException expected){}
  JSONObject prev=new JSONObject().put("id",12).put("location","Snapmaker U1 - Extrusor 2");
  ok(Api.shouldRelease(prev,1,15),"anterior liberado");
  ok(!Api.shouldRelease(prev,1,12),"novo não");
  ok(!Api.shouldRelease(prev,2,15),"outro canal não");
  ok(!Api.shouldRelease(new JSONObject().put("id",12).put("location","Snapmaker U1 - Extrusor 2 (seco)"),1,15),"parcial não");

  Map<Integer,JSONObject> spools=new TreeMap<>();
  spools.put(12,new JSONObject().put("id",12).put("location","Snapmaker U1 - Extrusor 2"));
  spools.put(15,new JSONObject().put("id",15).put("location","Prateleira"));
  spools.put(16,new JSONObject().put("id",16).put("location","Snapmaker U1 - Extrusor 2 (seco)"));
  List<String> log=new ArrayList<>();final String[] mode={"ok"};
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",ex->{try{
    byte[] in=ex.getRequestBody().readAllBytes();String uri=ex.getRequestURI().getRawPath()+(ex.getRequestURI().getRawQuery()==null?"":"?"+ex.getRequestURI().getRawQuery());
    String out;int code=200;
    if(uri.startsWith("/printer/gcode/script")){
      log.add(ex.getRequestMethod()+" "+uri+" body="+in.length);
      if(mode[0].equals("reject")){code=400;out="{\"error\":{\"code\":400,\"message\":\"SpoolLink: spool #99 not found\"}}";}
      else if(mode[0].equals("slow")){Thread.sleep(11000);out="{\"result\":\"ok\"}";}
      else out="{\"result\":\"ok\"}";
    }else if(uri.equals("/server/spoolman/proxy")){
      JSONObject req=new JSONObject(new String(in,StandardCharsets.UTF_8));String m=req.getString("request_method"),p=req.getString("path");
      log.add("proxy "+m+" "+p+(req.has("body")?" "+req.get("body"):""));
      Object resp;
      if(m.equals("GET")&&p.startsWith("/v1/spool?location=")){
        String term=URLDecoder.decode(p.substring(p.indexOf('=')+1).replace("+","%2B"),"UTF-8").toLowerCase(Locale.ROOT);
        JSONArray a=new JSONArray();for(JSONObject s:spools.values())if(s.getString("location").toLowerCase(Locale.ROOT).contains(term))a.put(s);resp=a;
      }else if(m.equals("PATCH")){int id=Integer.parseInt(p.substring(p.lastIndexOf('/')+1));JSONObject s=spools.get(id);s.put("location",req.getJSONObject("body").getString("location"));resp=s;}
      else resp=JSONObject.NULL;
      out=new JSONObject().put("result",new JSONObject().put("response",resp).put("error",JSONObject.NULL)).toString();
    }else{code=404;out="{}";}
    byte[] b=out.getBytes(StandardCharsets.UTF_8);ex.sendResponseHeaders(code,b.length);ex.getResponseBody().write(b);ex.close();
  }catch(Exception e){e.printStackTrace();ex.close();}});
  server.setExecutor(java.util.concurrent.Executors.newCachedThreadPool());
  server.start();
  try{
   Api api=new Api("127.0.0.1:"+server.getAddress().getPort(),"","","");
   ok(api.setSpoolId(1,15),"SET_SPOOL_ID aceito");
   ok(log.get(0).equals("POST /printer/gcode/script?script=SET_SPOOL_ID%20CHANNEL%3D1%20SPOOL_ID%3D15 body=0"),log.get(0));
   mode[0]="reject";
   try{api.setSpoolId(2,99);throw new AssertionError("recusa aceita");}catch(java.io.IOException e){ok(e.getMessage().contains("SpoolLink: spool #99 not found"),e.getMessage());}
   mode[0]="slow";int before=log.size();
   ok(!api.setSpoolId(1,15),"timeout = resposta perdida (confere, não repete)");
   ok(log.size()==before+1,"um único envio");
   mode[0]="ok";
   ok(api.locateInU1(15,1).getString("location").equals("Snapmaker U1 - Extrusor 2"),"local do novo");
   List<Integer> moved=api.releasePrevious(1,15);
   ok(moved.equals(Arrays.asList(12)),"só o anterior exato: "+moved);
   ok(spools.get(12).getString("location").equals("Armazém"),"anterior no Armazém");
   ok(spools.get(16).getString("location").equals("Snapmaker U1 - Extrusor 2 (seco)"),"local personalizado intacto");
   ok(spools.get(15).getString("location").equals("Snapmaker U1 - Extrusor 2"),"novo intacto");
   ok(api.releasePrevious(1,15).isEmpty(),"idempotente");
   System.out.println("PASS: SET_SPOOL_ID na query (corpo vazio), recusa, resposta perdida sem repetir, local do novo, anterior para o Armazém.");
  }finally{server.stop(0);}
  System.exit(0);
 }
}
