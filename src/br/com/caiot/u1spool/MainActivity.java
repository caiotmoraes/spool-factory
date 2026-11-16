package br.com.caiot.u1spool;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.nfc.*;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import android.webkit.*;
import java.io.*;
import org.json.*;
import java.util.*;

public class MainActivity extends Activity implements NfcAdapter.ReaderCallback {
    private final Handler ui=new Handler(Looper.getMainLooper());
    private NfcAdapter nfc;
    private WebView web;
    private boolean ready;
    private JSONObject deleteCandidate;
    private volatile boolean registrationMode;
    private volatile boolean inventoryBusy;
    private JSONArray managerStock;
    private long managerStockAt;
    private final java.util.concurrent.ExecutorService inventoryWork=java.util.concurrent.Executors.newSingleThreadExecutor();
    /** Tema claro do display (spec/tokens.json: bg #F2F0EC). */
    private static final int BG=0xfff2f0ec;
    private SharedPreferences prefs(){return getSharedPreferences("config",0);}
    private String printerHost(){SharedPreferences p=prefs();String m=p.getString("printer","");return m.isEmpty()?p.getString("discovered",""):m;}
    private Api inventoryApi(){SharedPreferences p=prefs();return new Api(printerHost(),p.getString("spoolman",""),p.getString("printerKey",""),p.getString("spoolKey",""));}
    private void inventoryError(Exception error){try{inventoryResult(new JSONObject().put("ok",false).put("message",error.getMessage()));}catch(Exception ignored){}}
    private void inventoryResult(JSONObject result){if(ready&&!isDestroyed())web.evaluateJavascript("window.inventoryResult("+result.toString()+");",null);}
    private void refreshInventory(Api api)throws Exception{JSONArray loaded=api.inventory();long at=System.currentTimeMillis();prefs().edit().putString("stock",loaded.toString()).putLong("stockAt",at).apply();runOnUiThread(()->{managerStock=loaded;managerStockAt=at;render();});}
    private void forgetSpool(int id)throws Exception{
        SharedPreferences p=prefs();JSONArray all=new JSONArray(p.getString("stock","[]")),kept=new JSONArray();for(int i=0;i<all.length();i++)if(all.getJSONObject(i).optInt("id")!=id)kept.put(all.get(i));long at=System.currentTimeMillis();p.edit().putString("stock",kept.toString()).putLong("stockAt",at).apply();runOnUiThread(()->{managerStock=kept;managerStockAt=at;render();});
    }
    private String lastUid="";
    private long lastTap;
    private final Runnable refresh=new Runnable(){@Override public void run(){render();ui.postDelayed(this,500);}};
    private String versionName(){try{return getPackageManager().getPackageInfo(getPackageName(),0).versionName;}catch(Exception e){return "?";}}
    @Override public void onCreate(Bundle b){
        super.onCreate(b);try{L10n.load(getAssets().open("strings.json"));}catch(Exception e){android.util.Log.e("SpoolFactory","Localization unavailable",e);}applyLanguage();nfc=NfcAdapter.getDefaultAdapter(this);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(BG));
        /* Android 15 (target 35) desenha atrás das barras. Padding no WebView não move
         * position:fixed; o recuo vai no FrameLayout pra o viewport já nascer insetado. */
        if(Build.VERSION.SDK_INT>=30){
            getWindow().setDecorFitsSystemWindows(false);
        }else{
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|(Build.VERSION.SDK_INT>=26?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0));
        }
        if(Build.VERSION.SDK_INT>=29){
            getWindow().setStatusBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarColor(android.graphics.Color.TRANSPARENT);
            getWindow().setNavigationBarContrastEnforced(false);
        }else{
            getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        }
        FrameLayout root=new FrameLayout(this);root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v,insets)->{
            int left,top,right,bottom;
            if(Build.VERSION.SDK_INT>=30){
                android.graphics.Insets sys=insets.getInsets(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout()|WindowInsets.Type.ime());
                left=sys.left;top=sys.top;right=sys.right;bottom=sys.bottom;
            }else{
                left=insets.getSystemWindowInsetLeft();top=insets.getSystemWindowInsetTop();right=insets.getSystemWindowInsetRight();bottom=insets.getSystemWindowInsetBottom();
            }
            v.setPadding(left,top,right,bottom);
            return Build.VERSION.SDK_INT>=30?WindowInsets.CONSUMED:insets.consumeSystemWindowInsets();
        });
        web=new WebView(this);web.setBackgroundColor(BG);
        root.addView(web,new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,FrameLayout.LayoutParams.MATCH_PARENT));
        setContentView(root);root.requestApplyInsets();
        /* O controlador das barras só existe depois que a DecorView é criada (setContentView). */
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController bars=getWindow().getInsetsController();
            if(bars!=null)bars.setSystemBarsAppearance(WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }
        WebSettings ws=web.getSettings();ws.setJavaScriptEnabled(true);ws.setAllowFileAccess(false);ws.setAllowContentAccess(false);ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);ws.setJavaScriptCanOpenWindowsAutomatically(false);ws.setSupportMultipleWindows(false);
        web.addJavascriptInterface(new Bridge(),"Native");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                try{
                    android.net.Uri u=r.getUrl();String path=u.getPath();
                    if(!"https".equals(u.getScheme())||!"appassets.androidplatform.net".equals(u.getHost())||path==null||!path.startsWith("/assets/")||path.contains(".."))throw new IOException();
                    String name=path.substring(8);
                    return new WebResourceResponse(mime(name),"UTF-8",getAssets().open(name));
                }catch(Exception e){return new WebResourceResponse("text/plain","UTF-8",404,"Not found",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
            }
            @Override public void onPageFinished(WebView v,String url){ready=true;render();}
        });
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},4);
        connect();
    }
    static String mime(String name){
        String n=name.toLowerCase(Locale.ROOT);
        return n.endsWith(".html")?"text/html":n.endsWith(".css")?"text/css":n.endsWith(".js")?"application/javascript":n.endsWith(".json")?"application/json":n.endsWith(".woff2")?"font/woff2":n.endsWith(".woff")?"font/woff":n.endsWith(".ttf")?"font/ttf":n.endsWith(".png")?"image/png":n.endsWith(".svg")?"image/svg+xml":"application/octet-stream";
    }
    private void confirm(String title,String body,String ok,Runnable action){
        new AlertDialog.Builder(MainActivity.this).setTitle(title).setMessage(body).setNegativeButton(L10n.t("common.back"),null).setPositiveButton(ok,(d,w)->action.run()).show();
    }
    public final class Bridge {
        @JavascriptInterface public String settingsData(){
            SharedPreferences p=prefs();
            try{return new JSONObject().put("printer",p.getString("printer","")).put("discovered",p.getString("discovered","")).put("spoolman",p.getString("spoolman","")).put("printerKey",p.getString("printerKey","")).put("spoolKey",p.getString("spoolKey","")).put("timeout",p.getInt("timeout",180)).put("languagePreference",p.getString("language","auto")).put("language",L10n.resolve(p.getString("language","auto"),Locale.getDefault().getLanguage())).put("version",versionName()).toString();}catch(Exception e){return "{}";}
        }
        @JavascriptInterface public void settingsForm(String operation,String raw){runOnUiThread(()->{
            try{
                if(inventoryBusy)throw new Exception(L10n.t("common.saving"));
                JSONObject data=ConnectionConfig.validate(new JSONObject(raw));
                if("save".equals(operation)){
                    SharedPreferences p=prefs();boolean networkChanged=!p.getString("printer","").equals(data.getString("printer"))||!p.getString("spoolman","").equals(data.getString("spoolman"))||!p.getString("printerKey","").equals(data.getString("printerKey"))||!p.getString("spoolKey","").equals(data.getString("spoolKey"));
                    SharedPreferences.Editor editor=p.edit().putString("printer",data.getString("printer")).putString("spoolman",data.getString("spoolman")).putString("printerKey",data.getString("printerKey")).putString("spoolKey",data.getString("spoolKey")).putInt("timeout",data.getInt("timeout")).putString("language",data.getString("language"));
                    if(networkChanged)editor.remove("stock").remove("stockAt");editor.commit();applyLanguage();
                    if(networkChanged){stopService(new Intent(MainActivity.this,SpoolService.class));ui.postDelayed(MainActivity.this::connect,300);}
                    managerStock=null;managerStockAt=0;registrationMode=false;settingsResult(new JSONObject().put("ok",true).put("saved",true).put("message",L10n.t("set.saved")));render();
                }else if("test".equals(operation)){
                    inventoryWork.execute(()->{JSONObject result=new JSONObject();try{
                        String host=data.getString("printer");if(host.isEmpty())host=Api.discover(prefs().getString("discovered",""));
                        String u1=L10n.english()?"U1":"U1";
                        if(host.isEmpty())result.put("printer",L10n.t("conn.u1_not_found")).put("printerError",true);
                        else try{Api probe=new Api(host,"",data.getString("printerKey"),"");probe.status();String ext;try{ext=probe.externalMode()?L10n.t("conn.external_ok"):L10n.t("conn.external_required");}catch(Exception e){ext=L10n.t("conn.external_unknown");}
                            result.put("printer",L10n.t("conn.test_u1_ok","ip",host)+" · "+ext);}catch(Exception e){result.put("printer",L10n.t("conn.test_fail","what",u1,"error",e.getMessage())).put("printerError",true);}
                        try{Api sm=new Api(host,data.getString("spoolman"),data.getString("printerKey"),data.getString("spoolKey"));int count=sm.inventory().length();
                            result.put("spoolman",L10n.t("conn.test_spoolman_ok","n",count)+" · "+(sm.viaProxy()?L10n.t("conn.via_proxy"):L10n.t("conn.via_direct","url",sm.spoolman)));}
                        catch(Exception e){result.put("spoolman",L10n.t("conn.test_fail","what","Spoolman","error",e.getMessage())).put("spoolmanError",true);}
                        result.put("ok",!result.optBoolean("printerError")&&!result.optBoolean("spoolmanError"));
                    }catch(Exception e){try{result.put("ok",false).put("message",e.getMessage());}catch(Exception ignored){}}
                    runOnUiThread(()->settingsResult(result));});
                }
            }catch(Exception e){try{settingsResult(new JSONObject().put("ok",false).put("message",e.getMessage()));}catch(Exception ignored){}}
        });}
        @JavascriptInterface public void previewDelete(int id){runOnUiThread(()->{
            if(inventoryBusy)return;final Api api;try{api=inventoryApi();int ch=installedChannel(id);if(ch>=0)throw new Exception(L10n.t("delete.installed","id",id,"ch",ch+1));}catch(Exception e){inventoryError(e);return;}
            inventoryWork.execute(()->{try{JSONObject spool=(JSONObject)new InventoryManager(api).get("spool/"+id);runOnUiThread(()->{deleteCandidate=spool;try{inventoryResult(new JSONObject().put("deletePreview",spool));}catch(Exception ignored){}});}catch(Exception e){runOnUiThread(()->inventoryError(e));}});
        });}
        /** Confirmação única (igual ao display): resumo + digitar o número do rolo. */
        @JavascriptInterface public void deleteSpool(int id,String typed){runOnUiThread(()->{
            try{if(inventoryBusy||deleteCandidate==null||deleteCandidate.getInt("id")!=id)throw new Exception(L10n.t("delete.changed"));
                if(typed==null||!typed.trim().equals(String.valueOf(id)))throw new Exception(L10n.t("delete.wrong_id"));
                int ch=installedChannel(id);if(ch>=0)throw new Exception(L10n.t("delete.installed","id",id,"ch",ch+1));
                final Api api=inventoryApi();final String identity=InventoryManager.identity(deleteCandidate);deleteCandidate=null;inventoryBusy=true;
                inventoryWork.execute(()->{JSONObject result=new JSONObject();try{new InventoryManager(api).delete(id,identity);forgetSpool(id);result.put("deleted",id).put("ok",true).put("message",L10n.t("delete.done","id",id));try{refreshInventory(api);}catch(Exception ignored){}}
                catch(Exception e){try{result.put("ok",false).put("deleteError",true).put("message",e.getMessage());}catch(Exception ignored){}}
                runOnUiThread(()->{inventoryBusy=false;inventoryResult(result);});});
            }catch(Exception e){try{inventoryResult(new JSONObject().put("deleteError",true).put("ok",false).put("message",e.getMessage()));}catch(Exception ignored){}}
        });}
        @JavascriptInterface public void registration(boolean enabled){runOnUiThread(()->registrationMode=enabled);}
        @JavascriptInterface public void catalog(){runOnUiThread(()->{
            final Api api;try{api=inventoryApi();}catch(Exception e){inventoryError(e);return;}
            inventoryWork.execute(()->{JSONObject result=new JSONObject();try{JSONObject catalog=new InventoryManager(api).catalog();refreshInventory(api);result.put("catalog",catalog).put("ok",true);}
                catch(Exception e){try{result.put("ok",false).put("message",L10n.t("conn.spoolman_down")+": "+e.getMessage());}catch(Exception ignored){}}
                runOnUiThread(()->{inventoryResult(result);});});
        });}
        @JavascriptInterface public void saveInventory(String raw){runOnUiThread(()->{
            if(inventoryBusy){try{inventoryResult(new JSONObject().put("ok",false).put("message",L10n.t("common.saving")));}catch(Exception ignored){}return;}
            final JSONObject data;final Api api;try{if(raw.length()>12000)throw new Exception("Formulário inválido.");data=new JSONObject(raw);api=inventoryApi();}catch(Exception e){inventoryError(e);return;}
            String operation=data.optString("mode");int id=data.optInt("id");
            String title="create".equals(operation)?L10n.t("create.title"):"bind".equals(operation)?L10n.t("bind.title"):L10n.t("common.edit")+" #"+id;
            String body="bind".equals(operation)?L10n.t("bind.confirm","uid",Api.normalize(data.optString("uid")),"id",id):L10n.t("save.confirm");
            new AlertDialog.Builder(MainActivity.this).setTitle(title).setMessage(body).setNegativeButton(L10n.t("common.back"),(d,w)->{try{inventoryResult(new JSONObject().put("cancelled",true));}catch(Exception ignored){}}).setPositiveButton(L10n.t("common.save"),(d,w)->{
                inventoryBusy=true;inventoryWork.execute(()->{JSONObject result=new JSONObject();try{
                    JSONObject saved=new InventoryManager(api).save(data);int sid=saved.getInt("id");
                    String msg="bind".equals(operation)?L10n.t("bind.done","uid",Api.normalize(data.optString("uid")),"id",sid):"create".equals(operation)?(data.optString("uid","").trim().isEmpty()?L10n.t("create.done","id",sid):L10n.t("create.done_tag","id",sid,"uid",Api.normalize(data.optString("uid")))):L10n.t("update.done","id",sid);
                    result.put("ok",true).put("saved",saved).put("message",msg);
                    try{refreshInventory(api);}catch(Exception ignored){}
                }catch(InventoryManager.Already a){try{result.put("ok",true).put("saved",a.spool).put("message",a.getMessage());}catch(Exception ignored){}}
                catch(Exception e){try{result.put("ok",false).put("message",e.getMessage()+("create".equals(operation)?" "+L10n.t("create.partial"):""));}catch(Exception ignored){}}
                runOnUiThread(()->{inventoryBusy=false;inventoryResult(result);});});
            }).setOnCancelListener(d->{try{inventoryResult(new JSONObject().put("cancelled",true));}catch(Exception ignored){}}).show();
        });}
        @JavascriptInterface public void manual(String operation,int channel,int spoolId){runOnUiThread(()->{
            if(!SpoolService.running){connect();return;}
            if(channel<0||channel>3||!Arrays.asList("ASSIGN","LOAD","UNLOAD").contains(operation))return;
            if("ASSIGN".equals(operation)&&spoolId<=0)return;
            String title="ASSIGN".equals(operation)?L10n.t("assign.title")+" · T"+(channel+1):L10n.t("LOAD".equals(operation)?"feed.load":"feed.unload")+" · T"+(channel+1);
            String detail="ASSIGN".equals(operation)?L10n.t("assign.confirm","ch",channel+1):L10n.t("feed.warning");
            confirm(title,detail,L10n.t("common.confirm"),()->startService(new Intent(MainActivity.this,SpoolService.class).setAction(operation).putExtra("channel",channel).putExtra("spool_id",spoolId)));
        });}
        /** Controle: pause/resume/cancel/estop/restart pedem confirmação; temperatura também; ventiladores não (igual ao display). */
        @JavascriptInterface public void control(String op,int a,int b){runOnUiThread(()->{
            if(!SpoolService.running){connect();return;}
            Runnable send=()->startService(new Intent(MainActivity.this,SpoolService.class).setAction("CONTROL").putExtra("op",op).putExtra("a",a).putExtra("b",b));
            switch(op){
                case "pause":confirm(L10n.t("ctl.pause"),L10n.t("ctl.pause_confirm"),L10n.t("ctl.pause"),send);break;
                case "resume":confirm(L10n.t("ctl.resume"),L10n.t("ctl.resume_confirm"),L10n.t("ctl.resume"),send);break;
                case "cancel":confirm(L10n.t("ctl.cancel"),L10n.t("ctl.cancel_confirm"),L10n.t("ctl.cancel"),send);break;
                case "estop":confirm(L10n.t("ctl.estop"),L10n.t("ctl.estop_confirm"),L10n.t("ctl.estop"),send);break;
                case "restart":confirm(L10n.t("ctl.restart"),L10n.t("ctl.restart"),L10n.t("common.confirm"),send);break;
                case "temp":{String what=a==4?L10n.t("ctl.bed"):L10n.t("ctl.nozzle","ch",a+1);confirm(L10n.t("ctl.temps"),L10n.t("ctl.set_temp","what",what,"t",b),L10n.t("common.confirm"),send);break;}
                case "fan":send.run();break;
            }
        });}
        @JavascriptInterface public void action(String name){runOnUiThread(()->{
            switch(name){
                case "inventoryRefresh":catalog();break;
                case "nfcSettings":startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));break;
                case "connect":connect();break;
                case "rescan":if(!SpoolService.running)connect();command("RESCAN");break;
                case "scan":if(nfc==null||!nfc.isEnabled())startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));else connect();break;
                case "cancel":command("CANCEL");break;
                case "tagClear":command("TAG_CLEAR");break;
                case "firmware":{String h=printerHost();if(!h.isEmpty())startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(Api.webBase(h)+"/firmware-config/")));break;}
                case "share":share();break;
            }
        });}
    }
    private void connect(){
        if(SpoolService.running)return;
        try{startForegroundService(new Intent(this,SpoolService.class).setAction("CONNECT"));}catch(Exception e){toast(e.getMessage());}
    }
    private void command(String action){if(SpoolService.running)startService(new Intent(this,SpoolService.class).setAction(action));}
    @Override protected void onResume(){super.onResume();if(nfc!=null){Bundle opts=new Bundle();opts.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY,250);nfc.enableReaderMode(this,this,NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,opts);}web.onResume();ui.post(refresh);}
    @Override protected void onPause(){ui.removeCallbacks(refresh);if(nfc!=null)nfc.disableReaderMode(this);web.onPause();super.onPause();}
    @Override protected void onDestroy(){ui.removeCallbacks(refresh);inventoryWork.shutdownNow();web.removeJavascriptInterface("Native");web.destroy();super.onDestroy();}
    @Override public void onTagDiscovered(Tag tag){
        byte[] bytes=tag.getId();if(bytes==null||(bytes.length!=4&&bytes.length!=7&&bytes.length!=10)){runOnUiThread(()->toast(L10n.t("uid.unsupported")));return;}
        StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02X",v&255));String uid=b.toString();
        long now=SystemClock.elapsedRealtime();synchronized(this){if(uid.equals(lastUid)&&now-lastTap<3000)return;lastUid=uid;lastTap=now;}
        if(registrationMode){runOnUiThread(()->{if(ready)web.evaluateJavascript("window.registrationTag("+JSONObject.quote(uid)+");",null);});return;}
        String type=Arrays.asList(tag.getTechList()).contains("android.nfc.tech.MifareClassic")?"M1":"NTAG";
        if(!SpoolService.running){runOnUiThread(this::connect);ui.postDelayed(()->startService(new Intent(this,SpoolService.class).setAction("SCAN").putExtra("uid",uid).putExtra("type",type)),800);return;}
        startService(new Intent(this,SpoolService.class).setAction("SCAN").putExtra("uid",uid).putExtra("type",type));
        runOnUiThread(()->{if(ready)web.evaluateJavascript("window.showTab&&window.showTab('nfc');",null);});
    }
    private void render(){
        if(!ready)return;
        try{
            JSONObject v=new JSONObject(SpoolService.view);SharedPreferences p=prefs();
            boolean active=SpoolService.running;
            v.put("running",active).put("connected",active&&SystemClock.elapsedRealtime()-v.optLong("statusAt",0)<4000).put("nfc",nfc!=null&&nfc.isEnabled()).put("nfcHardware",nfc!=null);
            if(!active){v.put("armed",false).put("manualOperation",false);v.put("stock",new JSONArray(p.getString("stock","[]"))).put("stockAt",p.getLong("stockAt",0));v.put("history",new JSONArray(Arrays.asList(p.getString("history","").split("\\n"))));}
            if(managerStock!=null&&managerStockAt>=v.optLong("stockAt",0))v.put("stock",managerStock).put("stockAt",managerStockAt);
            v.put("inventoryBusy",inventoryBusy).put("languagePreference",p.getString("language","auto")).put("language",L10n.resolve(p.getString("language","auto"),Locale.getDefault().getLanguage())).put("timeout",p.getInt("timeout",180)).put("version",versionName());
            web.evaluateJavascript("window.updateApp("+v.toString()+");",null);
        }catch(Exception e){android.util.Log.e("SpoolFactory","render",e);}
    }
    private void applyLanguage(){L10n.set(prefs().getString("language","auto"),Locale.getDefault().getLanguage());}
    private void settingsResult(JSONObject result){if(ready&&!isDestroyed())web.evaluateJavascript("window.settingsResult("+result.toString()+");",null);}
    private int installedChannel(int id)throws Exception{
        JSONObject view=new JSONObject(SpoolService.view),status=view.optJSONObject("status");
        if(status!=null)for(int i=0;i<4;i++)if(Api.sensors(status)[i]&&Api.spoolId(status,i)==id)return i;
        if(view.optBoolean("armed")&&view.optJSONObject("spool")!=null&&view.getJSONObject("spool").optInt("id")==id)return Math.max(0,view.optInt("verifyChannel",0));
        return -1;
    }
    private void share(){
        try{JSONObject d=new JSONObject(SpoolService.view);d.put("app","Spool Factory "+versionName());d.put("device",Build.MANUFACTURER+" "+Build.MODEL);d.put("android",Build.VERSION.RELEASE);
            Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,"Spool Factory").putExtra(Intent.EXTRA_TEXT,d.toString(2));startActivity(Intent.createChooser(send,L10n.t("set.share")));
        }catch(Exception e){toast(e.getMessage());}
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
}
