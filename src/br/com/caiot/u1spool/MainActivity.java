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
    private boolean ready;
    private String lastUid="";
    private long lastTap;
    private static final int BG=0xff080f23,CARD=0xff18232d,TEXT=0xffeef5fa,MUTED=0xffa8bac8,ACCENT=0xff51d8b5;
    private int dp(int v){return (int)(v*getResources().getDisplayMetrics().density+.5f);}
    private final Runnable refresh=new Runnable(){@Override public void run(){render();ui.postDelayed(this,500);}};
    @Override public void onCreate(Bundle b){
        super.onCreate(b);nfc=NfcAdapter.getDefaultAdapter(this);
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
            @Override public void onPageFinished(WebView v,String url){ready=true;render();}
        });
        web.loadUrl("https://appassets.androidplatform.net/assets/index.html");
        if(Build.VERSION.SDK_INT>=33&&checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},4);
        SharedPreferences configuration=getSharedPreferences("config",0);
        if(configuration.getString("printer","").isEmpty()||configuration.getString("spoolman","").isEmpty())ui.post(this::settings);
    }
    public final class Bridge {
        @JavascriptInterface public void manual(String operation,int channel,int spoolId){runOnUiThread(()->{
            if(!SpoolService.running){toast("Conecte à U1 primeiro.");return;}
            if(channel<0||channel>3||!Arrays.asList("ASSIGN","LOAD","UNLOAD").contains(operation))return;
            if("ASSIGN".equals(operation)&&spoolId<=0){toast("Escolha um rolo do estoque.");return;}
            String title="ASSIGN".equals(operation)?"Associar rolo #"+spoolId+" ao feeder "+(channel+1):("LOAD".equals(operation)?"Carregar":"Retirar")+" filamento do feeder "+(channel+1);
            String detail="ASSIGN".equals(operation)?"Confirme que este é o rolo conectado à entrada escolhida. A associação não transfere nem regrava tags NFC.":"A U1 pode aquecer o bico e movimentar as cabeças e o filamento. Confira a área da impressora antes de iniciar.";
            new AlertDialog.Builder(MainActivity.this).setTitle(title).setMessage(detail).setNegativeButton("Voltar",null).setPositiveButton("Confirmar",(d,w)->startService(new Intent(MainActivity.this,SpoolService.class).setAction(operation).putExtra("channel",channel).putExtra("spool_id",spoolId))).show();
        });}
        @JavascriptInterface public void action(String name){runOnUiThread(()->{
            switch(name){
                case "connect":if(SpoolService.running)stopService(new Intent(MainActivity.this,SpoolService.class));else connect();break;
                case "scan":if(nfc==null||!nfc.isEnabled())startActivity(new Intent(Settings.ACTION_NFC_SETTINGS));else if(!SpoolService.running)connect();else toast("Aproxime a tag da traseira do celular.");break;
                case "cancel":command("CANCEL");break;
                case "settings":settings();break;
                case "share":share();break;
            }
        });}
    }
    private TextView text(LinearLayout parent,String value,int size,int color,boolean bold){
        TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setPadding(0,dp(5),0,dp(7));t.setLineSpacing(dp(3),1);
        if(bold)t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);parent.addView(t,new LinearLayout.LayoutParams(-1,-2));return t;
    }
    private LinearLayout card(LinearLayout parent){
        LinearLayout c=new LinearLayout(this);c.setOrientation(LinearLayout.VERTICAL);c.setPadding(dp(16),dp(12),dp(16),dp(12));
        GradientDrawable bg=new GradientDrawable();bg.setColor(CARD);bg.setCornerRadius(dp(18));c.setBackground(bg);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.topMargin=dp(12);lp.bottomMargin=dp(4);parent.addView(c,lp);return c;
    }
    private Button button(LinearLayout parent,String value,View.OnClickListener listener){
        Button b=new Button(this);b.setText(value);b.setAllCaps(false);b.setTextColor(TEXT);b.setOnClickListener(listener);
        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(parent.getOrientation()==LinearLayout.HORIZONTAL?0:-1,-2);
        if(parent.getOrientation()==LinearLayout.HORIZONTAL)lp.weight=1;parent.addView(b,lp);return b;
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
    @Override protected void onDestroy(){ui.removeCallbacks(refresh);web.removeJavascriptInterface("Native");web.destroy();super.onDestroy();}
    @Override public void onTagDiscovered(Tag tag){
        if(!SpoolService.running){runOnUiThread(()->toast("Toque em Conectar antes de ler o rolo."));return;}
        byte[] bytes=tag.getId();if(bytes==null||(bytes.length!=4&&bytes.length!=7&&bytes.length!=10)){runOnUiThread(()->toast("Tag sem UID NFC compatível."));return;}
        StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02X",v&255));String uid=b.toString();
        long now=SystemClock.elapsedRealtime();synchronized(this){if(uid.equals(lastUid)&&now-lastTap<3000)return;lastUid=uid;lastTap=now;}
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
            web.evaluateJavascript("window.updateApp("+v.toString()+");",null);
        }catch(Exception e){android.util.Log.e("SpoolManager","Falha ao atualizar tela",e);}
    }
    private String state(String s){switch(s){case "wait_insert":return "Aguardando inserção";case "preload_prepare":return "Preparando alimentação";case "preload_feeding":return "Puxando filamento";case "preload_finish":return "Alimentação concluída";case "load_finish":return "Carregamento concluído";default:return s.replace('_',' ');}}
    private void settings(){
        SharedPreferences prefs=getSharedPreferences("config",0);
        LinearLayout box=new LinearLayout(this);box.setOrientation(LinearLayout.VERTICAL);box.setPadding(dp(20),dp(8),dp(20),dp(8));
        EditText p=field(box,"Endereço da U1",prefs.getString("printer",""),false);
        EditText s=field(box,"Endereço do Spoolman",prefs.getString("spoolman",""),false);
        p.setHint("192.168.1.50 ou http://u1.local");s.setHint("http://spoolman.local:7912");
        EditText pk=field(box,"Chave API da U1 (opcional)",prefs.getString("printerKey",""),true);
        EditText sk=field(box,"Chave API do Spoolman (opcional)",prefs.getString("spoolKey",""),true);
        EditText tm=field(box,"Prazo para inserir (60–600 segundos)",""+prefs.getInt("timeout",180),false);tm.setInputType(2);
        TextView testStatus=text(box,"Informe os endereços da sua U1 e do Spoolman. Rede local ou VPN/Tailscale da sua conta. O botão Testar apenas consulta os serviços.",13,MUTED,false);
        ScrollView sc=new ScrollView(this);sc.addView(box);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Conexão e leitura").setView(sc).setNegativeButton("Voltar",null).setNeutralButton("Testar",null).setPositiveButton("Salvar",null).create();
        dialog.setOnShowListener(d->{dialog.getButton(-3).setOnClickListener(v->{try{
            Api candidate=new Api(p.getText().toString(),s.getText().toString(),pk.getText().toString().trim(),sk.getText().toString().trim());
            testStatus.setText("Testando U1 e Spoolman…");dialog.getButton(-3).setEnabled(false);
            new Thread(()->{String result;try{candidate.status();int count=candidate.inventory().length();result="Conexão OK: U1 pronta; "+count+" rolos encontrados no Spoolman.";}catch(Exception e){result="Teste falhou: "+e.getMessage();}
                final String message=result;runOnUiThread(()->{if(dialog.isShowing()){testStatus.setText(message);dialog.getButton(-3).setEnabled(true);}});
            },"connection-test").start();
        }catch(Exception e){testStatus.setText(e.getMessage());}});
        dialog.getButton(-1).setOnClickListener(v->{try{
            String printer=Api.base(p.getText().toString()),spoolman=Api.base(s.getText().toString());int timeout=Integer.parseInt(tm.getText().toString());if(timeout<60||timeout>600)throw new Exception("Prazo deve ser de 60 a 600 segundos.");
            stopService(new Intent(this,SpoolService.class));
            prefs.edit().putString("printer",printer).putString("spoolman",spoolman).putString("printerKey",pk.getText().toString().trim()).putString("spoolKey",sk.getText().toString().trim()).putInt("timeout",timeout).remove("stock").remove("stockAt").apply();
            dialog.dismiss();toast("Configuração salva. Toque em Conectar.");
        }catch(Exception e){toast(e.getMessage());}});});dialog.show();
    }
    private EditText field(LinearLayout box,String label,String value,boolean secret){
        text(box,label,13,MUTED,false);EditText e=new EditText(this);e.setSingleLine(true);e.setTextColor(TEXT);e.setText(value);e.setInputType(secret?129:17);box.addView(e);return e;
    }
    private void share(){
        try{JSONObject d=new JSONObject(SpoolService.view);d.put("app","Spool Manager 1.3.0");d.put("device",Build.MANUFACTURER+" "+Build.MODEL);d.put("android",Build.VERSION.RELEASE);
            Intent send=new Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_SUBJECT,"Diagnóstico Spool Manager").putExtra(Intent.EXTRA_TEXT,d.toString(2));startActivity(Intent.createChooser(send,"Compartilhar diagnóstico"));
        }catch(Exception e){toast(e.getMessage());}
    }
    private void toast(String text){Toast.makeText(this,text,Toast.LENGTH_LONG).show();}
}
