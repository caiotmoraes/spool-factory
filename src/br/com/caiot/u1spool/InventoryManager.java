package br.com.caiot.u1spool;

import org.json.*;
import java.io.IOException;
import java.util.*;

/** Operações só no Spoolman (proxy da U1 ou direto): nunca movimenta a impressora. */
final class InventoryManager {
    private final Api api;
    InventoryManager(Api api){this.api=api;}
    Object get(String path)throws Exception{return api.spoolmanGet("/v1/"+path);}
    JSONObject write(String path,JSONObject data,String method)throws Exception{return (JSONObject)api.spoolman(method,"/v1/"+path,data,10000);}
    JSONObject catalog()throws Exception{return new JSONObject().put("vendors",get("vendor")).put("filaments",get("filament"));}
    static String text(JSONObject d,String key,int max,boolean required)throws Exception{String v=d.optString(key,"").trim();if(v.length()>max||(required&&v.isEmpty()))throw new IOException("Confira o campo "+key+".");return v;}
    static double number(JSONObject d,String key,double min)throws Exception{double v=d.optDouble(key,Double.NaN);if(!Double.isFinite(v)||v<min)throw new IOException("Valor inválido: "+key);return v;}
    /** UID obrigatório (associar) — Spec v1. */
    static String uid(JSONObject d)throws Exception{return Api.validUid(d.optString("uid",""));}
    /** Comparação exata por igualdade (nunca substring). */
    void unique(String uid,int self)throws Exception{JSONArray all=api.inventory();for(int i=0;i<all.length();i++){JSONObject s=all.getJSONObject(i);if(s.getInt("id")!=self&&Api.uids(s).contains(uid))throw new IOException(L10n.t(s.optBoolean("archived")?"uid.taken_archived":"uid.taken","id",s.getInt("id")));}}
    void tagField()throws Exception{JSONArray fields=(JSONArray)get("field/spool");for(int i=0;i<fields.length();i++){JSONObject f=fields.getJSONObject(i);if("card_uids".equals(f.optString("key"))){if(!"text".equals(f.optString("field_type")))throw new IOException(L10n.t("uid.field_type"));return;}}api.spoolman("POST","/v1/field/spool/card_uids",new JSONObject().put("name","Card UIDs").put("field_type","text").put("order",1).put("default_value",JSONObject.quote("")),10000);}
    /** Identidade usada na exclusão: igual ao display (#id · marca material · nome|filament_id). */
    static String identity(JSONObject spool)throws Exception{return Api.label(spool)+"|"+spool.getJSONObject("filament").optInt("id");}
    void delete(int id,String expected)throws Exception{
        if(id<=0)throw new IOException("Rolo inválido.");JSONObject fresh=(JSONObject)get("spool/"+id);
        if(!identity(fresh).equals(expected))throw new IOException(L10n.t("delete.changed"));
        api.spoolman("DELETE","/v1/spool/"+id,null,10000);
    }
    JSONObject save(JSONObject d)throws Exception{
        String mode=d.optString("mode");
        if("bind".equals(mode)){
            int id=d.getInt("id");if(id<=0)throw new IOException("Escolha um rolo válido.");String u=uid(d);unique(u,id);tagField();JSONObject fresh=(JSONObject)get("spool/"+id);if(fresh.optBoolean("archived"))throw new IOException(L10n.t("bind.archived"));
            if(Api.parseCardUids(Api.rawCardUids(fresh)).valid.contains(u))throw new Already(L10n.t("bind.already","uid",u,"id",id),fresh);
            JSONObject extra=fresh.optJSONObject("extra");extra=extra==null?new JSONObject():new JSONObject(extra.toString());
            // Spec v1: texto JSON "A,B" canônico; migra formatos legados; tags[] não são copiadas.
            extra.put("card_uids",Api.canonical(Api.rawCardUids(fresh),u));
            JSONObject saved=write("spool/"+id,new JSONObject().put("extra",extra),"PATCH");if(!Api.uids(saved).contains(u))throw new IOException(L10n.t("bind.not_confirmed"));return saved;
        }
        if("update".equals(mode)){
            int id=d.getInt("id");if(id<=0)throw new IOException("Rolo inválido.");JSONObject patch=new JSONObject().put("location",text(d,"location",64,false)).put("comment",text(d,"comment",1024,false));
            if(d.has("remaining_weight"))patch.put("remaining_weight",number(d,"remaining_weight",0));
            return write("spool/"+id,patch,"PATCH");
        }
        if(!"create".equals(mode))throw new IOException("Operação inválida.");
        // Tag opcional no cadastro (igual ao display).
        String rawUid=d.optString("uid","").trim();String u=rawUid.isEmpty()?"":Api.validUid(rawUid);
        String location=text(d,"location",64,false),comment=text(d,"comment",1024,false);double initial=number(d,"initial_weight",0.01),remaining=number(d,"remaining_weight",0);
        if(remaining>initial)throw new IOException(L10n.t("create.weight"));
        int filamentId=d.optInt("filament_id",0);JSONObject newFilament=null;String vendorName="";
        if(filamentId<=0){
            vendorName=text(d,"vendor",64,true);String colors=text(d,"colors",80,true).replace("#","").replace(" ","").toUpperCase(Locale.ROOT);String[] list=colors.split(",",-1);if(list.length>5)throw new IOException("Use até cinco cores.");for(String c:list)if(!c.matches("[0-9A-F]{6}"))throw new IOException("Use cores hexadecimais com seis dígitos.");
            newFilament=new JSONObject().put("name",text(d,"name",64,true)).put("material",text(d,"material",64,true)).put("density",number(d,"density",0.01)).put("diameter",number(d,"diameter",0.01)).put("weight",initial).put("color_hex",list[0]);
            if(list.length>1){String direction=d.optString("direction","coaxial");if(!Arrays.asList("coaxial","longitudinal").contains(direction))throw new IOException("Direção das cores inválida.");newFilament.put("multi_color_hexes",colors).put("multi_color_direction",direction);}
        }
        if(!u.isEmpty()){unique(u,0);tagField();} // Valida tudo antes de escrever no catálogo.
        if(newFilament!=null){
            JSONArray vendors=(JSONArray)get("vendor");int vendorId=0;
            for(int i=0;i<vendors.length();i++){JSONObject v=vendors.getJSONObject(i);if(vendorName.equalsIgnoreCase(v.optString("name"))){vendorId=v.getInt("id");break;}}
            if(vendorId==0)vendorId=write("vendor",new JSONObject().put("name",vendorName),"POST").getInt("id");
            newFilament.put("vendor_id",vendorId);
            JSONArray filaments=(JSONArray)get("filament");for(int i=0;i<filaments.length();i++){JSONObject f=filaments.getJSONObject(i),v=f.optJSONObject("vendor");if(v!=null&&v.optInt("id")==vendorId&&same(f,newFilament)){filamentId=f.getInt("id");break;}}
            if(filamentId==0)filamentId=write("filament",newFilament,"POST").getInt("id");
        }else get("filament/"+filamentId);
        if(!u.isEmpty())unique(u,0);
        JSONObject body=new JSONObject().put("filament_id",filamentId).put("initial_weight",initial).put("used_weight",initial-remaining).put("location",location).put("comment",comment);
        if(!u.isEmpty())body.put("extra",new JSONObject().put("card_uids",Api.canonical(null,u)));
        JSONObject saved=write("spool",body,"POST");if(!u.isEmpty()&&!Api.uids(saved).contains(u))throw new IOException(L10n.t("create.not_confirmed"));return saved;
    }
    static final class Already extends IOException{final JSONObject spool;Already(String m,JSONObject s){super(m);spool=s;}}
    private boolean same(JSONObject f,JSONObject candidate){for(String k:new String[]{"name","material","color_hex","multi_color_hexes","multi_color_direction"})if(!f.optString(k,"").equalsIgnoreCase(candidate.optString(k,"")))return false;for(String k:new String[]{"density","diameter","weight"})if(f.optDouble(k,-1)!=candidate.optDouble(k,-2))return false;return true;}
}
