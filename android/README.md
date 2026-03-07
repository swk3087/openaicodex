# Codex Mobile (Android Scaffold)

이 디렉터리는 세션 중심 구조의 안드로이드 앱 스캐폴드입니다.

## 포함 내용
- Compose 기반 `MainActivity`
- Session/Runtime 도메인 타입
- Room 기반 세션/패치 엔티티 초안
- 명령 allowlist 기반 `CommandGate`

## 빌드
```bash
cd android
gradle --no-daemon :app:processDebugResources :app:assembleDebug
```

## 트러블슈팅
- `Theme.Material3.DayNight.NoActionBar not found`가 나오면 `app/build.gradle.kts`에
  `com.google.android.material:material` 의존성이 포함됐는지 확인하세요.
- 네트워크 제한 환경에서 Maven Central `403`이 발생하면, 사내 미러/프록시 설정을 먼저 점검하세요.

## 다음 단계
- Gradle Wrapper 추가(`gradle wrapper`) 후 CI 명령을 `./gradlew assembleDebug`로 전환
- Hilt/WorkManager/파일 접근 모듈 연동
- 실제 PTY/ForegroundService 구현
