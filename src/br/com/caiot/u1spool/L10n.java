package br.com.caiot.u1spool;
import org.json.*;
import java.io.*;
import java.util.*;

/**
 * Catálogo de textos por chave (U1 Spool Spec v1: spec/strings.json, copiado para
 * assets/strings.json no build). pt-BR e en; placeholders {nome}.
 * Texto sem chave conhecida (ex.: erro cru do Spoolman) passa sem alteração.
 */
final class L10n {
 private static volatile boolean english;
 private static JSONObject catalog=new JSONObject();
 static synchronized void load(InputStream input)throws Exception{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=input){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);}
  catalog=new JSONObject(bytes.toString("UTF-8"));
 }
 static String resolve(String preference,String system){if("pt".equals(preference)||"en".equals(preference))return preference;return system!=null&&system.toLowerCase(Locale.ROOT).matches("pt(?:[-_].*)?")?"pt":"en";}
 static void set(String preference,String system){english="en".equals(resolve(preference,system));}
 static boolean english(){return english;}
 static boolean has(String key){return catalog.has(key);}
 /** t("nfc.linked","ch",2,"spool","#38 · …") */
 static String t(String key,Object... args){
  if(key==null)return "";
  JSONObject entry=catalog.optJSONObject(key);
  String text=entry==null?key:entry.optString(english?"en":"pt",entry.optString("pt",key));
  for(int i=0;i+1<args.length;i+=2)text=text.replace("{"+args[i]+"}",String.valueOf(args[i+1]));
  return text;
 }
}
