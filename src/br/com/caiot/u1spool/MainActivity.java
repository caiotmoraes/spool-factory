package br.com.caiot.u1spool;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
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
    private boolean ready,settingsPending;
    private JSONObject deleteCandidate;
    private volatile boolean registrationMode;
    private volatile boolean inventoryBusy;
    private JSONArray managerStock;
    private long managerStockAt;
    private final java.util.concurrent.ExecutorService inventoryWork=java.util.concurrent.Executors.newSingleThreadExecutor();
    private Api inventoryApi(){SharedPreferences p=getSharedPreferences("config",0);String address=p.getString("spoolman","");return new Api(address,address,"",p.getString("spoolKey",""));}
    private void inventoryError(Exception error){try{inventoryResult(new JSONObject().put("ok",false).put("message",error.getMessage()));}catch(Exception ignored){}}
    private void inventoryResult(JSONObject result){if(ready&&!isDestroyed())web.evaluateJavascript("window.inventoryResult("+result.toString()+");",null);}
    private void refreshInventory(Api api)throws Exception{JSONArray loaded=api.inventory();if(!Api.base(getSharedPreferences("config",0).getString("spoolman","")).equals(api.spoolman))return;long at=System.currentTimeMillis();getSharedPreferences("config",0).edit().putString("stock",loaded.toString()).putLong("stockAt",at).apply();runOnUiThread(()->{managerStock=loaded;managerStockAt=at;render();});}

    private void forgetSpool(int id)throws Exception{
        SharedPreferences p=getSharedPreferences("config",0);JSONArray all=new JSONArray(p.getString("stock","[]")),kept=new JSONArray();for(int i=0;i<all.length();i++)if(all.getJSONObject(i).optInt("id")!=id)kept.put(all.get(i));long at=System.currentTimeMillis();p.edit().putString("stock",kept.toString()).putLong("stockAt",at).apply();runOnUiThread(()->{managerStock=kept;managerStockAt=at;render();});
    }
    private String lastUid="";
    private long lastTap;
    private static final int BG=0xff080f23,CARD=0xff18232d,TEXT=0xffeef5fa,MUTED=0xffa8bac8,ACCENT=0xff51d8b5;
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private final Runnable refresh=new Runnable(){@Override public void run(){render();ui.postDelayed(this,500);}};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);try{L10n.load(getAssets().open("translations.json"));}catch(Exception e){android.util.Log.e("SpoolFactory","Localization unavailable",e);}applyLanguage();nfc=NfcAdapter.getDefaultAdapter(this);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        web=new WebView(this);web.setBackgroundColor(BG);setContentView(web);
        web.setOnApplyWindowInsetsListener((v,insets)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets sys=insets.getInsets(WindowInsets.Type.systemBars());v.setPadding(sys.left,sys.top,sys.right,sys.bottom);}else v.setPadding(insets.getSystemWindowInsetLeft(),insets.getSystemWindowInsetTop(),insets.getSystemWindowInsetRight(),insets.getSystemWindowInsetBottom());return insets;});
        WebSettings ws=web.getSettings();ws.setJavaScriptEnabled(true);ws.setAllowFileAccess(false);ws.setAllowContentAccess(false);ws.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);ws.setJavaScriptCanOpenWindowsAutomatically(false);ws.setSupportMultipleWindows(false);
        web.addJavascriptInterface(new Bridge(),"Native");
        web.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,WebResourceRequest r){return true;}
            @Override public WebResourceResponse shouldInterceptRequest(WebView v,WebResourceRequest r){
                try{
                    android.net.Uri u=r.getUrl();String path=u.getPath();
                    if(!"https".equals(u.getScheme())||!"appassets.androidplatform.net".equals(u.getHost())||path==null||!path.startsWith("/assets/")||path.contains(".."))throw new IOException();
                    String name=path.substring(8),mime=name.endsWith(".html")?"text/html":name.endsWith(".css")?"text/css":name.endsWith(".js")?"application/javascript":name.endsWith(".ttf")?"font/ttf":name.endsWith(".png")?"image/png":"application/octet-stream";
                    return new WebResourceResponse(mime,"UTF-8",getAssets().open(name));
                }catch(Exception e){return new WebResourceResponse("text/plain","UTF-8",404,"Not found",Collections.emptyMap(),new ByteArrayInputStream(new byte[0]));}
            }
            @Override public void onPageFinished(WebView v,String url){ready=true;render();if(settingsPending){settingsPending=false;settings();}}
        });
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},4);
        SharedPreferences configuration=getSharedPreferences("config",0);
        if(configuration.getString("spoolman","").isEmpty())ui.post(this::settings);
    }
    public final class Bridge {
        @JavascriptInterface public void settingsForm(String operation,String raw){runOnUiThread(()->{
            try{
                if(inventoryBusy)throw new Exception("Aguarde terminar o salvamento no Spoolman.");
                JSONObject data=ConnectionConfig.validate(new JSONObject(raw));
                if("save".equals(operation)){
                    SharedPreferences p=getSharedPreferences("config",0);boolean networkChanged=!p.getString("printer","").equals(data.getString("printer"))||!p.getString("spoolman","").equals(data.getString("spoolman"))||!p.getString("printerKey","").equals(data.getString("printerKey"))||!p.getString("spoolKey","").equals(data.getString("spoolKey"));if(networkChanged)stopService(new Intent(MainActivity.this,SpoolService.class));boolean changed=!p.getString("spoolman","").equals(data.getString("spoolman"));
                    SharedPreferences.Editor editor=p.edit().putString("printer",data.getString("printer")).putString("spoolman",data.getString("spoolman")).putString("printerKey",data.getString("printerKey")).putString("spoolKey",data.getString("spoolKey")).putInt("timeout",data.getInt("timeout")).putString("language",data.getString("language"));if(changed)editor.remove("stock").remove("stockAt");editor.apply();applyLanguage();
                    managerStock=null;managerStockAt=0;registrationMode=false;settingsResult(new JSONObject().put("ok",true).put("saved",true).put("message",networkChanged?"Configurações salvas. Conecte a fábrica ou atualize o Armazém.":"Configurações salvas."));render();
                }else if("test".equals(operation)){
                    inventoryWork.execute(()->{JSONObject result=new JSONObject();try{
                        String printer=data.getString("printer"),spoolman=data.getString("spoolman");Api candidate=new Api(printer.isEmpty()?spoolman:printer,spoolman,data.getString("printerKey"),data.getString("spoolKey"));
                        try{int count=candidate.inventory().length();result.put("spoolman","OK · "+count+" rolos encontrados");}catch(Exception e){result.put("spoolman","Falha · "+e.getMessage()).put("spoolmanError",true);}
                        if(printer.isEmpty())result.put("printer","Não configurada · Armazém disponível");else try{candidate.status();result.put("printer",candidate.externalMode()?"OK · U1 em modo External":"U1 conectada · selecione Filament Detection = External");}catch(Exception e){result.put("printer","Falha · "+e.getMessage()).put("printerError",true);}
                        result.put("ok",!result.optBoolean("printerError")&&!result.optBoolean("spoolmanError"));
                    }catch(Exception e){try{result.put("ok",false).put("message",e.getMessage());}catch(Exception ignored){}}
                    runOnUiThread(()->settingsResult(result));});
                }
            }catch(Exception e){try{settingsResult(new JSONObject().put("ok",false).put("message",e.getMessage()));}catch(Exception ignored){}}
        });}
        @JavascriptInterface public void previewDelete(int id){runOnUiThread(()->{
            if(inventoryBusy)return;final Api api;try{api=inventoryApi();if(installedForDeletion(id))throw new Exception("Este rolo está indicado em um feeder ou associação pendente. Retire o filamento e atualize a U1 antes de excluir.");}catch(Exception e){inventoryError(e);return;}
            inventoryWork.execute(()->{JSONObject result=new JSONObject();try{JSONObject spool=(JSONObject)new InventoryManager(api).get("spool/"+id);runOnUiThread(()->{deleteCandidate=spool;try{inventoryResult(new JSONObject().put("deletePreview",spool));}catch(Exception ignored){}});}catch(Exception e){runOnUiThread(()->inventoryError(e));}});
        });}
        @JavascriptInterface public void deleteSpool(int id){runOnUiThread(()->{
            try{if(inventoryBusy||deleteCandidate==null||deleteCandidate.getInt("id")!=id)throw new Exception("Abra a confirmação de exclusão novamente.");if(installedForDeletion(id))throw new Exception("O rolo está indicado em um feeder. Retire e atualize a U1 antes de excluir.");
                final Api api=inventoryApi();final String identity=InventoryManager.identity(deleteCandidate);deleteCandidate=null;inventoryBusy=true;
                inventoryWork.execute(()->{JSONObject result=new JSONObject();try{new InventoryManager(api).delete(id,identity);forgetSpool(id);result.put("deleted",id).put("ok",true).put("message","Rolo #"+id+" excluído do Spoolman.");try{refreshInventory(api);}catch(Exception e){result.put("message","Rolo excluído. Atualize o estoque para obter a lista mais recente.");}}
                catch(Exception e){try{result.put("ok",false).put("deleteError",true).put("message","Exclusão não confirmada: "+e.getMessage()+". Atualize o estoque antes de repetir.");}catch(Exception ignored){}}
                runOnUiThread(()->{inventoryBusy=false;inventoryResult(result);});});
            }catch(Exception e){try{inventoryResult(new JSONObject().put("deleteError",true).put("ok",false).put("message",e.getMessage()));}catch(Exception ignored){}}
        });}
        @JavascriptInterface public void registration(boolean enabled){runOnUiThread(()->registrationMode=enabled);}
        @JavascriptInterface public void inspectTag(String raw){runOnUiThread(()->{
            final Api api;try{api=inventoryApi();}catch(Exception e){inventoryError(e);return;}
            inventoryWork.execute(()->{JSONObject result=new JSONObject();try{String u=InventoryManager.uid(new JSONObject().put("uid",raw));JSONArray all=api.inventory(),matches=new JSONArray();for(int i=0;i<all.length();i++)if(Api.uids(all.getJSONObject(i)).contains(u))matches.put(all.getJSONObject(i));result.put("inspection",true).put("uid",u).put("matches",matches).put("ok",true);}
                catch(Exception e){try{result.put("inspection",true).put("uid",raw).put("ok",false).put("message","Não foi possível verificar a tag: "+e.getMessage());}catch(Exception ignored){}}
                runOnUiThread(()->{inventoryResult(result);});});
        });}
        @JavascriptInterface public void catalog(){runOnUiThread(()->{
            final Api api;try{api=inventoryApi();}catch(Exception e){inventoryError(e);return;}
            inventoryWork.execute(()->{JSONObject result=new JSONObject();try{JSONObject catalog=new InventoryManager(api).catalog();refreshInventory(api);result.put("catalog",catalog).put("ok",true);}
                catch(Exception e){try{result.put("ok",false).put("message","Não foi possível consultar Spoolman: "+e.getMessage());}catch(Exception ignored){}}
                runOnUiThread(()->{inventoryResult(result);});});
        });}
        @JavascriptInterface public void saveInventory(String raw){runOnUiThread(()->{
            if(inventoryBusy){try{inventoryResult(new JSONObject().put("ok",false).put("message","Aguarde o Spoolman terminar a operação."));}catch(Exception ignored){}return;}
            final JSONObject data;final Api api;try{if(raw.length()>12000)throw new Exception("Formulário inválido.");data=new JSONObject(raw);api=inventoryApi();}catch(Exception e){inventoryError(e);return;}
            String operation=data.optString("mode"),title="create".equals(operation)?"Cadastrar novo rolo":"bind".equals(operation)?"Vincular NFC ao rolo #"+data.optInt("id"):"Atualizar rolo #"+data.optInt("id");
            new AlertDialog.Builder(MainActivity.this).setTitle(L10n.t(title)).setMessage(L10n.t("Salvar os dados no seu Spoolman? Isso não movimenta a impressora nem grava conteúdo na tag NFC.")).setNegativeButton(L10n.t("Voltar"),(d,w)->{try{inventoryResult(new JSONObject().put("cancelled",true));}catch(Exception ignored){}}).setPositiveButton(L10n.t("Salvar"),(d,w)->{
                inventoryBusy=true;inventoryWork.execute(()->{JSONObject result=new JSONObject();try{
                    JSONObject saved=new InventoryManager(api).save(data);result.put("ok",true).put("saved",saved).put("message","Rolo #"+saved.getInt("id")+" salvo no Spoolman.");
                    try{refreshInventory(api);}catch(Exception e){result.put("message","Rolo salvo. Atualize o estoque para confirmar os dados mais recentes.");}
                }catch(Exception e){try{result.put("ok",false).put("message",e.getMessage()+" Se houve falha de rede, atualize e confira o estoque antes de repetir; marca ou filamento podem já ter sido criados.");}catch(Exception ignored){}}
                runOnUiThread(()->{inventoryBusy=false;inventoryResult(result);});});
            }).setOnCancelListener(d->{try{inventoryResult(new JSONObject().put("cancelled",true));}catch(Exception ignored){}}).show();
        });}
        @JavascriptInterface public void manual(String operation,int channel,int spoolId){runOnUiThread(()->{
            if(!SpoolService.running){toast("Conecte à U1 primeiro.");return;}
            if(channel<0||channel>3||!Arrays.asList("ASSIGN","LOAD","UNLOAD").contains(operation))return;
            if("ASSIGN".equals(operation)&&spoolId<=0){toast("Escolha um rolo do estoque.");return;}
            String title="ASSIGN".equals(operation)?"Associar rolo #"+spoolId+" ao feeder "+(channel+1):("LOAD".equals(operation)?"Carregar":"Retirar")+" filamento do feeder "+(channel+1);
            String detail="ASSIGN".equals(operation)?"Confirme que este é o rolo conectado à entrada escolhida. A associação não transfere nem regrava tags NFC.":"A U1 pode aquecer o bico e movimentar as cabeças e o filamento. Confira a área da impressora antes de iniciar.";
            new AlertDialog.Builder(MainActivity.this).setTitle(L10n.t(title)).setMessage(L10n.t(detail)).setNegativeButton(L10n.t("Voltar"),null).setPositiveButton(L10n.t("Confirmar"),(d,w)->startService(new Intent(MainActivity.this,SpoolService.class).setAction(operation).putExtra("channel",channel).putExtra("spool_id",spoolId))).show();
        });}
        @JavascriptInterface public void action(String name){runOnUiThread(()->{
            switch(name){
                case "inventoryRefresh":catalog();break;
                case "registrationRead":if(nfc==null||!nfc.isEnabled())startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));else toast("Aproxime a tag para conferir o cadastro no Spoolman.");break;
                case "connect":if(SpoolService.running)stopService(new Intent(MainActivity.this,SpoolService.class));else connect();break;
                case "scan":if(nfc==null||!nfc.isEnabled())startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));else if(!SpoolService.running)connect();else toast("Aproxime a tag da traseira do celular.");break;
                case "cancel":command("CANCEL");break;
                case "settings":settings();break;
                case "firmware":try{String base=Api.base(getSharedPreferences("config",0).getString("printer",""));startActivity(new Intent(Intent.ACTION_VIEW,android.net.Uri.parse(base+"/firmware-config/")));}catch(Exception e){settings();}break;
                case "share":share();break;
            }
        });}
    }
    private void connect(){
        SharedPreferences configuration=getSharedPreferences("config",0);
        if(configuration.getString("printer","").isEmpty()||configuration.getString("spoolman","").isEmpty()){settings();return;}
        try{
            SharedPreferences p=getSharedPreferences("config",0);Api.base(p.getString("printer",""));Api.base(p.getString("spoolman",""));
            startForegroundService(new Intent(this,SpoolService.class).setAction("CONNECT"));
        }catch(Exception e){toast(e.getMessage());}
    }
    private void command(String action){if(SpoolService.running)startService(new Intent(this,SpoolService.class).setAction(action));}
    @Override protected void onResume(){super.onResume();if(nfc!=null){Bundle opts=new Bundle();opts.putInt(NfcAdapter.EXTRA_READER_PRESENCE_CHECK_DELAY,250);nfc.enableReaderMode(this,this,NfcAdapter.FLAG_READER_NFC_A|NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK,opts);}web.onResume();ui.post(refresh);}
    @Override protected void onPause(){ui.removeCallbacks(refresh);if(nfc!=null)nfc.disableReaderMode(this);web.onPause();super.onPause();}
    @Override protected void onDestroy(){ui.removeCallbacks(refresh);inventoryWork.shutdownNow();web.removeJavascriptInterface("Native");web.destroy();super.onDestroy();}
    @Override public void onTagDiscovered(Tag tag){
        byte[] bytes=tag.getId();if(bytes==null||(bytes.length!=4&&bytes.length!=7&&bytes.length!=10)){runOnUiThread(()->toast("Tag sem UID NFC compatível."));return;}
        StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02X",v&255));String uid=b.toString();
        long now=SystemClock.elapsedRealtime();synchronized(this){if(uid.equals(lastUid)&&now-lastTap<3000)return;lastUid=uid;lastTap=now;}
        if(registrationMode){runOnUiThread(()->{if(ready)web.evaluateJavascript("window.registrationTag("+JSONObject.quote(uid)+");",null);});return;}
        if(!SpoolService.running){runOnUiThread(()->toast("Abra Novo rolo no Armazém para cadastrar, ou conecte à U1 para associar."));return;}
        String type=Arrays.asList(tag.getTechList()).contains("android.nfc.tech.MifareClassic")?"M1":"NTAG";
        startService(new Intent(this,SpoolService.class).setAction("SCAN").putExtra("uid",uid).putExtra("type",type));
    }
    private void render(){
        if(!ready)return;
        try{
            JSONObject v=new JSONObject(SpoolService.view);SharedPreferences p=getSharedPreferences("config",0);
            boolean active=SpoolService.running;
            v.put("running",active).put("connected",active&&SystemClock.elapsedRealtime()-v.optLong("statusAt",0)<4000).put("nfc",nfc!=null&&nfc.isEnabled());
            if(!active){v.put("armed",false).put("manualOperation",false);v.put("stock",new JSONArray(p.getString("stock","[]"))).put("stockAt",p.getLong("stockAt",0));v.put("history",new JSONArray(Arrays.asList(p.getString("history","").split("\\n"))));}
            if(managerStock!=null&&managerStockAt>=v.optLong("stockAt",0))v.put("stock",managerStock).put("stockAt",managerStockAt);
            v.put("inventoryBusy",inventoryBusy).put("languagePreference",p.getString("language","auto")).put("language",L10n.resolve(p.getString("language","auto"),Locale.getDefault().getLanguage()));
            web.evaluateJavascript("window.updateApp("+v.toString()+");",null);
        }catch(Exception e){android.util.Log.e("SpoolManager","Falha ao atualizar tela",e);}
    }
    private void applyLanguage(){L10n.set(getSharedPreferences("config",0).getString("language","auto"),Locale.getDefault().getLanguage());}
    private void settings(){
        if(!ready){settingsPending=true;return;}
        SharedPreferences p=getSharedPreferences("config",0);
        try{JSONObject data=new JSONObject().put("printer",p.getString("printer","")).put("spoolman",p.getString("spoolman","")).put("printerKey",p.getString("printerKey","")).put("spoolKey",p.getString("spoolKey","")).put("timeout",p.getInt("timeout",180)).put("languagePreference",p.getString("language","auto")).put("language",L10n.resolve(p.getString("language","auto"),Locale.getDefault().getLanguage())).put("systemLanguage",Locale.getDefault().getLanguage());
            web.evaluateJavascript("window.openSettings("+data.toString()+");",null);
        }catch(Exception e){toast(e.getMessage());}
    }
    private void settingsResult(JSONObject result){if(ready&&!isDestroyed())web.evaluateJavascript("window.settingsResult("+result.toString()+");",null);}
    private boolean installedForDeletion(int id)throws Exception{
        JSONObject view=new JSONObject(SpoolService.view),status=view.optJSONObject("status");if(status==null)return false;
        for(int i=0;i<4;i++)if(Api.sensors(status)[i]&&Api.spoolId(status,i)==id)return true;
        return view.optBoolean("armed")&&view.optJSONObject("spool")!=null&&view.getJSONObject("spool").optInt("id")==id;
    }
    private void share(){
        try{JSONObject d=new JSONObject(SpoolService.view);d.put("app","Spool Factory 1.8.0");d.put("device",Build.MANUFACTURER+" "+Build.MODEL);d.put("android",Build.VERSION.RELEASE);
            Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,L10n.t("Diagnóstico Spool Factory")).putExtra(Intent.EXTRA_TEXT,d.toString(2));startActivity(Intent.createChooser(send,L10n.t("Compartilhar diagnóstico")));
        }catch(Exception e){toast(e.getMessage());}
    }
    private void toast(String text){Toast.makeText(this,L10n.t(text),Toast.LENGTH_LONG).show();}
}
