# openaicodex
for android

## 문서
- `docs/runtime-poc-decision.md`: runtime-poc 최소 기능 검증 결과 및 의사결정.
- `docs/runtime-manager-spec.md`: 런타임 매니페스트/검증/복구/버전 전환 사양.
- `docs/terminal-session-spec.md`: 터미널 세션 서비스/매니저 분리 및 보안 실행 사양.
- `docs/test-failure-template.md`: 테스트 실패 재현 표준 템플릿.
- `docs/branch-protection-policy.md`: 브랜치 보호 규칙의 필수 체크 정책.

## Android 코드
- `android/`: 세션 중심 아키텍처 스캐폴드 앱
- `.github/workflows/android-apk.yml`: GitHub Actions Android CI (assembleDebug + test) 워크플로우
