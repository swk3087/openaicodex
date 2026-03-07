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
gradle assembleDebug
```

## 다음 단계
- Gradle Wrapper 추가(`gradle wrapper`) 후 CI 명령을 `./gradlew assembleDebug`로 전환
- Hilt/WorkManager/전체 파일 권한(MANAGE_EXTERNAL_STORAGE) 연동
- 실제 PTY/ForegroundService 구현
