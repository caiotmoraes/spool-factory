'use strict';
/* Spool Factory 2.0 — mesma navegação, fluxos e textos do U1 Factory Display (U1 Spool Spec v1). */
const $=id=>document.getElementById(id);
const esc=x=>String(x??'').replace(/[&<>"']/g,c=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[c]));
const STR=(typeof window!=='undefined'&&window.STRINGS)||{};
let lang='pt';
function t(key,vars){const e=STR[key];let s=e?(e[lang]||e.pt):key;if(vars)for(const k in vars)s=s.split('{'+k+'}').join(vars[k]);return s;}
const N=()=>window.Native||null;
const call=(fn,...a)=>{const n=N();if(n&&typeof n[fn]==='function')return n[fn](...a);};

/* ---------- Regras da Spec v1 (iguais ao Api.java e ao firmware) ---------- */
const SEP=/[,;\s\[\]"']+/;
function uidToken(acc,tok){if(!tok)return;const norm=tok.replace(/[^0-9a-f]/gi,'').toUpperCase();if(/^[0-9a-f:-]+$/i.test(tok)&&[8,14,20].includes(norm.length)){if(!acc.valid.includes(norm))acc.valid.push(norm);}else if(!acc.invalid.includes(tok))acc.invalid.push(tok);}
function uidValue(acc,raw,depth){
 if(raw==null)return;
 if(Array.isArray(raw)){for(const it of raw)if(typeof it==='string')uidValue(acc,it,9);return;}
 const s=String(raw).trim();if(!s)return;
 if(depth<2&&(s[0]==='"'||s[0]==='[')){try{const v=JSON.parse(s);if(Array.isArray(v)){for(const it of v)if(typeof it==='string')uidValue(acc,it,9);return;}if(typeof v==='string'){uidValue(acc,v,depth+1);return;}}catch(e){}}
 for(const tok of s.split(SEP))uidToken(acc,tok);
}
function parseCardUids(raw){const acc={valid:[],invalid:[]};uidValue(acc,raw,0);return acc;}
function spoolUids(s){const a=parseCardUids(s?.extra?.card_uids);for(const tg of (s?.tags||[])){const u=typeof tg==='string'?tg:tg?.uid;const one=parseCardUids(String(u||''));for(const v of one.valid)if(!a.valid.includes(v))a.valid.push(v);}return a;}
function cardUidHex(raw){
 let bytes=[];
 if(Array.isArray(raw)){for(const v of raw){if(!Number.isInteger(v)||v<0||v>255)return '';bytes.push(v);}}
 else if(typeof raw==='string'){const s=raw.trim();if(!s)return '';for(const p of s.split(/[ ,\t]+/)){if(!p)continue;if(!/^\d{1,3}$/.test(p))return '';const b=+p;if(b>255)return '';bytes.push(b);}}
 else return '';
 if(![4,7,10].includes(bytes.length)||!bytes.some(Boolean))return '';
 return bytes.map(b=>b.toString(16).padStart(2,'0')).join('').toUpperCase();
}
const COLOR_NAMES=[["black",18,19,24],["white",247,247,244],["gray",139,144,150],["silver",195,201,207],["blue",20,99,211],["navy",0,55,118],["cyan",25,203,219],["green",45,163,89],["lime",165,224,48],["yellow",255,222,28],["gold",223,166,42],["orange",255,75,0],["red",212,38,38],["pink",238,99,155],["purple",137,72,182],["brown",125,75,42],["beige",215,191,153]];
function hex(raw,fb='8A919E'){const s=String(raw??'').replace(/^#/,'');return /^[0-9a-f]{6}([0-9a-f]{2})?$/i.test(s)?'#'+s.slice(0,6).toUpperCase():'#'+fb;}
function colors(s){const f=s?.filament||{},list=String(f.multi_color_hexes||'').split(',').filter(Boolean);return list.length?list.map(x=>hex(x)):[hex(f.color_hex)];}
function colorName(cs){if(cs.length>1)return t('stock.multicolor',{n:cs.length});const rgb=cs[0].slice(1).match(/../g).map(x=>parseInt(x,16));let best=Infinity,name='';for(const n of COLOR_NAMES){const d=(rgb[0]-n[1])**2+(rgb[1]-n[2])**2+(rgb[2]-n[3])**2;if(d<best){best=d;name=n[0];}}return t('color.'+name);}
function swatch(cs){return cs.length>1?`conic-gradient(${cs.map((c,i)=>`${c} ${i*100/cs.length}% ${(i+1)*100/cs.length}%`).join(',')})`:cs[0];}
function label(s){const f=s?.filament||{};const v=f.vendor?.name||'';return `#${s?.id} · ${(v+' '+(f.material||'')).trim()} · ${f.name||''}`.replace(/ · $/,'');}
const LOW=100,LOW_FILTER=200;
function grams(s){const n=s?.remaining_weight;return typeof n==='number'&&isFinite(n)?Math.max(0,Math.round(n)):null;}
if(typeof module!=='undefined'){module.exports={parseCardUids,spoolUids,cardUidHex,colorName,label,t,setLang:l=>{lang=l;}};}

/* ---------- Estado ---------- */
let state={stock:[],history:[]},tab='home',catalog=null,detailId=0,pickUid='',bindId=0,createForm=null,deleteId=0;
const filt={q:'',material:'',vendor:'',location:'',sort:'id',archived:false,low:false,compact:false};
const st=()=>state.status||null;
const online=()=>!!(state.connected&&st());
function channel(i){return st()?.[i<2?'filament_feed left':'filament_feed right']?.['extruder'+i]||{};}
function printState(){return online()?(st().print_stats?.state||''):'offline';}
const printing=()=>['printing','paused'].includes(printState());
function stockById(id){return (state.stock||[]).find(s=>s.id===id);}
function feeder(i){
 const s=st(),cfg=s?.print_task_config||{},c=channel(i),present=!!c.filament_detected,id=+(cfg.filament_spool_id?.[i]||0);
 const sp=id?stockById(id):null,def=v=>typeof v==='string'&&v.trim()&&!['NONE','UNKNOWN','NULL'].includes(v.toUpperCase());
 const mat=def(cfg.filament_type?.[i])?cfg.filament_type[i]:sp?.filament?.material||'';
 const rgba=cfg.filament_color_rgba?.[i];const col=sp?colors(sp):def(mat)&&rgba?[hex(rgba)]:[];
 return {i,present,id,sp,mat,col,state:c.channel_state||'',error:c.channel_error&&c.channel_error!=='ok'};
}
function idleForTemps(){if(!online())return false;const job=printState(),idle=st().idle_timeout?.state||'';return ['standby','complete','cancelled','error',''].includes(job)&&['Idle','Ready'].includes(idle);}

/* ---------- Infra de tela ---------- */
function applyStatic(){
 document.documentElement.lang=lang==='pt'?'pt-BR':'en';
 document.querySelectorAll('[data-t]').forEach(e=>e.textContent=t(e.dataset.t));
 document.querySelectorAll('[data-tp]').forEach(e=>e.placeholder=t(e.dataset.tp));
}
function put(id,html){const e=$(id);if(e&&e._h!==html){e.innerHTML=html;e._h=html;}}
let toastTimer=0;function toast(msg){const e=$('toast');e.textContent=msg;e.classList.add('show');clearTimeout(toastTimer);toastTimer=setTimeout(()=>e.classList.remove('show'),3500);}
function openModal(html){$('sheet').innerHTML=html;$('modal').hidden=false;applyStatic();}
function closeModal(){$('modal').hidden=true;$('sheet').innerHTML='';if(bindId){bindId=0;call('registration',false);}if(createForm){createForm=null;call('registration',false);}}
function showTab(name){tab=name;document.querySelectorAll('nav button').forEach(b=>b.classList.toggle('on',b.dataset.tab===name));document.querySelectorAll('main section').forEach(s=>s.hidden=s.id!=='tab-'+name);if(name==='settings')fillSettings();if(name==='stock'&&!catalog)call('catalog');render();window.scrollTo(0,0);}
window.showTab=showTab;

/* ---------- Banner ---------- */
function renderBanner(){
 let html='';
 const ic={err:'&#xf071;',ok:'&#xf00c;',info:'&#xf05a;'};
 if(state.phase&&tab==='nfc'){html='';}
 else if(state.phase==='insert'){
  html=`<div class="banner"><i class="i">&#xf017;</i><div class="spacer"><b>${esc(t('nfc.insert'))}</b><div class="small t2">${esc(state.spool?label(state.spool):'')} · ${esc(t('nfc.remaining',{s:state.remaining||0}))}</div></div><button data-act="cancel">${esc(t('common.cancel'))}</button></div>`;
 }else if(state.phase){
  html=`<div class="banner"><i class="i">&#xf021;</i><div class="spacer">${esc(state.message)}</div><button data-act="cancel">${esc(t('common.cancel'))}</button></div>`;
 }else if(state.klippy){
  html=`<div class="banner err"><i class="i">${ic.err}</i><div class="spacer">${esc(t('conn.not_ready'))} (${esc(state.klippy)})</div><button data-ctl="restart">${esc(t('ctl.restart'))}</button></div>`;
 }else if(!state.connected){
  html=`<div class="banner ${state.discovering?'':'err'}"><i class="i">${state.discovering?'&#xf1eb;':ic.err}</i><div class="spacer">${esc(state.discovering?t('conn.searching'):t('conn.offline'))}${state.host?' · '+esc(state.host):''}</div><button data-act="connect">${esc(t('conn.rescan'))}</button></div>`;
 }else if(state.externalMode===0&&tab!=='stock'){
  html=`<div class="banner err"><i class="i">${ic.err}</i><div class="spacer small">${esc(t('conn.external_required'))}</div><button data-act="firmware">Firmware Config</button></div>`;
 }else if(state.message&&state.kind&&state.kind!=='info'&&tab!=='nfc'){
  html=`<div class="banner ${state.kind}"><i class="i">${ic[state.kind]}</i><div class="spacer">${esc(state.message)}</div></div>`;
 }
 put('banner',html);
}

/* ---------- Início ---------- */
function fmtDur(sec){sec=Math.max(0,Math.round(sec));const h=Math.floor(sec/3600),m=Math.round(sec%3600/60);return h?`${h}h ${m}min`:`${m}min`;}
function renderHome(){
 const s=st(),ps=printState(),ps_=s?.print_stats||{};
 const prog=s?Math.round(100*(s.virtual_sdcard?.progress??s.display_status?.progress??0)):0;
 const active=['printing','paused'].includes(ps);
 const eta=active&&prog>0&&ps_.print_duration?ps_.print_duration*(100/prog-1):0;
 const layer=ps_.info?.current_layer!=null&&ps_.info?.total_layer?t('print.layer',{a:ps_.info.current_layer,b:ps_.info.total_layer}):'';
 const dotc={printing:'var(--accent)',paused:'var(--warn)',error:'var(--danger)',offline:'var(--danger)',complete:'var(--ok)'}[ps]||'var(--muted)';
 const stName=t('print.'+({standby:'idle','':'idle'}[ps]||ps));
 put('print-card',`<div class="hero"><div class="pct">${active||ps==='complete'?prog:'—'}<small> %</small></div><div class="spacer">
  <div class="t2">${active&&eta?esc(t('print.eta',{time:fmtDur(eta)})):esc(stName)}</div>
  <div class="bar"><i style="width:${active||ps==='complete'?prog:0}%"></i></div><div class="small muted">${esc(layer)}</div></div></div>
  <h2 style="margin-top:10px">${esc(ps_.filename||t('ctl.needs_print'))}</h2>
  <div class="small t2"><span class="dot" style="background:${dotc}"></span>${esc(stName)}${state.host?' · U1 '+esc(state.host):''}</div>`);
 put('home-feeders',[0,1,2,3].map(i=>feederCard(feeder(i))).join(''));
 const tiles=[];
 for(let i=0;i<4;i++){const e=s?.['extruder'+(i?i:'')];if(e)tiles.push(tempTile(t('ctl.nozzle',{ch:i+1}),e,''));}
 if(s?.heater_bed)tiles.push(tempTile(t('ctl.bed'),s.heater_bed,'bed'));
 if(s?.chamber)tiles.push(tempTile(t('ctl.chamber'),s.chamber,''));
 put('home-temps',tiles.join('')||`<span class="muted">${esc(t('print.offline'))}</span>`);
}
function tempTile(name,o,cls){const cur=Math.round(o.temperature??0),tg=Math.round(o.target??0);return `<div class="temp ${cls}"><h3>${esc(name)}</h3><div class="v">${cur}<small> °C</small></div><div class="small muted">${tg?esc(t('home.target',{t:tg})):'&nbsp;'}</div></div>`;}
function feederCard(f){
 const sel=state.verifyChannel===f.i;
 const g=f.sp?grams(f.sp):null;
 const light=f.col.length&&f.col[0].slice(1).match(/../g).map(x=>parseInt(x,16)).reduce((a,b)=>a+b,0)/3>200;
 const spool=f.present&&f.col.length?`<div class="spool" style="background:${swatch(f.col)};border-color:${light?'#C9CDD4':f.col[0]}"></div>`:`<div class="spool empty"></div>`;
 return `<div class="feeder${sel?' sel':''}"><b><span class="dot" style="background:${f.col[0]||'var(--line)'}"></span>T${f.i+1}</b>
  <div class="mat">${esc(f.present?(f.mat||t('feeder.unidentified')):t('feeder.empty'))}</div><div class="g">${g!=null&&f.present?g+' g':'&nbsp;'}</div>${spool}
  <div class="id">${f.present&&f.id?'#'+f.id:'&nbsp;'}</div></div>`;
}

/* ---------- Controle ---------- */
function renderControl(){
 const s=st(),ps=printState(),file=s?.print_stats?.filename||'';
 put('ctl-print',`<div class="row"><h2 class="spacer">${esc(t('print.'+({standby:'idle','':'idle'}[ps]||ps)))}${file?' · '+esc(file):''}</h2></div>
  <div class="row" style="margin-top:10px">
  <button data-ctl="pause" ${ps==='printing'?'':'disabled'}><i class="i">&#xf04c;</i> ${esc(t('ctl.pause'))}</button>
  <button data-ctl="resume" ${ps==='paused'?'':'disabled'}><i class="i">&#xf04b;</i> ${esc(t('ctl.resume'))}</button>
  <button class="ghost-danger" data-ctl="cancel" ${printing()?'':'disabled'}><i class="i">&#xf04d;</i> ${esc(t('ctl.cancel'))}</button></div>
  ${printing()?'':`<p class="small muted">${esc(t('ctl.needs_print'))}</p>`}`);
 const idle=idleForTemps();const temps=[];
 for(let i=0;i<4;i++){const e=s?.['extruder'+(i?i:'')];temps.push(heaterTile(i,t('ctl.nozzle',{ch:i+1}),e,[0,200,235],idle));}
 temps.push(heaterTile(4,t('ctl.bed'),s?.heater_bed,[0,55,75],idle));
 put('ctl-temps',temps.join(''));
 put('ctl-temps-note',idle||!online()?'':esc(t('ctl.blocked_print')));
 const fans=[['fan','fan.part'],['fan_generic exhaust_fan','fan.exhaust'],['fan_generic circulation_fan','fan.circulation'],['fan_generic cavity_fan','fan.cavity']];
 put('ctl-fans',fans.map(([obj,key],i)=>{const o=s?.[obj];const pct=o?Math.round((o.speed||0)*100):null;
  return `<div class="tile"><div class="name"><i class="i">&#xf72e;</i> ${esc(t(key))}</div><div class="val">${pct==null?'—':pct+'%'}</div><div class="row">${[0,25,50,75,100].map(v=>`<button class="chip${pct===v?' on':''}" data-fan="${i}" data-v="${v}" ${online()&&o?'':'disabled'}>${v}%</button>`).join('')}</div></div>`;}).join(''));
 put('ctl-feeders',[0,1,2,3].map(i=>{const f=feeder(i);const blocked=!online()||printing()||state.manualOperation||state.armed;
  return `<div class="tile"><div class="name">T${i+1} · ${esc(f.present?(f.mat||t('feeder.unidentified')):t('feeder.empty'))}${f.id&&f.present?' · #'+f.id:''}</div>
  <div class="small muted">${esc(f.state||'')}</div><div class="row">
  <button class="chip" data-feed="LOAD" data-ch="${i}" ${blocked||!f.present?'disabled':''}>${esc(t('feed.load'))}</button>
  <button class="chip" data-feed="UNLOAD" data-ch="${i}" ${blocked||!f.present?'disabled':''}>${esc(t('feed.unload'))}</button>
  <button class="chip" data-assign-ch="${i}" ${!online()||!f.present||state.manualOperation?'disabled':''}>${esc(t('assign.title'))}</button></div></div>`;}).join('')+(printing()?`<p class="small muted">${esc(t('feeder.print_blocked'))}</p>`:''));
}
function heaterTile(h,name,o,presets,idle){
 const cur=o?Math.round(o.temperature??0):null,tg=o?Math.round(o.target??0):0;
 return `<div class="tile"><div class="name"><i class="i">&#xf2c9;</i> ${esc(name)}</div><div class="val">${cur==null?'—':cur+' °C'}</div><div class="small muted">${tg?esc(t('home.target',{t:tg})):'&nbsp;'}</div>
  <div class="row">${presets.map(v=>`<button class="chip${tg===v&&o?' on':''}" data-temp="${h}" data-v="${v}" ${idle&&o?'':'disabled'}>${v?v+'°':esc(t('ctl.off'))}</button>`).join('')}</div></div>`;
}

/* ---------- Armazém ---------- */
function options(sel,vals,cur,allKey){const e=$(sel);const html=`<option value="">${esc(t(allKey))}: ${esc(t('common.all'))}</option>`+vals.map(v=>`<option${v===cur?' selected':''}>${esc(v)}</option>`).join('');if(e._h!==html){e.innerHTML=html;e._h=html;e.value=cur;}}
function renderStock(){
 const all=state.stock||[];
 const uniq=f=>[...new Set(all.map(f).filter(Boolean))].sort((a,b)=>a.localeCompare(b));
 options('f-material',uniq(s=>s.filament?.material),filt.material,'field.material');
 options('f-vendor',uniq(s=>s.filament?.vendor?.name),filt.vendor,'field.vendor');
 options('f-location',uniq(s=>s.location),filt.location,'field.location');
 const q=filt.q.trim().toLowerCase();
 let list=all.filter(s=>(filt.archived?s.archived:!s.archived)
  &&(!filt.low||(grams(s)??1e9)<=LOW_FILTER)
  &&(!filt.material||s.filament?.material===filt.material)&&(!filt.vendor||s.filament?.vendor?.name===filt.vendor)&&(!filt.location||s.location===filt.location)
  &&(!q||[('#'+s.id),s.filament?.name,s.filament?.material,s.filament?.vendor?.name,s.location,colorName(colors(s)),...spoolUids(s).valid].join(' ').toLowerCase().includes(q)));
 const by={id:(a,b)=>a.id-b.id,weight:(a,b)=>(grams(a)??1e9)-(grams(b)??1e9),material:(a,b)=>String(a.filament?.material).localeCompare(b.filament?.material)||a.id-b.id,brand:(a,b)=>String(a.filament?.vendor?.name).localeCompare(b.filament?.vendor?.name)||a.id-b.id}[filt.sort];
 list.sort(by);
 const total=list.reduce((n,s)=>n+(grams(s)||0),0),lows=list.filter(s=>(grams(s)??1e9)<LOW).length;
 put('stock-summary',esc([t('stock.count',{n:list.length}),t('stock.total',{g:(total/1000).toLocaleString(lang==='pt'?'pt-BR':'en-US',{maximumFractionDigits:1})}),t('stock.low_count',{n:lows})].join(' · ')));
 for(const [id,on] of [['c-archived',filt.archived],['c-low',filt.low],['c-compact',filt.compact]])$(id).classList.toggle('on',on);
 $('stock-list').classList.toggle('compact',filt.compact);
 put('stock-updated',state.stockAt?esc(t('common.updated_at',{time:new Date(state.stockAt).toLocaleTimeString(lang==='pt'?'pt-BR':'en-US',{hour:'2-digit',minute:'2-digit'})})):'');
 put('stock-list',list.length?list.map(s=>{const cs=colors(s),g=grams(s),low=g!=null&&g<LOW;
  return `<div class="item" data-spool="${s.id}"><div class="sw" style="background:${swatch(cs)}"></div><div class="main"><div class="title">${esc((s.filament?.material||'')+'  '+(s.filament?.vendor?.name||''))}</div>
  <div class="t2 small">${esc(s.filament?.name||colorName(cs))}</div>
  <div class="meta"><span class="${low?'low':''}">#${s.id}</span><span class="${low?'low':''}">${g==null?'— g':g+' g'}</span><span>${esc(s.location||t('stock.no_location'))}</span>${s.archived?`<span class="badge">${esc(t('stock.archived_badge'))}</span>`:''}${low?`<span class="badge low">${esc(t('stock.low_badge'))}</span>`:''}</div></div></div>`;}).join(''):`<p class="muted">${esc(t('stock.empty'))}</p>`);
}
function openDetail(id){
 const s=stockById(id);if(!s)return;detailId=id;const f=s.filament||{},cs=colors(s),u=spoolUids(s),g=grams(s);
 openModal(`<div class="row"><div class="item" style="border:0;padding:0;flex:1"><div class="sw" style="background:${swatch(cs)};width:44px;height:44px"></div><div class="main"><h2>${esc(label(s))}</h2><div class="small t2">${esc(t('stock.detail'))}</div></div></div><button data-act="close"><i class="i">&#xf00d;</i></button></div>
 <dl class="kv"><dt>${esc(t('field.material'))}</dt><dd>${esc(f.material||'—')}</dd><dt>${esc(t('field.vendor'))}</dt><dd>${esc(f.vendor?.name||'—')}</dd>
 <dt>${esc(t('field.colors'))}</dt><dd>${esc(colorName(cs))} · ${esc(cs.join(', '))}</dd>
 <dt>${esc(t('field.remaining'))}</dt><dd>${g==null?'—':g+' g'}${s.initial_weight?' / '+Math.round(s.initial_weight)+' g':''}</dd>
 <dt>${esc(t('field.location'))}</dt><dd>${esc(s.location||t('stock.no_location'))}</dd>
 ${s.lot_nr?`<dt>${esc(t('field.lot'))}</dt><dd>${esc(s.lot_nr)}</dd>`:''}${s.comment?`<dt>${esc(t('field.comment'))}</dt><dd>${esc(s.comment)}</dd>`:''}
 <dt>${esc(t('field.tags'))}</dt><dd><div class="tags">${u.valid.map(x=>`<code>${esc(x)}</code>`).join('')||'—'}${u.invalid.map(x=>`<code class="bad" title="${esc(t('uid.invalid_stored',{uid:x}))}">${esc(x)}</code>`).join('')}</div>
 ${u.invalid.length?`<div class="small" style="color:var(--danger);margin-top:4px">${esc(t('uid.invalid_stored',{uid:u.invalid.join(', ')}))}</div>`:''}</dd></dl>
 <div class="actions"><button class="ghost-danger" data-act="delete"><i class="i">&#xf1f8;</i> ${esc(t('delete.title'))}</button><span class="spacer"></span>
 <button data-act="edit"><i class="i">&#xf304;</i> ${esc(t('common.edit'))}</button>
 <button data-act="bind" ${s.archived?'disabled':''}><i class="i">&#xf02b;</i> ${esc(t('bind.title'))}</button>
 <button class="primary" data-act="assign" ${s.archived?'disabled':''}><i class="i">&#xf0c1;</i> ${esc(t('assign.title'))}</button></div>`);
}
function openEdit(id){const s=stockById(id);if(!s)return;
 openModal(`<h2>${esc(t('common.edit'))} · #${id}</h2><label>${esc(t('field.remaining'))}</label><input id="e-rem" type="number" min="0" step="1" value="${grams(s)??''}">
 <label>${esc(t('field.location'))}</label><input id="e-loc" maxlength="64" value="${esc(s.location||'')}"><label>${esc(t('field.comment'))}</label><textarea id="e-com" maxlength="1024" rows="3">${esc(s.comment||'')}</textarea>
 <div class="actions"><button data-act="close">${esc(t('common.back'))}</button><button class="primary" data-act="edit-save">${esc(t('common.save'))}</button></div>`);}
function openBind(id){bindId=id;call('registration',true);
 openModal(`<h2>${esc(t('bind.title'))}</h2><div class="reader" style="margin:16px 0"><i class="i big">&#xf519;</i><p>${esc(t('bind.tap',{id}))}</p></div>
 <label>UID</label><input id="b-uid" autocomplete="off" placeholder="04A1B2C3D4E5F6"><div class="actions"><button data-act="close">${esc(t('common.cancel'))}</button><button class="primary" data-act="bind-save">${esc(t('bind.title'))}</button></div>`);}
function openAssign(id){const s=stockById(id);
 openModal(`<h2>${esc(t('assign.title'))}</h2><p class="t2">${esc(s?label(s):'#'+id)}</p><p>${esc(t('assign.pick'))}</p>
 <div class="feeders">${[0,1,2,3].map(i=>{const f=feeder(i);return `<button data-assign="${i}" data-id="${id}" ${f.present?'':'disabled'}>T${i+1}<br><span class="small t2">${esc(f.present?(f.mat||t('feeder.unidentified')):t('feeder.empty'))}</span></button>`;}).join('')}</div>
 <div class="actions"><button data-act="close">${esc(t('common.back'))}</button></div>`);}
function openPicker(uid){pickUid=uid;const list=(state.stock||[]).filter(s=>!s.archived).sort((a,b)=>a.id-b.id);
 openModal(`<h2>${esc(t('nfc.link_this'))}</h2><p class="t2">${esc(t('nfc.link_pick',{uid}))}</p><input id="p-q" type="search" placeholder="${esc(t('stock.search'))}"><div class="list compact" id="p-list" style="margin-top:8px">${list.map(s=>`<div class="item" data-pick="${s.id}"><div class="sw" style="background:${swatch(colors(s))}"></div><div class="main"><div class="title">${esc(label(s))}</div></div></div>`).join('')}</div>
 <div class="actions"><button data-act="close">${esc(t('common.back'))}</button></div>`);}
function openDelete(sp){deleteId=sp.id;
 openModal(`<h2>${esc(t('delete.title'))} · #${sp.id}</h2><p><b>${esc(label(sp))}</b></p><p class="t2">${esc(t('delete.body'))}</p>
 <label>${esc(t('delete.hint'))}</label><input id="d-id" inputmode="numeric" autocomplete="off">
 <div class="actions"><button data-act="close">${esc(t('common.cancel'))}</button><button class="danger" id="d-go" data-act="delete-go" disabled>${esc(t('delete.button'))}</button></div>`);}
function openCreate(uid){
 createForm={uid:uid||''};call('registration',true);if(!catalog)call('catalog');
 const fil=(catalog?.filaments||[]).slice().sort((a,b)=>String(a.vendor?.name).localeCompare(b.vendor?.name)||String(a.name).localeCompare(b.name));
 openModal(`<h2>${esc(t('create.title'))}</h2>
 <label>${esc(t('create.tag_optional'))}</label><input id="n-uid" value="${esc(uid||'')}" placeholder="${esc(t('nfc.tap'))}" autocomplete="off">
 <label>${esc(t('create.catalog'))}</label><select id="n-fil"><option value="0">${esc(t('create.new_filament'))}</option>${fil.map(f=>`<option value="${f.id}">${esc((f.vendor?.name||'')+' · '+(f.material||'')+' · '+(f.name||''))}</option>`).join('')}</select>
 <div id="n-new"><label>${esc(t('field.vendor'))}</label><input id="n-vendor" maxlength="64"><label>${esc(t('field.name'))}</label><input id="n-name" maxlength="64">
 <label>${esc(t('field.material'))}</label><input id="n-material" maxlength="64" placeholder="PLA"><label>${esc(t('field.colors'))}</label><input id="n-colors" placeholder="FFFFFF">
 <label>${esc(t('field.direction'))}</label><select id="n-dir"><option value="coaxial">${esc(t('field.coaxial'))}</option><option value="longitudinal">${esc(t('field.longitudinal'))}</option></select>
 <div class="filters" style="grid-template-columns:1fr 1fr"><div><label>${esc(t('field.density'))}</label><input id="n-density" type="number" step="0.01" value="1.24"></div><div><label>${esc(t('field.diameter'))}</label><input id="n-diameter" type="number" step="0.01" value="1.75"></div></div></div>
 <div class="filters" style="grid-template-columns:1fr 1fr"><div><label>${esc(t('field.initial'))}</label><input id="n-initial" type="number" value="1000"></div><div><label>${esc(t('field.remaining'))}</label><input id="n-remaining" type="number" value="1000"></div></div>
 <label>${esc(t('field.location'))}</label><input id="n-location" maxlength="64"><label>${esc(t('field.comment'))}</label><textarea id="n-comment" rows="2" maxlength="1024"></textarea>
 <div class="actions"><button data-act="close">${esc(t('common.cancel'))}</button><button class="primary" data-act="create-save">${esc(t('common.save'))}</button></div>`);
 $('n-fil').addEventListener('change',()=>$('n-new').hidden=$('n-fil').value!=='0');
}

/* ---------- NFC ---------- */
function renderNfc(){
 const quiet=state.phase||state.tag||(state.kind&&state.kind!=='info');
 put('nfc-status',esc(state.nfcHardware===false?t('uid.unsupported'):state.nfc===false?t('nfc.off'):quiet?'':(state.message||'')));
 $('nfc-onoff').hidden=state.nfc!==false||state.nfcHardware===false;
 const tag=state.tag;let html='';
 if(state.phase==='insert'){
  html=`<div class="card"><h3>${esc(t('nfc.identified',{id:state.spool?.id||''}))}</h3><h2>${esc(state.spool?label(state.spool):'')}</h2>
  <div class="row" style="margin-top:10px"><div class="count">${state.remaining||0}s</div><div class="spacer t2">${esc(t('nfc.insert'))}<br><span class="small muted">${esc(t('nfc.insert_one'))}</span></div><button data-act="cancel">${esc(t('common.cancel'))}</button></div></div>`;
 }else if(state.phase){
  html=`<div class="card"><h2>${esc(state.spool?label(state.spool):'')}</h2><p class="t2">${esc(state.message)}</p><button data-act="cancel">${esc(t('common.cancel'))}</button></div>`;
 }else if(tag&&tag.state==='unknown'){
  html=`<div class="card"><h3>UID ${esc(tag.uid)}</h3><h2>${esc(t('nfc.unknown',{uid:tag.uid}))}</h2><div class="row" style="margin-top:12px">
  <button class="primary" data-act="nfc-create" data-uid="${esc(tag.uid)}"><i class="i">&#xf067;</i> ${esc(t('nfc.register'))}</button>
  <button data-act="nfc-link" data-uid="${esc(tag.uid)}"><i class="i">&#xf0c1;</i> ${esc(t('nfc.link_this'))}</button></div></div>`;
 }else if(tag&&tag.state==='known'){
  html=`<div class="card"><h3>UID ${esc(tag.uid)}</h3><h2>${esc(label(tag.spool))}</h2><p class="${state.kind==='err'?'':'t2'}" style="${state.kind==='err'?'color:var(--danger)':''}">${esc(state.message)}</p></div>`;
 }else if(state.message&&state.kind==='ok'){
  html=`<div class="card"><h2 style="color:var(--ok)"><i class="i">&#xf00c;</i> ${esc(state.message)}</h2></div>`;
 }else if(state.message&&state.kind==='err'){
  html=`<div class="card"><p style="color:var(--danger)"><i class="i">&#xf071;</i> ${esc(state.message)}</p></div>`;
 }
 put('nfc-result',html);
 const cur=state.timeout||180;
 put('nfc-deadline',[60,120,180,300,600].map(v=>`<button class="chip${v===cur?' on':''}" data-deadline="${v}">${esc(t('nfc.minutes',{n:v/60}))}</button>`).join(''));
}

/* ---------- Ajustes ---------- */
let settings={};
function fillSettings(){try{settings=JSON.parse(call('settingsData')||'{}');}catch(e){settings={};}
 $('s-printer').value=settings.printer||'';$('s-printer').placeholder=settings.discovered?t('set.auto_ip',{ip:settings.discovered}):t('set.lang_auto');
 $('s-spoolman').value=settings.spoolman||'';$('s-spoolman').placeholder=t('conn.via_proxy');
 $('s-printerKey').value=settings.printerKey||'';$('s-spoolKey').value=settings.spoolKey||'';$('s-language').value=settings.languagePreference||'auto';}
/** Prazo de inserção salvo a partir dos ajustes guardados (não do formulário, que pode ter edições não salvas). */
function savedSettings(extra){let s={};try{s=JSON.parse(call('settingsData')||'{}');}catch(e){}return Object.assign({printer:s.printer||'',spoolman:s.spoolman||'',printerKey:s.printerKey||'',spoolKey:s.spoolKey||'',language:s.languagePreference||'auto',timeout:s.timeout||180},extra||{});}
function formData(extra){return Object.assign({printer:$('s-printer').value,spoolman:$('s-spoolman').value,printerKey:$('s-printerKey').value,spoolKey:$('s-spoolKey').value,language:$('s-language').value,timeout:state.timeout||settings.timeout||180},extra||{});}
function renderSettings(){
 const parts=[];
 parts.push(state.connected?t('conn.test_u1_ok',{ip:state.host||''}):state.discovering?t('conn.searching'):t('conn.offline'));
 parts.push(state.proxy===false?t('conn.via_direct',{url:settings.spoolman||''}):t('conn.via_proxy'));
 if(state.externalMode===1)parts.push(t('conn.external_ok'));else if(state.externalMode===0)parts.push(t('conn.external_required'));
 put('set-conn',esc(parts.join(' · ')));
 const h=(state.history||[]).filter(Boolean);
 put('log',h.length?h.map(x=>`<li>${esc(x)}</li>`).join(''):`<li class="muted">${esc(t('log.empty'))}</li>`);
 put('about',esc(`Spool Factory ${state.version||settings.version||''} · U1 Spool Spec v1`));
}

/* ---------- Render ---------- */
function render(){
 renderBanner();
 if(tab==='home')renderHome();else if(tab==='control')renderControl();else if(tab==='stock')renderStock();else if(tab==='nfc')renderNfc();else renderSettings();
}
window.updateApp=v=>{const prev=lang;state=v||{};const next=state.language==='en'?'en':'pt';if(next!==prev){lang=next;applyStatic();document.querySelectorAll('[id]').forEach(e=>{e._h=undefined;});}render();};
window.inventoryResult=r=>{
 if(r.catalog){catalog=r.catalog;if(createForm&&!$('modal').hidden&&$('n-fil')&&$('n-fil').options.length<=1){const u=$('n-uid').value;openCreate(u);}return;}
 if(r.deletePreview){openDelete(r.deletePreview);return;}
 if(r.cancelled)return;
 if(r.message)toast(r.message);
 if(r.ok&&(r.saved||r.deleted)){const keep=bindId;bindId=0;createForm=null;call('registration',false);$('modal').hidden=true;if(r.saved&&r.saved.id&&!r.deleted&&!pickUid&&keep)openDetail(r.saved.id);pickUid='';if(r.saved)call('action','tagClear');}
};
window.settingsResult=r=>{const lines=[r.message,r.printer,r.spoolman].filter(Boolean);$('s-result').textContent=lines.join('\n');$('s-result').style.whiteSpace='pre-line';$('s-result').style.color=r.ok?'var(--ok)':'var(--danger)';if(r.saved){fillSettings();}};
window.registrationTag=uid=>{
 if(bindId&&$('b-uid')){$('b-uid').value=uid;call('saveInventory',JSON.stringify({mode:'bind',id:bindId,uid}));return;}
 if(createForm&&$('n-uid')){$('n-uid').value=uid;return;}
};

/* ---------- Eventos ---------- */
if(typeof document!=='undefined'&&document.getElementById('nav')){
 document.querySelectorAll('nav button').forEach(b=>b.addEventListener('click',()=>showTab(b.dataset.tab)));
 $('estop').addEventListener('click',()=>call('control','estop',0,0));
 $('stock-refresh').addEventListener('click',()=>call('action','inventoryRefresh'));
 $('stock-new').addEventListener('click',()=>openCreate(''));
 $('search').addEventListener('input',e=>{filt.q=e.target.value;renderStock();});
 for(const [id,key] of [['f-material','material'],['f-vendor','vendor'],['f-location','location'],['f-sort','sort']])$(id).addEventListener('change',e=>{filt[key]=e.target.value;renderStock();});
 $('c-archived').addEventListener('click',()=>{filt.archived=!filt.archived;renderStock();});
 $('c-low').addEventListener('click',()=>{filt.low=!filt.low;renderStock();});
 $('c-compact').addEventListener('click',()=>{filt.compact=!filt.compact;renderStock();});
 $('c-clear').addEventListener('click',()=>{Object.assign(filt,{q:'',material:'',vendor:'',location:'',sort:'id',archived:false,low:false});$('search').value='';$('f-sort').value='id';renderStock();});
 $('s-save').addEventListener('click',()=>call('settingsForm','save',JSON.stringify(formData())));
 $('s-test').addEventListener('click',()=>{$('s-result').textContent=t('common.loading');$('s-result').style.color='';call('settingsForm','test',JSON.stringify(formData()));});
 $('s-rescan').addEventListener('click',()=>{call('action','rescan');toast(t('conn.searching'));});
 $('s-share').addEventListener('click',()=>call('action','share'));
 $('s-firmware').addEventListener('click',()=>call('action','firmware'));
 $('nfc-onoff').addEventListener('click',()=>call('action','nfcSettings'));
 $('modal').addEventListener('click',e=>{if(e.target.id==='modal')closeModal();});
 document.addEventListener('input',e=>{
  if(e.target.id==='d-id')$('d-go').disabled=e.target.value.trim()!==String(deleteId);
  if(e.target.id==='p-q'){const q=e.target.value.toLowerCase();document.querySelectorAll('#p-list .item').forEach(it=>it.hidden=!it.textContent.toLowerCase().includes(q));}
 });
 document.addEventListener('click',e=>{
  const el=e.target.closest('[data-act],[data-ctl],[data-fan],[data-temp],[data-feed],[data-assign-ch],[data-assign],[data-spool],[data-pick],[data-deadline]');if(!el||el.disabled)return;
  const d=el.dataset;
  if(d.ctl)return call('control',d.ctl,0,0);
  if(d.fan)return call('control','fan',+d.fan,+d.v);
  if(d.temp)return call('control','temp',+d.temp,+d.v);
  if(d.feed)return call('manual',d.feed,+d.ch,0);
  if(d.assignCh!==undefined){const ch=+d.assignCh;pickUid='';const list=(state.stock||[]).filter(s=>!s.archived).sort((a,b)=>a.id-b.id);
   openModal(`<h2>${esc(t('assign.title'))} · T${ch+1}</h2><input id="p-q" type="search" placeholder="${esc(t('stock.search'))}"><div class="list compact" id="p-list" style="margin-top:8px">${list.map(s=>`<div class="item" data-assign="${ch}" data-id="${s.id}"><div class="sw" style="background:${swatch(colors(s))}"></div><div class="main"><div class="title">${esc(label(s))}</div></div></div>`).join('')}</div><div class="actions"><button data-act="close">${esc(t('common.back'))}</button></div>`);return;}
  if(d.assign!==undefined){closeModal();return call('manual','ASSIGN',+d.assign,+d.id);}
  if(d.spool)return openDetail(+d.spool);
  if(d.pick){const id=+d.pick;return call('saveInventory',JSON.stringify({mode:'bind',id,uid:pickUid}));}
  if(d.deadline){state.timeout=+d.deadline;call('settingsForm','save',JSON.stringify(savedSettings({timeout:+d.deadline})));renderNfc();return;}
  switch(d.act){
   case 'close':return closeModal();
   case 'cancel':return call('action','cancel');
   case 'connect':return call('action','rescan');
   case 'firmware':return call('action','firmware');
   case 'delete':return call('previewDelete',detailId);
   case 'delete-go':return call('deleteSpool',deleteId,$('d-id').value);
   case 'edit':return openEdit(detailId);
   case 'edit-save':{const rem=$('e-rem').value.trim();const data={mode:'update',id:detailId,location:$('e-loc').value,comment:$('e-com').value};if(rem!=='')data.remaining_weight=+rem;return call('saveInventory',JSON.stringify(data));}
   case 'bind':return openBind(detailId);
   case 'bind-save':return call('saveInventory',JSON.stringify({mode:'bind',id:bindId,uid:$('b-uid').value}));
   case 'assign':return openAssign(detailId);
   case 'nfc-create':return openCreate(d.uid);
   case 'nfc-link':return openPicker(d.uid);
   case 'create-save':{const fid=+$('n-fil').value;const data={mode:'create',uid:$('n-uid').value,filament_id:fid,initial_weight:+$('n-initial').value,remaining_weight:+$('n-remaining').value,location:$('n-location').value,comment:$('n-comment').value};
    if(!fid)Object.assign(data,{vendor:$('n-vendor').value,name:$('n-name').value,material:$('n-material').value,colors:$('n-colors').value,direction:$('n-dir').value,density:+$('n-density').value,diameter:+$('n-diameter').value});
    return call('saveInventory',JSON.stringify(data));}
  }
 });
 applyStatic();render();
}
