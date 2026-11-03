package br.com.caiot.u1spool;
import org.json.*;
import java.io.IOException;
import java.util.*;

public class InventoryManagerTest {
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 interface Attempt{void run()throws Exception;}
 static void reject(Attempt a,String msg)throws Exception{try{a.run();throw new AssertionError("Accepted: "+msg);}catch(IOException e){ok(e.getMessage().contains(msg),e.getMessage());}}
 static class Fake extends Api {
  JSONArray rolls=new JSONArray(),vendors=new JSONArray(),filaments=new JSONArray();JSONObject extra=new JSONObject();int writes,postSpools;boolean fields=true,failPost;String lastMethod,lastPath;JSONObject lastBody;
  Fake(){super("http://printer.invalid","http://spoolman.invalid","","secret");}
  @Override Object requestMethod(String base,String path,JSONObject body,String key,int timeout,String method)throws Exception{
   ok(base.equals(spoolman),"inventory must never contact printer");ok("secret".equals(key),"API key sent");
   if(method.equals("GET")){
    if(path.startsWith("/api/v1/spool?"))return rolls;
    if(path.equals("/api/v1/vendor"))return vendors;if(path.equals("/api/v1/filament"))return filaments;
    if(path.equals("/api/v1/field/spool"))return fields?new JSONArray().put(new JSONObject().put("key","card_uids").put("field_type","text")):new JSONArray();
    if(path.startsWith("/api/v1/spool/")){int id=Integer.parseInt(path.substring(path.lastIndexOf('/')+1));for(int i=0;i<rolls.length();i++)if(rolls.getJSONObject(i).getInt("id")==id)return rolls.getJSONObject(i);throw new IOException("Missing spool");}
    if(path.startsWith("/api/v1/filament/"))return filaments.getJSONObject(0);
    throw new IOException("Unexpected GET "+path);
   }
   writes++;lastBody=body==null?null:new JSONObject(body.toString());lastMethod=method;lastPath=path;
   if(path.equals("/api/v1/field/spool/card_uids")){fields=true;return new JSONArray();}
   if(path.equals("/api/v1/vendor")){JSONObject v=new JSONObject(body.toString()).put("id",10);vendors.put(v);return v;}
   if(path.equals("/api/v1/filament")){JSONObject f=new JSONObject(body.toString()).put("id",20).put("vendor",vendors.getJSONObject(0));filaments.put(f);return f;}
   if(path.equals("/api/v1/spool")){postSpools++;if(failPost)throw new IOException("Network failure");JSONObject s=new JSONObject(body.toString()).put("id",30).put("filament",filaments.getJSONObject(0));rolls.put(s);return s;}
   if(method.equals("DELETE")){int id=Integer.parseInt(path.substring(path.lastIndexOf('/')+1));JSONArray kept=new JSONArray();for(int i=0;i<rolls.length();i++)if(rolls.getJSONObject(i).getInt("id")!=id)kept.put(rolls.get(i));rolls=kept;return new JSONObject().put("message","Success!");}
   if(method.equals("PATCH")){JSONObject s=(JSONObject)requestMethod(base,path,null,key,timeout,"GET");Iterator<String> keys=body.keys();while(keys.hasNext()){String k=keys.next();s.put(k,body.get(k));}return s;}
   throw new IOException("Unexpected mutation");
  }
 }
 static JSONObject form()throws Exception{return new JSONObject().put("mode","create").put("uid","04A1B2C3D4E5F6").put("vendor","Example").put("name","Silk trio").put("material","PLA").put("colors","DFA62A,F1681E,DB39AF").put("density",1.24).put("diameter",1.75).put("initial_weight",1000).put("remaining_weight",700).put("location","Drybox");}
 public static void main(String[] args)throws Exception{
  Fake f=new Fake();InventoryManager m=new InventoryManager(f);JSONObject bad=form().put("remaining_weight",1001);reject(()->m.save(bad),"peso restante");ok(f.writes==0,"validate before writes");final JSONObject malformed=form().put("uid","bad!");reject(()->m.save(malformed),"UID");ok(f.writes==0,"invalid UID no writes");
  f.fields=false;JSONObject saved=m.save(form());ok(f.fields,"creates missing text field");ok(saved.getInt("id")==30,"new spool ID");ok(Api.uids(saved).contains("04A1B2C3D4E5F6"),"SpoolLink compatible UID");ok(f.filaments.getJSONObject(0).getString("multi_color_hexes").equals("DFA62A,F1681E,DB39AF"),"multicolor preserved");ok(f.lastBody.getDouble("used_weight")==300,"net weight calculation");int before=f.writes;reject(()->m.save(form()),"já vinculada");ok(f.writes==before,"duplicate tag makes no writes");
  saved.put("extra",new JSONObject().put("card_uids",JSONObject.quote("04A1B2C3D4E5F6,AABBCCDD")).put("custom",JSONObject.quote("keep me")));
  m.save(new JSONObject().put("mode","bind").put("id",30).put("uid","11223344"));ok(Api.uids(saved).size()==3,"append NFC preserves all old tags");ok(saved.getJSONObject("extra").getString("custom").equals(JSONObject.quote("keep me")),"unrelated extra fields preserved");ok("PATCH".equals(f.lastMethod),"patch metadata");
  saved.put("archived",true);reject(()->m.save(form()),"arquivado");reject(()->m.save(new JSONObject().put("mode","bind").put("id",30).put("uid","55667788")),"Desarquive");saved.put("archived",false);
  m.save(new JSONObject().put("mode","update").put("id",30).put("location","Shelf A").put("comment","Measured"));ok(!f.lastBody.has("remaining_weight"),"location-only edit preserves live weight");ok(!f.lastBody.has("extra"),"location-only edit preserves NFC");
  Fake retry=new Fake();retry.failPost=true;InventoryManager retryManager=new InventoryManager(retry);reject(()->retryManager.save(form()),"Network");ok(retry.postSpools==1,"no automatic POST retry");retry.failPost=false;retryManager.save(form());ok(retry.vendors.length()==1&&retry.filaments.length()==1,"partial catalog creation reused on explicit retry");
  JSONObject comma=new JSONObject().put("extra",new JSONObject().put("card_uids",JSONObject.quote("AABBCCDD,11223344")));ok(Api.uids(comma).size()==2,"comma separated tags parsed");
  before=f.writes;reject(()->m.delete(30,"old identity"),"mudou");ok(f.writes==before,"stale confirmation cannot delete");reject(()->m.delete(-1,""),"inválido");m.delete(30,InventoryManager.identity(saved));ok("DELETE".equals(f.lastMethod)&&"/api/v1/spool/30".equals(f.lastPath),"delete only exact spool");ok(f.rolls.length()==0&&f.filaments.length()==1&&f.vendors.length()==1,"preserve catalog after deletion");
  JSONObject config=ConnectionConfig.validate(new JSONObject().put("printer","printer.example").put("spoolman","spoolman.example:7912").put("timeout",180));ok(config.getString("printer").equals("http://printer.example"),"normalize settings");ok(ConnectionConfig.validate(new JSONObject().put("spoolman","spoolman.example:7912").put("timeout",240)).getString("printer").isEmpty(),"warehouse-only settings and custom timeout");reject(()->ConnectionConfig.validate(new JSONObject().put("timeout",180)),"Spoolman");reject(()->ConnectionConfig.validate(new JSONObject().put("spoolman","spoolman.example").put("timeout",601)),"prazo");
  System.out.println("PASS: settings validation, confirmed exact spool DELETE with stale guard and catalog preservation;  Spoolman-only create/catalog, field setup, multicolor, weights, duplicate/archived guards, NFC append with extra preservation, edits, no POST retry and partial catalog recovery.");
 }
}


