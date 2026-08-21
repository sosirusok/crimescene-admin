const fs=require("node:fs");
const vm=require("node:vm");

const code=fs.readFileSync("_site/assets/admin-final.js","utf8");
const today=new Intl.DateTimeFormat("en-CA",{timeZone:"Asia/Seoul",year:"numeric",month:"2-digit",day:"2-digit"}).format(new Date());
const pastDate="2026-08-18";
const app={innerHTML:""};
const emptyClassList={add(){},remove(){},toggle(){return false;},contains(){return false;}};
const document={
  documentElement:{classList:emptyClassList},
  body:{classList:emptyClassList,append(){}},
  querySelector(selector){return selector==="#app"?app:null;},
  querySelectorAll(){return[];},
  createElement(){return{className:"",append(){},remove(){}};},
};
const storage={
  getItem(key){if(key==="crimescene-admin-token")return"test-token";if(key==="crimescene-admin-user")return JSON.stringify({displayName:"서면점 운영자"});return null;},
  setItem(){},removeItem(){},
};
const mockDashboard={
  settings:{bookingWindowDays:30,paymentMode:"ONSITE"},
  payment:{onlineEnabled:false,integrationReady:false},
  metrics:{today:1,recruitingRooms:0,playableRooms:0,active:2,revenue:0},
  themes:[{id:"A",shortTitle:"신입생 오티 살인사건",times:["10:00"],price:23000,minPlayers:4,totalCapacity:8}],
  reservations:[
    {id:"today-row",source:"ONLINE",theme_id:"A",theme_title:"신입생 오티 살인사건",play_date:today,start_time:"10:00",customer_name:"오늘예약자",phone:"010-0000-0000",party_size:4,open_room:false,total_amount:92000,status:"CONFIRMED",payment_status:"READY",special_request:"",admin_note:""},
    {id:"past-row",source:"PHONE",theme_id:"A",theme_title:"신입생 오티 살인사건",play_date:pastDate,start_time:"11:00",customer_name:"지난예약자",phone:"010-1111-1111",party_size:4,open_room:false,total_amount:92000,status:"CONFIRMED",payment_status:"READY",special_request:"",admin_note:""},
  ],
  openRooms:[],slots:[],inquiries:[],notices:[],auditLogs:[],
};
const context={
  document,
  location:{hostname:"sosirusok.github.io",pathname:"/crimescene-admin/",search:"?view=reservations",href:""},
  localStorage:storage,sessionStorage:storage,
  addEventListener(){},console,Intl,Date,URL,URLSearchParams,AbortController,TextEncoder,TextDecoder,setTimeout,clearTimeout,
  fetch:async()=>new Response(JSON.stringify(mockDashboard),{status:200,headers:{"content-type":"application/json"}}),
  Response,Request,Headers,Blob,
};
vm.createContext(context);
vm.runInContext(code,context,{timeout:5000,filename:"admin-final.js"});
(async()=>{
  await context.__CRIMESCENE_ADMIN_READY__;
  if(!app.innerHTML.includes("예약 관리"))throw new Error("예약 관리 화면이 렌더링되지 않았습니다.");
  if(!app.innerHTML.includes("admin-reservation-today"))throw new Error("오늘 예약 요약이 없습니다.");
  if(!app.innerHTML.includes("admin-reservation-datebar"))throw new Error("날짜 선택 영역이 없습니다.");
  if((app.innerHTML.match(/data-reservation-date=/g)||[]).length!==30)throw new Error("운영 설정의 30일 날짜가 반영되지 않았습니다.");
  if(!app.innerHTML.includes("오늘예약자"))throw new Error("오늘 예약이 기본 목록에 없습니다.");
  if(app.innerHTML.includes("지난예약자"))throw new Error("지난 예약이 오늘 목록에 섞였습니다.");
  if(!app.innerHTML.includes("오늘 목록 표시 중"))throw new Error("현재 날짜 상태가 명확하지 않습니다.");
  if(!app.innerHTML.includes("오늘 이후 전체")||!app.innerHTML.includes("지난 예약")||!app.innerHTML.includes("최근 전체"))throw new Error("예약 범위 선택이 없습니다.");
  if(app.innerHTML.includes("예약번호"))throw new Error("고객에게 쓰지 않는 예약번호 개념이 노출됐습니다.");
  console.log(`Admin reservation renderer passed (${app.innerHTML.length} bytes).`);
})().catch(error=>{console.error(error);process.exit(1);});
