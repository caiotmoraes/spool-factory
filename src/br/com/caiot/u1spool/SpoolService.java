package br.com.caiot.u1spool;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Monitor da U1 em primeiro plano: estado, fluxo NFC (ler → inserir → confirmar),
 * ativar/carregar/retirar e controle. Regras e textos iguais ao U1 Factory Display (Spec v1):
 * borda estável 1,2 s; confirmação = 4 leituras em 20 s; prazo de inserção 60–600 s;
 * carregar/retirar e NFC→inserir bloqueados durante a impressão; associar/ativar permitidos.
 */
public class SpoolService extends Service {
    public static volatile String view="{}";
    public static volatile boolean running=false;
    static final int CONFIRM_READS=4, CONFIRM_WINDOW_MS=20000;
    private volatile boolean alive;
    private final ScheduledExecutorService work=Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService stockWork=Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService controlWork=Executors.newSingleThreadExecutor();
    private final ExecutorService discoverWork=Executors.newSingleThreadExecutor();
    /** Controle separado da carga/retirada (que pode levar 240 s); parada de emergência nunca espera fila. */
    private final ExecutorService ctlWork=Executors.newSingleThreadExecutor();
    private AssociationLedger ledger;
    private volatile boolean manualOperation, discovering;
    private boolean manualVerify;
    private volatile JSONArray stock=new JSONArray();
    private volatile String stockError="";
    private volatile int externalMode=-1; // -1 desconhecido, 0 não, 1 sim
    private volatile long stockAt;
    private long statusAt;
    private int failures;
    private final AtomicInteger generation=new AtomicInteger();
    private final EdgeDetector detector=new EdgeDetector();
    private volatile Api api;
    private volatile String host="";
    private JSONObject status, spool;
    private String uid="", tagType="NTAG", message="", kind="info", phase="";
    private volatile String klippy="";
    private JSONObject lastTag;
    private final ArrayList<String> history=new ArrayList<>();
    private int armGeneration, verifyChannel=-1, verifyCount;
    private long expires, verifyDeadline, lastLog, lastNotify;
    private PowerManager.WakeLock wake;
    private long now(){return SystemClock.elapsedRealtime();}
    private SharedPreferences prefs(){return getSharedPreferences("config",0);}
    @Override public void onCreate(){
        super.onCreate();try{L10n.load(getAssets().open("strings.json"));}catch(Exception e){android.util.Log.e("SpoolFactory","Localization unavailable",e);}L10n.set(prefs().getString("language","auto"),Locale.getDefault().getLanguage());alive=true;running=true;
        message=L10n.t("conn.searching");
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("monitor","Spool Factory",NotificationManager.IMPORTANCE_LOW));
        startForeground(31,notification(message));
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"U1Spool:monitor");
        wake.acquire(12*60*60*1000L);
        SharedPreferences p=prefs();
        String saved=p.getString("history","");if(!saved.isEmpty())history.addAll(Arrays.asList(saved.split("\n")));
        try{stock=new JSONArray(p.getString("stock","[]"));stockAt=p.getLong("stockAt",0);}catch(Exception ignored){}
        connect(false);
        stockWork.scheduleWithFixedDelay(()->{Api a=api;if(a==null)return;try{
            try{externalMode=a.printer.isEmpty()?-1:(a.externalMode()?1:0);}catch(Exception unavailable){externalMode=-1;}
            JSONArray loaded=a.inventory();if(!alive)return;
            stock=loaded;stockAt=System.currentTimeMillis();stockError="";
            prefs().edit().putString("stock",stock.toString()).putLong("stockAt",stockAt).apply();
        }catch(Exception e){stockError=L10n.t("conn.spoolman_down")+": "+e.getMessage();}},0,30,TimeUnit.SECONDS);
        work.scheduleWithFixedDelay(this::tick,0,500,TimeUnit.MILLISECONDS);
    }
    /** Resolve o host: IP manual nos Ajustes ou descoberta automática (IP lembrado primeiro). */
    private void connect(boolean force){
        SharedPreferences p=prefs();String manual=p.getString("printer","");
        if(!manual.isEmpty()){use(manual);return;}
        String remembered=p.getString("discovered","");
        if(!force&&!remembered.isEmpty()){use(remembered);return;}
        if(discovering)return;discovering=true;message=L10n.t("conn.searching");kind="info";publish();
        discoverWork.execute(()->{
            String found=Api.discover(remembered);discovering=false;if(!alive)return;
            if(found.isEmpty()){
                if(!p.getString("spoolman","").isEmpty())use("");
                message=L10n.t("conn.u1_not_found");kind="err";log(message);publish();return;}
            p.edit().putString("discovered",found).apply();use(found);
            message=L10n.t("conn.found","ip",found);kind="ok";log(message);publish();
        });
    }
    private void use(String h){
        SharedPreferences p=prefs();
        try{api=new Api(h,p.getString("spoolman",""),p.getString("printerKey",""),p.getString("spoolKey",""));host=h;}
        catch(Exception e){message=e.getMessage();kind="err";publish();return;}
        if(ledger==null)try{
            String binding=p.getString("ledgerPrinter",h);
            ledger=new AssociationLedger(binding.equals(h)&&p.getInt("identityModelVersion",0)>=2?p.getString("associations","{}"):"{}");
            saveLedger();p.edit().putInt("identityModelVersion",2).putBoolean("ledgerMigrated",true).putString("ledgerPrinter",h).apply();
        }catch(Exception e){try{ledger=new AssociationLedger("{}");}catch(Exception ignored){}}
    }
    private Notification notification(String text){
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop=new Intent(this,SpoolService.class).setAction("STOP");
        PendingIntent sp=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"monitor").setSmallIcon(br.com.caiot.u1spool.R.drawable.ic_spool).setContentTitle("Spool Factory").setContentText(text).setContentIntent(pi).setOngoing(true).addAction(new Notification.Action.Builder(null,L10n.english()?"Stop":"Parar",sp).build()).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        String action=intent==null||intent.getAction()==null?"":intent.getAction();
        switch(action){
            case "STOP":generation.incrementAndGet();stopSelf();return START_NOT_STICKY;
            case "RESCAN":work.execute(()->connect(true));break;
            case "SCAN":{int g=generation.incrementAndGet();String u=intent.getStringExtra("uid"),t=intent.getStringExtra("type");work.execute(()->scan(u,t,g));break;}
            case "CANCEL":generation.incrementAndGet();work.execute(()->{lastTag=null;cancel(L10n.t("nfc.cancelled"),"info");});break;
            case "ASSIGN":{int g=generation.incrementAndGet(),ch=intent.getIntExtra("channel",-1),spoolId=intent.getIntExtra("spool_id",0);work.execute(()->manualAssign(ch,spoolId,g));break;}
            case "LOAD":case "UNLOAD":{int ch=intent.getIntExtra("channel",-1);boolean load="LOAD".equals(action);work.execute(()->manualFeed(ch,load));break;}
            case "CONTROL":{String op=intent.getStringExtra("op");int a=intent.getIntExtra("a",0),b=intent.getIntExtra("b",0);if("estop".equals(op))new Thread(()->control(op,a,b),"estop").start();else ctlWork.execute(()->control(op,a,b));break;}
            case "TAG_CLEAR":work.execute(()->{lastTag=null;publish();});break;
        }
        return START_NOT_STICKY;
    }
    private boolean current(){return alive&&running&&armGeneration==generation.get();}
    private void saveLedger(){if(ledger!=null)prefs().edit().putString("associations",ledger.persisted()).apply();}
    private Api requireApi()throws Exception{Api a=api;if(a==null||a.printer.isEmpty())throw new Exception(L10n.t("conn.u1_not_found"));return a;}

    private void control(String op,int a,int b){
        try{Api c=requireApi();
            switch(op==null?"":op){
                case "pause":case "resume":case "cancel":c.printAction(op);break;
                case "estop":c.emergencyStop();break;
                case "restart":c.firmwareRestart();break;
                case "fan":c.setFan(a,b);break;
                case "temp":c.setTemp(a,b);break;
                default:throw new Exception("Operação inválida.");
            }
            message=L10n.t("common.sent");kind="ok";log(op+" · "+message);
        }catch(Exception e){message=e.getMessage();kind="err";log(message);}
        publish();
    }
    private void manualAssign(int ch,int id,int g){
        if(manualOperation){cancel(L10n.t("feed.busy_op"),"err");return;}
        armGeneration=g;detector.cancel();verifyChannel=-1;manualVerify=false;phase="";
        try{
            if(ch<0||ch>3||id<=0)throw new Exception(L10n.t("feeder.invalid"));
            Api c=requireApi();JSONObject selected=c.spool(id);
            if(!current())return;
            c.assign(ch,selected);spool=selected;JSONObject fresh=c.status();uid=Api.tagUid(fresh,ch);manualVerify=true;
            verifyChannel=ch;verifyCount=0;verifyDeadline=now()+CONFIRM_WINDOW_MS;expires=verifyDeadline;phase="confirming";
            message=L10n.t("nfc.confirming");kind="info";publish();
        }catch(Exception e){if(current())cancel(e.getMessage(),"err");}
    }
    private void manualFeed(int ch,boolean load){
        if(manualOperation){cancel(L10n.t("feed.busy_op"),"err");return;}
        try{
            if(detector.armed||verifyChannel>=0)throw new Exception(L10n.t("feed.busy_nfc"));
            Api c=requireApi();Api.feederScript(c.status(),ch,load);
            manualOperation=true;message=L10n.t(load?"feed.loading":"feed.unloading","ch",ch+1);kind="info";log(message);publish();
            controlWork.execute(()->{
                String error=null;try{if(alive)c.feed(ch,load);}catch(Exception e){error=e.getMessage();}
                final String result=error;if(!alive)return;
                work.execute(()->{
                    manualOperation=false;
                    try{JSONObject fresh=c.status();JSONObject cs=Api.channel(fresh,ch);String st=cs.optString("channel_state");
                        boolean done=load?"load_finish".equals(st):Arrays.asList("unload_finish","wait_insert").contains(st);
                        if(st.endsWith("_fail")){message=L10n.t("feed.fail","ch",ch+1);kind="err";}
                        else if(done){message=L10n.t(load?"feed.load_done":"feed.unload_done","ch",ch+1);kind="ok";}
                        else{message=L10n.t("feed.unconfirmed","ch",ch+1)+(result==null?"":" ("+result+")");kind="err";}
                    }catch(Exception e){message=L10n.t("feed.unconfirmed","ch",ch+1);kind="err";}
                    log(message);publish();
                });
            });
        }catch(Exception e){cancel(e.getMessage(),"err");}
    }
    private void scan(String u,String t,int g){
        if(manualOperation){message=L10n.t("feed.busy_op");kind="err";publish();return;}
        detector.cancel();verifyChannel=-1;manualVerify=false;uid=u;tagType=t;spool=null;armGeneration=g;phase="";
        message=L10n.t("nfc.reading","uid",uid);kind="info";lastTag=null;publish();
        try {
            Api c=api;if(c==null)throw new Exception(L10n.t("conn.u1_not_found"));
            JSONObject found;
            try{found=c.lookup(uid);}
            catch(Api.UnknownTag unknown){lastTag=new JSONObject().put("uid",uid).put("state","unknown").put("type",t);cancel(unknown.getMessage(),"info");return;}
            spool=found;lastTag=new JSONObject().put("uid",uid).put("state","known").put("spool",found).put("type",t);
            message=L10n.t("nfc.identified","id",found.getInt("id"));kind="ok";publish();
            if(c.printer.isEmpty())throw new Exception(L10n.t("conn.u1_not_found"));
            JSONObject fresh=c.status();
            if(!current())return;
            if(Api.printing(fresh))throw new Exception(L10n.t("nfc.printing"));
            c.requireExternal();
            int id=found.getInt("id");boolean[] sensors=Api.sensors(fresh);
            for(int i=0;i<4;i++)if(Api.spoolId(fresh,i)==id&&sensors[i])throw new Exception(L10n.t("nfc.already_in","ch",i+1));
            for(int i=0;i<4;i++){JSONObject ch=Api.channel(fresh,i);if(!"ok".equals(ch.optString("channel_error","ok")))throw new Exception(L10n.t("nfc.feeder_error"));}
            boolean available=false;for(boolean b:sensors)available|=!b;
            if(!available)throw new Exception(L10n.t("nfc.all_full"));
            status=fresh;
            long timeout=Math.max(60,Math.min(600,prefs().getInt("timeout",180)))*1000L;
            expires=now()+timeout;detector.arm(sensors,now(),timeout);phase="insert";
            message=L10n.t("nfc.insert");kind="info";
            log(L10n.t("nfc.identified","id",id)+" · UID "+uid);publish();
        }catch(Exception e){if(current())cancel(e.getMessage(),"err");}
    }
    private void tick(){
        Api c=api;if(c==null||c.printer.isEmpty()||!alive||!running)return;
        try {
            JSONObject fresh=c.status();if(!alive)return;status=fresh;statusAt=now();failures=0;klippy="";
            if(ledger!=null&&ledger.observe(fresh))saveLedger();
            boolean busy=Api.printing(fresh);
            if((detector.armed||verifyChannel>=0)&&!current()){detector.cancel();verifyChannel=-1;phase="";}
            if(detector.armed&&busy)cancel(L10n.t("nfc.state_changed"),"err");
            if(detector.armed){
                int ch=detector.update(Api.sensors(fresh),now());
                if(ch==-2)cancel(L10n.t("nfc.two_feeders"),"err");
                else if(ch==-3)cancel(L10n.t("nfc.timeout"),"err");
                else if(ch==-4)cancel(L10n.t("nfc.removed"),"err");
                else if(ch>=0){
                    JSONObject cs=Api.channel(fresh,ch);
                    String state=cs.optString("channel_state");
                    if(!"ok".equals(cs.optString("channel_error","ok")))cancel(L10n.t("feeder.error","ch",ch+1),"err");
                    else if(state.endsWith("_fail"))cancel(L10n.t("feed.fail","ch",ch+1),"err");
                    else if("preload_finish".equals(state)||"load_finish".equals(state)){
                        if(now()>=expires){cancel(L10n.t("nfc.timeout"),"err");return;}
                        // Revalida antes de gravar: a tag pode ter mudado de dono desde a leitura.
                        JSONObject check=c.lookup(uid);
                        if(check.getInt("id")!=spool.getInt("id"))throw new Exception(L10n.t("nfc.state_changed"));
                        JSONObject latest=c.status();
                        int decision=detector.update(Api.sensors(latest),now());
                        if(decision!=ch||Api.printing(latest)){cancel(L10n.t("nfc.state_changed"),"err");return;}
                        if(!current())return;
                        c.inject(ch,uid,tagType);detector.cancel();verifyChannel=ch;verifyCount=0;verifyDeadline=now()+CONFIRM_WINDOW_MS;phase="confirming";
                        message=L10n.t("nfc.confirming");kind="info";
                    }else{phase="feeding";message=L10n.t("nfc.feeding","ch",ch+1);kind="info";}
                }
            }else if(verifyChannel>=0){
                int ch=verifyChannel;
                if(!manualVerify&&!Api.sensors(fresh)[ch])cancel(L10n.t("nfc.removed"),"err");
                else if(Api.spoolId(fresh,ch)==spool.getInt("id")&&(manualVerify||uid.equals(Api.tagUid(fresh,ch)))){
                    if(++verifyCount>=CONFIRM_READS){
                        verifyChannel=-1;phase="";
                        if(ledger!=null){ledger.record(ch,spool.getInt("id"),manualVerify?Api.tagUid(fresh,ch):uid,manualVerify?"manual":"NFC");saveLedger();}
                        message=manualVerify?L10n.t("assign.done","id",spool.getInt("id"),"ch",ch+1):L10n.t("nfc.linked","ch",ch+1,"spool",Api.label(spool));kind="ok";
                        lastTag=null;log(message);vibrate();
                    }
                }else {verifyCount=0;if(now()>verifyDeadline)cancel(L10n.t("nfc.not_confirmed","ch",ch+1),"err");}
            }
            if(message.equals(L10n.t("conn.searching"))||message.equals(L10n.t("conn.offline"))||message.startsWith(L10n.t("conn.found","ip",""))){message=L10n.t("nfc.tap");kind="info";}
            publish();
        }catch(Exception e){
            if(!alive)return;
            if(e instanceof Api.NotReady){klippy=((Api.NotReady)e).state;failures=0;}else klippy="";
            if(ledger!=null)ledger.disconnect();
            if(detector.armed||verifyChannel>=0)log(L10n.t("nfc.state_changed"));
            detector.cancel();verifyChannel=-1;phase="";
            message=klippy.isEmpty()?L10n.t("conn.offline"):e.getMessage();kind="err";
            if(now()-lastLog>15000){log(message+": "+e.getMessage());lastLog=now();}
            // Sem IP manual: depois de ~15 s offline, procura a U1 de novo (DHCP pode ter mudado o IP).
            if(++failures==30&&prefs().getString("printer","").isEmpty())connect(true);
            publish();
        }
    }
    private void cancel(String why,String k){detector.cancel();verifyChannel=-1;phase="";message=why;kind=k;log(why);publish();}
    private void log(String text){
        String time=new java.text.SimpleDateFormat("dd/MM HH:mm:ss",Locale.getDefault()).format(new Date());
        synchronized(history){history.add(0,time+" · "+text);while(history.size()>60)history.remove(history.size()-1);
        prefs().edit().putString("history",String.join("\n",history)).apply();}
    }
    private void vibrate(){try{getSystemService(Vibrator.class).vibrate(VibrationEffect.createOneShot(180,VibrationEffect.DEFAULT_AMPLITUDE));}catch(Exception ignored){}}
    private synchronized void publish(){try{
        if(!alive)return;
        Api c=api;
        JSONObject v=new JSONObject().put("message",message).put("kind",kind).put("phase",phase).put("armed",detector.armed||verifyChannel>=0).put("remaining",Math.max(0,(expires-now())/1000)).put("uid",uid)
            .put("stock",stock).put("stockAt",stockAt).put("stockError",stockError).put("statusAt",statusAt).put("host",host).put("discovering",discovering)
            .put("proxy",c==null||c.viaProxy()).put("manualOperation",manualOperation).put("externalMode",externalMode).put("klippy",klippy);
        synchronized(history){v.put("history",new JSONArray(history));}
        if(verifyChannel>=0)v.put("verifyChannel",verifyChannel);
        if(ledger!=null)v.put("associations",ledger.snapshot());
        if(lastTag!=null)v.put("tag",lastTag);
        if(spool!=null)v.put("spool",spool);if(status!=null)v.put("status",status);view=v.toString();
        if(now()-lastNotify>2500){getSystemService(NotificationManager.class).notify(31,notification(message));lastNotify=now();}
    }catch(Exception ignored){}}
    @Override public void onDestroy(){alive=false;running=false;generation.incrementAndGet();work.shutdownNow();stockWork.shutdownNow();controlWork.shutdownNow();discoverWork.shutdownNow();ctlWork.shutdownNow();if(wake!=null&&wake.isHeld())wake.release();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
