const fs = require("node:fs");
const file = "pages-src/admin-final.js";
let source = fs.readFileSync(file, "utf8");

const replacements = [
  [
    'const paymentLabels={READY:"미결제",PAID:"결제 완료",FAILED:"결제 실패",REFUNDED:"환급 완료"};',
    'const paymentLabels={READY:"결제 대기",PAID:"결제 완료",FAILED:"결제 실패",REFUNDED:"환급 완료"};',
  ],
  ["<h2>결제 준비 상태</h2>", "<h2>결제 운영</h2>"],
  [
    '<select name="paymentMode"><option value="ONSITE" ${s.paymentMode==="ONSITE"?"selected":""}>방문 당일 매장 결제</option><option value="ONLINE" ${s.paymentMode==="ONLINE"?"selected":""}>온라인 카드 결제</option></select>',
    '<select name="paymentMode"><option value="ONSITE" selected>방문 당일 매장 결제</option><option value="ONLINE" disabled>온라인 카드 결제 (연동 후 사용)</option></select>',
  ],
  [
    '<div class="payment-readiness"><header><div><span>온라인 결제</span><strong>${p.onlineEnabled?"사용 가능":"준비 중"}</strong></div><b>${p.onlineEnabled?"바로 사용 가능":"매장 결제로 안전하게 운영 중"}</b></header><ul><li><span>가맹점 연동 정보</span><b class="${p.configured?"ok":"wait"}">${p.configured?"등록 완료":"미등록"}</b></li><li><span>통신판매업 신고번호</span><b class="${s.mailOrderRegistrationNumber?"ok":"wait"}">${s.mailOrderRegistrationNumber?"입력 완료":"입력 필요"}</b></li><li><span>환불 기준 확인</span><b class="${s.refundPolicyConfirmed?"ok":"wait"}">${s.refundPolicyConfirmed?"확인 완료":"확인 필요"}</b></li></ul></div>',
    '<div class="payment-readiness"><header><div><span>현재 결제 방식</span><strong>방문 당일 매장 결제</strong></div><b>예약은 결제 연동 없이 바로 확정됩니다.</b></header><ul><li><span>온라인 카드 결제</span><b class="wait">연동 전</b></li><li><span>카드 승인·취소 모듈</span><b class="${p.integrationReady?"ok":"wait"}">${p.integrationReady?"연동 완료":"연동 필요"}</b></li><li><span>가맹점 연동 정보</span><b class="${p.configured?"ok":"wait"}">${p.configured?"등록 완료":"미등록"}</b></li><li><span>통신판매업 신고번호</span><b class="${s.mailOrderRegistrationNumber?"ok":"wait"}">${s.mailOrderRegistrationNumber?"입력 완료":"입력 필요"}</b></li><li><span>환불 기준 확인</span><b class="${s.refundPolicyConfirmed?"ok":"wait"}">${s.refundPolicyConfirmed?"확인 완료":"확인 필요"}</b></li></ul></div>',
  ],
  [
    'shell(content,"매장 설정","주소, 연락처, 예약 기준과 결제 준비 상태를 관리합니다.");',
    'shell(content,"매장 설정","주소, 연락처와 예약 기준을 관리합니다. 온라인 결제는 연동 작업 후 별도로 활성화됩니다.");',
  ],
];

for (const [before, after] of replacements) {
  const count = source.split(before).length - 1;
  if (count !== 1) throw new Error(`Expected one match, found ${count}: ${before.slice(0, 100)}`);
  source = source.replace(before, after);
}

fs.writeFileSync(file, source);
console.log("Admin payment guard and wording updated.");
