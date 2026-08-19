from pathlib import Path

js_path = Path("pages-src/admin-final.js")
shell_path = Path("pages-src/shell.html")
js = js_path.read_text(encoding="utf-8")
shell = shell_path.read_text(encoding="utf-8")


def replace_once(text: str, old: str, new: str, label: str) -> str:
    count = text.count(old)
    if count != 1:
        raise RuntimeError(f"{label}: expected 1 occurrence, found {count}")
    return text.replace(old, new, 1)


# 테마 난이도는 관리자 화면에서도 완전히 없앤다.
difficulty_field = '<label><span>난이도 표시</span><input name="difficulty" value="${h(current.difficulty)}"></label>'
js = js.replace(difficulty_field, "")

submit_old = 'body:JSON.stringify({...values,id:current.id,times:editedTimes,episode:Number(values.episode),price:Number(values.price),duration:Number(values.duration),minPlayers:Number(values.minPlayers),suspectCapacity:Number(values.suspectCapacity),detectiveCapacity:Number(values.detectiveCapacity)})'
submit_new = 'body:JSON.stringify({...values,difficulty:"",id:current.id,times:editedTimes,episode:Number(values.episode),price:Number(values.price),duration:Number(values.duration),minPlayers:Number(values.minPlayers),suspectCapacity:Number(values.suspectCapacity),detectiveCapacity:Number(values.detectiveCapacity)})'
if submit_new not in js:
    js = replace_once(js, submit_old, submit_new, "clear difficulty on theme save")

# 브랜드명과 실제 사업자 상호를 구분해 운영자가 헷갈리지 않게 한다.
store_old = '<label><span>상호명</span><input name="storeName" value="${h(s.storeName)}" required></label><label><span>지점명</span><input name="branchName" value="${h(s.branchName)}" required></label><label><span>대표자명</span>'
store_new = '<label><span>고객 사이트 이름</span><input name="storeName" value="${h(s.storeName)}" required></label><label><span>지점명</span><input name="branchName" value="${h(s.branchName)}" required></label><label><span>사업자 상호</span><input value="(주)싱글" readonly aria-readonly="true"></label><label><span>대표자명</span>'
if store_new not in js:
    js = replace_once(js, store_old, store_new, "business name settings field")

if 'BUSINESS_INFORMATION_UPDATED:"사업자 정보 변경"' not in js:
    js = js.replace(
        'ADMIN_STORE_SETTINGS_UPDATED:"매장 설정 변경",INQUIRY_CREATED:',
        'ADMIN_STORE_SETTINGS_UPDATED:"매장 설정 변경",BUSINESS_INFORMATION_UPDATED:"사업자 정보 변경",INQUIRY_CREATED:',
        1,
    )

shell = shell.replace("20260816-31", "20260819-01")

required = [
    '고객 사이트 이름',
    '사업자 상호',
    'value="(주)싱글" readonly',
    'difficulty:""',
]
for needle in required:
    if needle not in js:
        raise RuntimeError(f"missing admin change: {needle}")
if "난이도 표시" in js or "current.difficulty" in js:
    raise RuntimeError("difficulty remains in admin renderer")

js_path.write_text(js, encoding="utf-8")
shell_path.write_text(shell, encoding="utf-8")
print("Owner feedback admin patch applied")
