const fs=require("node:fs");
const vm=require("node:vm");
const code=fs.readFileSync("_site/assets/admin-final.js","utf8");
const app={innerHTML:""};
const emptyClassList={add(){},remove(){},toggle(){return false;}};
const document={documentElement:{classList:emptyClassList},body:{classList:emptyClassList,append(){}},querySelector(selector){return selector==="#app"?app:null;},querySelectorAll(){return[];},createElement(){return{className:"",append(){},remove(){}};}};
const storage={getItem(){return null;},setItem(){},removeItem(){}};
const context={document,location:{hostname:"sosirusok.github.io",pathname:"/crimescene-admin/",href:""},localStorage:storage,sessionStorage:storage,addEventListener(){},console,Intl,Date,URL,URLSearchParams,AbortController,TextEncoder,TextDecoder,setTimeout,clearTimeout,fetch:async()=>{throw new Error("로그인 전에는 네트워크 요청이 없어야 합니다.");},Response,Request,Headers,Blob};
vm.createContext(context);
vm.runInContext(code,context,{timeout:5000,filename:"admin-final.js"});
(async()=>{
  await context.__CRIMESCENE_ADMIN_READY__;
  if(!app.innerHTML.includes("운영 관리"))throw new Error("관리자 로그인 화면이 렌더링되지 않았습니다.");
  if(!app.innerHTML.includes("관리자 암호키"))throw new Error("로그인 입력 항목이 없습니다.");
  if(/Supabase|OWNER|AES-GCM|서비스 역할/.test(app.innerHTML))throw new Error("관리자 화면에 개발자용 문구가 남아 있습니다.");
  if(app.innerHTML.includes("boot-screen"))throw new Error("로딩 화면이 남았습니다.");
  console.log(`Admin renderer passed (${app.innerHTML.length} bytes).`);
})().catch(error=>{console.error(error);process.exit(1);});
