package br.com.caiot.u1spool;
import org.json.*;
import java.nio.file.*;
import java.util.*;
public class AssociationLedgerTest {
 static void ok(boolean b,String text){if(!b)throw new AssertionError(text);}
 public static void main(String[] args)throws Exception{
  JSONObject s=new JSONObject(Files.readString(Path.of("tests/fixtures/status.json")));
  AssociationLedger l=new AssociationLedger("{}");
  Api.channel(s,1).put("filament_detected",true);s.getJSONObject("print_task_config").getJSONArray("filament_spool_id").put(1,0);
  l.record(1,31,"53AABBCCDD0001","NFC");l.observe(s);
  ok(l.snapshot().getJSONObject("1").getInt("spool_id")==31,"native empty data must not erase same insertion");
  ok(l.snapshot().getJSONObject("1").getBoolean("verified"),"continuous insertion remains confirmed");
  s.getJSONObject("print_stats").put("state","printing");l.observe(s);ok(l.snapshot().getJSONObject("1").getBoolean("verified"),"printing does not discard identity");
  AssociationLedger restored=new AssociationLedger(l.persisted());ok(!restored.snapshot().getJSONObject("1").getBoolean("verified"),"restart is explicitly unverified");
  l.disconnect();ok(!l.snapshot().getJSONObject("1").getBoolean("verified"),"connection loss invalidates continuity");
  Api.channel(s,1).put("filament_detected",false);l.observe(s);ok(!l.snapshot().has("1"),"remove invalidates cached identity");
  Api.channel(s,1).put("filament_detected",true);l.observe(s);ok(!l.snapshot().has("1"),"reinsertion cannot reuse removed identity");
  l.record(1,31,"AABBCCDD","NFC");s.getJSONObject("filament_detect").getJSONArray("info").getJSONObject(1).put("CARD_UID",new JSONArray().put(1).put(2).put(3).put(4));l.observe(s);ok(!l.snapshot().has("1"),"new tag invalidates old identity");
  s.getJSONObject("print_task_config").getJSONArray("filament_spool_id").put(1,17);l.observe(s);ok(l.snapshot().getJSONObject("1").getInt("spool_id")==17,"live U1 spool wins over history");
  AssociationLedger migrated=new AssociationLedger("{}");migrated.migrate(Arrays.asList("Filamento retirado do feeder 3.","Associado ao feeder 2: #31 · Volt PLA","Associado ao feeder 3: #17 · PETG"));
  ok(migrated.snapshot().getJSONObject("1").getInt("spool_id")==31,"recover confirmed association from old version history");ok(!migrated.snapshot().getJSONObject("1").getBoolean("verified"),"history never claims current physical identity");ok(!migrated.snapshot().has("2"),"newer removal wins in migration");
  System.out.println("PASS: missing metadata during printing, continuity, restart, disconnect, removal, reinsertion, conflicting tag, live authority, history migration.");
 }
}
