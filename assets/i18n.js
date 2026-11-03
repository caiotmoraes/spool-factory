'use strict';
let activeLanguage=/^pt\b/i.test(navigator.language)?'pt':'en',activeLocale=activeLanguage==='pt'?'pt-BR':'en-US';
const staticText=[],staticAttributes=[];
const walker=document.createTreeWalker(document.body,NodeFilter.SHOW_TEXT);let node;
while((node=walker.nextNode())){if(!['SCRIPT','STYLE'].includes(node.parentElement?.tagName)&&node.textContent.trim())staticText.push([node,node.textContent]);}
for(const element of document.querySelectorAll('[placeholder],[aria-label]'))for(const attr of ['placeholder','aria-label'])if(element.hasAttribute(attr))staticAttributes.push([element,attr,element.getAttribute(attr)]);
function resolveLanguage(preference,system=navigator.language){return preference==='pt'||preference==='en'?preference:/^pt(?:-|$)/i.test(system)?'pt':'en';}
function tr(text){return activeLanguage==='en'?(Object.prototype.hasOwnProperty.call(TRANSLATIONS.ui,text)?TRANSLATIONS.ui[text]:text):text;}
function tHtml(literal){return literal.replace(/(^|>)([^<>]*)(?=<|$)/g,(_,prefix,text)=>{const trimmed=text.trim();if(!trimmed)return prefix+text;return prefix+text.replace(trimmed,tr(trimmed));});}
function staticLanguage(){document.documentElement.lang=activeLanguage==='pt'?'pt-BR':'en';for(const [n,text] of staticText)if(n.isConnected){const key=text.trim();n.textContent=text.replace(key,tr(key));}for(const [element,attr,text] of staticAttributes)if(element.isConnected)element.setAttribute(attr,tr(text));}
function setLanguage(language){const next=resolveLanguage(language);if(next===activeLanguage){staticLanguage();return;}activeLanguage=next;activeLocale=next==='pt'?'pt-BR':'en-US';staticLanguage();if(window.languageChanged)window.languageChanged();}
const quoteRegex=value=>value.replace(/[.*+?^${}()|[\]\\]/g,'\\$&');
const messagePatterns=Object.entries(TRANSLATIONS.messages).sort((a,b)=>b[0].replace(/\{[a-z]+\}/g,'').length-a[0].replace(/\{[a-z]+\}/g,'').length).filter(([key])=>key.includes('{')).map(([source,target])=>{const names=[];let cursor=0,pattern='';for(const m of source.matchAll(/\{([a-z]+)\}/g)){pattern+=quoteRegex(source.slice(cursor,m.index))+'([\\s\\S]+?)';names.push(m[1]);cursor=m.index+m[0].length;}pattern+=quoteRegex(source.slice(cursor));return {regex:new RegExp('^'+pattern+'$'),names,target};});
function messageText(raw,depth=0){const value=String(raw??'');if(activeLanguage!=='en'||depth>6)return value;if(Object.prototype.hasOwnProperty.call(TRANSLATIONS.messages,value))return TRANSLATIONS.messages[value];const time=value.match(/^(\d{2}\/\d{2} \d{2}:\d{2}(?::\d{2})? · )([\s\S]*)$/);if(time)return time[1]+messageText(time[2],depth+1);for(const item of messagePatterns){const match=value.match(item.regex);if(match)return item.target.replace(/\{([a-z]+)\}/g,(_,key)=>{const captured=match[item.names.indexOf(key)+1];return key==='error'?messageText(captured,depth+1):captured;});}return value;}
staticLanguage();
