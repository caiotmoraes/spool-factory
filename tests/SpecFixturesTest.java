package br.com.caiot.u1spool;
import java.nio.file.*;
import java.util.*;
import org.json.*;
/** Mesmos casos do firmware (tests/test_spec_fixtures.cpp): spec/fixtures/uids.json. */
public class SpecFixturesTest {
 static void ok(boolean b,String m){if(!b)throw new AssertionError(m);}
 static List<String> list(JSONArray a){List<String> l=new ArrayList<>();for(int i=0;i<a.length();i++)l.add(a.getString(i));return l;}
 static JSONObject spool(JSONObject c){JSONObject s=new JSONObject().put("id",1);if(!c.isNull("extra"))s.put("extra",new JSONObject().put("card_uids",c.get("extra")));if(c.has("tags")){JSONArray t=new JSONArray();for(Object u:c.getJSONArray("tags"))t.put(new JSONObject().put("uid",u));s.put("tags",t);}return s;}
 public static void main(String[] args)throws Exception{
  L10n.load(Files.newInputStream(Path.of("assets/strings.json")));L10n.set("pt","pt");
  JSONObject fx=new JSONObject(new String(Files.readAllBytes(Path.of("spec/fixtures/uids.json")),"UTF-8"));int n=0;
  for(Object o:fx.getJSONArray("card_uids_read")){JSONObject c=(JSONObject)o;JSONObject s=spool(c);
   ok(new ArrayList<>(Api.uids(s)).equals(list(c.getJSONArray("valid"))),"read valid "+c.getString("name")+": "+Api.uids(s));
   ok(new ArrayList<>(Api.invalidUids(s)).equals(list(c.getJSONArray("invalid"))),"read invalid "+c.getString("name"));n++;}
  for(Object o:fx.getJSONArray("card_uids_write")){JSONObject c=(JSONObject)o;Object raw=c.isNull("extra")?null:c.get("extra");
   if(c.has("error")){try{Api.canonical(raw,c.getString("add"));throw new AssertionError("limit "+c.getString("name"));}catch(java.io.IOException e){ok(e.getMessage().equals(L10n.t(c.getString("error"),"n",8)),e.getMessage());}}
   else{String got=Api.canonical(raw,c.getString("add"));ok(got.equals(c.getString("expect")),"write "+c.getString("name")+": "+got);
    if(c.optBoolean("already"))ok(Api.parseCardUids(raw).valid.contains(Api.validUid(c.getString("add"))),"already "+c.getString("name"));}
   n++;}
  for(Object o:fx.getJSONArray("card_uid")){JSONObject c=(JSONObject)o;Object v=c.isNull("value")?null:c.get("value");ok(Api.cardUidHex(v).equals(c.getString("expect")),"CARD_UID "+v);n++;}
  System.out.println("PASS: "+n+" casos de spec/fixtures/uids.json (leitura tolerante, escrita canônica, CARD_UID lista/texto).");
 }
}
