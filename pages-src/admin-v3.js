(() => {
  "use strict";

  const API = "https://jhjbiejqtbidloxcwryr.supabase.co/functions/v1/api";
  const PUBLISHABLE_KEY = "sb_publishable_mA5DOfPA-ExloawT3aJpNw_2PeVgEEc";
  const ROOT = document.querySelector("#app");
  const TOKEN_KEY = "crimescene-admin-token";
  const USER_KEY = "crimescene-admin-user";
  const CUSTOMER_SITE = "https://sosirusok.github.io/crimescene/";

  const reservationLabels = {
    PENDING_PAYMENT:"접수 완료", CONFIRMED:"예약 확정", COMPLETED:"이용 완료",
    CANCEL_REQUESTED:"취소 처리 중", CANCELED:"예약 취소", NO_SHOW:"미방문",
  };
  const paymentLabels = { READY:"결제 대기", PAID:"결제 완료", FAILED:"결제 실패", REFUNDED:"환불 완료" };
  const inquiryLabels = { NEW:"새 문의", IN_PROGRESS:"확인 중", ANSWERED:"답변 완료", CLOSED:"처리 완료" };
  const stateLabels = {
    AVAILABLE:"예약 가능", OPEN_RECRUITING:"오픈룸 모집 중", OPEN_PLAYABLE:"게임 진행 가능",
    FULL:"정원 마감", PRIVATE_BOOKED:"단독팀 예약", BLOCKED:"운영 중지",
  };

  const state = {
    token: localStorage.getItem(TOKEN_KEY) || "",
    user: readJson(localStorage.getItem(USER_KEY)) || null,
    data: null,
    notices: [],
    view: "dashboard",
    selectedTheme: "A",
    scheduleDate: koreaToday(),
    reservationQuery: "",
    reservationTheme: "",
    reservationStatus: "",
    roomFilter: "",
    busy: "",
  };

  const escapeHtml = (value) => String(value ?? "").replace(/[&<>'"]/g, (character) => ({
    "&":"&amp;", "<":"&lt;", ">":"&gt;", "'":"&#39;", '"':"&quot;",
  })[character]);
  const money = (value) => `${Number(value || 0).toLocaleString("ko-KR")}원`;
  const hhmm = (value) => String(value ?? "").slice(0,5);
  const formatDate = (value) => new Intl.DateTimeFormat("ko-KR", { year:"numeric",month:"long",day:"numeric",weekday:"short" }).format(new Date(`${value}T12:00:00+09:00`));
  const formatDateTime = (value) => value ? new Intl.DateTimeFormat("ko-KR", { timeZone:"Asia/Seoul",year:"numeric",month:"2-digit",day:"2-digit",hour:"2-digit",minute:"2-digit" }).format(new Date(value)) : "-";

  function readJson(value) { try { return JSON.parse(value || "null"); } catch { return null; } }
  function koreaToday() { return new Intl.DateTimeFormat("en-CA", { timeZone:"Asia/Seoul",year:"numeric",month:"2-digit",day:"2-digit" }).format(new Date()); }
  function tomorrow(value, amount=1) { const date=new Date(`${value}T00:00:00+09:00`);date.setDate(date.getDate()+amount);return new Intl.DateTimeFormat("en-CA",{timeZone:"Asia/Seoul",year:"numeric",month:"2-digit",day:"2-digit"}).format(date); }

  async function api(path, options = {}, admin = false) {
    const response = await fetch(`${API}${path}`, {
      ...options,
      cache:"no-store",
      headers: {
        apikey:PUBLISHABLE_KEY,
        "Content-Type":"application/json",
        ...(admin && state.token ? { Authorization:`Bearer ${state.token}` } : {}),
        ...(options.headers || {}),
      },
    });
    const data = await response.json().catch(() => ({}));
    if (!response.ok) {
      const error = new Error(data.error || "요청을 처리하지 못했습니다.");
      error.status = response.status;
      throw error;
    }
    return data;
  }

  function notify(message, type = "") {
    let stack=document.querySelector(".cs-admin-toasts");
    if(!stack){stack=document.createElement("div");stack.className="cs-admin-toasts";document.body.append(stack);}
    const item=document.createElement("div");item.className=`cs-admin-toast ${type}`;item.textContent=message;stack.append(item);setTimeout(()=>item.remove(),4500);
  }

  function roomInfo(room = {}) {
    const stateName=room.state||"AVAILABLE",count=Number(room.bookedCount||0),capacity=Number(room.capacity||0),minimum=Number(room.minimumPlayers||4),remaining=Math.max(0,Number(room.remaining??capacity-count));
    const descriptions={
      AVAILABLE:"새 예약을 받을 수 있습니다.",
      OPEN_RECRUITING:`현재 ${count}명입니다. ${Math.max(0,minimum-count)}명 이상 더 모이면 게임 진행이 가능합니다.`,
      OPEN_PLAYABLE:`최소 인원이 충족되었습니다. 남은 ${remaining}자리도 합류할 수 있습니다.`,
      FULL:"오픈룸 정원이 모두 예약되었습니다.",
      PRIVATE_BOOKED:"단독팀 예약으로 마감된 회차입니다.",
      BLOCKED:"운영 중지된 회차입니다.",
    };
    return {state:stateName,count,capacity,minimum,remaining,label:stateLabels[stateName]||stateName,description:descriptions[stateName]||"",className:`state-${String(stateName).toLowerCase().replaceAll("_","-")}`};
  }

  function setSession(result) {
    state.token=result.token;state.user=result.user;
    localStorage.setItem(TOKEN_KEY,result.token);localStorage.setItem(USER_KEY,JSON.stringify(result.user));
  }
  function clearSession() { state.token="";state.user=null;state.data=null;localStorage.removeItem(TOKEN_KEY);localStorage.removeItem(USER_KEY); }

  function loginPage(message = "") {
    ROOT.innerHTML=`<main class="cs-admin-login"><section><header><span>크라임씬플레이 서면1호점</span><h1>운영 관리</h1><p>예약, 오픈룸 매칭, 테마와 시간대를 한 화면에서 관리합니다.</p></header><form id="admin-login-form"><label><span>관리자 암호키</span><input type="password" name="accessKey" autocomplete="current-password" autocapitalize="none" spellcheck="false" required autofocus></label>${message?`<p class="cs-admin-form-error">${escapeHtml(message)}</p>`:""}<button type="submit">관리 화면 열기</button></form><footer><a href="${CUSTOMER_SITE}">고객 사이트 보기</a><small>암호키는 브라우저에 저장하지 않습니다.</small></footer></section></main>`;
    document.querySelector("#admin-login-form")?.addEventListener("submit",async(event)=>{event.preventDefault();const form=event.currentTarget,button=form.querySelector("button");button.disabled=true;button.textContent="확인 중";try{const result=await api("/admin/login",{method:"POST",body:JSON.stringify(Object.fromEntries(new FormData(form)))});setSession(result);await loadDashboard();}catch(error){loginPage(error.message);}});
  }

  const navItems = [
    ["dashboard","오늘 운영","홈"],
    ["openrooms","오픈룸 매칭","팀"],
    ["reservations","예약 관리","예약"],
    ["schedule","회차 관리","시간"],
    ["themes","테마 관리","테마"],
    ["inquiries","문의 관리","문의"],
    ["notices","공지 관리","공지"],
    ["security","보안 설정","보안"],
  ];

  function shell(content, title, description, actions = "") {
    const metrics=state.data?.metrics||{};
    ROOT.innerHTML=`<div class="cs-admin-app"><aside class="cs-admin-sidebar"><a class="cs-admin-brand" href="${CUSTOMER_SITE}"><b>CS</b><span>크라임씬플레이<br>서면1호점</span></a><nav>${navItems.map(([key,label,short])=>`<button type="button" data-view="${key}" class="${state.view===key?"is-active":""}"><span>${short}</span><strong>${label}</strong>${key==="openrooms"&&metrics.recruitingRooms?`<i>${metrics.recruitingRooms}</i>`:""}${key==="inquiries"&&state.data?.inquiries?.filter((x)=>x.status==="NEW").length?`<i>${state.data.inquiries.filter((x)=>x.status==="NEW").length}</i>`:""}</button>`).join("")}</nav><div class="cs-admin-connection"><i></i><div><strong>Supabase 연결됨</strong><small>실시간 운영 데이터</small></div></div></aside><div class="cs-admin-main"><header class="cs-admin-top"><button class="cs-admin-menu" type="button" aria-label="메뉴 열기">☰</button><div><span>${escapeHtml(state.user?.displayName||"서면점 운영자")}</span><small>${escapeHtml(state.user?.role||"OWNER")}</small></div><a href="${CUSTOMER_SITE}" target="_blank" rel="noreferrer">고객 사이트</a><button type="button" id="admin-logout">로그아웃</button></header><main class="cs-admin-content"><header class="cs-admin-heading"><div><p>크라임씬플레이 서면1호점</p><h1>${escapeHtml(title)}</h1><span>${escapeHtml(description)}</span></div><div>${actions}<button type="button" id="admin-refresh">새로고침</button></div></header><section id="admin-view">${content}</section></main></div><div class="cs-admin-sidebar-backdrop"></div></div>`;
    bindShell();
  }

  function bindShell() {
    document.querySelectorAll("[data-view]").forEach((button)=>button.addEventListener("click",()=>{state.view=button.dataset.view;renderCurrent();document.querySelector(".cs-admin-app")?.classList.remove("menu-open");}));
    document.querySelector("#admin-logout")?.addEventListener("click",()=>{clearSession();loginPage();});
    document.querySelector("#admin-refresh")?.addEventListener("click",loadDashboard);
    document.querySelector(".cs-admin-menu")?.addEventListener("click",()=>document.querySelector(".cs-admin-app")?.classList.toggle("menu-open"));
    document.querySelector(".cs-admin-sidebar-backdrop")?.addEventListener("click",()=>document.querySelector(".cs-admin-app")?.classList.remove("menu-open"));
  }

  async function loadDashboard() {
    if(!state.token){loginPage();return;}
    ROOT.innerHTML=`<main class="cs-admin-boot"><span>운영 데이터를 불러오고 있습니다.</span><i></i></main>`;
    try{state.data=await api("/admin/dashboard",{},true);if(!state.user)state.user={displayName:"서면점 운영자",role:"OWNER"};renderCurrent();}
    catch(error){if(error.status===401){clearSession();loginPage("로그인이 만료되었습니다. 암호키를 다시 입력해 주세요.");}else{ROOT.innerHTML=`<main class="cs-admin-fatal"><h1>운영 데이터를 불러오지 못했습니다.</h1><p>${escapeHtml(error.message)}</p><button type="button">다시 시도</button></main>`;ROOT.querySelector("button")?.addEventListener("click",loadDashboard);}}
  }

  function metricCard(label, value, unit, note, tone="") { return `<article class="cs-admin-metric ${tone}"><span>${escapeHtml(label)}</span><strong>${escapeHtml(value)}<small>${escapeHtml(unit)}</small></strong><p>${escapeHtml(note)}</p></article>`; }

  function dashboardView() {
    const data=state.data,metrics=data.metrics||{},today=koreaToday(),todayRows=(data.reservations||[]).filter((x)=>x.play_date===today&&!['CANCELED','NO_SHOW'].includes(x.status)).sort((a,b)=>a.start_time.localeCompare(b.start_time));
    const open=(data.openRooms||[]).filter((x)=>x.playDate>=today).slice(0,4);
    const content=`<div class="cs-admin-metrics">${metricCard("오늘 예약",String(metrics.today||0),"건","오늘 이용 예정 예약","primary")}${metricCard("모집 중 오픈룸",String(metrics.recruitingRooms||0),"개","최소 인원 미충족","warning")}${metricCard("진행 가능 오픈룸",String(metrics.playableRooms||0),"개","4명 이상 매칭 완료","success")}${metricCard("활성 예약",String(metrics.active||0),"건","취소·미방문 제외")}${metricCard("결제 완료 매출",Number(metrics.revenue||0).toLocaleString("ko-KR"),"원","결제 완료 건 기준")}</div><div class="cs-admin-two-column"><section class="cs-admin-panel"><header><div><span>오늘 일정</span><h2>${escapeHtml(formatDate(today))}</h2></div><button type="button" data-go-view="schedule">회차 관리</button></header>${todayRows.length?`<div class="cs-admin-today-list">${todayRows.map((row)=>`<article><time>${escapeHtml(row.start_time)}</time><div><strong>${escapeHtml(row.theme_title)}</strong><span>${escapeHtml(row.customer_name)} · ${row.party_size}명 · ${row.open_room?"오픈룸":"단독팀"}</span></div><b>${escapeHtml(reservationLabels[row.status]||row.status)}</b></article>`).join("")}</div>`:`<p class="cs-admin-empty">오늘 등록된 예약이 없습니다.</p>`}</section><section class="cs-admin-panel"><header><div><span>오픈룸 우선 확인</span><h2>팀 매칭 현황</h2></div><button type="button" data-go-view="openrooms">전체 보기</button></header>${open.length?`<div class="cs-admin-room-summary">${open.map(roomSummaryCard).join("")}</div>`:`<p class="cs-admin-empty">확인할 오픈룸이 없습니다.</p>`}</section></div><section class="cs-admin-panel cs-admin-quick"><header><div><span>빠른 작업</span><h2>자주 쓰는 운영 메뉴</h2></div></header><div><button type="button" data-go-view="reservations"><b>예약 상태 변경</b><span>확정, 완료, 취소, 결제 상태 관리</span></button><button type="button" data-go-view="themes"><b>테마와 시간대 수정</b><span>제목, 정원, 가격, 운영 시간 변경</span></button><button type="button" data-go-view="notices"><b>공지 게시</b><span>고객 사이트 공지 등록과 수정</span></button></div></section>`;
    shell(content,"오늘 운영","오늘 일정과 오픈룸 모집 상태를 먼저 확인합니다.");
    document.querySelectorAll("[data-go-view]").forEach((button)=>button.addEventListener("click",()=>{state.view=button.dataset.goView;renderCurrent();}));
  }

  function roomSummaryCard(room) {
    const info=roomInfo(room);return `<article class="cs-admin-room-summary-card ${info.className}"><div><strong>${escapeHtml(room.themeTitle)}</strong><span>${escapeHtml(room.playDate)} · ${escapeHtml(room.startTime)}</span></div><b>${info.count}/${info.capacity}명</b><small>${escapeHtml(info.label)}</small></article>`;
  }

  function openRoomsView() {
    const rooms=(state.data.openRooms||[]).filter((room)=>!state.roomFilter||room.state===state.roomFilter);
    const content=`<div class="cs-admin-filterbar"><label><span>상태</span><select id="room-state-filter"><option value="">전체 오픈룸</option>${["OPEN_RECRUITING","OPEN_PLAYABLE","FULL"].map((value)=>`<option value="${value}" ${state.roomFilter===value?"selected":""}>${stateLabels[value]}</option>`).join("")}</select></label><p>같은 사건, 날짜, 회차의 서로 다른 예약팀을 한 방으로 묶어서 표시합니다.</p></div>${rooms.length?`<div class="cs-admin-openroom-grid">${rooms.map((room)=>{const info=roomInfo(room);return `<article class="cs-admin-openroom ${info.className}"><header><div><span>${escapeHtml(room.playDate)} · ${escapeHtml(room.startTime)}</span><h2>${escapeHtml(room.themeTitle)}</h2></div><strong>${info.count}/${info.capacity}명</strong></header><div class="cs-admin-progress"><i style="width:${info.capacity?Math.min(100,info.count/info.capacity*100):0}%"></i></div><p><b>${escapeHtml(info.label)}</b>${escapeHtml(info.description)}</p><div class="cs-admin-team-list">${(room.teams||[]).map((team)=>`<section><span>팀 ${team.teamNumber}</span><div><strong>${escapeHtml(team.customerName)} · ${team.partySize}명</strong><small>${escapeHtml(team.phone)} · ${escapeHtml(reservationLabels[team.status]||team.status)} · ${escapeHtml(paymentLabels[team.paymentStatus]||team.paymentStatus)}</small><blockquote>${escapeHtml(team.message||"소개가 입력되지 않았습니다.")}</blockquote></div></section>`).join("")}</div></article>`;}).join("")}</div>`:`<p class="cs-admin-empty large">조건에 맞는 오픈룸이 없습니다.</p>`}`;
    shell(content,"오픈룸 매칭","모집 중인 팀과 게임 가능 여부를 회차 단위로 확인합니다.");
    document.querySelector("#room-state-filter")?.addEventListener("change",(event)=>{state.roomFilter=event.target.value;openRoomsView();});
  }

  function filteredReservations() {
    const query=state.reservationQuery.trim().toLowerCase();
    return (state.data.reservations||[]).filter((row)=>{
      if(state.reservationTheme&&row.theme_id!==state.reservationTheme)return false;
      if(state.reservationStatus&&row.status!==state.reservationStatus)return false;
      if(query&&!`${row.customer_name} ${row.phone} ${row.theme_title} ${row.lookup_code} ${row.play_date} ${row.start_time}`.toLowerCase().includes(query))return false;
      return true;
    });
  }

  function reservationsView() {
    const rows=filteredReservations(),themes=state.data.themes||[];
    const content=`<div class="cs-admin-filterbar reservations"><label class="grow"><span>검색</span><input id="reservation-search" value="${escapeHtml(state.reservationQuery)}" placeholder="예약자, 연락처, 사건, 날짜 검색"></label><label><span>테마</span><select id="reservation-theme"><option value="">전체</option>${themes.map((t)=>`<option value="${t.id}" ${state.reservationTheme===t.id?"selected":""}>${escapeHtml(t.shortTitle)}</option>`).join("")}</select></label><label><span>예약 상태</span><select id="reservation-status"><option value="">전체</option>${Object.entries(reservationLabels).map(([value,label])=>`<option value="${value}" ${state.reservationStatus===value?"selected":""}>${label}</option>`).join("")}</select></label><strong>${rows.length}건</strong></div>${rows.length?`<div class="cs-admin-reservation-list">${rows.map((row)=>`<article data-reservation="${row.id}"><header><div><span>${escapeHtml(row.play_date)} · ${escapeHtml(row.start_time)}</span><h2>${escapeHtml(row.theme_title)}</h2></div><b>${escapeHtml(row.lookup_code)}</b></header><div class="cs-admin-reservation-body"><dl><div><dt>예약자</dt><dd>${escapeHtml(row.customer_name)}</dd></div><div><dt>연락처</dt><dd>${escapeHtml(row.phone)}</dd></div><div><dt>인원</dt><dd>${row.party_size}명 · ${row.open_room?"오픈룸":"단독팀"}</dd></div><div><dt>금액</dt><dd>${money(row.total_amount)}</dd></div><div><dt>접수 시각</dt><dd>${escapeHtml(formatDateTime(row.created_at))}</dd></div></dl>${row.open_room?`<blockquote><b>오픈룸 소개</b>${escapeHtml(row.special_request||"소개 없음")}</blockquote>`:""}</div><footer><label><span>예약 상태</span><select data-res-status>${Object.entries(reservationLabels).map(([value,label])=>`<option value="${value}" ${row.status===value?"selected":""}>${label}</option>`).join("")}</select></label><label><span>결제 상태</span><select data-pay-status>${Object.entries(paymentLabels).map(([value,label])=>`<option value="${value}" ${row.payment_status===value?"selected":""}>${label}</option>`).join("")}</select></label><button type="button" data-save-reservation>변경 저장</button></footer></article>`).join("")}</div>`:`<p class="cs-admin-empty large">검색 조건에 맞는 예약이 없습니다.</p>`}`;
    shell(content,"예약 관리","예약자 정보와 예약·결제 상태를 빠르게 변경합니다.");
    let searchTimer;
    document.querySelector("#reservation-search")?.addEventListener("input",(event)=>{clearTimeout(searchTimer);state.reservationQuery=event.target.value;searchTimer=setTimeout(reservationsView,250);});
    document.querySelector("#reservation-theme")?.addEventListener("change",(event)=>{state.reservationTheme=event.target.value;reservationsView();});
    document.querySelector("#reservation-status")?.addEventListener("change",(event)=>{state.reservationStatus=event.target.value;reservationsView();});
    document.querySelectorAll("[data-save-reservation]").forEach((button)=>button.addEventListener("click",async()=>{const card=button.closest("[data-reservation]");button.disabled=true;try{await api("/admin/reservations",{method:"PATCH",body:JSON.stringify({id:card.dataset.reservation,status:card.querySelector("[data-res-status]").value,paymentStatus:card.querySelector("[data-pay-status]").value})},true);notify("예약 상태를 저장했습니다.","success");await reloadData("reservations");}catch(error){notify(error.message,"error");button.disabled=false;}}));
  }

  async function scheduleView() {
    const themes=state.data.themes||[],theme=themes.find((t)=>t.id===state.selectedTheme)||themes[0];if(theme)state.selectedTheme=theme.id;
    const content=`<div class="cs-admin-filterbar"><label><span>운영 날짜</span><input type="date" id="schedule-date" value="${state.scheduleDate}"></label><label class="grow"><span>테마</span><select id="schedule-theme">${themes.map((t)=>`<option value="${t.id}" ${state.selectedTheme===t.id?"selected":""}>${escapeHtml(t.shortTitle)}</option>`).join("")}</select></label><button type="button" id="next-day">다음 날</button><button type="button" data-go-theme="${theme?.id||""}">시간대 수정</button></div><div id="schedule-grid"><p class="cs-admin-loading">회차 상태를 불러오고 있습니다.</p></div>`;
    shell(content,"회차 관리","날짜별 예약 인원과 운영 여부를 확인하고 회차를 열거나 중지합니다.");
    document.querySelector("#schedule-date")?.addEventListener("change",(event)=>{state.scheduleDate=event.target.value;loadSchedule();});
    document.querySelector("#schedule-theme")?.addEventListener("change",(event)=>{state.selectedTheme=event.target.value;loadSchedule();});
    document.querySelector("#next-day")?.addEventListener("click",()=>{state.scheduleDate=tomorrow(state.scheduleDate);document.querySelector("#schedule-date").value=state.scheduleDate;loadSchedule();});
    document.querySelector("[data-go-theme]")?.addEventListener("click",()=>{state.view="themes";state.selectedTheme=theme?.id||"A";themesView();});
    loadSchedule();
  }

  async function loadSchedule() {
    const host=document.querySelector("#schedule-grid");if(!host)return;host.innerHTML=`<p class="cs-admin-loading">회차 상태를 불러오고 있습니다.</p>`;
    try{const data=await api(`/availability?date=${encodeURIComponent(state.scheduleDate)}&theme=${encodeURIComponent(state.selectedTheme)}`),theme=data.themes?.[0];if(!theme)throw new Error("테마 회차를 찾을 수 없습니다.");host.innerHTML=`<section class="cs-admin-schedule-head"><div><span>${escapeHtml(formatDate(state.scheduleDate))}</span><h2>${escapeHtml(theme.shortTitle)}</h2><p>용의자 ${theme.suspectCapacity}명 + 탐정 최대 ${theme.detectiveCapacity}명 · 최소 ${theme.minPlayers}명 · 최대 ${theme.totalCapacity}명</p></div><strong>${theme.times.length}개 회차</strong></section><div class="cs-admin-slot-grid">${theme.times.map((slot)=>{const info=roomInfo(slot);return `<article class="${info.className}"><header><strong>${escapeHtml(slot.time)}</strong><span>${info.count}/${info.capacity}명</span></header><b>${escapeHtml(info.label)}</b><p>${escapeHtml(info.description)}</p><footer><button type="button" data-slot-time="${slot.time}" data-slot-status="OPEN" ${slot.status==="OPEN"?"disabled":""}>예약 열기</button><button type="button" data-slot-time="${slot.time}" data-slot-status="BLOCKED" ${slot.bookedCount>0||slot.status==="BLOCKED"?"disabled":""}>운영 중지</button></footer></article>`;}).join("")}</div>`;host.querySelectorAll("[data-slot-time]").forEach((button)=>button.addEventListener("click",async()=>{button.disabled=true;try{await api("/admin/availability",{method:"PATCH",body:JSON.stringify({themeId:state.selectedTheme,playDate:state.scheduleDate,startTime:button.dataset.slotTime,status:button.dataset.slotStatus})},true);notify("회차 상태를 변경했습니다.","success");await loadSchedule();}catch(error){notify(error.message,"error");button.disabled=false;}}));}
    catch(error){host.innerHTML=`<p class="cs-admin-form-error">${escapeHtml(error.message)}</p>`;}
  }

  function themeEditor(theme) {
    return `<form id="theme-form" class="cs-admin-theme-form"><input type="hidden" name="id" value="${theme.id}"><div class="cs-admin-form-section"><header><span>기본 정보</span><h2>${escapeHtml(theme.shortTitle)}</h2></header><div class="cs-admin-form-grid"><label><span>정식 제목</span><input name="title" value="${escapeHtml(theme.title)}" required></label><label><span>짧은 제목</span><input name="shortTitle" value="${escapeHtml(theme.shortTitle)}" required></label><label><span>에피소드 번호</span><input type="number" name="episode" min="1" value="${theme.episode}" required></label><label><span>상태</span><select name="status"><option value="ACTIVE" ${theme.status==="ACTIVE"?"selected":""}>공개 운영</option><option value="HIDDEN" ${theme.status==="HIDDEN"?"selected":""}>예약 숨김</option><option value="ARCHIVED" ${theme.status==="ARCHIVED"?"selected":""}>운영 종료</option></select></label><label class="wide"><span>한 줄 소개</span><input name="tagline" maxlength="300" value="${escapeHtml(theme.tagline||"")}"></label><label class="wide"><span>사건 개요</span><textarea name="synopsis" maxlength="2000">${escapeHtml(theme.synopsis||"")}</textarea></label><label><span>난이도 표시</span><input name="difficulty" value="${escapeHtml(theme.difficulty||"")}"></label><label><span>이미지 경로</span><input name="image" value="${escapeHtml(theme.image||"")}"></label></div></div><div class="cs-admin-form-section"><header><span>역할과 요금</span><h2>인원 설정</h2></header><div class="cs-admin-form-grid compact"><label><span>용의자 수</span><input type="number" name="suspectCapacity" min="0" max="20" value="${theme.suspectCapacity}" required></label><label><span>탐정 최대 수</span><input type="number" name="detectiveCapacity" min="0" max="20" value="${theme.detectiveCapacity}" required></label><label><span>최소 진행 인원</span><input type="number" name="minPlayers" min="1" max="9" value="${theme.minPlayers}" required></label><label><span>1인 요금</span><input type="number" name="price" min="0" step="1000" value="${theme.price}" required></label><label><span>이용 시간</span><input type="number" name="duration" min="30" max="360" value="${theme.duration}" required></label><label><span>강조 색상</span><input type="text" name="accent" value="${escapeHtml(theme.accent||"#c84b42")}"></label></div><div class="cs-admin-capacity-preview" id="capacity-preview">용의자 ${theme.suspectCapacity}명 + 탐정 ${theme.detectiveCapacity}명 · 총 ${theme.totalCapacity}명</div></div><div class="cs-admin-form-section"><header><span>예약 시간표</span><h2>회차 시간 수정</h2></header><p>한 줄에 하나씩 24시간 형식으로 입력합니다. 예: 10:00</p><textarea class="cs-admin-times" name="times" required>${(theme.times||[]).join("\n")}</textarea></div><div class="cs-admin-form-actions"><span id="theme-form-message"></span><button type="submit">테마 정보 저장</button></div></form>`;
  }

  function themesView() {
    const themes=state.data.themes||[],theme=themes.find((t)=>t.id===state.selectedTheme)||themes[0];if(!theme)return;
    const content=`<div class="cs-admin-theme-tabs">${themes.map((t)=>`<button type="button" data-theme-tab="${t.id}" class="${t.id===theme.id?"is-active":""}"><span>EP.${t.episode}</span><strong>${escapeHtml(t.shortTitle)}</strong><small>최대 ${t.totalCapacity}명 · ${(t.times||[]).length}회차</small></button>`).join("")}</div>${themeEditor(theme)}`;
    shell(content,"테마 관리","테마 설명, 역할 정원, 요금과 예약 시간대를 직접 수정합니다.");
    document.querySelectorAll("[data-theme-tab]").forEach((button)=>button.addEventListener("click",()=>{state.selectedTheme=button.dataset.themeTab;themesView();}));
    const form=document.querySelector("#theme-form"),preview=document.querySelector("#capacity-preview");
    function updatePreview(){if(!form||!preview)return;const suspect=Number(form.suspectCapacity.value||0),detective=Number(form.detectiveCapacity.value||0);preview.textContent=`용의자 ${suspect}명 + 탐정 ${detective}명 · 총 ${suspect+detective}명`;}
    form?.suspectCapacity.addEventListener("input",updatePreview);form?.detectiveCapacity.addEventListener("input",updatePreview);
    form?.addEventListener("submit",async(event)=>{event.preventDefault();const button=form.querySelector('[type="submit"]'),message=form.querySelector("#theme-form-message"),values=Object.fromEntries(new FormData(form));button.disabled=true;message.textContent="저장 중";try{await api("/admin/themes",{method:"PATCH",body:JSON.stringify({...values,episode:Number(values.episode),suspectCapacity:Number(values.suspectCapacity),detectiveCapacity:Number(values.detectiveCapacity),minPlayers:Number(values.minPlayers),price:Number(values.price),duration:Number(values.duration),times:String(values.times).split(/[\s,]+/).filter(Boolean)})},true);notify("테마와 시간대를 저장했습니다.","success");await reloadData("themes");}catch(error){message.textContent=error.message;message.className="error";button.disabled=false;}});
  }

  function inquiriesView() {
    const rows=state.data.inquiries||[];
    const content=rows.length?`<div class="cs-admin-inquiry-list">${rows.map((row)=>`<article data-inquiry="${row.id}"><header><div><span>${escapeHtml(inquiryLabels[row.status]||row.status)}</span><h2>${escapeHtml(row.subject)}</h2></div><time>${escapeHtml(formatDateTime(row.created_at))}</time></header><dl><div><dt>고객</dt><dd>${escapeHtml(row.customer_name)}</dd></div><div><dt>연락처</dt><dd>${escapeHtml(row.phone)}</dd></div></dl><p>${escapeHtml(row.content)}</p><label><span>답변 및 처리 메모</span><textarea data-inquiry-response>${escapeHtml(row.response||"")}</textarea></label><footer><select data-inquiry-status>${Object.entries(inquiryLabels).map(([value,label])=>`<option value="${value}" ${row.status===value?"selected":""}>${label}</option>`).join("")}</select><button type="button" data-save-inquiry>처리 상태 저장</button></footer></article>`).join("")}</div>`:`<p class="cs-admin-empty large">접수된 문의가 없습니다.</p>`;
    shell(content,"문의 관리","고객 문의 내용과 연락처를 확인하고 처리 상태를 기록합니다.");
    document.querySelectorAll("[data-save-inquiry]").forEach((button)=>button.addEventListener("click",async()=>{const card=button.closest("[data-inquiry]");button.disabled=true;try{await api("/admin/inquiries",{method:"PATCH",body:JSON.stringify({id:card.dataset.inquiry,status:card.querySelector("[data-inquiry-status]").value,response:card.querySelector("[data-inquiry-response]").value})},true);notify("문의 처리 상태를 저장했습니다.","success");await reloadData("inquiries");}catch(error){notify(error.message,"error");button.disabled=false;}}));
  }

  async function noticesView() {
    const content=`<div class="cs-admin-two-column notices"><form id="notice-create" class="cs-admin-panel cs-admin-notice-form"><header><div><span>새 공지</span><h2>공지 게시</h2></div></header><label><span>제목</span><input name="title" maxlength="100" required></label><label><span>내용</span><textarea name="content" maxlength="4000" required></textarea></label><label class="check"><input type="checkbox" name="pinned"><span>중요 공지로 상단 고정</span></label><button type="submit">공지 게시</button></form><section class="cs-admin-panel"><header><div><span>게시된 공지</span><h2>공지 목록</h2></div></header><div id="notice-list"><p class="cs-admin-loading">공지를 불러오는 중입니다.</p></div></section></div>`;
    shell(content,"공지 관리","고객 사이트의 공지를 등록, 수정, 숨김 또는 삭제합니다.");
    document.querySelector("#notice-create")?.addEventListener("submit",async(event)=>{event.preventDefault();const form=event.currentTarget,button=form.querySelector("button"),values=Object.fromEntries(new FormData(form));button.disabled=true;try{await api("/admin/notices",{method:"POST",body:JSON.stringify({...values,pinned:form.pinned.checked,published:true})},true);form.reset();notify("공지를 게시했습니다.","success");await loadNotices();}catch(error){notify(error.message,"error");button.disabled=false;}});
    await loadNotices();
  }

  async function loadNotices() {
    const host=document.querySelector("#notice-list");if(!host)return;
    try{let result;try{result=await api("/admin/notices",{},true);}catch{result=await api("/notices");}state.notices=result.notices||[];host.innerHTML=state.notices.length?`<div class="cs-admin-notice-list">${state.notices.map((notice)=>`<article data-notice="${notice.id}"><header><span>${notice.pinned?"중요 공지":"일반 공지"}</span><time>${escapeHtml(formatDateTime(notice.created_at))}</time></header><input data-notice-title value="${escapeHtml(notice.title)}"><textarea data-notice-content>${escapeHtml(notice.content)}</textarea><footer><label><input type="checkbox" data-notice-pinned ${notice.pinned?"checked":""}>상단 고정</label><label><input type="checkbox" data-notice-published ${notice.published===false?"":"checked"}>고객 공개</label><button type="button" data-save-notice>저장</button><button type="button" class="danger" data-delete-notice>삭제</button></footer></article>`).join("")}</div>`:`<p class="cs-admin-empty">등록된 공지가 없습니다.</p>`;host.querySelectorAll("[data-save-notice]").forEach((button)=>button.addEventListener("click",async()=>{const card=button.closest("[data-notice]");button.disabled=true;try{await api("/admin/notices",{method:"PATCH",body:JSON.stringify({id:Number(card.dataset.notice),title:card.querySelector("[data-notice-title]").value,content:card.querySelector("[data-notice-content]").value,pinned:card.querySelector("[data-notice-pinned]").checked,published:card.querySelector("[data-notice-published]").checked})},true);notify("공지를 저장했습니다.","success");await loadNotices();}catch(error){notify(error.message,"error");button.disabled=false;}}));host.querySelectorAll("[data-delete-notice]").forEach((button)=>button.addEventListener("click",async()=>{const card=button.closest("[data-notice]");if(!confirm("이 공지를 삭제하시겠습니까?"))return;button.disabled=true;try{await api("/admin/notices",{method:"DELETE",body:JSON.stringify({id:Number(card.dataset.notice)})},true);notify("공지를 삭제했습니다.","success");await loadNotices();}catch(error){notify(error.message,"error");button.disabled=false;}}));}
    catch(error){host.innerHTML=`<p class="cs-admin-form-error">${escapeHtml(error.message)}</p>`;}
  }

  function securityView() {
    const content=`<div class="cs-admin-security"><section><span>관리자 암호키</span><h2>암호키 변경</h2><p>12자 이상의 긴 암호키를 사용하세요. 변경 후 현재 로그인은 유지되며 다음 로그인부터 새 암호키가 적용됩니다.</p><form id="password-form"><label><span>현재 암호키</span><input type="password" name="currentAccessKey" autocomplete="current-password" required></label><label><span>새 암호키</span><input type="password" name="nextAccessKey" minlength="12" autocomplete="new-password" required></label><label><span>새 암호키 확인</span><input type="password" name="confirmAccessKey" minlength="12" autocomplete="new-password" required></label><button type="submit">암호키 변경</button></form></section><aside><b>보안 상태</b><dl><div><dt>관리자 세션</dt><dd>8시간 만료</dd></div><div><dt>로그인 제한</dt><dd>15분 내 5회</dd></div><div><dt>개인정보</dt><dd>AES-GCM 암호화</dd></div><div><dt>데이터 접근</dt><dd>서비스 역할 전용</dd></div></dl></aside></div>`;
    shell(content,"보안 설정","관리자 암호키와 운영 데이터 보안 상태를 관리합니다.");
    document.querySelector("#password-form")?.addEventListener("submit",async(event)=>{event.preventDefault();const form=event.currentTarget,values=Object.fromEntries(new FormData(form)),button=form.querySelector("button");if(values.nextAccessKey!==values.confirmAccessKey){notify("새 암호키 확인이 일치하지 않습니다.","error");return;}button.disabled=true;try{await api("/admin/change-password",{method:"POST",body:JSON.stringify({currentAccessKey:values.currentAccessKey,nextAccessKey:values.nextAccessKey})},true);form.reset();notify("관리자 암호키를 변경했습니다.","success");}catch(error){notify(error.message,"error");button.disabled=false;}});
  }

  async function reloadData(view=state.view) { state.view=view;state.data=await api("/admin/dashboard",{},true);renderCurrent(); }

  function renderCurrent() {
    if(!state.data){loadDashboard();return;}
    if(state.view==="dashboard")dashboardView();
    else if(state.view==="openrooms")openRoomsView();
    else if(state.view==="reservations")reservationsView();
    else if(state.view==="schedule")scheduleView();
    else if(state.view==="themes")themesView();
    else if(state.view==="inquiries")inquiriesView();
    else if(state.view==="notices")noticesView();
    else if(state.view==="security")securityView();
    else{state.view="dashboard";dashboardView();}
  }

  if (!ROOT) return;
  document.documentElement.classList.add("cs-admin-v3");
  if (state.token) loadDashboard(); else loginPage();
})();
