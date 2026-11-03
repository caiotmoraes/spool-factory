package br.com.caiot.u1spool;
import org.json.*;
import java.io.*;
import java.util.*;
import java.util.regex.*;

/** Presentation-only translation. Protocol values and catalog data remain unchanged. */
final class L10n {
 private static volatile boolean english;
 private static JSONObject exact=new JSONObject();
 private static final List<Rule> rules=new ArrayList<>();
 private static final class Rule {Pattern pattern;String target;List<String> names;Rule(Pattern p,String t,List<String> n){pattern=p;target=t;names=n;}}
 static synchronized void load(InputStream input)throws Exception{
  ByteArrayOutputStream bytes=new ByteArrayOutputStream();try(InputStream in=input){byte[] buffer=new byte[4096];int n;while((n=in.read(buffer))!=-1)bytes.write(buffer,0,n);}
  exact=new JSONObject(bytes.toString("UTF-8")).getJSONObject("messages");rules.clear();Iterator<String> keys=exact.keys();Pattern tokens=Pattern.compile("\\{([a-z]+)\\}");
  // JSONObject key iteration has no ordering contract. Specific rules precede broad error wrappers.
  List<String> ordered=new ArrayList<>();while(keys.hasNext())ordered.add(keys.next());ordered.sort((a,b)->Integer.compare(b.replaceAll("\\{[a-z]+\\}","").length(),a.replaceAll("\\{[a-z]+\\}","").length()));
  for(String source:ordered){Matcher m=tokens.matcher(source);StringBuilder regex=new StringBuilder("^");List<String> names=new ArrayList<>();int end=0;while(m.find()){regex.append(Pattern.quote(source.substring(end,m.start()))).append("([\\s\\S]+?)");names.add(m.group(1));end=m.end();}if(names.isEmpty())continue;regex.append(Pattern.quote(source.substring(end))).append('$');rules.add(new Rule(Pattern.compile(regex.toString()),exact.getString(source),names));}
 }
 static String resolve(String preference,String system){if("pt".equals(preference)||"en".equals(preference))return preference;return system!=null&&system.toLowerCase(Locale.ROOT).matches("pt(?:[-_].*)?")?"pt":"en";}
 static void set(String preference,String system){english="en".equals(resolve(preference,system));}
 static synchronized String t(String text){return translate(text,0);}
 private static String translate(String value,int depth){
  if(value==null)return "";if(!english||depth>6)return value;String found=exact.optString(value,"");if(!found.isEmpty())return found;
  for(Rule rule:rules){Matcher match=rule.pattern.matcher(value);if(!match.matches())continue;Matcher placeholders=Pattern.compile("\\{([a-z]+)\\}").matcher(rule.target);StringBuffer out=new StringBuffer();while(placeholders.find()){String key=placeholders.group(1),capture=match.group(rule.names.indexOf(key)+1);placeholders.appendReplacement(out,Matcher.quoteReplacement("error".equals(key)?translate(capture,depth+1):capture));}placeholders.appendTail(out);return out.toString();}return value;
 }
}
