# openaicodex
for android

## 문서
- `docs/runtime-poc-decision.md`: runtime-poc 최소 기능 검증 결과 및 의사결정.
- `docs/runtime-manager-spec.md`: 런타임 매니페스트/검증/복구/버전 전환 사양.
- `docs/terminal-session-spec.md`: 터미널 세션 서비스/매니저 분리 및 보안 실행 사양.
- `docs/test-failure-template.md`: 테스트 실패 재현 표준 템플릿.

## Android 코드
- `android/`: 세션 중심 아키텍처 스캐폴드 앱
- `.github/workflows/android-apk.yml`: GitHub Actions APK 빌드 워크플로우

## CI 안정화 참고
- Android 리소스 링크 오류 방지를 위해 Material 의존성을 포함했습니다.
- CI는 `:app:processDebugResources`와 `:app:assembleDebug`를 함께 실행하도록 구성했습니다.
