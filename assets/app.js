'use strict';
const $=id=>document.getElementById(id);
let state={stock:[],history:[]}, tab='factory', includeArchived=false, lastCards='',lastStock='',lastEvents='',moving=!matchMedia('(prefers-reduced-motion: reduce)').matches, clock=0,lastFrame=0,celebrateUntil=0,lastMessage='';
const frame=$('factory-scene'),ctx=frame.getContext('2d'),background=new Image();background.src='factory.png';
const esc=x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
function action(name){if(window.Native)window.Native.action(name);}
document.querySelectorAll('[data-action]').forEach(b=>b.addEventListener('click',()=>action(b.dataset.action)));
document.querySelectorAll('[data-tab]').forEach(b=>b.addEventListener('click',()=>{
 tab=b.dataset.tab;document.querySelectorAll('.page').forEach(p=>p.hidden=p.id!==tab);document.querySelectorAll('[data-tab]').forEach(x=>x.classList.toggle('selected',x===b));window.scrollTo(0,0);renderStock();
}));
$('motion').addEventListener('click',()=>{moving=!moving;$('motion').textContent=moving?'Ⅱ':'▶';$('motion').setAttribute('aria-label',moving?'Pausar animações':'Retomar animações');});
$('archived').addEventListener('click',()=>{includeArchived=!includeArchived;$('archived').textContent=includeArchived?'Ocultar arquivados':'Mostrar arquivados';lastStock='';renderStock();});
$('search').addEventListener('input',()=>{lastStock='';renderStock();});
function hex(raw,fallback='77849d'){const s=String(raw??'').replace(/^#/,'');return /^[0-9a-f]{6}([0-9a-f]{2})?$/i.test(s)?'#'+s.slice(0,6):'#'+fallback;}
function stockColors(s){const f=s?.filament||{},list=String(f.multi_color_hexes||'').split(',').filter(Boolean);return list.length?list.map(x=>hex(x)):[hex(f.color_hex)];}
function colorName(colors){
 if(colors.length>1)return colors.length+' cores';
 const rgb=colors[0].slice(1).match(/../g).map(x=>parseInt(x,16));
 const names=[['Preto',18,19,24],['Branco',247,247,244],['Cinza',139,144,150],['Prata',195,201,207],['Azul',20,99,211],['Azul-marinho',0,55,118],['Ciano',25,203,219],['Verde',45,163,89],['Verde-lima',165,224,48],['Amarelo',255,222,28],['Dourado',223,166,42],['Laranja',255,75,0],['Vermelho',212,38,38],['Rosa',238,99,155],['Roxo',137,72,182],['Marrom',125,75,42],['Bege',215,191,153]];
 let score=Infinity,name='Cor personalizada';for(const n of names){const d=(rgb[0]-n[1])**2+(rgb[1]-n[2])**2+(rgb[2]-n[3])**2;if(d<score){score=d;name=n[0];}}return name;
}
function grams(s){const n=s?.remaining_weight;return typeof n==='number'&&Number.isFinite(n)?Math.max(0,Math.round(n)).toLocaleString('pt-BR')+' g':'— g';}
function percent(s){let total=s?.initial_weight??s?.filament?.weight;return typeof total==='number'&&total>0&&typeof s?.remaining_weight==='number'?Math.min(100,Math.max(0,s.remaining_weight/total*100)):0;}
function channel(i){return state.status?.[i<2?'filament_feed left':'filament_feed right']?.['extruder'+i]||{};}
const defined=v=>typeof v==='string'&&v.trim()&&!['NONE','UNKNOWN','NULL'].includes(v.toUpperCase());
function tagUid(i){const raw=state.status?.filament_detect?.info?.[i]?.CARD_UID;return Array.isArray(raw)&&raw.some(Number)?raw.map(n=>Number(n).toString(16).padStart(2,'0')).join('').toUpperCase():'';}
function registeredUids(s){let value=s.extra?.card_uids;try{value=JSON.parse(value);}catch{}return (Array.isArray(value)?value:[value]).filter(v=>typeof v==='string').map(v=>v.replace(/[^a-f0-9]/gi,'').toUpperCase());}
function installed(i){
 const cfg=state.status?.print_task_config||{},c=channel(i),uid=tagUid(i),last=state.associations?.[i];
 let id=Number(cfg.filament_spool_id?.[i]||0),source='',fromCatalog=false;
 if(!id&&uid){const matches=(state.stock||[]).filter(s=>!s.archived&&registeredUids(s).includes(uid));if(matches.length===1){id=matches[0].id;source='Identificado pela tag NFC';fromCatalog=true;}}
 if(!id&&!defined(cfg.filament_type?.[i])&&c.filament_detected&&last?.spool_id&&(!uid||!last.uid||uid===last.uid)){id=last.spool_id;source=last.verified?'Associação confirmada no app · U1 sem identificação':'Última associação · confirme o rolo';fromCatalog=true;}
 const s=(state.stock||[]).find(s=>s.id===id)||(state.spool?.id===id?state.spool:null),exists=id>0||c.filament_detected===true;
 const identified=!!(defined(cfg.filament_type?.[i])||s?.filament?.material);
 let colors=(!fromCatalog&&defined(cfg.filament_type?.[i]))?cfg.filament_color_multi?.[i]?.colors?.slice(0,cfg.filament_color_multi[i].nums||1).filter(Boolean).map(x=>hex(x)):null;
 if(!colors?.length)colors=(!fromCatalog&&defined(cfg.filament_type?.[i])&&cfg.filament_color_rgba?.[i])?[hex(cfg.filament_color_rgba[i])]:(s?stockColors(s):['#77849d']);
 if(exists&&!identified&&!source)source='U1 sem identificação · selecione o rolo';
 return {channel:i,id,s,c,colors:exists?colors:['#48516a'],exists,identified,source,material:defined(cfg.filament_type?.[i])&&!fromCatalog?cfg.filament_type[i]:(s?.filament?.material||'Não identificado'),brand:defined(cfg.filament_vendor?.[i])&&!fromCatalog?cfg.filament_vendor[i]:(s?.filament?.vendor?.name||'')};
}
function readable(s){return ({wait_insert:'Entrada livre',preload_prepare:'Preparando alimentação',preload_feeding:'Puxando filamento',preload_finish:'Alimentação concluída',load_prepare:'Preparando carregamento',load_homing:'Posicionando cabeças',load_picking:'Selecionando cabeça',load_heating:'Aquecendo para carregar',load_feeding:'Carregando filamento',load_extruding:'Extrudando filamento',load_flushing:'Limpando bico',load_finish:'Carregamento concluído',unload_prepare:'Preparando retirada',unload_heating:'Aquecendo para retirar',unload_doing:'Retirando filamento',unload_finish:'Retirada concluída',preload_fail:'Falha na alimentação'})[s]||String(s||'Aguardando').replace(/_/g,' ');}
function colorsMarkup(colors){return `<span class="swatch" style="background:${colors.length>1?'linear-gradient(90deg,'+colors.join(',')+')':colors[0]}"></span>${esc(colorName(colors))}`;}
function card(data,title,stockMode=false){
 const {id,s,c,colors,exists,material,brand}=data;
 const stale=!state.connected&&!stockMode;
 const known=data.identified!==false;
 const details=exists?(known?`<div class="colorname">${colorsMarkup(colors)}</div><div class="id">${id>0?'#'+id+' · ':''}${colors.map(x=>x.toUpperCase()).join(' / ')}</div><div class="weight"><span class="bar"><b style="width:${percent(s)}%"></b></span><span>${grams(s)}</span></div>`:'<div class="colorname">Cor não identificada</div>'):'';
 const bottom=stockMode?`<div class="location">${esc(s?.filament?.name||'')}${s?.archived?' · Arquivado':''}<br>${esc(s?.location||'Local não informado')}</div>`:`<div class="feeder-state">${stale?'Última leitura · sem conexão':esc(readable(c.channel_state))}${data.source?'<br><span class="identity-note">'+esc(data.source)+'</span>':''}</div><button class="manage-feeder" data-manual-channel="${data.channel}">Trocar / associar</button>`;
 return `<article class="spool-card ${exists?'':'empty'} ${!stockMode&&c.filament_detected&&state.connected?'active':''}" aria-label="${esc(title)}"><canvas class="spool-icon" width="40" height="52" data-colors="${colors.join(',')}" data-empty="${!exists||!known}" aria-hidden="true"></canvas><div class="details"><h3>${esc(title)}</h3><div class="material">${esc(exists?material:'Sem rolo')}</div><div class="brand">${esc(exists&&brand?brand:exists?'Identificação pendente':'Entrada disponível')}</div>${details}</div>${bottom}</article>`;
}
let manualStockSignature='';
function manualOptions(preferred){
 const search=$('manual-search').value.toLocaleLowerCase('pt-BR'),current=preferred??Number($('manual-spool').value),all=(state.stock||[]).filter(s=>!s.archived&&[s.id,s.filament?.material,s.filament?.vendor?.name,s.filament?.name,colorName(stockColors(s))].join(' ').toLocaleLowerCase('pt-BR').includes(search));
 $('manual-spool').innerHTML='<option value="0">Selecione um rolo</option>'+all.map(s=>`<option value="${Number(s.id)}">#${Number(s.id)} · ${esc(s.filament?.vendor?.name||'')} ${esc(s.filament?.material||'')} · ${esc(s.filament?.name||colorName(stockColors(s)))}</option>`).join('');
 $('manual-spool').value=all.some(s=>s.id===current)?String(current):'0';manualStockSignature=JSON.stringify(state.stock||[]);
}
function openManual(ch=0){$('manual-channel').value=String(ch);$('manual-search').value='';manualOptions(installed(ch).id);renderManual();$('manual-dialog').showModal();}
$('manual-open').addEventListener('click',()=>openManual());
$('feeders').addEventListener('click',e=>{const button=e.target.closest('[data-manual-channel]');if(button)openManual(Number(button.dataset.manualChannel));});
$('manual-channel').addEventListener('change',()=>{manualOptions(installed(Number($('manual-channel').value)).id);renderManual();});
$('manual-search').addEventListener('input',()=>{manualOptions();renderManual();});$('manual-spool').addEventListener('change',renderManual);
for(const [id,operation] of [['manual-assign','ASSIGN'],['manual-load','LOAD'],['manual-unload','UNLOAD']])$(id).addEventListener('click',()=>{if(window.Native?.manual)window.Native.manual(operation,Number($('manual-channel').value),Number($('manual-spool').value));});
function renderManual(){
 const ch=Number($('manual-channel').value),c=channel(ch),printing=['printing','paused'].includes(state.status?.print_stats?.state),phases=['wait_insert','preload_finish','load_finish','unload_finish','none','inited'];
 const feedersIdle=[0,1,2,3].every(i=>phases.includes(channel(i).channel_state)&&channel(i).channel_error==='ok');
 const allowed=state.connected&&!printing&&!state.manualOperation&&!state.armed&&feedersIdle;
 $('manual-assign').disabled=!allowed||!Number($('manual-spool').value)||!c.filament_detected;
 $('manual-load').disabled=!allowed||!c.filament_detected||!defined(state.status?.print_task_config?.filament_type?.[ch]);
 $('manual-unload').disabled=!allowed||!c.filament_detected;
 $('manual-note').textContent=!state.connected?'Conecte à U1 para usar os controles.':printing?'Impressão em andamento ou pausada: trocas manuais bloqueadas.':state.manualOperation?'Operação física em andamento. '+readable(c.channel_state):state.armed?'Aguarde ou cancele a associação pendente.':!feedersIdle?'Aguarde os feeders terminarem a operação atual.':'Feeder '+(ch+1)+' · '+readable(c.channel_state)+'. Associe o rolo correto antes de carregar.';
}
function paintCards(parent){parent.querySelectorAll('canvas.spool-icon').forEach(c=>{const x=c.getContext('2d');x.imageSmoothingEnabled=false;drawSpool(x,20,26,1.7,c.dataset.colors.split(','),c.dataset.empty==='true');});}
function drawSpool(x,cx,cy,scale,colors,empty=false){
 x.save();x.translate(Math.round(cx-10*scale),Math.round(cy-13*scale));x.scale(scale,scale);
 for(let y=0;y<26;y++)for(let a=0;a<20;a++){
  const q=((a-9.5)/9.5)**2+((y-12.5)/12.5)**2;if(q>1)continue;
  let color='#8c95af';if(q<.83)color='#252b43';if(q<.65)color=empty?'#283048':colors[Math.min(colors.length-1,Math.floor(a/20*colors.length))];
  if(q<.65&&q>.16&&(a+y)%5===0&&!empty)color=shade(color,.68);
  if(q<.16)color='#080e20';if(q>.83&&a<7)color='#c7cbd4';
  x.fillStyle=color;x.fillRect(a,y,1,1);
 }x.restore();
}
function shade(c,m){const n=c.slice(1).match(/../g).map(v=>Math.round(parseInt(v,16)*m));return '#'+n.map(v=>v.toString(16).padStart(2,'0')).join('');}
window.updateApp=function(data){
 state=data;if(!state.running)state.armed=false;
 $('lamp').classList.toggle('online',!!state.connected);
 $('connection').textContent=state.connected?'U1 conectada':state.running?'Conectando / sem resposta':'U1 desconectada';
 $('connect').textContent=state.running?'Desconectar':'Conectar';
 const msg=state.running?(state.message||'Conectando à U1…'):'Conecte a U1 para começar.';
 $('message').textContent=msg;$('cancel').hidden=!state.armed||!state.running;$('countdown').textContent=state.armed?Math.max(0,state.remaining||0)+' s':'';
 document.querySelector('.feedback').classList.toggle('error',state.running&&/falha|erro|cancelad|não confirmada|sem conexão|expir|encerrad/i.test(msg));
 $('reader-title').textContent=state.armed?'INSIRA O FILAMENTO':'APROXIME O ROLO';
 $('reader-hint').textContent=state.armed?'O próximo feeder ativado receberá este rolo.':!state.nfc?'Ative o NFC no celular para ler a tag.':'Leia a tag e insira no próximo feeder.';
 $('read').textContent=state.running?'LER NFC':'CONECTAR';
 $('scene-status').textContent=!state.connected?'AGUARDANDO CONEXÃO':state.armed?'OPERÁRIOS PREPARANDO A TROCA':state.status?.print_stats?.state==='printing'?'U1 IMPRIMINDO':'FÁBRICA PRONTA';
 if(msg!==lastMessage&&msg.startsWith('Associado ao feeder'))celebrateUntil=clock+5000;lastMessage=msg;
 const pending=state.spool;$('pending').hidden=!pending||!state.armed;
 if(pending&&state.armed){const f=pending.filament||{};$('pending').innerHTML=`<strong>ROLO #${Number(pending.id)} · ${esc(f.vendor?.name||'')} ${esc(f.material||'')}</strong><br>${colorsMarkup(stockColors(pending))} · ${grams(pending)}<br><span class="id">UID ${esc(state.uid||'')}</span>`;}
 const items=[0,1,2,3].map(installed);const signature=JSON.stringify([state.connected,items]);
 if(signature!==lastCards){lastCards=signature;$('feeders').innerHTML=items.map((d,i)=>card(d,'FEEDER '+(i+1))).join('');paintCards($('feeders'));}
 renderStock();
 if($('manual-dialog').open&&manualStockSignature!==JSON.stringify(state.stock||[]))manualOptions();renderManual();
 const events=(state.history||[]).filter(Boolean),ev=JSON.stringify(events);if(ev!==lastEvents){lastEvents=ev;$('events').innerHTML=events.length?events.map(t=>'<li>'+esc(t)+'</li>').join(''):'<li>Nenhuma leitura registrada ainda.</li>';}
};
function renderStock(){
 const query=$('search').value.trim().toLocaleLowerCase('pt-BR'),all=state.stock||[];
 const signature=JSON.stringify([all,query,includeArchived,state.stockAt,state.stockError,state.connected]);if(signature===lastStock)return;lastStock=signature;
 const filtered=all.filter(s=>(includeArchived||!s.archived)&&[s.id,s.filament?.name,s.filament?.material,s.filament?.vendor?.name,colorName(stockColors(s)),s.location].join(' ').toLocaleLowerCase('pt-BR').includes(query)).sort((a,b)=>a.id-b.id);
 $('stock-count').textContent=filtered.length+' rolos';
 $('stock-note').textContent=(state.stockError?state.stockError+'. ':'')+(state.stockAt?'Atualizado em '+new Date(state.stockAt).toLocaleString('pt-BR')+(!state.running?' · dados salvos no celular':''):'Conecte para consultar seu estoque no Spoolman.');
 $('stock-list').innerHTML=filtered.length?filtered.map(s=>card({id:s.id,s,c:{},colors:stockColors(s),exists:true,material:s.filament?.material||'Material não informado',brand:s.filament?.vendor?.name||''},'ROLO #'+s.id,true)).join(''):'<p class="notice">'+(all.length?'Nenhum rolo encontrado para este filtro.':'Seu estoque aparecerá aqui após conectar ao Spoolman.')+'</p>';paintCards($('stock-list'));
}
function worker(x,y,direction,step,color,carrying){
 ctx.save();ctx.translate(Math.round(x),Math.round(y));ctx.scale(direction*1.5,1.5);
 const r=(color,x,y,w,h)=>{ctx.fillStyle=color;ctx.fillRect(x,y,w,h);};
 r('#061022',-7,0,17,2); // shadow
 const swing=Math.sin(step)>0?2:-2;
 r('#132a55',-4,-9,4,8);r('#2354a3',1,-9,4,8);r('#0a132b',-5+swing,-2,5,3);r('#0a132b',2-swing,-2,5,3);
 r('#2562b8',-5,-17,11,10);r('#3686de',-4,-15,3,6);r('#fff0dc',-7,-18,4,7);r('#ebbe90',-7,-12,3,4);
 r('#e4b283',-3,-25,8,8);r('#fff0d8',3,-24,3,6);r('#10192d',4,-23,2,2);r('#603b27',-4,-25,3,7);
 r('#ffb52b',-5,-29,11,5);r('#ffe461',-3,-31,6,4);r('#ffcc3d',-7,-25,16,2);
 if(carrying){drawSpool(ctx,9,-13,.57,[color]);r('#ffd1a4',2,-13,5,3);r('#fff0dc',-1,-17,5,5);}else{r('#fff0dc',5,-17,3,5);r('#e4b283',6,-12+Math.round(Math.sin(step)*2),3,4);}
 ctx.restore();
}
function animate(time){
 if(!lastFrame)lastFrame=time;
 if(moving&&!document.hidden&&tab==='factory')clock+=Math.min(80,time-lastFrame);
 lastFrame=time;
 if(tab==='factory'&&!document.hidden){
  ctx.imageSmoothingEnabled=false;ctx.clearRect(0,0,480,360);
  if(background.complete&&background.naturalWidth)ctx.drawImage(background,0,0,480,360);else{ctx.fillStyle='#101b33';ctx.fillRect(0,0,480,360);}
  const items=[0,1,2,3].map(installed),online=!!state.connected;
  for(let i=0;i<4;i++){if(items[i].exists)drawSpool(ctx,176+i*48,289,1.18,items[i].colors,!online);ctx.fillStyle=online&&items[i].c.filament_detected?'#68f0b8':'#66728a';ctx.fillRect(172+i*48,313,8,3);}
  const color=state.armed&&state.spool?stockColors(state.spool)[0]:(items.find(i=>i.exists)?.colors[0]||'#56d6b4');
  const t=clock*(state.armed ? .06 : .024),a=t%660,b=(t*.72+130)%680;
  worker(56+(a<330?a:660-a),343,a<330?1:-1,clock/170,color,true);
  worker(420-(b<340?b:680-b),346,b<340?-1:1,clock/190,items[3].colors[0],true);
  worker(67,144,1,clock/450,'#50dbb8',false);
  worker(437,329,-1,clock/400,'#50dbb8',false);
  if(state.armed&&online){ctx.fillStyle=Math.floor(clock/400)%2?'#59ecc5':'#294746';ctx.fillRect(306,135,24,4);}
  if(clock<celebrateUntil){for(let i=0;i<16;i++){ctx.fillStyle=['#59ecc5','#ffcd3d','#eef0dc'][i%3];ctx.fillRect((i*31+clock*.02)%460,25+(clock*.04+i*19)%180,3,5);}}
 }
 requestAnimationFrame(animate);
}
drawSpool($('brand-spool').getContext('2d'),24,26,1.7,['#59ecc5']);
$('motion').textContent=moving?'Ⅱ':'▶';
window.updateApp({running:false,connected:false,nfc:true,stock:[],history:[]});requestAnimationFrame(animate);
