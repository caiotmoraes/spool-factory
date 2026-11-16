package br.com.caiot.u1spool;
import java.nio.file.*;
import org.json.*;
public class L10nTest {
 static void ok(boolean b,String message){if(!b)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  L10n.load(Files.newInputStream(Path.of("assets/strings.json")));
  JSONObject spec=new JSONObject(new String(Files.readAllBytes(Path.of("spec/strings.json")),"UTF-8")),app=new JSONObject(new String(Files.readAllBytes(Path.of("assets/strings.json")),"UTF-8"));
  ok(spec.similar(app),"assets/strings.json igual a spec/strings.json");
  for(String k:spec.keySet()){JSONObject e=spec.getJSONObject(k);ok(!e.optString("pt").isEmpty()&&!e.optString("en").isEmpty(),"pt+en: "+k);
   java.util.regex.Matcher a=java.util.regex.Pattern.compile("\\{[a-z]+\\}").matcher(e.getString("pt"));java.util.Set<String> pa=new java.util.TreeSet<>(),pb=new java.util.TreeSet<>();while(a.find())pa.add(a.group());java.util.regex.Matcher b=java.util.regex.Pattern.compile("\\{[a-z]+\\}").matcher(e.getString("en"));while(b.find())pb.add(b.group());ok(pa.equals(pb),"placeholders "+k);}
  ok(L10n.resolve("auto","pt-BR").equals("pt"),"Portuguese system language");ok(L10n.resolve("auto","fr").equals("en"),"English fallback");ok(L10n.resolve("pt","en-US").equals("pt"),"explicit Portuguese override");
  L10n.set("en","pt-BR");ok(L10n.t("common.save").equals("Save"),"keyed English");
  ok(L10n.t("nfc.linked","ch",2,"spool","#30 · Masterprint PETG · Branco").equals("Assigned to T2: #30 · Masterprint PETG · Branco"),"placeholders, catalog label untouched");
  ok(L10n.t("Monitoring error from server").equals("Monitoring error from server"),"unknown text preserved");
  L10n.set("pt","en");ok(L10n.t("nfc.linked","ch",2,"spool","#30").equals("Associado ao T2: #30"),"Portuguese = display text");
  ok(L10n.t("delete.button").equals("Excluir definitivamente"),"same delete button as display");
  JSONObject d=ConnectionConfig.validate(new JSONObject().put("timeout",180).put("language","en"));ok(d.getString("language").equals("en"),"language retained");
  System.out.println("PASS: catálogo compartilhado (spec/strings.json) pt+en, placeholders, seleção automática PT/EN.");
 }
}
