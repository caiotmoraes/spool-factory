package br.com.caiot.u1spool;
import org.json.*;
import java.util.*;
import java.util.regex.*;

/** Last identified insertion is distinct from a live identity supplied by the U1. */
final class AssociationLedger {
    private final JSONObject entries;
    private final boolean[] verified=new boolean[4];
    AssociationLedger(String json)throws Exception{entries=new JSONObject(json);}
    boolean record(int ch,int id,String uid,String source)throws Exception{
        JSONObject old=entries.optJSONObject(""+ch);
        boolean changed=old==null||old.optInt("spool_id")!=id||!old.optString("uid").equals(uid);
        if(changed)entries.put(""+ch,new JSONObject().put("spool_id",id).put("uid",uid).put("source",source).put("confirmed_at",System.currentTimeMillis()));
        verified[ch]=true;return changed;
    }
    void migrate(List<String> history)throws Exception{
        boolean[] seen=new boolean[4];
        Pattern assigned=Pattern.compile("Associado ao feeder ([1-4]): #(\\d+)"),removed=Pattern.compile("Filamento retirado do feeder ([1-4])");
        for(String text:history){Matcher m=assigned.matcher(text),r=removed.matcher(text);
            if(r.find()){seen[Integer.parseInt(r.group(1))-1]=true;continue;}
            if(m.find()){int ch=Integer.parseInt(m.group(1))-1;if(!seen[ch]&&!entries.has(""+ch)){record(ch,Integer.parseInt(m.group(2)),"","histórico");verified[ch]=false;}seen[ch]=true;}
        }
    }
    boolean observe(JSONObject status)throws Exception{
        boolean changed=false;boolean[] present=Api.sensors(status);
        for(int ch=0;ch<4;ch++){
            int id=Api.spoolId(status,ch);String uid=Api.tagUid(status,ch);JSONObject old=entries.optJSONObject(""+ch);
            if(id>0&&present[ch]){changed|=record(ch,id,uid,"U1");continue;}
            if(!present[ch]||(old!=null&&!uid.isEmpty()&&!old.optString("uid").isEmpty()&&!uid.equals(old.optString("uid")))){
                if(entries.has(""+ch)){entries.remove(""+ch);changed=true;}verified[ch]=false;
            }
        }return changed;
    }
    void disconnect(){Arrays.fill(verified,false);}
    String persisted(){return entries.toString();}
    JSONObject snapshot()throws Exception{JSONObject copy=new JSONObject(entries.toString());for(int i=0;i<4;i++)if(copy.has(""+i))copy.getJSONObject(""+i).put("verified",verified[i]);return copy;}
}
