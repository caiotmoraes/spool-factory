package br.com.caiot.u1spool;
import org.json.*;
import com.sun.net.httpserver.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;

public class ApiTest {
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 interface Attempt{void run()throws Exception;}
 static void rejects(Attempt a,String text)throws Exception{try{a.run();throw new AssertionError("Expected rejection: "+text);}catch(java.io.IOException e){ok(e.getMessage().contains(text),e.getMessage());}}
 public static void main(String[] args)throws Exception{
  L10n.load(Files.newInputStream(Path.of("assets/strings.json")));L10n.set("pt","pt");
  ok("http://192.0.2.50:7125".equals(Api.moonraker("192.0.2.50")),"Moonraker :7125 por padrão");
  ok("http://192.0.2.50".equals(Api.webBase("192.0.2.50:7125")),"firmware-config no :80");
  ok("http://192.0.2.50:8080".equals(Api.moonraker("http://192.0.2.50:8080/")),"porta explícita respeitada");
  ok("http://spoolman.example:7912".equals(Api.base("spoolman.example:7912/")),"bare hostname and port");
  try{Api.base("http://user:password@printer.example");throw new AssertionError("embedded credentials must be rejected");}catch(IllegalArgumentException expected){}
  try{new Api("","","","");throw new AssertionError("U1 ou Spoolman obrigatório");}catch(IllegalArgumentException expected){}
  JSONObject sample=new JSONObject(Files.readString(Path.of("tests/fixtures/spool.json")));
  String uid="04A1B2C3D4E5F6";
  ok(Api.uids(sample).contains(uid),"quoted UID");
  JSONObject legacy=new JSONObject(sample.toString());legacy.getJSONObject("extra").put("card_uids","[\"04:a1:b2:c3:d4:e5:f6\",\"AABBCCDD\"]");
  ok(Api.uids(legacy).size()==2&&Api.uids(legacy).contains(uid),"JSON array UID");
  // Correção do bug de substring do display: 04A1B2C3 não casa com 04A1B2C3D4E5F6.
  ok(!Api.uids(sample).contains("04A1B2C3"),"comparação exata, nunca substring");
  JSONObject status=new JSONObject(Files.readString(Path.of("tests/fixtures/status.json")));
  ok(Api.sensors(status).length==4,"real sensor shape");status.put("temperature_sensor cavity",new JSONObject().put("temperature",31.5));
  ok(Api.cardUidHex(new JSONArray("[83,245,153,180,149,0,1]")).equals("53F599B4950001")&&Api.cardUidHex("83 245 153 180 149 0 1").equals("53F599B4950001"),"CARD_UID lista e texto");
  ok(Api.tagUid(status,0).isEmpty(),"CARD_UID 0 = sem tag");
  ok("M106 S128".equals(Api.fanScript(0,50))&&"SET_FAN_SPEED FAN=exhaust_fan SPEED=0.50".equals(Api.fanScript(1,50))&&"SET_FAN_SPEED FAN=cavity_fan SPEED=1.00".equals(Api.fanScript(3,100)),"fans iguais ao display");
  ok("SET_HEATER_TEMPERATURE HEATER=extruder2 TARGET=235".equals(Api.tempScript(2,235))&&"SET_HEATER_TEMPERATURE HEATER=heater_bed TARGET=55".equals(Api.tempScript(4,55)),"temps iguais ao display");
  rejects(()->Api.tempScript(4,150),"inválida");
  ok(Boolean.TRUE.equals(Api.rfidExternal(new JSONObject("{\"result\":{\"snapmaker\":{\"items\":[{\"id\":\"rfid\",\"current\":\"external\"}]}}}"))),"firmware-config aninhado");

  AtomicReference<String> spools=new AtomicReference<>(new JSONArray().put(sample).toString());
  AtomicReference<JSONObject> payload=new AtomicReference<>();
  AtomicReference<String> endpoint=new AtomicReference<>();
  AtomicReference<String> readerMode=new AtomicReference<>("external");
  List<String> proxied=new ArrayList<>();
  HttpServer server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
  server.createContext("/",exchange->{try{
   String path=exchange.getRequestURI().getPath();String response;
   if(path.equals("/server/spoolman/proxy")){
    JSONObject req=new JSONObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8));
    ok(req.getBoolean("use_v2_response"),"use_v2_response");proxied.add(req.getString("request_method")+" "+req.getString("path"));
    Object r=req.getString("path").startsWith("/v1/spool?")?new JSONArray(spools.get()):req.getString("path").equals("/v1/spool/30")?sample:req.getString("path").equals("/v1/spool/99")?null:new JSONObject();
    response=new JSONObject().put("result",r==null?new JSONObject().put("response",JSONObject.NULL).put("error",new JSONObject().put("status_code",404).put("message","Spool not found")):new JSONObject().put("response",r).put("error",JSONObject.NULL)).toString();
   }
   else if(path.equals("/firmware-config/api/settings"))response=new JSONObject().put("snapmaker_components",new JSONObject().put("items",new JSONArray().put(new JSONObject().put("id","rfid").put("current",readerMode.get())))).toString();
   else if(path.endsWith("/objects/list"))response=new JSONObject().put("result",new JSONObject().put("objects",new JSONArray().put("heater_bed").put("temperature_sensor cavity"))).toString();
   else if(path.endsWith("/objects/query")){ok(exchange.getRequestURI().getRawQuery().contains("temperature_sensor%20cavity"),"câmara consultada");response=new JSONObject().put("result",new JSONObject().put("status",status)).toString();}
   else {endpoint.set(path);payload.set(new JSONObject(new String(exchange.getRequestBody().readAllBytes(),StandardCharsets.UTF_8)));response="{\"result\":{\"state\":\"success\"}}";}
   byte[] bytes=response.getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(200,bytes.length);exchange.getResponseBody().write(bytes);exchange.close();
  }catch(Throwable e){e.printStackTrace();exchange.sendResponseHeaders(500,-1);exchange.close();}});
  server.start();String host="127.0.0.1:"+server.getAddress().getPort();Api api=new Api(host,"","","");
  try{
   ok(api.viaProxy(),"Spoolman pelo proxy por padrão");
   ok(api.lookup(uid).getInt("id")==30,"lookup via proxy");ok(proxied.contains("GET /v1/spool?allow_archived=true"),"rota do proxy");
   try{api.lookup("00000000");throw new AssertionError("unknown");}catch(Api.UnknownTag e){ok(e.uid.equals("00000000")&&e.getMessage().contains("não cadastrada"),"tag desconhecida");}
   rejects(()->api.spool(99),"Spool not found");
   spools.set(new JSONArray().put(sample).put(sample).toString());rejects(()->api.lookup(uid),"duplicado");
   JSONObject archived=new JSONObject(sample.toString()).put("archived",true);spools.set(new JSONArray().put(archived).toString());rejects(()->api.lookup(uid),"arquivado");
   JSONObject s=api.status();ok(s.has("chamber"),"câmara exposta");
   api.inject(2,uid,"NTAG");JSONObject body=payload.get();
   ok(body.getInt("channel")==2,"zero based channel");JSONObject info=body.getJSONObject("info");
   ok(info.length()==2,"UID only");JSONArray bytes=info.getJSONArray("CARD_UID");ok(bytes.length()==7&&bytes.getInt(1)==161,"UID byte order");
   api.inject(1,"04A1B2C3","M1");ok("M1".equals(payload.get().getJSONObject("info").getString("CARD_TYPE")),"CARD_TYPE M1");
   // External: exigido para spoollink/set e filament_detect/set, NÃO para AUTO_FEEDING (U1 em openrfid).
   payload.set(null);readerMode.set("openrfid");rejects(()->api.inject(2,uid,"NTAG"),"External");rejects(()->api.assign(2,sample),"External");ok(payload.get()==null,"sem escrita fora do modo External");
   Api.channel(status,2).put("filament_detected",true);status.getJSONObject("print_task_config").getJSONArray("filament_type").put(2,"PETG");
   api.feed(2,true);ok("/printer/gcode/script".equals(endpoint.get())&&payload.get().getString("script").equals("AUTO_FEEDING EXTRUDER=2 LOAD=1"),"carregar sem exigir External (igual ao display)");
   readerMode.set("external");
   api.assign(2,api.spool(30));ok("/printer/spoollink/set".equals(endpoint.get()),"spoollink/set");
   JSONObject manual=payload.get().getJSONObject("info");ok(manual.getInt("SPOOL_ID")==30&&"PETG".equals(manual.getString("MAIN_TYPE")),"assign exact spool");ok(manual.getJSONArray("CARD_UID").length()==0,"no UID transfer");
   // Durante a impressão: associar/ativar permitido; carregar/retirar bloqueado.
   status.getJSONObject("print_stats").put("state","printing");
   api.assign(2,sample);ok("/printer/spoollink/set".equals(endpoint.get()),"ativar no alimentador durante impressão");
   payload.set(null);rejects(()->Api.feederScript(status,2,true),"impressão");rejects(()->api.feed(2,false),"impressão");ok(payload.get()==null,"feed bloqueado durante impressão");
   rejects(()->api.setTemp(0,200),"durante a impressão");
   api.setFan(1,25);ok(payload.get().getString("script").equals("SET_FAN_SPEED FAN=exhaust_fan SPEED=0.25"),"ventilador durante impressão");
   api.printAction("pause");ok("/printer/print/pause".equals(endpoint.get()),"pausar");rejects(()->api.printAction("resume"),"Sem impressão");
   api.printAction("cancel");ok("/printer/print/cancel".equals(endpoint.get()),"cancelar");
   api.emergencyStop();ok("/printer/emergency_stop".equals(endpoint.get()),"parada de emergência");
   status.getJSONObject("print_stats").put("state","standby");status.put("idle_timeout",new JSONObject().put("state","Idle"));
   api.setTemp(4,55);ok(payload.get().getString("script").equals("SET_HEATER_TEMPERATURE HEATER=heater_bed TARGET=55"),"temp ociosa");
   rejects(()->api.printAction("pause"),"Sem impressão");
   Api.channel(status,3).put("channel_state","load_heating");rejects(()->Api.feederScript(status,2,true),"Aguarde");Api.channel(status,3).put("channel_state","wait_insert");
   status.getJSONObject("print_task_config").getJSONArray("filament_type").put(2,"NONE");rejects(()->Api.feederScript(status,2,true),"Identifique");
   Api.channel(status,2).put("filament_detected",false);rejects(()->Api.feederScript(status,2,false),"Insira");
   rejects(()->api.assign(2,sample),"Insira");
   System.out.println("PASS: Moonraker :7125 + proxy do Spoolman, UID Spec v1, lookup, External só para escrita de tag, associar durante impressão, controle (pausa/retomar/cancelar/emergência/temps/ventiladores).");
  }finally{server.stop(0);}
  if(args.length>=1){Api live=new Api(args[0],args.length>1?args[1]:"","","");JSONObject actual=live.status();JSONArray items=live.inventory();System.out.println("LIVE READ ONLY: inventory items="+items.length()+"; sensors="+Arrays.toString(Api.sensors(actual))+"; external="+live.externalMode());}
 }
}
