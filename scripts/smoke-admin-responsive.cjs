const fs=require("node:fs");

// Static guards for the mobile layout regressions fixed on 2026-10-19. Run after the admin build.
const read=file=>fs.readFileSync(`_site/${file}`,"utf8");
const fail=message=>{throw new Error(message);};

const css=read("assets/final.css");
const dateCss=read("assets/admin-date.css");
const code=read("assets/admin-final.js");
const html=read("index.html");

if(/["']menu-open["']/.test(code))fail("관리자 메뉴 상태 클래스가 고객용 .menu-open 버튼 스타일과 충돌합니다.");
if(css.includes(".admin-app.menu-open")||!css.includes(".admin-app.is-nav-open .admin-sidebar{left:0}"))fail("관리자 모바일 메뉴 열림 상태 규칙이 올바르지 않습니다.");
if(!code.includes('classList.toggle("is-nav-open")'))fail("관리자 모바일 메뉴 토글이 is-nav-open 상태를 사용하지 않습니다.");
if(css.includes(":has("))fail("final.css에 :has() 선택자가 있습니다. 미지원 모바일 브라우저에서 규칙 전체가 무시됩니다.");
if(/body\{[^}]*min-width:320px/.test(css))fail("body min-width:320px가 남아 있어 320px 미만 화면의 오른쪽이 잘립니다.");
if(!css.includes("-webkit-text-size-adjust:100%"))fail("iOS 가로 모드 글자 자동 확대 방지가 없습니다.");
if(!css.includes(".open-choice,.consents label,.simple-check{position:relative}"))fail("숨김 체크박스가 라벨 밖으로 벗어나 설정·공지 화면에 가로 스크롤이 생깁니다.");
if(!css.includes("@media(pointer:coarse),(max-width:600px){input:not([type=checkbox]):not([type=radio]),select,textarea{font-size:16px!important}}"))fail("모바일 입력칸 16px 규칙이 없어 iPhone에서 입력 시 화면이 확대됩니다.");
if(!/\.admin-modal\s*\{[^}]*max-height:\s*calc\(100dvh - 40px\)/.test(dateCss))fail("관리자 모달 높이가 모바일 실제 화면 높이를 따르지 않습니다.");
if(!/<meta name="viewport" content="width=device-width,initial-scale=1">/.test(html))fail("관리자 viewport 설정이 올바르지 않습니다.");

console.log("Admin responsive static checks passed.");
