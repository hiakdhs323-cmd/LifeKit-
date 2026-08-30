(function(){
'use strict';
const $=id=>document.getElementById(id);
const toast=m=>window.showToast?window.showToast(m):window.toast?window.toast(m):null;
const esc=s=>String(s??'').replace(/[&<>"']/g,m=>({'&':'&amp;','<':'&lt;','>':'&gt;','"':'&quot;',"'":'&#39;'}[m]));

/* ---------- persistent settings ---------- */
function saveState(){ try{ if(window.saveData) window.saveData(); else if(window.Android?.saveData) window.Android.saveData(JSON.stringify(window.state)); }catch(_){} }
function getState(){ return window.state || (window.state={user:{},water:{},habits:[],expenses:[],school:null,ddays:[],routes:[]}); }

/* ---------- SCHOOL: real NEIS search + timetable + meals ---------- */
const REGIONS=[['','전국'],['B10','서울'],['C10','부산'],['D10','대구'],['E10','인천'],['F10','광주'],['G10','대전'],['H10','울산'],['I10','세종'],['J10','경기'],['K10','강원'],['M10','충북'],['N10','충남'],['P10','전북'],['Q10','전남'],['R10','경북'],['S10','경남'],['T10','제주']];
let selectedSchool=null;
function schoolModal(){return $('schoolSearchModal')||$('schoolModal')}
function schoolKey(){const s=getState(); return String(s.user?.neisKey||'').trim();}
async function neis(endpoint,params){
  const key=schoolKey();
  if(!key){throw new Error('NEIS 인증키가 없습니다. 설정에서 인증키를 입력해주세요.');}
  const q=new URLSearchParams({KEY:key,Type:'json,pindex=1',pIndex:'1',pSize:'100',...params});
  const r=await fetch('https://open.neis.go.kr/hub/'+endpoint+'?'+q.toString(),{cache:'no-store'});
  if(!r.ok) throw new Error('NEIS HTTP '+r.status);
  const j=await r.json();
  if(j.RESULT?.CODE && !String(j.RESULT.CODE).startsWith('INFO-')) throw new Error(j.RESULT.MESSAGE||j.RESULT.CODE);
  return j;
}
function neisRows(j,root){const a=j?.[root]; if(!Array.isArray(a)||a.length<2)return[]; const h=a[0]?.head?.[1]?.list||[]; return (a[1]?.row||[]).map(r=>Object.fromEntries(h.map(x=>[x,r[x]])));}
function ensureSchoolUi(){
  const m=schoolModal(); if(!m)return;
  const sheet=m.querySelector('.modal-animate-in')||m.querySelector('[class*="rounded-[32px"]')||m.firstElementChild; if(!sheet)return;
  let box=$('lkSchoolV3'); if(box)return;
  box=document.createElement('div'); box.id='lkSchoolV3';
  box.style.cssText='margin-top:12px;display:grid;gap:10px';
  box.innerHTML=`
    <div style="display:flex;gap:8px">
      <select id="lkNeisRegion" class="input" style="width:110px">${REGIONS.map(x=>`<option value="${x[0]}">${x[1]}</option>`).join('')}</select>
      <input id="lkNeisQuery" class="input" placeholder="학교명 2글자 이상" autocomplete="off" style="flex:1">
      <button id="lkNeisSearch" class="btn primary">검색</button>
    </div>
    <div id="lkNeisStatus" class="small" style="min-height:16px;color:var(--muted)"></div>
    <div id="lkNeisResults" class="list" style="max-height:210px;overflow:auto"></div>
    <div id="lkNeisPicked" style="display:none">
      <div id="lkNeisPickedCard" class="card" style="padding:12px"></div>
      <div class="two" style="margin-top:8px">
        <select id="lkNeisGrade" class="input"><option>1</option><option>2</option><option>3</option><option>4</option><option>5</option><option>6</option></select>
        <input id="lkNeisClass" class="input" type="number" min="1" max="50" value="1" placeholder="반">
      </div>
      <button id="lkNeisSave" class="btn primary" style="width:100%;margin-top:8px">이 학교로 저장</button>
    </div>
    <div class="small" style="line-height:1.5">학교 이름 검색은 NEIS 학교정보를 사용합니다. 시간표·급식은 선택한 학교의 공개 데이터를 조회합니다.</div>`;
  sheet.appendChild(box);
  $('lkNeisSearch').onclick=searchSchoolNEIS;
  $('lkNeisQuery').onkeydown=e=>{if(e.key==='Enter')searchSchoolNEIS()};
  $('lkNeisSave').onclick=confirmSchoolSelection;
  const old=getState().school;
  if(old){ $('lkNeisGrade').value=old.grade||'1'; $('lkNeisClass').value=old.classNo||old.classNum||'1'; }
}
window.openSchoolSearchModal=function(){ensureSchoolUi(); const m=schoolModal(); if(m)m.classList.remove('hidden'); if($('lkNeisQuery')) setTimeout(()=>$('lkNeisQuery').focus(),80)};
window.searchSchoolNEIS=async function(){
  ensureSchoolUi(); const q=$('lkNeisQuery')?.value.trim()||''; const reg=$('lkNeisRegion')?.value||'';
  if(q.length<2){toast('학교명을 2글자 이상 입력해주세요.');return;}
  if(!schoolKey()){toast('설정에서 NEIS 인증키를 먼저 입력해주세요.'); return;}
  $('lkNeisStatus').textContent='NEIS에서 학교를 찾는 중…'; $('lkNeisResults').innerHTML=''; $('lkNeisPicked').style.display='none'; selectedSchool=null;
  try{
    const params={SCHUL_NM:q}; if(reg)params.ATPT_OFCDC_SC_CODE=reg;
    const list=neisRows(await neis('schoolInfo',params),'schoolInfo');
    $('lkNeisStatus').textContent=list.length?`${Math.min(50,list.length)}개 결과`:'검색 결과가 없습니다.';
    list.slice(0,50).forEach(s=>{
      const d=document.createElement('div'); d.className='list-item';
      d.innerHTML=`<div style="flex:1"><b>${esc(s.SCHUL_NM)}</b><div class="small">${esc(s.SCHUL_KND_SC_NM||'학교')} · ${esc(s.ATPT_OFCDC_SC_NM||'')}<br>${esc(s.ROAD_NM||s.ORG_RDNMA||'')}</div></div><button class="btn primary">선택</button>`;
      d.querySelector('button').onclick=()=>{selectedSchool=s;$('lkNeisPickedCard').innerHTML=`<b>${esc(s.SCHUL_NM)}</b><div class="small">${esc(s.SCHUL_KND_SC_NM||'')} · ${esc(s.ATPT_OFCDC_SC_NM||'')}<br>${esc(s.ROAD_NM||s.ORG_RDNMA||'')}</div>`;$('lkNeisPicked').style.display='block'};
      $('lkNeisResults').appendChild(d);
    });
  }catch(e){$('lkNeisStatus').textContent='';$('lkNeisResults').innerHTML=`<div class="notice">실제 NEIS 조회에 실패했습니다.<br>${esc(e.message)}</div>`;}
};
window.confirmSchoolSelection=function(){
  if(!selectedSchool)return toast('먼저 학교를 선택해주세요.');
  const s=getState(); s.school={code:selectedSchool.SD_SCHUL_CODE,office:selectedSchool.ATPT_OFCDC_SC_CODE,name:selectedSchool.SCHUL_NM,kind:selectedSchool.SCHUL_KND_SC_NM,grade:$('lkNeisGrade').value,classNo:$('lkNeisClass').value,address:selectedSchool.ROAD_NM||selectedSchool.ORG_RDNMA||''};
  saveState(); window.renderSchoolInfo?.(); closeAll(); toast('학교가 저장되었습니다. 실제 시간표를 불러옵니다.'); loadTimetableV3();
};
function closeAll(){document.querySelectorAll('.fixed.inset-0').forEach(x=>x.classList.add('hidden'));}
window.renderSchoolInfo=function(){
  const s=getState().school; if($('schoolCardTitle'))$('schoolCardTitle').textContent=s?s.name:'학교를 등록해 주세요'; if($('schoolCardSub'))$('schoolCardSub').textContent=s?`${s.kind||'학교'} · ${s.grade}학년 ${s.classNo||s.classNum||1}반`:'학교 설정에서 학교를 검색해 연동하세요';
  if(!s){if($('timetableListContainer'))$('timetableListContainer').innerHTML='<div class="text-center py-6 text-xs text-slate-400">학교를 먼저 설정해주세요.</div>';if($('mealListContainer'))$('mealListContainer').innerHTML='<div class="text-center py-6 text-xs text-slate-400">학교를 먼저 설정해주세요.</div>';return}
  loadTimetableV3();
};
function endpointFor(kind){const k=String(kind||'');return k.includes('초등')?'elsTimetable':k.includes('중학교')?'misTimetable':'hisTimetable';}
function mondayDates(){const d=new Date();d.setHours(0,0,0,0); const day=(d.getDay()+6)%7; d.setDate(d.getDate()-day); return Array.from({length:5},(_,i)=>{const x=new Date(d);x.setDate(d.getDate()+i);return x.toISOString().slice(0,10).replaceAll('-','')})}
window.loadTimetableV3=async function(){
  const c=$('timetableListContainer'),s=getState().school; if(!c||!s)return; if(!schoolKey()){c.innerHTML='<div class="notice">NEIS 인증키가 설정되지 않았습니다.</div>';return}
  c.innerHTML='<div class="text-center py-6 text-xs text-slate-400">이번 주 실제 시간표를 불러오는 중…</div>';
  try{
    const ep=endpointFor(s.kind), dates=mondayDates(), days=['월','화','수','목','금']; let out='';
    for(let i=0;i<dates.length;i++){
      let rows=[]; try{rows=neisRows(await neis(ep,{ATPT_OFCDC_SC_CODE:s.office,SD_SCHUL_CODE:s.code,ALL_TI_YMD:dates[i],GRADE:s.grade,CLASS_NM:s.classNo}),ep)}catch(e){rows=[]}
      rows=rows.filter(x=>String(x.GRADE||s.grade)===String(s.grade)&&String(x.CLASS_NM||'').split('-').pop()===String(s.classNo)).sort((a,b)=>(+a.PERIO||0)-(+b.PERIO||0));
      out+=`<div class="day" style="margin-bottom:10px"><div class="row" style="margin-bottom:6px"><b>${days[i]} · ${dates[i].slice(4,6)}/${dates[i].slice(6,8)}</b><span class="small">${rows.length}개 수업</span></div>${rows.length?rows.map(x=>`<div class="lesson" style="display:flex;justify-content:space-between;gap:10px"><span>${esc(x.PERIO||'')}교시</span><b>${esc(x.ITRT_CNTNT||x.SUBJECT||'수업')}</b></div>`).join(''):'<div class="lesson">시간표 정보 없음</div>'}</div>`;
    }
    c.innerHTML=out+'<div class="small">출처: 교육부 NEIS 공개 데이터</div>';
  }catch(e){c.innerHTML=`<div class="notice">시간표 조회 실패<br>${esc(e.message)}</div>`}
};
window.loadMealsV3=async function(){
  const c=$('mealListContainer'),s=getState().school; if(!c||!s)return; if(!schoolKey()){c.innerHTML='<div class="notice">NEIS 인증키가 설정되지 않았습니다.</div>';return}
  c.innerHTML='<div class="text-center py-6 text-xs text-slate-400">실제 급식 정보를 불러오는 중…</div>';
  const f=new Date(),t=new Date(Date.now()+6*86400000),fmt=d=>d.toISOString().slice(0,10).replaceAll('-','');
  try{const rows=neisRows(await neis('mealServiceDietInfo',{ATPT_OFCDC_SC_CODE:s.office,SD_SCHUL_CODE:s.code,MLSV_FROM_YMD:fmt(f),MLSV_TO_YMD:fmt(t)}),'mealServiceDietInfo'); if(!rows.length){c.innerHTML='<div class="empty">이번 기간 급식 정보가 없습니다.</div>';return} c.innerHTML=rows.map(x=>`<div class="list-item"><div><b>${esc(x.MLSV_YMD||'')} · ${esc(x.MMEAL_SC_NM||'중식')}</b><div class="small" style="line-height:1.5;margin-top:4px">${esc(String(x.DDISH_NM||'').replace(/<br\s*\/?>(\r?\n)?/gi,' · ').replace(/\([^)]*\)/g,''))}</div></div></div>`).join('');}
  catch(e){c.innerHTML=`<div class="notice">급식 조회 실패<br>${esc(e.message)}</div>`}
};
window.switchSchoolSubTab=function(tab){
  $('schoolSubViewTimetable')?.classList.toggle('hidden',tab!=='timetable'); $('schoolSubViewMeals')?.classList.toggle('hidden',tab!=='meals');
  if(tab==='timetable')loadTimetableV3(); else loadMealsV3();
  $('btnSubTabTimetable')?.classList.toggle('bg-white',tab==='timetable'); $('btnSubTabMeals')?.classList.toggle('bg-white',tab==='meals');
};

/* ---------- GPS NAV: real map + speed + altitude-colored travelled path ---------- */
let map3=null,user3=null,watch3=null,tracking3=false,path3=[],dist3=0,last3=null,segments3=[],tiles3=null;
function hav(a,b){const R=6371000,p=Math.PI/180,da=(b[0]-a[0])*p,db=(b[1]-a[1])*p,x=Math.sin(da/2)**2+Math.cos(a[0]*p)*Math.cos(b[0]*p)*Math.sin(db/2)**2;return 2*R*Math.asin(Math.sqrt(x));}
function colorAltitude(alt){const vals=path3.map(p=>p.alt).filter(Number.isFinite);if(vals.length<2)return '#22c55e';const min=Math.min(...vals),max=Math.max(...vals),n=(max-min)<2?.5:(alt-min)/(max-min);return n<.34?'#22c55e':n<.67?'#eab308':'#ef4444';}
function redrawSegments(){if(!map3)return;segments3.forEach(x=>map3.removeLayer(x));segments3=[];for(let i=1;i<path3.length;i++){const a=path3[i-1],b=path3[i];const line=L.polyline([[a.lat,a.lng],[b.lat,b.lng]],{color:colorAltitude((a.alt+b.alt)/2),weight:7,opacity:.9,lineCap:'round'}).addTo(map3);segments3.push(line);}}
function makeMap(){
  if(map3)return; const host=$('mapContainer'); if(!host||!window.L)return;
  host.innerHTML=''; map3=L.map(host,{zoomControl:false,attributionControl:true}).setView([37.5665,126.978],15);
  tiles3=L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png',{maxZoom:19,attribution:'© OpenStreetMap contributors'}).addTo(map3);
  user3=L.marker([37.5665,126.978],{icon:L.divIcon({className:'',html:'<div style="width:18px;height:18px;border-radius:50%;background:#4f46e5;border:4px solid white;box-shadow:0 0 0 7px #4f46e533"></div>',iconSize:[18,18],iconAnchor:[9,9]})}).addTo(map3);
  setTimeout(()=>map3.invalidateSize(),150);
}
window.initMap=function(){makeMap()};
window.recenterMap=function(){if(last3&&map3){map3.setView([last3.lat,last3.lng],17,{animate:true});return}navigator.geolocation?.getCurrentPosition(p=>{makeMap();const ll=[p.coords.latitude,p.coords.longitude];user3?.setLatLng(ll);map3?.setView(ll,17);},()=>toast('현재 위치를 확인할 수 없습니다.'),{enableHighAccuracy:true,timeout:8000,maximumAge:1000})};
function pos3(p){const c=p.coords,now=Date.now(),ll=[c.latitude,c.longitude];let alt=Number.isFinite(c.altitude)?c.altitude:null; if(last3){const d=hav([last3.lat,last3.lng],ll);if(d>=2&&d<300)dist3+=d;if(alt!=null&&last3.alt!=null)alt=last3.alt*.7+alt*.3} const item={lat:ll[0],lng:ll[1],alt,t:now};path3.push(item);last3=item;user3?.setLatLng(ll);if(path3.length===1)map3?.setView(ll,17);else map3?.panTo(ll,{animate:true,duration:.25});redrawSegments();const rawSpeed=Number.isFinite(c.speed)&&c.speed>=0?c.speed:0;let kmh=rawSpeed*3.6;if(!rawSpeed&&path3.length>1){const prev=path3[path3.length-2],dt=(now-prev.t)/1000;if(dt>0)kmh=Math.min(200,hav([prev.lat,prev.lng],ll)/dt*3.6)} if($('mapSpeedDisplay'))$('mapSpeedDisplay').textContent=kmh.toFixed(1);if($('mapDistanceDisplay'))$('mapDistanceDisplay').textContent=(dist3/1000).toFixed(2);if($('mapStatusText'))$('mapStatusText').textContent=`GPS ±${Math.round(c.accuracy||0)}m · 고도 ${alt==null?'—':alt.toFixed(0)+'m'}`;}
window.toggleMapGps=function(){
  makeMap(); if(!navigator.geolocation)return toast('이 기기에서 GPS를 사용할 수 없습니다.');
  const btn=$('mapGpsToggleBtn'); if(tracking3){navigator.geolocation.clearWatch(watch3);watch3=null;tracking3=false;if(btn){btn.innerHTML='<i class="fa-solid fa-play"></i> 경로 기록 시작';btn.className='flex-1 bg-blue-600 text-white font-extrabold py-3.5 rounded-2xl shadow-lg touch-active transition duration-300 flex items-center justify-center gap-2 text-xs';}if(path3.length>1)toast('경로 기록을 중지했습니다.');return;}
  path3=[];segments3=[];dist3=0;last3=null;redrawSegments();if($('mapDistanceDisplay'))$('mapDistanceDisplay').textContent='0.00';if($('mapSpeedDisplay'))$('mapSpeedDisplay').textContent='0.0';tracking3=true;if(btn){btn.innerHTML='<i class="fa-solid fa-stop"></i> 기록 중지';btn.className='flex-1 bg-rose-500 text-white font-extrabold py-3.5 rounded-2xl shadow-lg touch-active transition duration-300 flex items-center justify-center gap-2 text-xs';}if($('mapStatusText'))$('mapStatusText').textContent='고정밀 GPS 위치 확인 중…';
  watch3=navigator.geolocation.watchPosition(pos3,e=>{if(e.code===1){toast('위치 권한을 허용해주세요.');navigator.geolocation.clearWatch(watch3);watch3=null;tracking3=false}else if($('mapStatusText'))$('mapStatusText').textContent='GPS 신호를 기다리는 중…';},{enableHighAccuracy:true,maximumAge:500,timeout:10000});
};
window.switchTab=(function(original){return function(tabId){if(original)original(tabId);if(tabId==='map'){setTimeout(()=>{makeMap();map3?.invalidateSize();},120)}}})(window.switchTab);

/* ---------- REAL URL SECURITY CHECK ---------- */
async function dnsLookup(host){const r=await fetch('https://cloudflare-dns.com/dns-query?name='+encodeURIComponent(host)+'&type=A',{headers:{Accept:'application/dns-json'},cache:'no-store'});if(!r.ok)throw new Error('DNS HTTP '+r.status);return r.json();}
async function urlhausLookup(target){const key=String(getState().user?.urlhausKey||'').trim();if(!key)return {skipped:true};const f=new URLSearchParams({url:target});const r=await fetch('https://urlhaus-api.abuse.ch/v1/url/',{method:'POST',headers:{'Content-Type':'application/x-www-form-urlencoded','Auth-Key':key},body:f.toString(),cache:'no-store'});if(!r.ok)throw new Error('URLhaus HTTP '+r.status);return r.json();}
window.scanMaliciousUrl=async function(){
  const inp=$('urlInput'),log=$('urlLogBox'),wrap=$('urlScanResult'),ver=$('urlFinalVerdict'),btn=$('urlScanBtn'); if(!inp||!log||!wrap||!ver||!btn)return;
  let target=inp.value.trim(); if(!target)return toast('URL을 입력해주세요.'); if(!/^https?:\/\//i.test(target))target='https://'+target; let u;try{u=new URL(target)}catch(_){return toast('올바른 URL 형식이 아닙니다.')}
  btn.disabled=true;btn.innerText='분석중';wrap.classList.remove('hidden');ver.classList.add('hidden');log.innerHTML='';const line=m=>{const d=document.createElement('div');d.textContent='> '+m;log.appendChild(d);log.scrollTop=log.scrollHeight};
  let score=0,flags=[],dnsOk=false,db=false;line('실시간 URL 안전성 검사 시작');line('대상: '+target);
  if(u.protocol==='http:'){score+=35;flags.push('암호화되지 않은 HTTP');line('[주의] HTTP 연결입니다.')}else line('[통과] HTTPS 사용');
  const ip=/^(?:\d{1,3}\.){3}\d{1,3}$/.test(u.hostname); if(ip){score+=45;flags.push('IP 주소 직접 접속');line('[주의] 도메인 대신 IP 주소를 사용합니다.')}
  if(u.hostname.toLowerCase().includes('xn--')){score+=40;flags.push('Punycode 도메인');line('[주의] Punycode 도메인 감지')}
  if(u.hostname.split('.').length>3){score+=15;flags.push('긴 서브도메인');line('[정보] 서브도메인이 많습니다.')}
  try{const d=await dnsLookup(u.hostname);dnsOk=d.Status===0&&(d.Answer||[]).length>0;if(dnsOk)line('[통과] Cloudflare DNS에서 도메인 확인');else{score+=30;flags.push('DNS 미해결');line('[경고] DNS 응답에서 주소를 찾지 못했습니다.')}}catch(e){line('[정보] DNS 조회 실패: '+e.message)}
  try{const h=await urlhausLookup(target);if(!h.skipped){db=h.query_status==='ok';if(db){score+=100;flags.push('URLhaus 악성 URL 등재');line('[위험] URLhaus에서 악성 URL 등재를 확인했습니다.')}else line('[통과] URLhaus DB에서 일치 항목이 없습니다.')}else line('[정보] URLhaus API 키가 없어 글로벌 악성 DB 조회는 건너뜁니다.')}catch(e){line('[정보] URLhaus 조회 실패: '+e.message)}
  line('분석 완료');ver.classList.remove('hidden');ver.className='text-xs font-bold p-3 rounded-2xl text-center '+(score>=50?'bg-rose-100 text-rose-600':score>0?'bg-amber-100 text-amber-600':'bg-emerald-100 text-emerald-600');ver.innerHTML=score>=50?`⚠️ <b>주의가 필요한 URL</b><br><span style="font-size:10px">위험 신호 ${score}점 · ${esc(flags.join(', '))}</span>`:score>0?`🟡 <b>주의</b><br><span style="font-size:10px">일부 위험 신호가 감지되었습니다.</span>`:`🟢 <b>현재 검사에서 알려진 위험 신호가 없습니다</b><br><span style="font-size:10px">URL이 안전하다고 보장하는 결과는 아닙니다.</span>`;
  btn.disabled=false;btn.innerText='분석';
};

/* ---------- settings: add real API key fields once ---------- */
function ensureSecuritySettings(){
  const modal=$('profileModal');if(!modal||$('lkApiSettings'))return;const body=modal.querySelector('.space-y-2')||modal.lastElementChild;const box=document.createElement('div');box.id='lkApiSettings';box.style.cssText='margin-top:12px;padding-top:12px;border-top:1px solid var(--line)';const s=getState();box.innerHTML=`<div class="small" style="margin-bottom:6px">실시간 데이터 API</div><div class="form"><input id="lkNeisKey" class="input" placeholder="NEIS 인증키" value="${esc(s.user?.neisKey||'')}"><input id="lkUrlhausKey" class="input" placeholder="URLhaus Auth-Key (선택)" value="${esc(s.user?.urlhausKey||'')}"><button id="lkApiSave" class="btn primary">API 설정 저장</button></div>`;body?.appendChild(box);$('lkApiSave').onclick=()=>{s.user=s.user||{};s.user.neisKey=$('lkNeisKey').value.trim();s.user.urlhausKey=$('lkUrlhausKey').value.trim();saveState();toast('API 설정을 저장했습니다.');};}
const oldOpen=window.openProfileModal;window.openProfileModal=function(){oldOpen?.();ensureSecuritySettings()};

/* startup: render school and keep the UI responsive */
setTimeout(()=>{ensureSchoolUi();ensureSecuritySettings();window.renderSchoolInfo?.();},200);
})();
