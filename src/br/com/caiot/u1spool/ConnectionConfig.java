package br.com.caiot.u1spool;
import org.json.*;
import java.io.IOException;
/** Ajustes: U1 opcional (vazio = descoberta automática) e Spoolman direto opcional (vazio = proxy da U1). */
final class ConnectionConfig {
 static JSONObject validate(JSONObject raw)throws Exception{
  String printer=raw.optString("printer","").trim(),spoolman=raw.optString("spoolman","").trim();
  if(!printer.isEmpty()){String b=Api.base(printer);printer=b.replaceFirst("^http://","");}
  if(!spoolman.isEmpty())spoolman=Api.base(spoolman);
  int timeout=raw.optInt("timeout",180);if(timeout<60||timeout>600)throw new IOException(L10n.t("set.deadline_range"));
  String language=raw.optString("language","auto");if(!java.util.Arrays.asList("auto","pt","en").contains(language))throw new IOException("Invalid language selection.");
  return new JSONObject().put("language",language).put("printer",printer).put("spoolman",spoolman).put("printerKey",raw.optString("printerKey","").trim()).put("spoolKey",raw.optString("spoolKey","").trim()).put("timeout",timeout);
 }
}
