# Branch protection policy (minimum scope for item 9)

`main` 브랜치 보호 규칙에 아래 필수 상태 체크를 등록한다.

- `assembleDebug`
- `test`

머지 조건은 위 두 체크가 모두 green(성공) 상태일 때만 허용한다.
