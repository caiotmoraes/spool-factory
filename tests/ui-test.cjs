const {chromium}=require((process.env.SPOOL_TOOLS_DIR||require('node:path').resolve(__dirname,'../tools'))+'/ui-test/node_modules/playwright-core');
const http=require('node:http'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../assets'),out=path.resolve(__dirname,'../build/ui');fs.mkdirSync(out,{recursive:true});
const server=http.createServer((req,res)=>{let file=path.join(root,decodeURIComponent(req.url.split('?')[0]==='/'?'/index.html':req.url.split('?')[0]));if(!file.startsWith(root+path.sep)){res.writeHead(403);res.end();return;}try{res.setHeader('Content-Type',({'html':'text/html','js':'application/javascript','css':'text/css','png':'image/png','ttf':'font/ttf'})[file.split('.').pop()]||'text/plain');res.end(fs.readFileSync(file));}catch{res.writeHead(404);res.end();}});
const fixture=JSON.parse(fs.readFileSync(path.resolve(__dirname,'fixtures/status.json'),'utf8'));
const stock=[['Generic','PETG','Azul','003776',720],['Volt','PLA','Dourado','DFA62A',540],['Masterprint','PETG','Branco','FFFFFF',700],['Masterprint','PETG','Laranja','FF4000',430]].map((x,i)=>({id:i+1,archived:false,remaining_weight:x[4],initial_weight:1000,location:'Drybox',filament:{vendor:{name:x[0]},material:x[1],name:x[2],color_hex:x[3],weight:1000}}));
fixture.print_task_config.filament_spool_id=[1,2,3,4];fixture.print_task_config.filament_vendor=stock.map(s=>s.filament.vendor.name);fixture.print_task_config.filament_type=stock.map(s=>s.filament.material);fixture.print_task_config.filament_color_rgba=stock.map(s=>s.filament.color_hex+'FF');fixture.print_task_config.filament_color_multi=stock.map(s=>({colors:[s.filament.color_hex]}));
const snapshot={connected:true,running:true,nfc:true,status:fixture,stock,stockAt:Date.now(),history:['02/11 18:00 · Associado ao feeder 3: Masterprint PETG'],message:'Conectado. Aproxime a tag de um rolo.'};
(async()=>{await new Promise(r=>server.listen(0,'127.0.0.1',r));let browser;try{
 browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true,args:['--disable-gpu']});
 const page=await browser.newPage();const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.Native={action:n=>{window.lastAction=n;},manual:(op,ch,id)=>{window.lastManual=[op,ch,id];}};});
 await page.goto('http://127.0.0.1:'+server.address().port);await page.evaluate(()=>document.fonts.ready);
 for(const [name,w,h] of [['fold-fechado',390,844],['fold-aberto',768,900],['compacto',320,740]]){
  await page.setViewportSize({width:w,height:h});await page.evaluate(s=>window.updateApp(s),snapshot);await page.waitForTimeout(200);
  assert.equal(await page.locator('#feeders .spool-card').count(),4);
  for(const color of ['Azul-marinho','Dourado','Branco','Laranja'])assert((await page.locator('#feeders').innerText()).includes(color),color);
  assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'horizontal overflow '+name);
  await page.screenshot({path:path.join(out,name+'.png'),fullPage:true});
 }
 await page.locator('[data-tab="stock"]').click();assert.equal(await page.locator('#stock-list .spool-card').count(),4);await page.locator('#search').fill('branco');assert.equal(await page.locator('#stock-list .spool-card').count(),1);
 await page.locator('[data-tab="history"]').click();assert((await page.locator('#events').innerText()).includes('Associado ao feeder 3'));
 await page.locator('[data-action="share"]').click();assert.equal(await page.evaluate(()=>window.lastAction),'share');
 await page.locator('[data-tab="factory"]').click();
 await page.locator('[data-manual-channel="1"]').click();assert(await page.locator('#manual-dialog').isVisible());assert.equal(await page.locator('#manual-channel').inputValue(),'1');
 await page.locator('#manual-assign').click();assert.deepEqual(await page.evaluate(()=>window.lastManual),['ASSIGN',1,2]);
 await page.locator('#manual-load').click();assert.deepEqual(await page.evaluate(()=>window.lastManual),['LOAD',1,2]);
 await page.locator('#manual-unload').click();assert.deepEqual(await page.evaluate(()=>window.lastManual),['UNLOAD',1,2]);
 await page.evaluate(s=>{const d=JSON.parse(JSON.stringify(s));d.status.print_stats.state='printing';window.updateApp(d);},snapshot);
 for(const id of ['manual-assign','manual-load','manual-unload'])assert(await page.locator('#'+id).isDisabled(),'manual blocked during printing');
 await page.evaluate(s=>window.updateApp(s),snapshot);await page.locator('#manual-dialog form button').click();
 await page.locator('[data-tab="factory"]').click();await page.evaluate(s=>window.updateApp({...s,armed:true,remaining:120,spool:s.stock[2],uid:'04A1B2C3D4E5F6',message:'Rolo identificado. Insira o filamento no feeder desejado.'}),snapshot);
 assert(await page.locator('#pending').isVisible());assert.equal(await page.locator('#countdown').innerText(),'120 s');await page.locator('#cancel').click();assert.equal(await page.evaluate(()=>window.lastAction),'cancel');
 const before=await page.locator('#factory-scene').evaluate(c=>c.toDataURL());await page.waitForTimeout(200);const after=await page.locator('#factory-scene').evaluate(c=>c.toDataURL());assert.notEqual(before,after,'workers animate');
 await page.locator('#motion').click();await page.waitForTimeout(100);const paused=await page.locator('#factory-scene').evaluate(c=>c.toDataURL());await page.waitForTimeout(200);assert.equal(await page.locator('#factory-scene').evaluate(c=>c.toDataURL()),paused,'pause animation');
 await page.evaluate(s=>window.updateApp({...s,connected:false,running:false,armed:true}),snapshot);assert((await page.locator('#feeders').innerText()).includes('sem conexão'));assert(await page.locator('#pending').isHidden());
 await page.evaluate(s=>{s.stock[0].filament.vendor.name='<img src=x onerror=alert(1)>';window.updateApp(s);},snapshot);assert.equal(await page.locator('#feeders img').count(),0,'untrusted inventory escaped');
 await page.evaluate(s=>{
  const d=JSON.parse(JSON.stringify(s));
  d.status.print_task_config.filament_spool_id[1]=0;d.status.print_task_config.filament_type[1]='NONE';d.status.print_task_config.filament_vendor[1]='NONE';d.status.print_task_config.filament_color_rgba[1]='FFFFFFFF';d.status.print_task_config.filament_color_multi[1]={nums:1,colors:['FFFFFF']};
  d.stock[1].filament.multi_color_hexes='DFA62A,F1681E,DB39AF';d.associations={'1':{spool_id:2,uid:'',verified:true}};window.updateApp(d);
 },snapshot);
 let recovered=await page.locator('#feeders .spool-card').nth(1).innerText();assert(recovered.includes('PLA')&&recovered.includes('Volt')&&recovered.includes('3 cores')&&recovered.includes('540 g')&&recovered.includes('confirmada no app'),'recover verified multicolor spool after native NONE/white reset');assert(!recovered.includes('Branco')&&!recovered.includes('NONE'),'never render firmware placeholders as filament');
 await page.evaluate(()=>{const d=JSON.parse(JSON.stringify(state));d.associations['1'].verified=false;window.updateApp(d);});assert((await page.locator('#feeders .spool-card').nth(1).innerText()).includes('Última associação · confirme'),'old cached identity is explicitly uncertain');
 await page.evaluate(()=>{const d=JSON.parse(JSON.stringify(state));d.associations={};window.updateApp(d);});recovered=await page.locator('#feeders .spool-card').nth(1).innerText();assert(recovered.includes('Não identificado')&&recovered.includes('Cor não identificada')&&!recovered.includes('#FFFFFF'),'no white fabricated without data');
 await page.screenshot({path:path.join(out,'regressao-identificacao.png'),fullPage:true});
 assert.deepEqual(errors,[]);console.log('PASS: responsive UI, colors, search, history, NFC actions, manual associate/load/unload, printing lock, animation, escaping, NONE/white reset recovery, multicolor preservation and honest unknown identity.');
}finally{if(browser)await browser.close();server.close();}})().catch(e=>{console.error(e);process.exitCode=1;server.close();});
