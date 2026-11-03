package br.com.caiot.u1spool;
import org.json.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.concurrent.atomic.AtomicReference;

public class ApiTest {
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 interface Attempt{void run()throws Exception;}
 static void rejects(Attempt a,String text)throws Exception{try{a.run();throw new AssertionError("Expected rejection: "+text);}catch(java.io.IOException e){ok(e.getMessage().contains(text),e.getMessage());}}
 public static void main(String[] args)throws Exception{
  ok("http://192.0.2.50".equals(Api.base("192.0.2.50")),"bare printer IP");
  ok("http://spoolman.example:7912".equals(Api.base("spoolman.example:7912/")),"bare hostname and port");
  ok("https://printer.example".equals(Api.base("https://printer.example/")),"preserve explicit HTTPS");
  try{Api.base("");throw new AssertionError("blank setup must be rejected");}catch(IllegalArgumentException expected){}
  try{Api.base("http://user:password@printer.example");throw new AssertionError("embedded credentials must be rejected");}catch(IllegalArgumentException expected){}
  JSONObject sample=new JSONObject(Files.readString(Path.of("tests/fixtures/spool.json")));
  String uid="04A1B2C3D4E5F6";
  ok(Api.uids(sample).contains(uid),"quoted UID");
  JSONObject array=new JSONObject(sample.toString());array.getJSONObject("extra").put("card_uids","[\"04:a1:b2:c3:d4:e5:f6\",\"AABBCCDD\"]");
  ok(Api.uids(array).size()==2&&Api.uids(array).contains(uid),"JSON array UID");
  JSONObject raw=new JSONObject(sample.toString());raw.getJSONObject("extra").put("card_uids",uid);ok(Api.uids(raw).contains(uid),"raw UID");
  JSONObject status=new JSONObject(Files.readString(Path.of("tests/fixtures/status.json")));
  ok(Api.sensors(status).length==4,"real sensor shape");
  AtomicReference<String> spools=new AtomicReference<>(new JSONArray().put(sample).toString());
  AtomicReference<JSONObject> payload=new AtomicReference<>();
  AtomicReference<String> endpoint=new AtomicReference<>();
  AtomicReference<String> readerMode=new AtomicReference<>("external");
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",exchange->{try{
   String path=exchange.getRequestURI().getPath();String response;
   if(path.equals("/api/v1/spool"))response=spools.get();
   else if(path.equals("/api/v1/spool/30"))response=sample.toString();
   else if(path.equals("/firmware-config/api/settings"))response=new JSONObject().put("snapmaker_components",new JSONObject().put("items",new JSONArray().put(new JSONObject().put("id","rfid").put("current",readerMode.get())))).toString();
   else if(path.endsWith("/objects/query"))response=new JSONObject().put("result",new JSONObject().put("status",status)).toString();
   else {endpoint.set(path);payload.set(new JSONObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)));response="{\"result\":{\"state\":\"success\"}}";}
   byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
  }catch(Exception e){throw new RuntimeException(e);}});
  server.start();String base="http://127.0.0.1:"+server.getAddress().getPort();Api api=new Api(base,base,"","");
  try{
   ok(api.lookup(uid).getInt("id")==30,"lookup correct spool");
   rejects(()->api.lookup("00000000"),"não cadastrada");
   spools.set(new JSONArray().put(sample).put(sample).toString());rejects(()->api.lookup(uid),"duplicado");
   JSONObject archived=new JSONObject(sample.toString()).put("archived",true);spools.set(new JSONArray().put(archived).toString());rejects(()->api.lookup(uid),"arquivado");
   api.status();api.inject(2,uid,"NTAG");JSONObject body=payload.get();
   ok(body.getInt("channel")==2,"zero based channel");JSONObject info=body.getJSONObject("info");
   ok(info.length()==2,"UID only: no overwritten material or inventory linkage");
   JSONArray bytes=info.getJSONArray("CARD_UID");ok(bytes.length()==7&&bytes.getInt(1)==161,"UID byte order");
   ok("NTAG".equals(info.getString("CARD_TYPE")),"tag type");
   payload.set(null);readerMode.set("openrfid");rejects(()->api.inject(2,uid,"NTAG"),"External");rejects(()->api.assign(2,sample),"External");rejects(()->api.feed(2,true),"External");ok(payload.get()==null,"internal readers cannot race with external writes");readerMode.set("external");
   Api.channel(status,2).put("filament_detected",true);api.assign(2,api.spool(30));ok("/printer/spoollink/set".equals(endpoint.get()),"manual association uses native metadata endpoint");
   JSONObject manual=payload.get().getJSONObject("info");ok(manual.getInt("SPOOL_ID")==30&&"PETG".equals(manual.getString("MAIN_TYPE")),"assign exact spool and material");ok(manual.getJSONArray("CARD_UID").length()==0,"manual selection cannot transfer a hardware UID");
   JSONObject multicolor=new JSONObject(sample.toString());multicolor.getJSONObject("filament").put("multi_color_hexes","DFA62A,F1681E,DB39AF");JSONObject multiInfo=Api.spoolInfo(multicolor);ok(multiInfo.getInt("COLOR_NUMS")==3&&multiInfo.getInt("RGB_3")==0xDB39AF,"preserve all spool colors");
   Api.channel(status,2).put("filament_detected",true);status.getJSONObject("print_task_config").getJSONArray("filament_type").put(2,"PETG");
   ok("AUTO_FEEDING EXTRUDER=2 LOAD=1".equals(Api.feederScript(status,2,true)),"load correct physical feeder");
   ok("AUTO_FEEDING EXTRUDER=2 UNLOAD=1".equals(Api.feederScript(status,2,false)),"unload correct physical feeder");
   payload.set(null);status.getJSONObject("print_stats").put("state","printing");rejects(()->api.assign(2,sample),"impressão");rejects(()->Api.feederScript(status,2,true),"impressão");ok(payload.get()==null,"no writes during printing");
   status.getJSONObject("print_stats").put("state","paused");rejects(()->Api.feederScript(status,2,false),"impressão");
   status.getJSONObject("print_stats").put("state","standby");Api.channel(status,3).put("channel_state","load_heating");rejects(()->Api.feederScript(status,2,true),"Aguarde");Api.channel(status,3).put("channel_state","wait_insert");
   status.getJSONObject("print_task_config").getJSONArray("filament_type").put(2,"NONE");rejects(()->Api.feederScript(status,2,true),"Identifique");
   Api.channel(status,2).put("filament_detected",false);rejects(()->Api.feederScript(status,2,false),"Insira");
   rejects(()->api.assign(2,sample),"Insira");
   System.out.println("PASS: real status schema, UID formats, lookup, unknown/duplicate/archived rejection, external reader request.");
  }finally{server.stop(0);}
  if(args.length==2){Api live=new Api(args[0],args[1],"","");JSONObject actual=live.status();JSONArray items=live.inventory();System.out.println("LIVE READ ONLY: inventory items="+items.length()+"; sensors="+java.util.Arrays.toString(Api.sensors(actual)));}
 }
}
