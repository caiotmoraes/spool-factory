package br.com.caiot.u1spool;

import android.app.*;
import android.content.*;
import android.os.*;
import org.json.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

public class SpoolService extends Service {
    public static volatile String view="{}";
    public static volatile boolean running=false;
    private volatile boolean alive;
    private final ScheduledExecutorService work=Executors.newSingleThreadScheduledExecutor();
    private final ScheduledExecutorService stockWork=Executors.newSingleThreadScheduledExecutor();
    private final ExecutorService controlWork=Executors.newSingleThreadExecutor();
    private AssociationLedger ledger;
    private volatile boolean manualOperation;
    private boolean manualVerify;
    private volatile JSONArray stock=new JSONArray();
    private volatile String stockError="";
    private volatile boolean externalMode;
    private volatile long stockAt;
    private long statusAt;
    private final AtomicInteger generation=new AtomicInteger();
    private final EdgeDetector detector=new EdgeDetector();
    private Api api;
    private JSONObject status, spool;
    private String uid="", tagType="NTAG", message="Conectando à U1…";
    private final ArrayList<String> history=new ArrayList<>();
    private int armGeneration, verifyChannel=-1, verifyCount;
    private long expires, verifyDeadline, lastLog, lastNotify;
    private PowerManager.WakeLock wake;
    private long now(){return SystemClock.elapsedRealtime();}
    @Override public void onCreate(){
        super.onCreate();alive=true;running=true;
        NotificationManager nm=getSystemService(NotificationManager.class);
        nm.createNotificationChannel(new NotificationChannel("monitor","Leitura e associação de rolos",NotificationManager.IMPORTANCE_LOW));
        startForeground(31,notification("Monitorando feeders da U1"));
        wake=((PowerManager)getSystemService(POWER_SERVICE)).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"U1Spool:monitor");
        wake.acquire(12*60*60*1000L);
        SharedPreferences p=getSharedPreferences("config",0);
        try {api=new Api(p.getString("printer",""),p.getString("spoolman",""),p.getString("printerKey",""),p.getString("spoolKey",""));}
        catch(Exception e){message=e.getMessage();publish();stopSelf();return;}
        String saved=p.getString("history","");if(!saved.isEmpty())history.addAll(Arrays.asList(saved.split("\n")));
        try{
            String binding=p.getString("ledgerPrinter",api.printer);
            ledger=new AssociationLedger(binding.equals(api.printer)&&p.getInt("identityModelVersion",0)>=2?p.getString("associations","{}"):"{}");
            saveLedger();p.edit().putInt("identityModelVersion",2).putBoolean("ledgerMigrated",true).putString("ledgerPrinter",api.printer).apply();
        }catch(Exception e){try{ledger=new AssociationLedger("{}");}catch(Exception ignored){}}
        try{stock=new JSONArray(p.getString("stock","[]"));stockAt=p.getLong("stockAt",0);}catch(Exception ignored){}
        stockWork.scheduleWithFixedDelay(()->{try{
            externalMode=api.externalMode();
            JSONArray loaded=api.inventory();if(!alive)return;
            stock=loaded;stockAt=System.currentTimeMillis();stockError="";
            getSharedPreferences("config",0).edit().putString("stock",stock.toString()).putLong("stockAt",stockAt).apply();
        }catch(Exception e){stockError="Estoque indisponível: "+e.getMessage();}},0,30,TimeUnit.SECONDS);
        work.scheduleWithFixedDelay(this::tick,0,450,TimeUnit.MILLISECONDS);
    }
    private Notification notification(String text){
        Intent open=new Intent(this,MainActivity.class);
        PendingIntent pi=PendingIntent.getActivity(this,0,open,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Intent stop=new Intent(this,SpoolService.class).setAction("STOP");
        PendingIntent sp=PendingIntent.getService(this,1,stop,PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        return new Notification.Builder(this,"monitor").setSmallIcon(br.com.caiot.u1spool.R.drawable.ic_spool).setContentTitle("Spool Factory").setContentText(text).setContentIntent(pi).setOngoing(true).addAction(new Notification.Action.Builder(null,"Parar",sp).build()).build();
    }
    @Override public int onStartCommand(Intent intent,int flags,int id){
        String action=intent==null?"":intent.getAction();
        if("STOP".equals(action)){generation.incrementAndGet();stopSelf();return START_NOT_STICKY;}
        if("SCAN".equals(action)){
            int g=generation.incrementAndGet();String u=intent.getStringExtra("uid"),t=intent.getStringExtra("type");
            work.execute(()->scan(u,t,g));
        } else if("CANCEL".equals(action)){
            generation.incrementAndGet();work.execute(()->cancel("Associação cancelada. Aproxime outra tag."));
        } else if("ASSIGN".equals(action)){
            int g=generation.incrementAndGet(),ch=intent.getIntExtra("channel",-1),spoolId=intent.getIntExtra("spool_id",0);
            work.execute(()->manualAssign(ch,spoolId,g));
        } else if("LOAD".equals(action)||"UNLOAD".equals(action)){
            int ch=intent.getIntExtra("channel",-1);boolean load="LOAD".equals(action);
            work.execute(()->manualFeed(ch,load));
        }
        return START_NOT_STICKY;
    }
    private boolean current(){return alive&&running&&armGeneration==generation.get();}
    private void saveLedger(){if(ledger!=null)getSharedPreferences("config",0).edit().putString("associations",ledger.persisted()).apply();}
    private void manualAssign(int ch,int id,int g){
        if(manualOperation){cancel("Aguarde a operação física terminar.");return;}
        armGeneration=g;detector.cancel();verifyChannel=-1;manualVerify=false;
        try{
            if(ch<0||ch>3||id<=0)throw new Exception("Escolha um feeder e um rolo válido.");
            JSONObject selected=api.spool(id);JSONObject fresh=api.status();Api.idleForManual(fresh);
            if(!current())return;
            api.assign(ch,selected);spool=selected;uid=Api.tagUid(fresh,ch);manualVerify=true;
            verifyChannel=ch;verifyCount=0;verifyDeadline=now()+20000;expires=verifyDeadline;
            message="Confirmando rolo #"+id+" no feeder "+(ch+1)+"…";publish();
        }catch(Exception e){if(current())cancel(e.getMessage());}
    }
    private void manualFeed(int ch,boolean load){
        if(manualOperation){cancel("Aguarde a operação física terminar.");return;}
        try{
            if(detector.armed||verifyChannel>=0)throw new Exception("Finalize ou cancele a associação antes de movimentar o filamento.");
            Api.feederScript(api.status(),ch,load);
            manualOperation=true;message=(load?"Carregando":"Retirando")+" filamento do feeder "+(ch+1)+". Acompanhe a U1.";log(message);publish();
            controlWork.execute(()->{
                String error=null;try{if(alive)api.feed(ch,load);}catch(Exception e){error=e.getMessage();}
                final String result=error;if(!alive)return;
                work.execute(()->{
                    manualOperation=false;
                    if(result!=null)message="Operação não confirmada: "+result+". Confira a U1 antes de repetir.";
                    else try{JSONObject fresh=api.status();String phase=Api.channel(fresh,ch).optString("channel_state");
                        boolean done=load?"load_finish".equals(phase):Arrays.asList("unload_finish","wait_insert").contains(phase);
                        message=done?(load?"Carregamento":"Retirada")+" concluído no feeder "+(ch+1):"Comando enviado ao feeder "+(ch+1)+". Estado: "+phase;
                    }catch(Exception e){message="Comando enviado. Não foi possível confirmar o estado; confira a U1.";}
                    log(message);publish();
                });
            });
        }catch(Exception e){cancel(e.getMessage());}
    }
    private void scan(String u,String t,int g){
        if(manualOperation){message="Aguarde a operação física terminar antes de ler outra tag.";publish();return;}
        detector.cancel();verifyChannel=-1;manualVerify=false;uid=u;tagType=t;spool=null;armGeneration=g;
        message="Identificando tag "+uid+"…";publish();
        try {
            api.requireExternal();
            JSONObject found=api.lookup(uid);
            JSONObject fresh=api.status();
            if(!current())return;
            if(Api.printing(fresh))throw new Exception("Impressora imprimindo ou pausada. Faça a troca após finalizar.");
            int id=found.getInt("id");
            for(int i=0;i<4;i++)if(Api.spoolId(fresh,i)==id && Api.sensors(fresh)[i])throw new Exception("Rolo já associado ao feeder "+(i+1)+" e com filamento presente.");
            boolean[] sensors=Api.sensors(fresh);boolean available=false;for(boolean b:sensors)available|=!b;
            if(!available)throw new Exception("Todos os feeders têm filamento. Retire o antigo e leia a tag novamente.");
            status=fresh;spool=found;
            long timeout=getSharedPreferences("config",0).getInt("timeout",180)*1000L;
            expires=now()+timeout;detector.arm(sensors,now(),timeout);
            message="Rolo identificado. Insira o filamento no feeder desejado.";
            log("Lido "+Api.label(spool)+" · UID "+uid);publish();
        }catch(Exception e){if(current()){cancel(e.getMessage());}}
    }
    private void tick(){
        if(api==null||!alive||!running)return;
        try {
            JSONObject fresh=api.status();if(!alive)return;status=fresh;statusAt=now();
            if(ledger!=null&&ledger.observe(fresh))saveLedger();
            boolean busy=Api.printing(fresh);
            if((detector.armed||verifyChannel>=0)&&!current()){detector.cancel();verifyChannel=-1;}
            if((detector.armed||verifyChannel>=0)&&busy)cancel("A impressão começou. Associação cancelada.");
            if(detector.armed){
                int ch=detector.update(Api.sensors(fresh),now());
                if(ch==-2)cancel("Dois feeders foram ativados. Leia a tag e insira em apenas um.");
                else if(ch==-3)cancel("Prazo encerrado. Leia a tag novamente.");
                else if(ch==-4)cancel("Filamento retirado antes de estabilizar. Leia novamente.");
                else if(ch>=0){
                    JSONObject c=Api.channel(fresh,ch);
                    String state=c.getString("channel_state");
                    if(!"ok".equals(c.optString("channel_error")))cancel("Feeder "+(ch+1)+" com erro: "+c.optString("channel_error"));
                    else if(state.endsWith("_fail"))cancel("Falha ao alimentar o feeder "+(ch+1));
                    else if("preload_finish".equals(state)||"load_finish".equals(state)){
                        if(now()>=expires){cancel("Prazo encerrado. Leia a tag novamente.");return;}
                        // Resolve again before writing: UID ownership may have changed since the tap.
                        JSONObject check=api.lookup(uid);
                        if(check.getInt("id")!=spool.getInt("id"))throw new Exception("Cadastro da tag mudou. Leia novamente.");
                        JSONObject latest=api.status();
                        int decision=detector.update(Api.sensors(latest),now());
                        if(decision!=ch||Api.printing(latest)){cancel("Estado da U1 mudou. Leia novamente.");return;}
                        if(!current())return;
                        api.inject(ch,uid,tagType);detector.cancel();verifyChannel=ch;verifyCount=0;verifyDeadline=now()+20000;
                        message="Feeder "+(ch+1)+" detectado. Confirmando associação…";
                    }else message="Feeder "+(ch+1)+" detectado. Aguardando terminar a alimentação…";
                }
            }else if(verifyChannel>=0){
                int ch=verifyChannel;
                if(!manualVerify&&!Api.sensors(fresh)[ch])cancel("Filamento retirado durante a confirmação. Leia novamente.");
                else if(Api.spoolId(fresh,ch)==spool.getInt("id")&&(manualVerify||uid.equals(Api.tagUid(fresh,ch)))){
                    if(++verifyCount>=4){
                        verifyChannel=-1;
                        if(ledger!=null){ledger.record(ch,spool.getInt("id"),uid,manualVerify?"manual":"NFC");saveLedger();}
                        message="Associado ao feeder "+(ch+1)+": "+Api.label(spool);
                        log(message);vibrate();
                    }
                }else {verifyCount=0;if(now()>verifyDeadline)cancel("Associação não confirmada pelo SpoolLink. Verifique Spoolman e firmware; leia novamente.");}
            }
            if("Conectando à U1…".equals(message)||message.startsWith("Sem conexão"))message="Conectado. Aproxime a tag de um rolo.";
            publish();
        }catch(Exception e){
            if(!alive)return;
            if(ledger!=null)ledger.disconnect();
            detector.cancel();verifyChannel=-1;
            message="Sem conexão ou resposta válida: "+e.getMessage()+". Reconecte e leia a tag novamente.";
            if(now()-lastLog>15000){log(message);lastLog=now();}publish();
        }
    }
    private void cancel(String why){detector.cancel();verifyChannel=-1;message=why;log(why);publish();}
    private void log(String text){
        String time=new java.text.SimpleDateFormat("dd/MM HH:mm:ss",Locale.getDefault()).format(new Date());
        history.add(0,time+" · "+text);while(history.size()>60)history.remove(history.size()-1);
        getSharedPreferences("config",0).edit().putString("history",String.join("\n",history)).apply();
    }
    private void vibrate(){try{getSystemService(Vibrator.class).vibrate(VibrationEffect.createOneShot(180,VibrationEffect.DEFAULT_AMPLITUDE));}catch(Exception ignored){}}
    private void publish(){try{
        if(!alive)return;
        JSONObject v=new JSONObject().put("message",message).put("armed",detector.armed||verifyChannel>=0).put("remaining",Math.max(0,(expires-now())/1000)).put("uid",uid).put("history",new JSONArray(history)).put("stock",stock).put("stockAt",stockAt).put("stockError",stockError).put("statusAt",statusAt);
        v.put("manualOperation",manualOperation).put("externalMode",externalMode);if(ledger!=null)v.put("associations",ledger.snapshot());
        if(spool!=null)v.put("spool",spool);if(status!=null)v.put("status",status);view=v.toString();
        if(now()-lastNotify>2500){getSystemService(NotificationManager.class).notify(31,notification(message));lastNotify=now();}
    }catch(Exception ignored){}}
    @Override public void onDestroy(){alive=false;running=false;generation.incrementAndGet();work.shutdownNow();stockWork.shutdownNow();controlWork.shutdownNow();if(wake!=null&&wake.isHeld())wake.release();stopForeground(STOP_FOREGROUND_REMOVE);super.onDestroy();}
    @Override public IBinder onBind(Intent i){return null;}
}
