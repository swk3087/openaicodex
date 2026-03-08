# Storage Access & Deployment Channel Policy

## Storage modes

### 1) Default: App-private workspace mode
- 기본 모드는 앱 전용 저장소(`context.filesDir/workspace`)를 사용합니다.
- 이 모드는 별도 고위험 권한 없이 동작하며, Play 배포 기본 정책에 맞춥니다.

### 2) Optional: All files mode
- 옵션으로 `All files mode`를 분리 제공합니다.
- Android 11+(API 30+)에서는 `MANAGE_EXTERNAL_STORAGE` 요청 플로우를 **저장소 설정 영역에서만** 노출합니다.
- 권한이 없으면 앱은 자동으로 app-private 워크스페이스로 graceful fallback 합니다.

## Graceful fallback behavior
- 사용자가 `All files mode`를 선택했더라도 권한 미허용 시:
  - 앱은 오류로 중단되지 않습니다.
  - 유효 동작 모드를 app-private로 강등하여 세션/파일 작업을 계속 수행합니다.

## Path access guard policy
- 시스템 경로 접근 차단: `/system`, `/proc`, `/dev`, `/sys`, `/apex`, `/vendor`.
- 워크스페이스 범위 밖 접근 차단: canonical path 검증을 통해 `PATH_OUT_OF_SCOPE` 처리.
- 삭제 금지 경로: 루트 삭제와 `Android`, `Android/data`, `Android/obb` 삭제를 차단.

## Deployment channel policy

### Play 배포
- 기본 모드(app-private) 중심으로 운영.
- `MANAGE_EXTERNAL_STORAGE`는 사용자 설정 화면에서 명시적으로 전환한 경우에만 사용.

### 사내/사이드로드 배포
- 조직 정책에 따라 `All files mode` 허용 가능.
- 운영 가이드에 권한 필요성, 사용 범위, 차단 경로 정책을 함께 문서화.
