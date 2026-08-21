const fs=require("node:fs");
const vm=require("node:vm");

const code=fs.readFileSync("_site/assets/admin-final.js","utf8");
const emptyClassList={add(){},remove(){},toggle(){return false;},contains(){return false;}};

function dashboard(ready,online=false){
  return{
    settings:{
      storeName:"크라임씬플레이",branchName:"서면1호점",representativeName:"운영자",businessRegistrationNumber:"000-00-00000",
      phone:"070-0000-0000",email:"owner@example.com",addressRoad:"부산광역시 부산진구",addressDetail:"4층",mapQuery:"부산광역시 부산진구",
      bookingWindowDays:30,arrivalMinutes:10,cancellationCutoffHours:24,privacyOfficerName:"운영자",privacyOfficerContact:"owner@example.com",
      customerNotice:"",paymentMode:online?"ONLINE":"ONSITE",paymentProvider:"NICEPAY",mailOrderRegistrationNumber:ready?"2026-부산진-0000":"",
      refundPolicyConfirmed:ready,
    },
    payment:{onlineEnabled:ready&&online,integrationReady:ready,configured:ready,legalReady:ready,provider:"NICEPAY"},
    metrics:{},themes:[],reservations:[],openRooms:[],slots:[],inquiries:[],notices:[],auditLogs:[],
  };
}

async function render(ready,online=false){
  const app={innerHTML:""};
  const document={
    documentElement:{classList:emptyClassList},body:{classList:emptyClassList,append(){}},
    querySelector(selector){return selector==="#app"?app:null;},querySelectorAll(){return[];},createElement(){return{className:"",append(){},remove(){}};},
  };
  const storage={
    getItem(key){if(key==="crimescene-admin-token")return"test-token";if(key==="crimescene-admin-user")return JSON.stringify({displayName:"서면점 운영자"});return null;},
    setItem(){},removeItem(){},
  };
  const context={
    document,location:{hostname:"sosirusok.github.io",pathname:"/crimescene-admin/",search:"?view=settings",href:""},
    localStorage:storage,sessionStorage:storage,addEventListener(){},console,Intl,Date,URL,URLSearchParams,AbortController,TextEncoder,TextDecoder,setTimeout,clearTimeout,
    fetch:async()=>new Response(JSON.stringify(dashboard(ready,online)),{status:200,headers:{"content-type":"application/json"}}),Response,Request,Headers,Blob,
  };
  vm.createContext(context);
  vm.runInContext(code,context,{timeout:5000,filename:"admin-final.js"});
  await context.__CRIMESCENE_ADMIN_READY__;
  return app.innerHTML;
}

(async()=>{
  const waiting=await render(false,false);
  const waitingOption=waiting.match(/<option value="ONLINE"[^>]*>/)?.[0]||"";
  if(!waitingOption.includes("disabled"))throw new Error("백엔드 준비 전 ONLINE 옵션이 활성화됐습니다.");
  if(!waiting.includes('name="paymentProvider" value="NICEPAY"'))throw new Error("결제 대행사가 NICEPAY로 고정되지 않았습니다.");
  if(waiting.includes("KISPG"))throw new Error("관리 화면에 이전 KISPG 표기가 남았습니다.");
  if(!waiting.includes("서버 승인·웹훅 모듈")||!waiting.includes("NICEPAY 가맹점 키"))throw new Error("NICEPAY 준비 상태 항목이 없습니다.");

  const ready=await render(true,false);
  const readyOption=ready.match(/<option value="ONLINE"[^>]*>/)?.[0]||"";
  if(readyOption.includes("disabled"))throw new Error("백엔드 준비 완료 후에도 ONLINE 옵션이 비활성화됐습니다.");
  if(!ready.includes("전환 가능"))throw new Error("온라인 결제 전환 가능 상태가 표시되지 않았습니다.");

  const online=await render(true,true);
  const onlineOption=online.match(/<option value="ONLINE"[^>]*>/)?.[0]||"";
  if(!onlineOption.includes("selected")||!online.includes("결제 승인과 결과 반영이 자동으로 처리됩니다."))throw new Error("NICEPAY 사용 중 상태가 올바르지 않습니다.");
  console.log("Admin NICEPAY renderer passed.");
})().catch(error=>{console.error(error);process.exit(1);});
