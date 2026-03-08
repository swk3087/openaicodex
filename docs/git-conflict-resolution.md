# Git conflict resolution policy (Android scaffold)

이 저장소는 Android 스캐폴드 파일이 동시에 수정될 때 충돌이 자주 발생할 수 있습니다.

## 적용 정책
- `.gitattributes`에서 텍스트 파일의 줄바꿈을 `LF`로 통일합니다.
- 아래 확장자는 `merge=union`으로 설정해 단순 텍스트 충돌 시 자동 병합 가능성을 높입니다.
  - `*.md`, `*.yml`, `*.yaml`, `*.kts`, `*.kt`, `*.xml`

## 수동 정리 체크리스트
1. `git status`로 `both modified` 파일 확인
2. 충돌 마커 검색: `rg -n "^(<<<<<<<|=======|>>>>>>>)"`
3. Android 빌드 파일/코드 중복 라인 수동 정리
4. 최소 검증:
   - `cd android && gradle --no-daemon :app:processDebugResources`
5. 커밋 후 다시 `git status`가 clean인지 확인

## 빠른 해결 스크립트
Android 스캐폴드 충돌 세트는 아래 스크립트로 현재 브랜치 기준(ours) 자동 정리할 수 있습니다.

```bash
./scripts/resolve_android_conflicts.sh
```

> 주의: 충돌 중인 상태에서만 동작하며, 실행 후 `git diff --staged`로 결과를 꼭 검토하세요.
