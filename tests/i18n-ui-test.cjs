const {chromium}=require((process.env.SPOOL_TOOLS_DIR||require('node:path').resolve(__dirname,'../tools'))+'/ui-test/node_modules/playwright-core');
const http=require('node:http'),fs=require('node:fs'),path=require('node:path'),assert=require('node:assert/strict');
const root=path.resolve(__dirname,'../assets'),out=path.resolve(__dirname,'../build/ui');fs.mkdirSync(out,{recursive:true});
const server=http.createServer((req,res)=>{let file=path.join(root,decodeURIComponent(req.url.split('?')[0]==='/'?'/index.html':req.url.split('?')[0]));if(!file.startsWith(root+path.sep)){res.writeHead(403);res.end();return;}try{res.setHeader('Content-Type',({'html':'text/html','js':'application/javascript','css':'text/css','png':'image/png','ttf':'font/ttf'})[file.split('.').pop()]||'text/plain');res.end(fs.readFileSync(file));}catch{res.writeHead(404);res.end();}});
const fixture=JSON.parse(fs.readFileSync(path.resolve(__dirname,'fixtures/status.json'),'utf8'));
const stock=[['Generic','PETG','Azul','003776',720],['Volt','PLA','Dourado','DFA62A',540],['Masterprint','PETG','Branco','FFFFFF',700],['Masterprint','PETG','Laranja','FF4000',430]].map((x,i)=>({id:i+1,archived:false,remaining_weight:x[4],initial_weight:1000,location:'Drybox',filament:{vendor:{name:x[0]},material:x[1],name:x[2],color_hex:x[3],weight:1000}}));
for(let i=0;i<4;i++){const side=i<2?'left':'right';fixture['filament_feed '+side]['extruder'+i].filament_detected=true;}fixture.print_task_config.filament_spool_id=[1,2,3,4];fixture.print_task_config.filament_vendor=stock.map(s=>s.filament.vendor.name);fixture.print_task_config.filament_type=stock.map(s=>s.filament.material);fixture.print_task_config.filament_color_rgba=stock.map(s=>s.filament.color_hex+'FF');fixture.print_task_config.filament_color_multi=stock.map(s=>({colors:[s.filament.color_hex]}));
stock.push({id:50,archived:true,remaining_weight:null,filament:{name:'Arquivado',material:'ABS',color_hex:'000000'}});
const snapshot={connected:true,running:true,nfc:true,externalMode:true,status:fixture,stock,stockAt:Date.now(),history:['02/11 18:00 · Associado ao feeder 3: Masterprint PETG'],message:'Conectado. Aproxime a tag de um rolo.'};

(async()=>{await new Promise(r=>server.listen(0,'127.0.0.1',r));let browser;try{
 browser=await chromium.launch({executablePath:'C:/Program Files/Google/Chrome/Application/chrome.exe',headless:true,args:['--disable-gpu']});
 const page=await browser.newPage({locale:'en-US'});const errors=[];page.on('pageerror',e=>errors.push(e.message));
 await page.addInitScript(()=>{window.Native={action:name=>{window.lastAction=name;},catalog:()=>{},registration:()=>{},inspectTag:()=>{},settingsForm:(operation,raw)=>{window.settingsCall={operation,data:JSON.parse(raw)};},saveInventory:raw=>{window.savedPayload=JSON.parse(raw);}};});
 await page.goto('http://127.0.0.1:'+server.address().port);await page.evaluate(()=>document.fonts.ready);
 assert.equal(await page.locator('html').getAttribute('lang'),'en');
 await page.evaluate(s=>window.updateApp(s),snapshot);
 assert((await page.locator('#feeders').innerText()).includes('Empty inlet'));
 assert((await page.locator('#feeders').innerText()).includes('White'));
 assert((await page.locator('#feeders').innerText()).includes('Masterprint'),'catalog brand is preserved');
 assert.equal(await page.evaluate(()=>messageText('Associado ao feeder 2: #30 · Masterprint PETG · Branco')),'Assigned to feeder 2: #30 · Masterprint PETG · Branco');
 assert.equal(await page.evaluate(()=>messageText('Estoque indisponível: HTTP 404 em /api/v1/spool. Confira o endereço e a porta deste serviço nas configurações.')),'Inventory unavailable: HTTP 404 at /api/v1/spool. Check this service\'s address and port in settings.');
 for(const [name,width,height] of [['phone',390,844],['fold',768,900],['compact',320,740]]){
  await page.setViewportSize({width,height});
  for(const tab of ['factory','stock','history']){await page.locator('[data-tab="'+tab+'"]').click();assert(await page.evaluate(()=>document.documentElement.scrollWidth<=innerWidth),'EN overflow '+name+'/'+tab);await page.screenshot({path:path.join(out,'en-'+name+'-'+tab+'.png'),fullPage:true});}
 }
 await page.locator('[data-tab="stock"]').click();assert.equal(await page.locator('#stock-list .stock-detail').first().innerText(),'View spool →');
 const inventory=await page.locator('#stock-list').innerText();for(const pt of ['Ver rolo','peso conhecido','Não informado','Na U1'])assert(!inventory.includes(pt),'untranslated '+pt);
 await page.locator('#new-spool').click();await page.evaluate(items=>window.inventoryResult({ok:true,catalog:{vendors:[],filaments:items.slice(0,4).map((s,i)=>({...s.filament,id:i+1}))}}),stock);await page.locator('#register-filament').selectOption('new');
 await page.evaluate(s=>{window.registrationTag('04A1B2C3D4E5F6');window.inventoryResult({inspection:true,ok:true,uid:'04A1B2C3D4E5F6',matches:[s]});},stock[2]);assert((await page.locator('#tag-check').innerText()).includes('Already registered · spool #3'));assert((await page.locator('#tag-check').innerText()).includes('Branco'));assert(await page.locator('#register-save').isDisabled());await page.locator('#register-dialog form[method="dialog"] button').click();
 await page.evaluate(()=>window.openSettings({printer:'printer.example',spoolman:'spoolman.example:7912',timeout:180,language:'en',languagePreference:'auto',systemLanguage:'en'}));
 await page.locator('#settings-language').selectOption('pt');assert.equal(await page.locator('html').getAttribute('lang'),'pt-BR');assert((await page.locator('#settings-dialog').innerText()).includes('SALA DE CONTROLE'));await page.locator('#settings-language').selectOption('en');assert((await page.locator('#settings-dialog').innerText()).includes('CONTROL ROOM'));
 await page.locator('#settings-save').click();assert.equal(await page.evaluate(()=>window.settingsCall.data.language),'en');await page.evaluate(()=>window.settingsResult({ok:true,saved:true,message:'Configurações salvas. Conecte a fábrica ou atualize o Armazém.'}));assert((await page.locator('#inventory-message').innerText()).startsWith('Settings saved'));
 await page.evaluate(()=>window.openSettings({spoolman:'spoolman.example:7912',language:'en',languagePreference:'en',systemLanguage:'pt'}));await page.locator('#settings-language').selectOption('auto');assert.equal(await page.locator('html').getAttribute('lang'),'pt-BR');await page.locator('#settings-dialog form[method="dialog"] button').click();await page.waitForFunction(()=>document.documentElement.lang==='en');
 await page.evaluate(()=>window.openSettings({spoolman:'spoolman.example:7912',language:'en',languagePreference:'en',systemLanguage:'en'}));await page.setViewportSize({width:390,height:844});await page.screenshot({path:path.join(out,'en-settings.png')});await page.locator('#settings-dialog form[method="dialog"] button').click();
 await page.evaluate(s=>window.deletePreview(s),stock[4]);assert((await page.locator('#delete-dialog').innerText()).includes('DELETE SPOOL'));assert((await page.locator('#delete-summary').innerText()).includes('Arquivado'),'catalog name stays unchanged');await page.screenshot({path:path.join(out,'en-delete.png')});await page.locator('#delete-dialog form button').click();
 const restarted=await browser.newPage({locale:'pt-BR'});await restarted.goto('http://127.0.0.1:'+server.address().port);await restarted.evaluate(s=>window.updateApp({...s,language:'en',languagePreference:'en'}),snapshot);assert.equal(await restarted.locator('html').getAttribute('lang'),'en');await restarted.close();
 assert.deepEqual(errors,[]);console.log('PASS: English automatic default, PT/EN/system preview and cancellation, native language payload, opaque catalog data, errors/history, NFC duplicates, warehouse/delete text and three responsive widths.');
}finally{if(browser)await browser.close();server.close();}})().catch(e=>{console.error(e);process.exitCode=1;server.close();});





