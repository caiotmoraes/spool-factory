package br.com.caiot.u1spool;

import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;

class Api {
    final String printer, spoolman, printerKey, spoolKey;
    Api(String p,String s,String pk,String sk) { printer=base(p); spoolman=base(s); printerKey=pk; spoolKey=sk; }
    static String base(String s) {
        s=s.trim().replaceAll("/+$", "");
        if(s.isEmpty())throw new IllegalArgumentException("Informe o IP ou endereço da U1 e do Spoolman nas configurações.");
        if(!s.contains("://"))s="http://"+s;
        URI u=URI.create(s);
        if (!("http".equals(u.getScheme())||"https".equals(u.getScheme())) || u.getHost()==null || u.getUserInfo()!=null || u.getQuery()!=null || u.getFragment()!=null)
            throw new IllegalArgumentException("Endereço inválido. Use http://IP:porta");
        return s;
    }
    Object request(String base,String path,JSONObject body,String key) throws Exception {
        return request(base,path,body,key,5000);
    }
    Object request(String base,String path,JSONObject body,String key,int timeout) throws Exception {
        return requestMethod(base,path,body,key,timeout,body==null?"GET":"POST");
    }
    Object requestMethod(String base,String path,JSONObject body,String key,int timeout,String method) throws Exception {
        HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection();
        c.setConnectTimeout(4000); c.setReadTimeout(timeout); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept","application/json");
        if(!key.isEmpty())c.setRequestProperty("X-Api-Key",key);
        try {
            c.setRequestMethod(method);
            if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            int code=c.getResponseCode();
            if(code<200||code>=300)throw new IOException("HTTP "+code+" em "+path.split("\\?")[0]+(code==404?". Confira o endereço e a porta deste serviço nas configurações.":code==401||code==403?". Confira a chave API e a autorização.":". Verifique o serviço e tente atualizar."));
            if(code==204)return new JSONObject();
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>8_000_000)throw new IOException("Resposta muito grande");}}
            Object v=new JSONTokener(out.toString("UTF-8")).nextValue();
            if(v instanceof JSONObject && ((JSONObject)v).has("error"))throw new IOException(((JSONObject)v).get("error").toString());
            return v;
        } finally {c.disconnect();}
    }
    JSONObject status() throws Exception {
        JSONObject r=(JSONObject)request(printer,"/printer/objects/query?filament_feed%20left&filament_feed%20right&filament_detect&print_task_config&print_stats&webhooks",null,printerKey);
        JSONObject s=r.getJSONObject("result").getJSONObject("status");
        if(!"ready".equals(s.getJSONObject("webhooks").optString("state")))throw new IOException("A impressora não está pronta");
        sensors(s); return s;
    }
    static JSONObject channel(JSONObject s,int i) throws Exception {return s.getJSONObject(i<2?"filament_feed left":"filament_feed right").getJSONObject("extruder"+i);}
    static boolean[] sensors(JSONObject s) throws Exception {
        boolean[] b=new boolean[4];for(int i=0;i<4;i++) {JSONObject c=channel(s,i);if(!c.optBoolean("module_exist")||!c.has("filament_detected"))throw new IOException("Feeder "+(i+1)+" indisponível");b[i]=c.getBoolean("filament_detected");}return b;
    }
    static boolean printing(JSONObject s) throws Exception {String v=s.getJSONObject("print_stats").getString("state");return "printing".equals(v)||"paused".equals(v);}
    boolean externalMode()throws Exception{
        JSONObject groups=(JSONObject)request(printer,"/firmware-config/api/settings",null,printerKey);
        Iterator<String> keys=groups.keys();while(keys.hasNext()){
            JSONObject group=groups.optJSONObject(keys.next());if(group==null)continue;JSONArray items=group.optJSONArray("items");if(items==null)continue;
            for(int i=0;i<items.length();i++){JSONObject item=items.optJSONObject(i);if(item!=null&&"rfid".equals(item.optString("id")))return "external".equals(item.optString("current"));}
        }throw new IOException("Não foi possível verificar Filament Detection. Confirme o modo External no Firmware Config.");
    }
    void requireExternal()throws Exception{if(!externalMode())throw new IOException("Selecione Filament Detection = External no Firmware Config antes de associar ou carregar rolos. Leitores internos podem apagar os dados.");}
    static String normalize(String s){return s.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);}
    static Set<String> uids(JSONObject spool) throws Exception {
        Set<String> set=new LinkedHashSet<>();JSONArray tags=spool.optJSONArray("tags");if(tags!=null)for(int i=0;i<tags.length();i++){JSONObject t=tags.optJSONObject(i);if(t!=null)set.add(normalize(t.optString("uid")));}JSONObject ex=spool.optJSONObject("extra");if(ex==null)return set;
        Object raw=ex.opt("card_uids");
        if(raw instanceof String){String r=((String)raw).trim();try{raw=new JSONTokener(r).nextValue();}catch(Exception ignored){raw=r;}}
        if(raw instanceof JSONArray){JSONArray a=(JSONArray)raw;for(int i=0;i<a.length();i++)set.add(normalize(a.getString(i)));}
        else if(raw instanceof String)for(String item:((String)raw).split("[,;\\s]+"))if(!item.isEmpty())set.add(normalize(item));
        set.remove("");
        return set;
    }
    JSONObject lookup(String uid) throws Exception {
        JSONArray all=inventory();
        JSONObject found=null;
        for(int i=0;i<all.length();i++){JSONObject s=all.getJSONObject(i);if(uids(s).contains(uid)){if(found!=null)throw new IOException("UID duplicado no Spoolman. Corrija os cadastros antes de associar.");found=s;}}
        if(found==null)throw new IOException("Tag não cadastrada no Spoolman: "+uid+". Use Novo rolo no Armazém para cadastrar.");
        if(found.optBoolean("archived"))throw new IOException("Tag já cadastrada: "+label(found)+". Este rolo está arquivado no Spoolman.");
        return found;
    }
    JSONArray inventory() throws Exception {return (JSONArray)request(spoolman,"/api/v1/spool?allow_archived=true",null,spoolKey);}
    JSONObject spool(int id)throws Exception{
        JSONObject s=(JSONObject)request(spoolman,"/api/v1/spool/"+id,null,spoolKey);
        if(s.getInt("id")!=id||s.optBoolean("archived"))throw new IOException("Rolo inválido ou arquivado.");return s;
    }
    static void idleForManual(JSONObject s)throws Exception{
        if(printing(s))throw new IOException("Troca manual indisponível durante impressão ou pausa.");
        for(int i=0;i<4;i++){
            JSONObject c=channel(s,i);String phase=c.optString("channel_state");
            if(!"ok".equals(c.optString("channel_error")))throw new IOException("Resolva o erro do feeder "+(i+1)+" na U1 antes da troca.");
            if(!Arrays.asList("wait_insert","preload_finish","load_finish","unload_finish","none","inited").contains(phase))throw new IOException("Aguarde a operação atual do feeder "+(i+1)+" terminar.");
        }
    }
    static JSONObject spoolInfo(JSONObject spool)throws Exception{
        JSONObject f=spool.getJSONObject("filament"),vendor=f.optJSONObject("vendor");
        String material=f.optString("material");if(material.isEmpty())throw new IOException("Rolo sem material cadastrado.");
        String multi=f.optString("multi_color_hexes","");String[] raw=multi.isEmpty()?new String[]{f.optString("color_hex","FFFFFF")}:multi.split(",");
        JSONObject info=new JSONObject().put("VENDOR",vendor==null?"Generic":vendor.optString("name","Generic")).put("MAIN_TYPE",material).put("SPOOL_ID",spool.getInt("id")).put("OFFICIAL",true).put("ALPHA",255).put("CARD_UID",new JSONArray());
        int count=0;for(String color:raw){color=color.trim().replace("#","");if(!color.matches("(?i)[0-9a-f]{6}"))throw new IOException("Cor inválida no cadastro do rolo.");if(count<5)info.put("RGB_"+(++count),Integer.parseInt(color,16));}
        if(count==0)throw new IOException("Rolo sem cor cadastrada.");
        info.put("COLOR_NUMS",count).put("ARGB_COLOR",(255L<<24)|info.getInt("RGB_1"));
        return info;
    }
    void assign(int ch,JSONObject selected)throws Exception{
        if(ch<0||ch>3)throw new IOException("Feeder inválido.");
        requireExternal();
        JSONObject fresh=status();idleForManual(fresh);
        if(!sensors(fresh)[ch])throw new IOException("Insira o filamento no feeder "+(ch+1)+" antes de associar o rolo.");
        for(int i=0;i<4;i++)if(i!=ch&&sensors(fresh)[i]&&spoolId(fresh,i)==selected.getInt("id"))throw new IOException("Este rolo já está associado ao feeder "+(i+1)+".");
        request(printer,"/printer/spoollink/set",new JSONObject().put("channel",ch).put("info",spoolInfo(selected)).put("message","Spool Factory: associação manual"),printerKey);
    }
    static String feederScript(JSONObject fresh,int ch,boolean load)throws Exception{
        if(ch<0||ch>3)throw new IOException("Feeder inválido.");idleForManual(fresh);
        if(!sensors(fresh)[ch])throw new IOException("Insira o filamento no feeder "+(ch+1)+" antes de carregar ou retirar.");
        if(load){String material=fresh.getJSONObject("print_task_config").getJSONArray("filament_type").optString(ch,"");if(material.isEmpty()||"NONE".equals(material))throw new IOException("Identifique o material antes de carregar.");if(channel(fresh,ch).optBoolean("disable_auto"))throw new IOException("Alimentação automática desativada neste feeder.");}
        return "AUTO_FEEDING EXTRUDER="+ch+(load?" LOAD=1":" UNLOAD=1");
    }
    void feed(int ch,boolean load)throws Exception{
        requireExternal();
        String script=feederScript(status(),ch,load);
        request(printer,"/printer/gcode/script",new JSONObject().put("script",script),printerKey,240000);
    }
    static String tagUid(JSONObject s,int ch) throws Exception {
        Object raw=s.getJSONObject("filament_detect").getJSONArray("info").getJSONObject(ch).opt("CARD_UID");
        if(!(raw instanceof JSONArray))return "";
        JSONArray a=(JSONArray)raw;StringBuilder b=new StringBuilder();boolean any=false;
        for(int i=0;i<a.length();i++){int n=a.getInt(i);any|=n!=0;b.append(String.format(Locale.ROOT,"%02X",n));}return any?b.toString():"";
    }
    static int spoolId(JSONObject s,int ch)throws Exception{return s.getJSONObject("print_task_config").getJSONArray("filament_spool_id").getInt(ch);}
    void inject(int ch,String uid,String type) throws Exception {
        requireExternal();
        JSONArray bytes=new JSONArray();for(int i=0;i<uid.length();i+=2)bytes.put(Integer.parseInt(uid.substring(i,i+2),16));
        JSONObject info=new JSONObject().put("CARD_UID",bytes).put("CARD_TYPE",type);
        JSONObject r=(JSONObject)request(printer,"/printer/filament_detect/set",new JSONObject().put("channel",ch).put("info",info),printerKey);
        JSONObject result=r.optJSONObject("result");if(result==null)result=r;
        if("error".equals(result.optString("state")))throw new IOException(result.optString("message","Falha na leitura externa"));
    }
    static String label(JSONObject spool)throws Exception {
        JSONObject f=spool.getJSONObject("filament");JSONObject v=f.optJSONObject("vendor");
        return "#"+spool.getInt("id")+" · "+(v==null?"":v.optString("name")+" ")+f.optString("material")+" · "+f.optString("name");
    }
}
