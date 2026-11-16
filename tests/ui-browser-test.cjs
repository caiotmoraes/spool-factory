'use strict';
// Teste da UI no Chromium (Playwright) com um Native falso; também gera as capturas de tela.
// Uso: node tests/ui-browser-test.cjs   (SPOOL_TOOLS_DIR/ui-test/node_modules/playwright-core; CHROME=caminho opcional; OUT=pasta das PNGs)
const path=require('node:path'),http=require('node:http'),fs=require('node:fs'),assert=require('node:assert/strict');
const {chromium}=require((process.env.SPOOL_TOOLS_DIR||path.resolve(__dirname,'../tools'))+'/ui-test/node_modules/playwright-core');
const root=path.resolve(__dirname,'../assets'),out=process.env.OUT||path.resolve(__dirname,'../build/ui');fs.mkdirSync(out,{recursive:true});
const types={'.html':'text/html','.css':'text/css','.js':'application/javascript','.json':'application/json','.woff2':'font/woff2'};
const server=http.createServer((req,res)=>{if(req.url==='/favicon.ico'){res.writeHead(204);return res.end();}const file=path.join(root,decodeURIComponent(req.url.split('?')[0]==='/'?'/index.html':req.url.split('?')[0]));
 if(!file.startsWith(root+path.sep)||!fs.existsSync(file)){res.writeHead(404);return res.end();}res.writeHead(200,{'Content-Type':types[path.extname(file)]||'application/octet-stream'});fs.createReadStream(file).pipe(res);});

// Dados fictícios (não são o estoque real).
const fil=(id,v,m,n,c,extra={})=>({id,name:n,material:m,color_hex:c,vendor:{id:id,name:v},weight:1000,density:1.24,diameter:1.75,...extra});
const sp=(id,f,rem,loc,uids,extra={})=>({id,filament:f,remaining_weight:rem,initial_weight:1000,location:loc,archived:false,extra:uids==null?{}:{card_uids:uids},...extra});
const stock=[
 sp(24,fil(1,'SUNLU','PLA','White','F7F7F4'),578,'Snapmaker U1','"53F595B4950001"'),
 sp(29,fil(2,'Masterprint','PETG','White','FFFFFF'),1000,'Armário','"04A1B2C3D4E5F6"'),
 sp(33,fil(3,'Masterprint','PETG','Gray','8B9096'),250,'Snapmaker U1','"04A1B2C3D4E5F7"'),
 sp(38,fil(4,'Masterprint','PETG','Preto','2B1B14'),107,'Snapmaker U1','"53F599B4950001,ABC"'),
 sp(16,fil(5,'Masterprint','PETG','Gray','808080'),29,'Armário','"04A1B2C3"'),
 sp(17,fil(6,'Masterprint','PETG','Orange','FF4B00'),121,'Armário','[\"04a1b2c4\"]'),
 sp(19,fil(7,'Masterprint','PETG','Gold','DFA62A'),845,'Armário',null),
 sp(41,fil(8,'Voolt3D','PLA','Silk trio','DFA62A',{multi_color_hexes:'DFA62A,F1681E,DB39AF',multi_color_direction:'coaxial'}),640,'Drybox','"AABBCCDD"'),
];
const feed=(present,state='load_finish')=>({module_exist:true,filament_detected:present,disable_auto:false,channel_state:present?state:'wait_insert',channel_error:'ok'});
function status(job){return {webhooks:{state:'ready'},print_stats:{state:job,filename:job==='standby'?'':'suporte_parede_v3.gcode',print_duration:5400,info:{current_layer:112,total_layer:180}},
 virtual_sdcard:{progress:job==='standby'?0:.62},idle_timeout:{state:job==='printing'?'Printing':'Idle'},
 extruder:{temperature:job==='printing'?214.6:25,target:job==='printing'?215:0},extruder1:{temperature:job==='printing'?215.1:25,target:job==='printing'?215:0},extruder2:{temperature:24.8,target:0},extruder3:{temperature:25,target:0},
 heater_bed:{temperature:job==='printing'?69.8:24,target:job==='printing'?70:0},chamber:{temperature:38},
 fan:{speed:job==='printing'?1:0},'fan_generic exhaust_fan':{speed:.5},'fan_generic circulation_fan':{speed:0},'fan_generic cavity_fan':{speed:.25},
 'filament_feed left':{extruder0:feed(true),extruder1:feed(true)},'filament_feed right':{extruder2:feed(true),extruder3:feed(job!=='standby')},
 filament_detect:{info:[{CARD_UID:[83,245,149,180,149,0,1]},{CARD_UID:[83,245,153,180,149,0,1]},{CARD_UID:0},{CARD_UID:0}]},
 print_task_config:{filament_spool_id:[33,38,24,job==='standby'?0:29],filament_type:['PETG','PETG','PLA',job==='standby'?'NONE':'PETG'],filament_color_rgba:['8B9096FF','2B1B14FF','F7F7F4FF','FFFFFFFF']}};}
const history=['16/11 14:02:11 · Associado ao T2: #38 · Masterprint PETG · Preto','16/11 13:58:40 · Carga concluída no T2.','16/11 13:41:07 · U1 encontrada em 192.168.0.18'];
const base={running:true,connected:true,statusAt:1,host:'192.168.0.18',proxy:true,externalMode:1,stock,stockAt:Date.UTC(2026,10,16,17,5),history,nfc:true,nfcHardware:true,timeout:180,version:'2.0.1',kind:'info',message:'Aproxime a tag do rolo',phase:''};

(async()=>{
 await new Promise(r=>server.listen(0,'127.0.0.1',r));const url=`http://127.0.0.1:${server.address().port}/`;
 const browser=await chromium.launch({executablePath:process.env.CHROME||(fs.existsSync('/opt/ungoogled-chromium/chrome')?'/opt/ungoogled-chromium/chrome':undefined),args:['--no-sandbox']});
 const errors=[];let shots=0;
 async function page(view,lang='pt'){
  const ctx=await browser.newContext({viewport:view,deviceScaleFactor:2,locale:lang==='pt'?'pt-BR':'en-US'});const p=await ctx.newPage();
  p.on('console',m=>{if(m.type()==='error')errors.push(m.text());});p.on('pageerror',e=>errors.push(String(e)));p.on('requestfailed',r=>errors.push('failed '+r.url()));
  await p.addInitScript(()=>{window.calls=[];window.Native=new Proxy({},{get:(_,k)=>k==='settingsData'?()=>JSON.stringify({printer:'',discovered:'192.168.0.18',spoolman:'',timeout:180,languagePreference:'auto',version:'2.0.1'}):(...a)=>{window.calls.push([k,...a]);}});});
  await p.goto(url);await p.evaluate(()=>document.fonts.ready);return p;}
 const upd=(p,v)=>p.evaluate(v=>window.updateApp(v),v);
 const shot=async(p,name)=>{await p.evaluate(()=>document.getElementById('toast').classList.remove('show'));await p.waitForTimeout(250);await p.screenshot({path:path.join(out,name+'.png')});shots++;};
 const phone={width:412,height:900};
 let p=await page(phone);
 assert.ok(await p.evaluate(()=>document.fonts.check('600 16px Montserrat')&&document.fonts.check('900 16px FA')),'fontes empacotadas');
 await upd(p,{...base,language:'pt',status:status('printing'),verifyChannel:undefined});await shot(p,'app200_inicio');
 assert.match(await p.textContent('#print-card'),/62/);assert.match(await p.textContent('#home-feeders'),/#38/);
 await p.click('nav [data-tab=control]');await shot(p,'app200_controle');
 await p.click('[data-ctl=pause]');await p.click('[data-fan="1"][data-v="75"]');
 assert.ok(await p.isDisabled('[data-temp="0"][data-v="200"]'),'temperatura bloqueada durante impressão');
 assert.ok(await p.isDisabled('[data-feed=LOAD][data-ch="0"]'),'carregar bloqueado durante impressão');
 assert.ok(!(await p.isDisabled('[data-assign-ch="0"]')),'ativar permitido durante impressão');
 let calls=await p.evaluate(()=>window.calls);assert.deepEqual(calls.slice(-2),[['control','pause',0,0],['control','fan',1,75]]);
 await p.click('nav [data-tab=stock]');await shot(p,'app200_armazem');
 await p.click('[data-spool="38"]');await shot(p,'app200_ficha');
 assert.match(await p.textContent('#sheet'),/Tag inválida no cadastro: ABC/);
 await p.click('[data-act=delete]');calls=await p.evaluate(()=>window.calls);assert.deepEqual(calls.at(-1),['previewDelete',38]);
 await p.evaluate(s=>window.inventoryResult({deletePreview:s}),stock[3]);
 assert.ok(await p.isDisabled('#d-go'),'excluir desativado até digitar o número');await p.fill('#d-id','37');assert.ok(await p.isDisabled('#d-go'));await p.fill('#d-id','38');assert.ok(!(await p.isDisabled('#d-go')));
 await shot(p,'app200_excluir');await p.click('#d-go');calls=await p.evaluate(()=>window.calls);assert.deepEqual(calls.at(-1),['deleteSpool',38,'38']);
 await p.evaluate(()=>window.inventoryResult({ok:true,deleted:38,message:'Rolo #38 excluído do Spoolman'}));
 await p.click('nav [data-tab=nfc]');
 await upd(p,{...base,language:'pt',status:status('standby'),phase:'insert',remaining:142,spool:stock[0],armed:true,message:'Insira o filamento no alimentador desejado'});await shot(p,'app200_nfc_inserir');
 await upd(p,{...base,language:'pt',status:status('standby'),tag:{uid:'04112233445566',state:'unknown'},message:'Tag não cadastrada: 04112233445566'});await shot(p,'app200_nfc_desconhecida');
 await p.click('[data-act=nfc-link]');await p.click('[data-pick="29"]');calls=await p.evaluate(()=>window.calls);assert.deepEqual(JSON.parse(calls.at(-1)[1]),{mode:'bind',id:29,uid:'04112233445566'});
 await p.evaluate(()=>{document.getElementById('modal').hidden=true;});
 await upd(p,{...base,language:'pt',status:status('standby'),kind:'ok',message:'Associado ao T3: #24 · SUNLU PLA · White',verifyChannel:undefined});await shot(p,'app200_nfc_associado');
 await p.click('[data-deadline="300"]');calls=await p.evaluate(()=>window.calls);assert.equal(JSON.parse(calls.at(-1)[2]).timeout,300);
 await p.click('nav [data-tab=settings]');await upd(p,{...base,language:'pt',status:status('standby')});await shot(p,'app200_ajustes');
 await p.close();
 p=await page(phone,'en');await upd(p,{...base,language:'en',status:status('printing'),connected:true});await shot(p,'app200_inicio_en');
 assert.match(await p.textContent('nav'),/Warehouse/);await p.close();
 p=await page(phone);await upd(p,{...base,language:'pt',connected:false,status:null,discovering:false,host:''});await shot(p,'app200_offline');await p.close();
 p=await page({width:1280,height:800});await upd(p,{...base,language:'pt',status:status('printing')});await shot(p,'app200_tablet_inicio');
 await p.click('nav [data-tab=stock]');await shot(p,'app200_tablet_armazem');await p.close();

 const overflows=async p=>{
  return p.evaluate(()=>{
   const bad=[];
   const vw=document.documentElement.clientWidth,vh=document.documentElement.clientHeight;
   if(document.documentElement.scrollWidth>vw+1)bad.push({el:'html',sw:document.documentElement.scrollWidth,cw:vw});
   if(document.body.scrollWidth>vw+1)bad.push({el:'body',sw:document.body.scrollWidth,cw:vw});
   const skip=el=>el.closest && (el.closest('.item .title')||el.closest('.item .meta')||el.classList.contains('title'));
   for(const el of document.querySelectorAll('main, nav, .card, .tile, .row, .ctl-grid, .feeders, .filters, .head, .sheet, h1, nav button, .tile button.chip')){
    const cs=getComputedStyle(el);
    if(cs.display==='none'||cs.visibility==='hidden')continue;
    const r=el.getBoundingClientRect();
    if(r.width<=0||r.height<=0)continue;
    if(el.scrollWidth>el.clientWidth+2 && !skip(el) && cs.overflowX!=='auto' && cs.overflowX!=='scroll' && cs.textOverflow!=='ellipsis'){
     bad.push({clip:true,tag:el.tagName,id:el.id,cls:String(el.className).slice(0,60),text:(el.innerText||'').replace(/\s+/g,' ').slice(0,48),sw:el.scrollWidth,cw:el.clientWidth});
    }
    if(r.left<-1||r.right>vw+1)bad.push({x:true,tag:el.tagName,id:el.id,cls:String(el.className).slice(0,60),text:(el.innerText||'').replace(/\s+/g,' ').slice(0,48),left:r.left,right:r.right,vw});
   }
   return bad;
  });
 };
 const tabs=['home','control','stock','nfc','settings'];
 for(const view of [{width:360,height:800,tag:'360x800'},{width:412,height:915,tag:'412x915'}]){
  p=await page(view);
  await upd(p,{...base,language:'pt',status:status('printing')});
  for(const tab of tabs){
   await p.click(`nav [data-tab=${tab}]`);
   await shot(p,`app201_${view.tag}_${tab}`);
   const bad=await overflows(p);
   assert.deepEqual(bad,[],`overflow ${view.tag} aba ${tab}: ${JSON.stringify(bad)}`);
  }
  await p.close();
 }

 // Área segura simulada (status 48px + gestos 32px), como no Android 15 edge-to-edge.
 p=await page({width:360,height:800});
 await p.addStyleTag({content:':root{--safe-top:48px;--safe-bottom:32px;--safe-left:0px;--safe-right:0px}'});
 await upd(p,{...base,language:'pt',status:status('printing')});
 await p.click('nav [data-tab=control]');
 await shot(p,'app201_360x800_safe_controle');
 const geo=await p.evaluate(()=>{
  const nav=document.querySelector('nav').getBoundingClientRect();
  const main=document.querySelector('main').getBoundingClientRect();
  const head=document.querySelector('#tab-control .head h1').getBoundingClientRect();
  const labels=[...document.querySelectorAll('nav button span')].map(s=>({t:s.innerText, ...s.getBoundingClientRect().toJSON()}));
  const chips=[...document.querySelectorAll('#ctl-fans .chip,#ctl-feeders .chip')].map(b=>({t:b.innerText,sw:b.scrollWidth,cw:b.clientWidth,h:b.getBoundingClientRect().height}));
  return {nav:nav.toJSON(),main:main.toJSON(),head:head.toJSON(),labels,chips,vh:innerHeight,vw:innerWidth};
 });
 assert.ok(geo.head.top>=48-0.5,`título Controle abaixo da status bar (top=${geo.head.top})`);
 assert.ok(geo.nav.bottom<=800+0.5 && geo.nav.height>=64+32-1,`nav inclui inset inferior (h=${geo.nav.height}, bottom=${geo.nav.bottom})`);
 for(const l of geo.labels){
  assert.ok(l.bottom<=800-32+1,`etiqueta "${l.t}" acima da barra de gestos (bottom=${l.bottom})`);
  assert.ok(l.width>=8 && l.right<=360+0.5,`etiqueta "${l.t}" visível`);
 }
 for(const c of geo.chips){
  assert.ok(c.sw<=c.cw+1,`chip "${c.t}" cortado (sw=${c.sw} cw=${c.cw})`);
 }
 const assign=geo.chips.filter(c=>c.t.includes('Ativar'));
 assert.ok(assign.length>=4,'botões Ativar no alimentador presentes');
 const hundred=geo.chips.filter(c=>c.t.trim()==='100%');
 assert.ok(hundred.length>=4,'chips 100% presentes e inteiros');
 await p.close();

 await browser.close();server.close();
 assert.deepEqual(errors,[],'sem erros no console nem requisições falhas');
 console.log(`PASS: UI no Chromium (${shots} capturas em ${out}).`);
})().catch(e=>{console.error(e);process.exit(1);});
