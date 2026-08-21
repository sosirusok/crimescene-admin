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
  settings:{bookingWindowDays:30,paymentMode:"ONSITE",paymentProvider:"NICEPAY"},
  payment:{onlineEnabled:false,integrationReady:false},
  metrics:{today:1,recruitingRooms:0,playableRooms:0,active:2,revenue:0},
  themes:[{id:"A",shortTitle:"신입생 오티 살인사건",times:["10:00"],price:23000,minPlayers:4,totalCapacity:8}],
  reservations:[
    {id:"today-row",source:"ONLINE",theme_id:"A",theme_title:"신입생 오티 살인사건",play_date:today,start_time:"10:00",customer_name:"오늘예약자",phone:"010-0000-0000",party_size:4,open_room:false,total_amount:92000,status:"PENDING_PAYMENT",payment_status:"VERIFYING",payment:{provider:"NICEPAY",status:"VERIFYING",provider_transaction_id:"nice-test-tid",receipt_url:"https://example.com/receipt"},special_request:"",admin_note:""},
    {id:"onsite-row",source:"PHONE",theme_id:"A",theme_title:"신입생 오티 살인사건",play_date:today,start_time:"11:00",customer_name:"현장결제예약자",phone:"010-2222-2222",party_size:4,open_room:false,total_amount:92000,status:"CONFIRMED",payment_status:"READY",payment:{provider:"ONSITE",status:"READY"},special_request:"",admin_note:""},
    {id:"failed-row",source:"ONLINE",theme_id:"A",theme_title:"신입생 오티 살인사건",play_date:today,start_time:"12:00",customer_name:"결제실패예약자",phone:"010-3333-3333",party_size:4,open_room:false,total_amount:92000,status:"CANCELED",payment_status:"FAILED",payment:{provider:"NICEPAY",status:"FAILED",failure_message:"카드 승인이 거절되었습니다.",result_code:"3001"},special_request:"",admin_note:""},
    {id:"paid-row",source:"ONLINE",theme_id:"A",theme_title:"신입생 오티 살인사건",play_date:today,start_time:"13:00",customer_name:"결제완료예약자",phone:"010-4444-4444",party_size:4,open_room:false,total_amount:92000,status:"CONFIRMED",payment_status:"PAID",payment:{provider:"NICEPAY",status:"PAID",provider_transaction_id:"nice-paid-tid",approved_at:"2026-08-21T03:00:00Z",raw_result_code:"0000"},special_request:"",admin_note:""},
    {id:"cancel-row",source:"ONLINE",theme_id:"A",theme_title:"신입생 오티 살인사건",play_date:today,start_time:"14:00",customer_name:"취소요청예약자",phone:"010-5555-5555",party_size:4,open_room:false,total_amount:92000,status:"CANCEL_REQUESTED",payment_status:"PAID",payment:{provider:"NICEPAY",status:"PAID",provider_transaction_id:"nice-cancel-tid",approved_at:"2026-08-21T04:00:00Z"},special_request:"",admin_note:""},
    {id:"refunded-row",source:"ONLINE",theme_id:"A",theme_title:"신입생 오티 살인사건",play_date:today,start_time:"15:00",customer_name:"환불완료예약자",phone:"010-6666-6666",party_size:4,open_room:false,total_amount:92000,status:"CANCELED",payment_status:"REFUNDED",payment:{provider:"NICEPAY",status:"REFUNDED",provider_transaction_id:"nice-refunded-tid",failure_message:"나이스페이먼츠에서 결제가 취소되었습니다.",failure_code:"0000"},special_request:"",admin_note:""},
    {id:"review-row",source:"ONLINE",theme_id:"A",theme_title:"신입생 오티 살인사건",play_date:today,start_time:"16:00",customer_name:"결제확인예약자",phone:"010-7777-7777",party_size:4,open_room:false,total_amount:92000,status:"CONFIRMED",payment_status:"PAID",payment:{provider:"NICEPAY",status:"PAID",provider_transaction_id:"nice-review-tid",failure_message:"나이스페이먼츠 상태(partialCancelled)를 관리자에서 확인해 주세요.",failure_code:"NICEPAY_STATUS_REVIEW"},special_request:"",admin_note:""},
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
  if(!code.includes('/admin/payments/nicepay/cancel')||!code.includes('취소가 승인되면 되돌릴 수 없습니다.'))throw new Error("NICEPAY 관리자 취소 요청과 확인 절차가 없습니다.");
  if(!app.innerHTML.includes("예약 관리"))throw new Error("예약 관리 화면이 렌더링되지 않았습니다.");
  if(!app.innerHTML.includes("admin-reservation-today"))throw new Error("오늘 예약 요약이 없습니다.");
  if(!app.innerHTML.includes("admin-reservation-datebar"))throw new Error("날짜 선택 영역이 없습니다.");
  if((app.innerHTML.match(/data-reservation-date=/g)||[]).length!==30)throw new Error("운영 설정의 30일 날짜가 반영되지 않았습니다.");
  if(!app.innerHTML.includes("오늘예약자"))throw new Error("오늘 예약이 기본 목록에 없습니다.");
  if(!app.innerHTML.includes("현장결제예약자"))throw new Error("현장 결제 예약이 기본 목록에 없습니다.");
  if(app.innerHTML.includes("지난예약자"))throw new Error("지난 예약이 오늘 목록에 섞였습니다.");
  if(!app.innerHTML.includes("오늘 목록 표시 중"))throw new Error("현재 날짜 상태가 명확하지 않습니다.");
  if(!app.innerHTML.includes("오늘 이후 전체")||!app.innerHTML.includes("지난 예약")||!app.innerHTML.includes("최근 전체"))throw new Error("예약 범위 선택이 없습니다.");
  if(app.innerHTML.includes("예약번호"))throw new Error("고객에게 쓰지 않는 예약번호 개념이 노출됐습니다.");
  if(!app.innerHTML.includes("NICEPAY · 나이스페이먼츠")||!app.innerHTML.includes("결제 확인 중"))throw new Error("NICEPAY 확인 상태가 표시되지 않았습니다.");
  if(!app.innerHTML.includes("카드 매출전표 보기")||!app.innerHTML.includes("https://example.com/receipt"))throw new Error("NICEPAY 매출전표 링크가 표시되지 않았습니다.");
  if(!app.innerHTML.includes("카드 승인이 거절되었습니다.")||!app.innerHTML.includes("코드 3001"))throw new Error("NICEPAY 실패 사유가 표시되지 않았습니다.");
  if((app.innerHTML.match(/data-field="paymentStatus"/g)||[]).length!==1)throw new Error("NICEPAY 예약에 수동 결제 상태 선택이 노출됐습니다.");
  if(!app.innerHTML.includes("NICEPAY 승인·웹훅 결과로 자동 반영됩니다."))throw new Error("NICEPAY 자동 반영 안내가 없습니다.");
  const waitingCard=app.innerHTML.match(/<article class="admin-card" data-reservation="today-row"[\s\S]*?<\/article>/)?.[0]||"";
  if(!waitingCard.includes('data-field="status" disabled')||!waitingCard.includes("자동 반영 대기"))throw new Error("결제 확인 중 예약 상태가 잠기지 않았습니다.");
  if(waitingCard.includes('data-edit="today-row"')||!waitingCard.includes("결제 취소 확인 후 새 예약"))throw new Error("NICEPAY 예약 정보 변경이 올바르게 제한되지 않았습니다.");
  const paidCard=app.innerHTML.match(/<article class="admin-card" data-reservation="paid-row"[\s\S]*?<\/article>/)?.[0]||"";
  if(!paidCard.includes('value="CANCEL_REQUESTED"')||paidCard.includes('value="CANCELED"'))throw new Error("결제 완료 예약의 안전한 상태 전이가 적용되지 않았습니다.");
  if(paidCard.includes("결제사 결과 코드를 확인해 주세요.")||paidCard.includes("코드 0000"))throw new Error("정상 승인 코드가 결제 실패처럼 표시됩니다.");
  if(paidCard.includes("data-confirm-nicepay-cancel"))throw new Error("일반 결제 완료 예약에 취소 확정 버튼이 노출됐습니다.");
  const cancelCard=app.innerHTML.match(/<article class="admin-card" data-reservation="cancel-row"[\s\S]*?<\/article>/)?.[0]||"";
  if(!cancelCard.includes('data-confirm-nicepay-cancel="cancel-row"')||!cancelCard.includes("결제 취소 확정"))throw new Error("취소 요청된 결제 완료 예약에 NICEPAY 취소 버튼이 없습니다.");
  if((app.innerHTML.match(/data-confirm-nicepay-cancel=/g)||[]).length!==1)throw new Error("NICEPAY 취소 버튼 노출 조건이 올바르지 않습니다.");
  const refundedCard=app.innerHTML.match(/<article class="admin-card" data-reservation="refunded-row"[\s\S]*?<\/article>/)?.[0]||"";
  if(!refundedCard.includes("admin-payment-note")||refundedCard.includes("admin-payment-failure"))throw new Error("환불 완료 결과가 결제 실패처럼 표시됩니다.");
  const reviewCard=app.innerHTML.match(/<article class="admin-card" data-reservation="review-row"[\s\S]*?<\/article>/)?.[0]||"";
  if(!reviewCard.includes("결제사 상태 확인 필요")||!reviewCard.includes("NICEPAY_STATUS_REVIEW"))throw new Error("미지원 NICEPAY 웹훅 상태 확인 경고가 표시되지 않습니다.");
  console.log(`Admin reservation renderer passed (${app.innerHTML.length} bytes).`);
})().catch(error=>{console.error(error);process.exit(1);});
