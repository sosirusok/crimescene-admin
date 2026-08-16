# Crime Scene Operations Console

크라임씬플레이의 관리자 전용 운영 콘솔 저장소입니다. 고객 예약 사이트와 같은 D1 스키마를 사용하며, 루트 접속 시 보안 관리자 경로 `/admin`으로 이동합니다.

## 운영 기능

- 오늘/활성/전체 예약과 결제 완료 매출 요약
- 예약 및 결제 상태 변경
- 고객 문의 확인, 답변 메모, 처리 상태 관리
- 사건별 회차 OPEN/BLOCK 관리
- 공지사항 등록
- 모든 변경에 대한 감사 로그

## 보안 원칙

관리자 페이지는 ChatGPT 로그인과 `ADMIN_EMAILS` 이메일 허용 목록을 모두 통과해야 합니다. `PII_ENCRYPTION_KEY`가 설정된 경우 고객 연락처를 AES-GCM으로 암호화해 저장하며, 비밀값은 저장소에 커밋하지 않습니다.

## 구성

```text
ADMIN_EMAILS=owner@example.com
PII_ENCRYPTION_KEY=long-random-secret
```

KISPG 운영 연결 전에는 결제 상태가 `READY`로 유지됩니다. 실제 MID·API 키·콜백 사양은 가맹점 기술 패키지를 받은 뒤 `docs/KISPG_SETUP.md`에 따라 연결합니다.

> 현재 운영 배포에서는 고객 사이트와 데이터베이스를 확실히 공유하기 위해 관리자 콘솔을 같은 Sites 프로젝트의 `/admin` 경로로 제공합니다. 이 저장소는 관리자 모듈의 독립 배포·감사·유지보수용 미러입니다.
