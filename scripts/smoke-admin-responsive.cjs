const fs=require("node:fs");

// Static guards for the mobile layout regressions fixed on 2026-10-19. Run after the admin build.
const read=file=>fs.readFileSync(`_site/${file}`,"utf8");
const fail=message=>{throw new Error(message);};
// Formatting-insensitive CSS: no comments, single spaces, no spaces around { } ; , > or after :, no trailing ;.
const norm=s=>s.replace(/\/\*[\s\S]*?\*\//g,"").replace(/\s+/g," ").replace(/ ?([{};,>]) ?/g,"$1").replace(/: /g,":").replace(/;}/g,"}").replace(/@media \(/g,"@media(");
const media=(s,query)=>{let out="",i=0;const open=`@media${query}{`;while((i=s.indexOf(open,i))!==-1){let depth=0,j=i+open.length-1;for(;j<s.length;j++){if(s[j]==="{")depth++;else if(s[j]==="}"&&!--depth)break;}out+=`}${s.slice(i+open.length,j)}`;i=j;}return out;};

const css=norm(read("assets/final.css"));
const dateCss=norm(read("assets/admin-date.css"));
const code=read("assets/admin-final.js");
const html=read("index.html");

if(/["']menu-open["']/.test(code))fail("관리자 메뉴 상태 클래스가 고객용 .menu-open 버튼 스타일과 충돌합니다.");
const nav860=media(css,"(max-width:860px)");
if(css.includes(".admin-app.menu-open")||!nav860.includes("}.admin-app.is-nav-open .admin-sidebar{left:0}"))fail("관리자 모바일 메뉴 열림 상태 규칙이 올바르지 않습니다.");
if(!nav860.includes("}.admin-app.is-nav-open .admin-sidebar-backdrop{display:block}"))fail("관리자 모바일 메뉴 배경(backdrop)이 is-nav-open 상태에서 표시되지 않습니다.");
const appCalls=[...code.matchAll(/querySelector\(\s*["']\.admin-app["']\s*\)\??\.classList\.(add|remove|toggle)\(\s*["']([^"']+)["']\s*\)/g)];
if(appCalls.some(m=>m[2]!=="is-nav-open"))fail("관리자 메뉴 열기/닫기 클래스가 is-nav-open으로 통일되지 않았습니다.");
if(!/classList\.(?:toggle|add)\(\s*["']is-nav-open["']\s*\)/.test(code))fail("관리자 모바일 메뉴 토글이 is-nav-open 상태를 사용하지 않습니다.");
const backdrop=code.match(/\.admin-sidebar-backdrop["']\s*\)\??\.addEventListener\(\s*["']click["']\s*,\s*(?:([\w$]+)\s*\)|(?:\([^)]*\)|[\w$]+)\s*=>\s*\{[^}]*\})/);
const named=backdrop?.[1]&&code.match(new RegExp(`(?:function\\s+${backdrop[1].replace(/\$/g,"\\$")}\\s*\\([^)]*\\)|\\b${backdrop[1].replace(/\$/g,"\\$")}\\s*=\\s*(?:\\([^)]*\\)|[\\w$]+)\\s*=>)\\s*\\{[^}]*\\}`));
if(!/classList\.remove\(\s*["']is-nav-open["']\s*\)/.test(named?.[0]||backdrop?.[0]||""))fail("관리자 모바일 메뉴 배경을 눌러도 메뉴가 닫히지 않습니다.");
if(css.includes(":has("))fail("final.css에 :has() 선택자가 있습니다. 미지원 모바일 브라우저에서 규칙 전체가 무시됩니다.");
if(/body\{[^}]*min-width:320px/.test(css))fail("body min-width:320px가 남아 있어 320px 미만 화면의 오른쪽이 잘립니다.");
if(!css.includes("-webkit-text-size-adjust:100%"))fail("iOS 가로 모드 글자 자동 확대 방지가 없습니다.");
if(!css.includes(".open-choice,.consents label,.simple-check{position:relative}"))fail("숨김 체크박스가 라벨 밖으로 벗어나 설정·공지 화면에 가로 스크롤이 생깁니다.");
if(!css.includes("@media(pointer:coarse),(max-width:600px){input:not([type=checkbox]):not([type=radio]),select,textarea{font-size:16px!important}}"))fail("모바일 입력칸 16px 규칙이 없어 iPhone에서 입력 시 화면이 확대됩니다.");
const filterLabel=media(dateCss,"(pointer:coarse),(max-width:600px)").match(/[},]\.admin-filterbar label\{(?:[^}]*;)?min-width:(\d+)px/);
if(!filterLabel||Number(filterLabel[1])<186)fail("16px 입력칸 화면에서 필터 칸이 좁아 테마 이름과 검색 안내가 잘립니다.");
if(!/(?:^|\})(?:[^{}]*,)?\.admin-app(?:,[^{}]*)?\{(?:[^}]*;)?min-height:100dvh/.test(dateCss))fail("관리자 화면 높이가 모바일 실제 화면 높이를 따르지 않습니다.");
if(!/(?:^|\})\.admin-modal\{(?:[^}]*;)?max-height:calc\(100dvh - 40px\)/.test(dateCss))fail("관리자 모달 높이가 모바일 실제 화면 높이를 따르지 않습니다.");
if(!/[},]\.admin-modal\{(?:[^}]*;)?max-height:calc\(100dvh - 16px\)/.test(media(dateCss,"(max-width:600px)")))fail("600px 이하 관리자 모달 높이가 모바일 실제 화면 높이를 따르지 않습니다.");
if(!/[},]\.admin-sidebar\{(?:[^}]*;)?height:100dvh/.test(media(dateCss,"(max-width:860px)")))fail("모바일 관리자 메뉴 높이가 실제 화면 높이를 따르지 않습니다.");
if(/setAttribute\(\s*["']aria-busy["']/.test(code)&&!/(?:^|\})\.admin-app\[aria-busy="true"\]\{(?:[^}]*;)?pointer-events:none/.test(dateCss))fail("inert 미지원 브라우저에서 새로고침 중에도 관리자 화면을 누를 수 있습니다.");
if(!/<meta name="viewport" content="width=device-width,\s*initial-scale=1">/.test(html))fail("관리자 viewport 설정이 올바르지 않습니다.");

console.log("Admin responsive static checks passed.");
