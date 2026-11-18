package br.com.caiot.u1spool;

import org.json.*;
import java.net.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;

/**
 * Cliente da U1 (Moonraker :7125, firmware-config :80) e do Spoolman.
 * Spoolman vai, por padrão, pelo proxy da U1 (POST /server/spoolman/proxy, use_v2_response);
 * um endereço direto do Spoolman é opcional. Regras de UID: U1 Spool Spec v1 (spec/README.md).
 */
class Api {
    static final int MOONRAKER_PORT=7125, UIDS_MAX=8, FEED_TIMEOUT_MS=240000;
    static final String[] FANS={"fan","exhaust_fan","circulation_fan","cavity_fan"};
    static final String[] HEATERS={"extruder","extruder1","extruder2","extruder3","heater_bed"};
    final String printer, web, spoolman, printerKey, spoolKey;
    private volatile String chamber;
    /** host = IP/endereço da U1 (vazio só se Spoolman direto); spoolmanDirect vazio = proxy. */
    Api(String host,String spoolmanDirect,String pk,String sk){
        String h=host==null?"":host.trim();
        if(h.isEmpty()){printer="";web="";}
        else{printer=moonraker(h);web=webBase(h);}
        spoolman=spoolmanDirect==null||spoolmanDirect.trim().isEmpty()?"":base(spoolmanDirect);
        if(printer.isEmpty()&&spoolman.isEmpty())throw new IllegalArgumentException(L10n.t("conn.u1_not_found"));
        printerKey=pk==null?"":pk;spoolKey=sk==null?"":sk;
    }
    boolean viaProxy(){return spoolman.isEmpty();}
    static String base(String s) {
        s=s.trim().replaceAll("/+$", "");
        if(s.isEmpty())throw new IllegalArgumentException("Informe o IP ou endereço.");
        if(!s.contains("://"))s="http://"+s;
        URI u=URI.create(s);
        if (!("http".equals(u.getScheme())||"https".equals(u.getScheme())) || u.getHost()==null || u.getUserInfo()!=null || u.getQuery()!=null || u.getFragment()!=null)
            throw new IllegalArgumentException("Endereço inválido. Use http://IP:porta");
        return s;
    }
    /** Moonraker: porta explícita é respeitada; sem porta, :7125 (como o display). */
    static String moonraker(String host){URI u=URI.create(base(host));int port=u.getPort();return u.getScheme()+"://"+u.getHost()+":"+(port>0?port:MOONRAKER_PORT)+(u.getPath()==null?"":u.getPath());}
    /** firmware-config fica no nginx (:80); em :7125 responde 404. */
    static String webBase(String host){URI u=URI.create(base(host));int port=u.getPort();return u.getScheme()+"://"+u.getHost()+(port>0&&port!=MOONRAKER_PORT?":"+port:"");}
    static String hostOf(String url){try{return URI.create(base(url)).getHost();}catch(Exception e){return "";}}

    Object request(String base,String path,JSONObject body,String key) throws Exception {return request(base,path,body,key,5000);}
    Object request(String base,String path,JSONObject body,String key,int timeout) throws Exception {return requestMethod(base,path,body,key,timeout,body==null?"GET":"POST");}
    Object requestMethod(String base,String path,JSONObject body,String key,int timeout,String method) throws Exception {
        if(base.isEmpty())throw new IOException(L10n.t("conn.u1_not_found"));
        HttpURLConnection c=(HttpURLConnection)new URL(base+path).openConnection();
        c.setConnectTimeout(4000); c.setReadTimeout(timeout); c.setInstanceFollowRedirects(false);
        c.setRequestProperty("Accept","application/json");
        if(!key.isEmpty())c.setRequestProperty("X-Api-Key",key);
        try {
            c.setRequestMethod(method);
            if(body!=null){c.setDoOutput(true);c.setRequestProperty("Content-Type","application/json; charset=utf-8");try(OutputStream o=c.getOutputStream()){o.write(body.toString().getBytes(StandardCharsets.UTF_8));}}
            else if("POST".equals(method)){c.setDoOutput(true);c.setFixedLengthStreamingMode(0);try(OutputStream o=c.getOutputStream()){o.flush();}}
            int code=c.getResponseCode();
            if(code<200||code>=300){String detail="";try(InputStream e=c.getErrorStream()){if(e!=null){String t=readAll(e);JSONObject j=new JSONObject(t);JSONObject err=j.optJSONObject("error");detail=err!=null?err.optString("message"):j.optString("message",j.optString("detail"));}}catch(Exception ignored){}
                throw new IOException("HTTP "+code+" em "+path.split("\\?")[0]+(detail.isEmpty()?"":": "+detail));}
            if(code==204)return new JSONObject();
            ByteArrayOutputStream out=new ByteArrayOutputStream();
            try(InputStream in=c.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>8_000_000)throw new IOException("Resposta muito grande");}}
            String text=out.toString("UTF-8");if(text.trim().isEmpty())return new JSONObject();
            Object v=new JSONTokener(text).nextValue();
            if(v instanceof JSONObject && ((JSONObject)v).has("error")&&!((JSONObject)v).isNull("error"))throw new IOException(errorText(((JSONObject)v).get("error")));
            return v;
        } finally {c.disconnect();}
    }
    /** InputStream.readAllBytes só existe no Android 13+; minSdk é 26. */
    static String readAll(InputStream in)throws IOException{ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){out.write(b,0,n);if(out.size()>8_000_000)throw new IOException("Resposta muito grande");}return out.toString("UTF-8");}
    static String errorText(Object e){if(e instanceof JSONObject){JSONObject o=(JSONObject)e;return o.optString("message",o.optString("error",o.toString()));}return String.valueOf(e);}

    // ---------- Spoolman (proxy por padrão) ----------
    /** path no formato do Spoolman sem /api, ex.: "/v1/spool?allow_archived=true". */
    Object spoolman(String method,String path,JSONObject body,int timeout)throws Exception{
        if(!spoolman.isEmpty())return requestMethod(spoolman,"/api"+path,body,spoolKey,timeout,method);
        JSONObject req=new JSONObject().put("request_method",method).put("path",path).put("use_v2_response",true);
        if(body!=null)req.put("body",body);
        Object r=request(printer,"/server/spoolman/proxy",req,printerKey,timeout);
        return unwrapProxy(r);
    }
    static Object unwrapProxy(Object r)throws Exception{
        if(!(r instanceof JSONObject))return r;
        JSONObject o=(JSONObject)r;JSONObject root=o.optJSONObject("result");if(root==null)root=o;
        if(root.has("error")&&!root.isNull("error"))throw new IOException(errorText(root.get("error")));
        if(root.has("response"))return root.isNull("response")?new JSONObject():root.get("response");
        return o.has("result")?o.get("result"):o;
    }
    Object spoolmanGet(String path)throws Exception{return spoolman("GET",path,null,8000);}
    JSONArray inventory() throws Exception {return (JSONArray)spoolman("GET","/v1/spool?allow_archived=true",null,10000);}
    JSONObject spoolAny(int id)throws Exception{JSONObject s=(JSONObject)spoolmanGet("/v1/spool/"+id);if(s.optInt("id")!=id)throw new IOException("Rolo #"+id+" não encontrado.");return s;}
    JSONObject spool(int id)throws Exception{JSONObject s=spoolAny(id);if(s.optBoolean("archived"))throw new IOException("Rolo inválido ou arquivado.");return s;}

    // ---------- U1 ----------
    JSONObject status() throws Exception {
        if(chamber==null)probeChamber();
        String q="/printer/objects/query?webhooks&print_stats&virtual_sdcard&display_status&idle_timeout&extruder&extruder1&extruder2&extruder3&heater_bed"
            +"&filament_feed%20left&filament_feed%20right&filament_detect&print_task_config&toolhead=homed_axes"
            +"&fan&fan_generic%20exhaust_fan&fan_generic%20circulation_fan&fan_generic%20cavity_fan"+(chamber.isEmpty()?"":"&"+chamber.replace(" ","%20"));
        JSONObject r=(JSONObject)request(printer,q,null,printerKey,4000);
        JSONObject s=r.getJSONObject("result").getJSONObject("status");
        String klippy=s.getJSONObject("webhooks").optString("state");
        if(!"ready".equals(klippy))throw new NotReady(klippy);
        if(!chamber.isEmpty()&&s.has(chamber))s.put("chamber",s.get(chamber));
        sensors(s); return s;
    }
    /** Igual ao display: primeiro temperature_sensor/heater_generic/temperature_fan com chamber/cavity/… */
    private void probeChamber(){
        String found="";
        try{JSONArray objs=((JSONObject)request(printer,"/printer/objects/list",null,printerKey,3000)).getJSONObject("result").getJSONArray("objects");
            outer:for(int i=0;i<objs.length();i++){String n=objs.optString(i);
                if(!(n.startsWith("temperature_sensor ")||n.startsWith("heater_generic ")||n.startsWith("temperature_fan ")))continue;
                String l=n.toLowerCase(Locale.ROOT);for(String h:new String[]{"chamber","cavity","enclosure","camara","box"})if(l.contains(h)){found=n;break outer;}}
            chamber=found;
        }catch(Exception e){chamber=null;}
        if(chamber==null)chamber="";
    }
    static JSONObject channel(JSONObject s,int i) throws Exception {return s.getJSONObject(i<2?"filament_feed left":"filament_feed right").getJSONObject("extruder"+i);}
    static boolean[] sensors(JSONObject s) throws Exception {
        boolean[] b=new boolean[4];for(int i=0;i<4;i++) {JSONObject c=channel(s,i);if(!c.optBoolean("module_exist")||!c.has("filament_detected"))throw new IOException(L10n.t("feeder.unavailable","ch",i+1));b[i]=c.getBoolean("filament_detected");}return b;
    }
    static String printState(JSONObject s){JSONObject p=s.optJSONObject("print_stats");return p==null?"":p.optString("state");}
    static boolean printing(JSONObject s) {String v=printState(s);return "printing".equals(v)||"paused".equals(v);}
    /** Igual ao display: temperaturas só com a U1 ociosa (job parado e idle_timeout Idle/Ready). */
    static boolean idleForTemps(JSONObject s){
        String job=printState(s);JSONObject it=s.optJSONObject("idle_timeout");String idle=it==null?"":it.optString("state");
        boolean jobIdle=Arrays.asList("standby","complete","cancelled","error","").contains(job);
        return jobIdle&&("Idle".equals(idle)||"Ready".equals(idle));
    }
    boolean externalMode()throws Exception{
        Object raw=request(web,"/firmware-config/api/settings",null,printerKey);
        Boolean v=rfidExternal(raw);if(v==null)throw new IOException(L10n.t("conn.external_unknown"));return v;
    }
    static Boolean rfidExternal(Object raw){
        if(raw instanceof JSONObject){JSONObject o=(JSONObject)raw;
            if("rfid".equals(o.optString("id"))&&o.has("current"))return "external".equals(o.optString("current"));
            for(Iterator<String> k=o.keys();k.hasNext();){Boolean r=rfidExternal(o.opt(k.next()));if(r!=null)return r;}}
        else if(raw instanceof JSONArray){JSONArray a=(JSONArray)raw;for(int i=0;i<a.length();i++){Boolean r=rfidExternal(a.opt(i));if(r!=null)return r;}}
        return null;
    }
    void requireExternal()throws Exception{if(!externalMode())throw new IOException(L10n.t("conn.external_required"));}

    // ---------- UIDs (U1 Spool Spec v1) ----------
    static String normalize(String s){return s.replaceAll("[^0-9A-Fa-f]", "").toUpperCase(Locale.ROOT);}
    /** Valida um UID digitado/lido: só hex, ':', '-', espaço; 4, 7 ou 10 bytes. */
    static String validUid(String raw)throws IOException{
        if(raw==null||raw.trim().isEmpty())throw new IOException(L10n.t("uid.empty"));
        if(!raw.matches("[0-9a-fA-F: -]+"))throw new IOException(L10n.t("uid.invalid"));
        String v=normalize(raw);if(!(v.length()==8||v.length()==14||v.length()==20))throw new IOException(L10n.t("uid.length"));return v;
    }
    static final class UidList{final LinkedHashSet<String> valid=new LinkedHashSet<>();final LinkedHashSet<String> invalid=new LinkedHashSet<>();}
    private static boolean sep(char c){return c==','||c==';'||c==' '||c=='\t'||c=='\r'||c=='\n'||c=='['||c==']'||c=='"'||c=='\'';}
    private static void token(UidList a,String t){
        if(t.isEmpty())return;
        boolean chars=t.matches("[0-9A-Fa-f:-]+");String n=normalize(t);
        if(chars&&(n.length()==8||n.length()==14||n.length()==20))a.valid.add(n);else a.invalid.add(t);
    }
    private static void split(UidList a,String s){int i=0;while(i<s.length()){while(i<s.length()&&sep(s.charAt(i)))i++;int e=i;while(e<s.length()&&!sep(s.charAt(e)))e++;token(a,s.substring(i,e));i=e;}}
    private static void value(UidList a,Object raw,int depth){
        if(raw==null||raw==JSONObject.NULL)return;
        if(raw instanceof JSONArray){JSONArray arr=(JSONArray)raw;for(int i=0;i<arr.length();i++)if(arr.opt(i) instanceof String)split(a,arr.optString(i));return;}
        String s=String.valueOf(raw).trim();if(s.isEmpty())return;
        if(depth<2&&(s.startsWith("\"")||s.startsWith("["))){
            try{JSONTokener t=new JSONTokener(s);Object v=t.nextValue();if(t.nextClean()==0){
                if(v instanceof JSONArray){value(a,v,depth+1);return;}
                if(v instanceof String){value(a,v,depth+1);return;}}}catch(Exception ignored){}
        }
        split(a,s);
    }
    /** Leitura tolerante de extra.card_uids (sem tags[]). */
    static UidList parseCardUids(Object raw){UidList a=new UidList();value(a,raw,0);return a;}
    static Object rawCardUids(JSONObject spool){JSONObject ex=spool.optJSONObject("extra");return ex==null?null:ex.opt("card_uids");}
    /** UIDs válidos do rolo: card_uids + tags[].uid (só em memória; tags nunca são gravadas em card_uids). */
    static Set<String> uids(JSONObject spool){
        UidList a=parseCardUids(rawCardUids(spool));
        JSONArray tags=spool.optJSONArray("tags");
        if(tags!=null)for(int i=0;i<tags.length();i++){Object t=tags.opt(i);String u=t instanceof JSONObject?((JSONObject)t).optString("uid"):t instanceof String?(String)t:"";UidList one=new UidList();split(one,u);a.valid.addAll(one.valid);}
        return a.valid;
    }
    static Set<String> invalidUids(JSONObject spool){return parseCardUids(rawCardUids(spool)).invalid;}
    /** Escrita canônica: texto JSON "A,B" (maiúsculas, válidos primeiro, inválidos crus no fim). */
    static String canonical(Object raw,String add)throws IOException{
        String u=validUid(add);UidList a=parseCardUids(raw);
        if(!a.valid.contains(u)){if(a.valid.size()>=UIDS_MAX)throw new IOException(L10n.t("uid.too_many","n",UIDS_MAX));a.valid.add(u);}
        List<String> all=new ArrayList<>(a.valid);all.addAll(a.invalid);
        return JSONObject.quote(String.join(",",all));
    }
    /** CARD_UID da U1: lista 0..255 ou texto decimal; 4/7/10 bytes; tudo zero = sem tag. */
    static String cardUidHex(Object raw){
        List<Integer> bytes=new ArrayList<>();
        if(raw instanceof JSONArray){JSONArray a=(JSONArray)raw;for(int i=0;i<a.length();i++){Object v=a.opt(i);if(!(v instanceof Integer||v instanceof Long))return "";long b=((Number)v).longValue();if(b<0||b>255)return "";bytes.add((int)b);}}
        else if(raw instanceof String){String s=((String)raw).trim();if(s.isEmpty())return "";for(String p:s.split("[ ,\\t]+")){if(p.isEmpty())continue;if(!p.matches("\\d{1,3}"))return "";int b=Integer.parseInt(p);if(b>255)return "";bytes.add(b);}}
        else return "";
        int n=bytes.size();if(n!=4&&n!=7&&n!=10)return "";
        boolean any=false;StringBuilder b=new StringBuilder();for(int v:bytes){any|=v!=0;b.append(String.format(Locale.ROOT,"%02X",v));}
        return any?b.toString():"";
    }
    static String tagUid(JSONObject s,int ch) {
        try{return cardUidHex(s.getJSONObject("filament_detect").getJSONArray("info").getJSONObject(ch).opt("CARD_UID"));}catch(Exception e){return "";}
    }
    static int spoolId(JSONObject s,int ch){try{return s.getJSONObject("print_task_config").getJSONArray("filament_spool_id").optInt(ch,0);}catch(Exception e){return 0;}}

    JSONObject lookup(String uid) throws Exception {
        JSONArray all=inventory();
        JSONObject found=null;
        for(int i=0;i<all.length();i++){JSONObject s=all.getJSONObject(i);if(uids(s).contains(uid)){if(found!=null)throw new IOException(L10n.t("uid.duplicate"));found=s;}}
        if(found==null)throw new UnknownTag(uid);
        if(found.optBoolean("archived"))throw new IOException(L10n.t("nfc.archived","spool",label(found)));
        return found;
    }
    /** Klipper fora de "ready" (ex.: shutdown após a parada de emergência): a UI oferece Reiniciar firmware. */
    static final class NotReady extends IOException{final String state;NotReady(String state){super(L10n.t("conn.not_ready")+" ("+state+")");this.state=state;}}
    static final class UnknownTag extends IOException{final String uid;UnknownTag(String uid){super(L10n.t("nfc.unknown","uid",uid));this.uid=uid;}}

    static final List<String> IDLE_PHASES=Arrays.asList("wait_insert","preload_finish","load_finish","unload_finish","none","inited");
    /** Feeders parados e sem erro. allowPrint: "Ativar no alimentador" é permitido durante a impressão. */
    static void feedersIdle(JSONObject s,boolean allowPrint)throws Exception{
        if(!allowPrint&&printing(s))throw new IOException(L10n.t("feeder.print_blocked"));
        for(int i=0;i<4;i++){
            JSONObject c=channel(s,i);String phase=c.optString("channel_state");
            if(!"ok".equals(c.optString("channel_error","ok")))throw new IOException(L10n.t("feeder.error","ch",i+1));
            if(!IDLE_PHASES.contains(phase))throw new IOException(L10n.t("feeder.busy","ch",i+1));
        }
    }
    static JSONObject spoolInfo(JSONObject spool)throws Exception{
        JSONObject f=spool.getJSONObject("filament"),vendor=f.optJSONObject("vendor");
        String material=f.optString("material");if(material.isEmpty())throw new IOException(L10n.t("assign.no_material"));
        String multi=f.optString("multi_color_hexes","");String[] raw=multi.isEmpty()?new String[]{f.optString("color_hex","FFFFFF")}:multi.split(",");
        JSONObject info=new JSONObject().put("VENDOR",vendor==null?"Generic":vendor.optString("name","Generic")).put("MAIN_TYPE",material).put("SPOOL_ID",spool.getInt("id")).put("OFFICIAL",true).put("ALPHA",255).put("CARD_UID",new JSONArray());
        int count=0;for(String color:raw){color=color.trim().replace("#","");if(!color.matches("(?i)[0-9a-f]{6}"))throw new IOException(L10n.t("assign.no_material"));if(count<5)info.put("RGB_"+(++count),Integer.parseInt(color,16));}
        if(count==0)throw new IOException(L10n.t("assign.no_material"));
        info.put("COLOR_NUMS",count).put("ARGB_COLOR",(255L<<24)|info.getInt("RGB_1"));
        return info;
    }
    /** Ativar no alimentador (spoollink/set). Permitido durante a impressão; exige modo External. */
    void assign(int ch,JSONObject selected)throws Exception{
        if(ch<0||ch>3)throw new IOException(L10n.t("feeder.invalid"));
        requireExternal();
        JSONObject fresh=status();feedersIdle(fresh,true);
        if(!sensors(fresh)[ch])throw new IOException(L10n.t("assign.insert_first","ch",ch+1));
        for(int i=0;i<4;i++)if(i!=ch&&sensors(fresh)[i]&&spoolId(fresh,i)==selected.getInt("id"))throw new IOException(L10n.t("assign.in_other","ch",i+1));
        request(printer,"/printer/spoollink/set",new JSONObject().put("channel",ch).put("info",spoolInfo(selected)).put("message","Spool Factory: associação manual"),printerKey);
    }
    // ---------- Vínculo extrusor → rolo (SpoolLink, fix 2026-11-18) ----------
    /** Local gravado no rolo que sai de um extrusor (U1 Spool Spec v1). */
    static final String STORED_LOCATION="Armazém";
    static String u1Location(int ch)throws IOException{if(ch<0||ch>3)throw new IOException(L10n.t("feeder.invalid"));return "Snapmaker U1 - Extrusor "+(ch+1);}
    /** Percent-encoding RFC 3986 (igual ao display: só A-Z a-z 0-9 - _ . ~ passam; espaço = %20). */
    static String urlEncode(String v){StringBuilder b=new StringBuilder();for(byte x:v.getBytes(StandardCharsets.UTF_8)){int c=x&0xff;if(c>='A'&&c<='Z'||c>='a'&&c<='z'||c>='0'&&c<='9'||c=='-'||c=='_'||c=='.'||c=='~')b.append((char)c);else b.append('%').append(String.format(Locale.ROOT,"%02X",c));}return b.toString();}
    static String setSpoolScript(int ch,int id)throws IOException{if(ch<0||ch>3||id<=0)throw new IOException(L10n.t("feeder.invalid"));return "SET_SPOOL_ID CHANNEL="+ch+" SPOOL_ID="+id;}
    /** Script na query string e corpo vazio (o JSON no corpo falhou calado na U1 real). */
    static String setSpoolPath(int ch,int id)throws IOException{return "/printer/gcode/script?script="+urlEncode(setSpoolScript(ch,id));}
    static String locationQuery(int ch)throws IOException{return "/v1/spool?location="+urlEncode(u1Location(ch));}
    /** Rolo anterior: local exatamente o do extrusor e não é o novo (o filtro do Spoolman é parcial). */
    static boolean shouldRelease(JSONObject s,int ch,int newId)throws IOException{int id=s.optInt("id");return id>0&&id!=newId&&u1Location(ch).equals(s.optString("location",""));}
    /** SET_SPOOL_ID. true = aceito; false = resposta perdida (timeout): pode ter rodado, quem chama confere sem repetir.
     *  Recusa (HTTP/erro do Moonraker) vira exceção. */
    boolean setSpoolId(int ch,int id)throws Exception{
        String path=setSpoolPath(ch,id);
        try{requestMethod(printer,path,null,printerKey,10000,"POST");return true;}
        catch(SocketTimeoutException lost){return false;}
    }
    /** PATCH location e confere o valor salvo. */
    JSONObject setLocation(int id,String location)throws Exception{
        Object r=spoolman("PATCH","/v1/spool/"+id,new JSONObject().put("location",location),8000);
        JSONObject saved=r instanceof JSONObject?(JSONObject)r:new JSONObject();
        if(!location.equals(saved.optString("location",null)))throw new IOException("local ficou \""+saved.optString("location","")+"\"");
        return saved;
    }
    JSONObject locateInU1(int id,int ch)throws Exception{return setLocation(id,u1Location(ch));}
    /** Rolos que ainda marcam o local deste extrusor vão para o Armazém. Devolve os ids movidos. */
    List<Integer> releasePrevious(int ch,int newId)throws Exception{
        Object r=spoolmanGet(locationQuery(ch));List<Integer> moved=new ArrayList<>();
        if(!(r instanceof JSONArray))throw new IOException("Falha ao consultar os locais no Spoolman.");
        JSONArray a=(JSONArray)r;
        for(int i=0;i<a.length()&&moved.size()<8;i++){JSONObject s=a.optJSONObject(i);if(s!=null&&shouldRelease(s,ch,newId)){setLocation(s.getInt("id"),STORED_LOCATION);moved.add(s.getInt("id"));}}
        return moved;
    }

    static String feederScript(JSONObject fresh,int ch,boolean load)throws Exception{
        if(ch<0||ch>3)throw new IOException(L10n.t("feeder.invalid"));feedersIdle(fresh,false);
        if(!sensors(fresh)[ch])throw new IOException(L10n.t("feed.insert_first","ch",ch+1));
        if(load){JSONObject cfg=fresh.optJSONObject("print_task_config");String material=cfg==null?"":cfg.optJSONArray("filament_type")==null?"":cfg.getJSONArray("filament_type").optString(ch,"");if(material.isEmpty()||"NONE".equals(material))throw new IOException(L10n.t("feed.no_material"));if(channel(fresh,ch).optBoolean("disable_auto"))throw new IOException(L10n.t("feed.auto_off"));}
        return "AUTO_FEEDING EXTRUDER="+ch+(load?" LOAD=1":" UNLOAD=1");
    }
    /** Carregar/retirar: igual ao display, NÃO exige modo External (U1 em openrfid continua funcionando). Janela de 240 s. */
    void feed(int ch,boolean load)throws Exception{
        String script=feederScript(status(),ch,load);
        request(printer,"/printer/gcode/script",new JSONObject().put("script",script),printerKey,FEED_TIMEOUT_MS);
    }
    void inject(int ch,String uid,String type) throws Exception {
        requireExternal();
        String u=validUid(uid);
        JSONArray bytes=new JSONArray();for(int i=0;i<u.length();i+=2)bytes.put(Integer.parseInt(u.substring(i,i+2),16));
        JSONObject info=new JSONObject().put("CARD_UID",bytes).put("CARD_TYPE","M1".equals(type)?"M1":"NTAG");
        JSONObject r=(JSONObject)request(printer,"/printer/filament_detect/set",new JSONObject().put("channel",ch).put("info",info),printerKey);
        JSONObject result=r.optJSONObject("result");if(result==null)result=r;
        if("error".equals(result.optString("state")))throw new IOException(result.optString("message","Falha na leitura externa"));
    }

    // ---------- Controle ----------
    JSONObject gcode(String script,int timeout)throws Exception{return (JSONObject)request(printer,"/printer/gcode/script",new JSONObject().put("script",script),printerKey,timeout);}
    void printAction(String op)throws Exception{
        if(!Arrays.asList("pause","resume","cancel").contains(op))throw new IOException("Operação inválida.");
        JSONObject s=status();String st=printState(s);
        if("pause".equals(op)&&!"printing".equals(st)||"resume".equals(op)&&!"paused".equals(st)||"cancel".equals(op)&&!printing(s))throw new IOException(L10n.t("ctl.needs_print"));
        request(printer,"/printer/print/"+op,new JSONObject(),printerKey,12000);
    }
    void emergencyStop()throws Exception{request(printer,"/printer/emergency_stop",new JSONObject(),printerKey,4000);}
    void firmwareRestart()throws Exception{request(printer,"/printer/firmware_restart",new JSONObject(),printerKey,8000);}
    static String fanScript(int fan,int pct)throws IOException{
        if(fan<0||fan>3||pct<0||pct>100)throw new IOException("Ventilador inválido.");
        if(fan==0)return "M106 S"+((pct*255+50)/100);
        return String.format(Locale.ROOT,"SET_FAN_SPEED FAN=%s SPEED=%.2f",FANS[fan],pct/100.0);
    }
    /** Ventiladores podem mudar durante a impressão (como no display). */
    void setFan(int fan,int pct)throws Exception{gcode(fanScript(fan,pct),12000);}
    static String tempScript(int heater,int target)throws IOException{
        if(heater<0||heater>4)throw new IOException("Aquecedor inválido.");
        int max=heater==4?110:300;if(target<0||target>max)throw new IOException("Temperatura inválida.");
        return "SET_HEATER_TEMPERATURE HEATER="+HEATERS[heater]+" TARGET="+target;
    }
    /** Temperaturas só com a U1 ociosa (como no display). heater 0..3 = bicos T1..T4, 4 = mesa. */
    void setTemp(int heater,int target)throws Exception{
        String script=tempScript(heater,target);
        if(!idleForTemps(status()))throw new IOException(L10n.t("ctl.blocked_print"));
        gcode(script,12000);
    }

    // ---------- Descoberta ----------
    static boolean probe(String host,int timeout){
        try{HttpURLConnection c=(HttpURLConnection)new URL("http://"+host+":"+MOONRAKER_PORT+"/printer/info").openConnection();
            c.setConnectTimeout(timeout);c.setReadTimeout(timeout+600);
            try{if(c.getResponseCode()!=200)return false;String t=readAll(c.getInputStream());
                JSONObject r=new JSONObject(t).optJSONObject("result");return r!=null&&(r.has("state")||r.has("klipper_path")||r.has("software_version"));}
            finally{c.disconnect();}
        }catch(Exception e){return false;}
    }
    /** Varre as /24 das interfaces locais em :7125/printer/info; primeiro o IP lembrado. */
    static String discover(String remembered){
        if(remembered!=null&&!remembered.isEmpty()&&probe(remembered,800))return remembered;
        List<String> prefixes=new ArrayList<>();
        try{for(NetworkInterface ni:Collections.list(NetworkInterface.getNetworkInterfaces())){
            if(!ni.isUp()||ni.isLoopback())continue;
            for(InterfaceAddress ia:ni.getInterfaceAddresses()){InetAddress a=ia.getAddress();
                if(a instanceof Inet4Address&&a.isSiteLocalAddress()){byte[] b=a.getAddress();String p=(b[0]&255)+"."+(b[1]&255)+"."+(b[2]&255)+".";if(!prefixes.contains(p))prefixes.add(p);}}}
        }catch(Exception ignored){}
        ExecutorService pool=Executors.newFixedThreadPool(48);
        try{
            for(String p:prefixes){
                ExecutorCompletionService<String> done=new ExecutorCompletionService<>(pool);
                for(int i=1;i<255;i++){String ip=p+i;done.submit(()->probe(ip,350)?ip:null);}
                for(int i=1;i<255;i++){String hit=done.take().get();if(hit!=null)return hit;}
            }
        }catch(Exception ignored){}finally{pool.shutdownNow();}
        return "";
    }

    static String label(JSONObject spool) {
        JSONObject f=spool.optJSONObject("filament");if(f==null)f=new JSONObject();JSONObject v=f.optJSONObject("vendor");
        String vendor=v==null?"":v.optString("name");String mat=f.optString("material");
        return ("#"+spool.optInt("id")+" · "+(vendor+" "+mat).trim()+" · "+f.optString("name")).replaceAll(" · $","");
    }
}
