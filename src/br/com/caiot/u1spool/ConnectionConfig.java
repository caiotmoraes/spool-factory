package br.com.caiot.u1spool;
import org.json.*;
import java.io.IOException;
final class ConnectionConfig {
 static JSONObject validate(JSONObject raw)throws Exception{
  String printer=raw.optString("printer","").trim(),spoolman=raw.optString("spoolman","").trim();
  if(spoolman.isEmpty())throw new IOException("Informe o endereço do Spoolman, incluindo a porta quando necessária.");
  spoolman=Api.base(spoolman);printer=printer.isEmpty()?"":Api.base(printer);
  int timeout=raw.optInt("timeout",0);if(timeout<60||timeout>600)throw new IOException("O prazo de inserção deve ficar entre 60 e 600 segundos.");
  return new JSONObject().put("printer",printer).put("spoolman",spoolman).put("printerKey",raw.optString("printerKey","").trim()).put("spoolKey",raw.optString("spoolKey","").trim()).put("timeout",timeout);
 }
}
