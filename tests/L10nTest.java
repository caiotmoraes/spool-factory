package br.com.caiot.u1spool;
import java.nio.file.*;
import org.json.*;
public class L10nTest {
 static void ok(boolean b,String message){if(!b)throw new AssertionError(message);}
 public static void main(String[] args)throws Exception{
  L10n.load(Files.newInputStream(Path.of("assets/translations.json")));
  ok(L10n.resolve("auto","pt-BR").equals("pt"),"Portuguese system language");ok(L10n.resolve("auto","fr").equals("en"),"English fallback");ok(L10n.resolve("pt","en-US").equals("pt"),"explicit Portuguese override");
  L10n.set("en","pt-BR");ok(L10n.t("Salvar").equals("Save"),"native confirmation button");ok(L10n.t("Associar rolo #30 ao feeder 2").equals("Assign spool #30 to feeder 2"),"native physical confirmation preserves IDs");
  ok(L10n.t("Associado ao feeder 2: #30 · Masterprint PETG · Branco").equals("Assigned to feeder 2: #30 · Masterprint PETG · Branco"),"do not translate catalog label");
  ok(L10n.t("Estoque indisponível: HTTP 404 em /api/v1/spool. Confira o endereço e a porta deste serviço nas configurações.").equals("Inventory unavailable: HTTP 404 at /api/v1/spool. Check this service's address and port in settings."),"nested service error");
  ok(L10n.t("Tag não cadastrada no Spoolman: 04A1B2C3D4E5F6. Use Novo rolo no Armazém para cadastrar.").contains("04A1B2C3D4E5F6"),"NFC UID untouched");ok(L10n.t("Monitoring error from server").equals("Monitoring error from server"),"unknown server message preserved");
  L10n.set("pt","en");ok(L10n.t("Salvar").equals("Salvar"),"Portuguese confirmation");
  JSONObject d=ConnectionConfig.validate(new JSONObject().put("spoolman","spoolman.example:7912").put("timeout",180).put("language","en"));ok(d.getString("language").equals("en"),"language retained by saved settings");
  ok(ConnectionConfig.validate(new JSONObject().put("spoolman","spoolman.example:7912").put("timeout",180)).getString("language").equals("auto"),"existing installation defaults to system language");
  System.out.println("PASS: native confirmations/notifications/errors, automatic PT/EN selection, saved language preference and untouched spool identities.");
 }
}
