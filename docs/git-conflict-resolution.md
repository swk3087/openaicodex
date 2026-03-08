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

## 다른 폴더에 새로 clone한 경우
다른 clone에 Android 스캐폴드/충돌 대응 파일을 다시 반영하려면 아래 스크립트를 사용하세요.

```bash
./scripts/reapply_android_scaffold_to_clone.sh /path/to/other/openaicodex
```

스크립트는 핵심 파일 세트를 대상 clone으로 복사합니다. 실행 후 대상 폴더에서 `git status`로 변경사항을 확인하고 커밋하세요.

## Kotlin 컴파일 오류가 갑자기 대량 발생할 때
merge 중 실험 파일(예: Hilt 모듈/UseCase)이 섞이면 `compileDebugKotlin`이 깨질 수 있습니다.
기본 스캐폴드 기준으로 정리하려면:

```bash
./scripts/normalize_android_scaffold.sh
```

그 후 다시 빌드하세요.
