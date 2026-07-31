# 포트폴리오 개선 TODO (2026-07-31 예정)

> 대상 파일: `src/main/resources/static/portfolio/index.html` (2026-07-31 portfolio.html에서 개명 — /portfolio는 forward로 서빙)
> 2026-07-30 리디자인("검증 원장" 컨셉) 완료 후 남은 개선 3건.
> 수정 후 `build/resources/main/static/portfolio/`에도 복사(동기화) → 커밋 → 푸시(자동 배포).

## 1. OG 태그 (공유 미리보기) — ✅ 완료 (2026-08-01, cea6566: og.png·favicon.svg·메타태그)

지원서 링크를 카톡/슬랙/메일에 붙였을 때 미리보기 카드가 뜨도록.

- `<head>`에 추가:
  - `og:title` — "Backend Developer — 경력기술서 & 포트폴리오"
  - `og:description` — "5년의 실측 기록 · 1,305 commits · 464 PRs · 정산 대조 458만 건 불일치 0"
  - `og:type` — website
  - `og:url`, `og:image` — **도메인 주소 필요 (사용자에게 물어볼 것)**
  - `twitter:card` — summary_large_image (og:image 있을 때)
- og:image는 1200×630 대표 이미지 제작 필요 (원장 테이블 스타일로 렌더 → 캡처하면 컨셉 일관성 유지)
- favicon도 없음 — 같은 김에 추가 (장부 녹색 #0E7A58 계열)

## 2. 스크린샷 라이트박스 (클릭 확대) — ✅ 완료 (2026-07-31, e35bb92)

재고 시스템 스크린샷 3장(`images/project0/`)이 3열 그리드라 작게 보임.

- 클릭 시 확대되는 라이트박스 추가 — 외부 라이브러리 없이 구현 권장:
  - `<dialog>` 요소 + `figure img`에 클릭 리스너 → dialog에 큰 이미지 표시
  - ESC/배경 클릭으로 닫기, `cursor: zoom-in`
- 아카이브 프로젝트 스크린샷들도 동일 적용

## 3. 브리치 카드 스캔성 — ✅ 완료 (2026-08-01, cea6566 배지 6종 + ed9c13d AI 카드 전후 비교표·사례)

브리치 카드가 불릿 10개로 밀도 높음 → 훑는 담당자를 위해 카드 상단에 mono 요약 스트립.

- 각 `.project .card-title` 아래에 `key-metrics` 줄 추가 (mono, 장부 녹색):
  - 관리형 프로모션: `458만 건 대조 불일치 0 · 스냅샷 190만 · 6모듈 64파일`
  - 멀티마켓 연동: `20+ 채널 · 신규 온보딩 2 (퀸잇·큐텐)`
  - 클레임 V2: `비동기 Job 36종 · 커밋·PR 373건`
  - 마이그레이션: `수집 17종 · 상품연동 8종 · 무중단`
  - 운영 안정성: `전체 커밋 28% 운영 대응`
  - AI 환경: `에이전트 6종 · 스킬 33 · 메모리 114 · +28.5%`
- CSS: `.key-metrics { font-family: var(--mono); font-size: 11.5px; color: var(--ledger); }` 정도

## 참고 (오늘 완료된 것)

- 리디자인: IBM Plex Sans KR/Mono, 잉크+장부녹색, 원장 테이블 헤더(카운트업), 스크롤 진행바, 카드 페이드업, 타임라인 레일, ARCHIVE 구분(대학생/인턴 ~2021), word-break keep-all
- 배포: main 푸시 → GitHub Actions 자동 배포 (blue-green)
- 계정: admin 하드코딩 제거됨, **admin 비밀번호 변경 아직 안 했으면 먼저 할 것**
- 로컬 캡처 도구: 스크래치패드에 playwright-core + msedge 스크립트 있음 (세션 바뀌면 재설치 필요)
