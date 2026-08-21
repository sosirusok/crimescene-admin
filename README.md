# 크라임씬플레이 서면1호점 운영 관리

고객 사이트와 분리된 GitHub Pages 관리자 화면입니다. 계정 목록을 만들지 않고 관리자 암호키를 입력하면 서버 서명 세션을 발급받으며, 고객 사이트와 동일한 Supabase 데이터와 Edge Function API를 사용합니다.

## 운영 화면

- 오늘 예약을 기본으로 표시하는 날짜별 예약 관리
- 운영 설정의 예약 가능 기간을 따르는 날짜 탭, 직접 날짜 선택, 오늘 이후·지난 예약·최근 예약 범위
- 날짜별 예약 건수·이용 인원·취소/미방문 집계와 현재 목록 CSV 저장
- 예약 직접 등록, 예약 정보·상태 변경과 현장 결제 상태 처리
- NICEPAY 결제 대기·확인 중·완료·실패 상태, 카드 정보와 매출전표 확인
- NICEPAY 온라인 결제 상태는 서버 승인·웹훅 결과로만 반영하며 관리자 수동 변경 차단
- 취소 요청된 NICEPAY 결제만 확인창을 거쳐 전액 취소하고 예약·좌석 상태 자동 반영
- 사건·날짜·회차별 오픈룸과 참여 팀 확인
- 회차 운영/중지, 사건 정보·가격·정원·시간대 수정
- 매장·사업자·NICEPAY 결제 준비 설정, 문의·공지·변경 기록 관리
- 관리자 암호키 변경

## 보안

- 암호키 원문을 브라우저나 저장소에 보관하지 않습니다.
- 로그인 성공 후 발급된 만료형 서버 세션으로 관리자 API를 호출합니다.
- 연락처는 서버에서만 복호화하며 공개 고객 API에는 노출하지 않습니다.
- 관리자 페이지에는 `noindex`, `nofollow`, `noarchive`와 크롤링 차단 `robots.txt`를 적용합니다.
- 서비스 역할 키와 개인정보 암호화 재료는 브라우저 번들에 포함하지 않습니다.

## GitHub Pages 배포 소스

```text
pages-src/admin-final.js             관리자 화면과 운영 기능
pages-src/final.css                  공통 디자인
pages-src/admin-date.css             날짜별 예약·폰트·모바일 보강
pages-src/build.mjs                  관리자 정적 빌드
pages-src/shell.html                 HTML 문서 셸
scripts/smoke-admin.cjs              로그인 화면 검증
scripts/smoke-admin-reservations.cjs 날짜별 예약 화면 검증
scripts/smoke-admin-payments.cjs     NICEPAY 상태·잠금·준비 조건 검증
.github/workflows/pages.yml          빌드·API 검증·GitHub Pages 배포
```

로컬 검증:

```bash
PAGES_BASE=/crimescene-admin ADMIN_ONLY=1 node pages-src/build.mjs
node scripts/smoke-admin.cjs
node scripts/smoke-admin-reservations.cjs
node scripts/smoke-admin-payments.cjs
node --check _site/assets/admin-final.js
```

`main`에 반영되면 GitHub Actions가 산출물과 관리자 API의 미인증 차단을 검증한 뒤 GitHub Pages에 배포합니다.
