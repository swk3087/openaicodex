# RuntimeManager 사양

## 목표
- 런타임 메타데이터를 `runtime-manifest.json`으로 표준화한다.
- 런타임 설치 경로를 `files/runtime/{version}`으로 고정해 세션 데이터와 분리한다.
- 앱 시작 시 런타임 무결성 검증(`verify`) 실패 시 자동 복구(`repair`)를 수행하고, 사용자에게 상태를 표시한다.

## 디렉터리 구조

```text
files/
  runtime/
    v18.20.8/
      runtime-manifest.json
      node/
      npm/
      codex/
  sessions/
    ...
```

- 런타임 데이터는 반드시 `files/runtime/{version}` 하위에만 배치한다.
- 세션 데이터(`files/sessions/...`)와 런타임 데이터는 교차 저장하지 않는다.

## `runtime-manifest.json` 스키마

```json
{
  "$schema": "https://json-schema.org/draft/2020-12/schema",
  "$id": "https://example.com/schemas/runtime-manifest.schema.json",
  "title": "Runtime Manifest",
  "type": "object",
  "required": [
    "nodeVersion",
    "npmVersion",
    "codexVersion",
    "checksums",
    "supportedAbis"
  ],
  "additionalProperties": false,
  "properties": {
    "nodeVersion": {
      "type": "string",
      "minLength": 1,
      "description": "Node.js semantic version"
    },
    "npmVersion": {
      "type": "string",
      "minLength": 1,
      "description": "npm semantic version"
    },
    "codexVersion": {
      "type": "string",
      "minLength": 1,
      "description": "codex semantic version"
    },
    "checksums": {
      "type": "object",
      "description": "Runtime 구성 파일별 sha256 맵",
      "minProperties": 1,
      "additionalProperties": {
        "type": "string",
        "pattern": "^[a-fA-F0-9]{64}$"
      }
    },
    "supportedAbis": {
      "type": "array",
      "description": "이 런타임이 지원하는 ABI 목록",
      "minItems": 1,
      "items": {
        "type": "string",
        "minLength": 1
      },
      "uniqueItems": true
    }
  }
}
```

### 매니페스트 예시

```json
{
  "nodeVersion": "18.20.8",
  "npmVersion": "10.9.4",
  "codexVersion": "1.2.3",
  "checksums": {
    "node/bin/node": "b1946ac92492d2347c6235b4d2611184c3a58f1f9f13f3e8f34f5f5f5a8a9a7b",
    "npm/bin/npm-cli.js": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
    "codex/bin/codex": "4f8b42c2c7b41f9ce2ac9fdc41f4a7f63dd8f1e1b6b665de1b5fcd7d919b0f3c"
  },
  "supportedAbis": [
    "arm64-v8a"
  ]
}
```

## `RuntimeManager` 인터페이스

```ts
interface RuntimeManager {
  prepareRuntime(version: string): Promise<void>;
  verifyRuntime(version: string): Promise<VerifyResult>;
  repairRuntime(version: string): Promise<RepairResult>;
  switchRuntimeVersion(version: string): Promise<void>;
}

interface VerifyResult {
  ok: boolean;
  failures: string[];
}

interface RepairResult {
  ok: boolean;
  repairedFiles: string[];
  message: string;
}
```

## 메서드 동작 정의

### `prepareRuntime()`
- 입력 버전에 대한 디렉터리 `files/runtime/{version}` 생성.
- 런타임 아카이브 다운로드/압축 해제.
- `runtime-manifest.json` 기록.
- 마지막에 `verifyRuntime(version)` 실행 후 실패 시 예외 반환.

### `verifyRuntime()`
- `runtime-manifest.json` 존재/스키마 유효성 확인.
- `supportedAbis`에 현재 ABI가 포함되는지 확인.
- `node`, `npm`, `codex` 실행 파일 존재 및 버전 일치 확인.
- `checksums`의 각 항목에 대해 sha256 재계산 후 일치 여부 확인.
- 실패 항목을 `failures`로 수집해 반환.

### `repairRuntime()`
- `verifyRuntime(version)` 결과를 입력으로 사용.
- 누락/손상 파일만 선택적으로 재다운로드(가능한 경우).
- 선택 복구 실패 또는 매니페스트 자체 손상 시 전체 재설치.
- 복구 완료 후 `verifyRuntime(version)` 재실행.
- 최종 상태와 복구된 파일 목록을 반환.

### `switchRuntimeVersion()`
- 타겟 버전이 없으면 `prepareRuntime(version)` 호출.
- 활성 런타임 포인터(예: `files/runtime/current`)를 타겟 버전으로 원자적 갱신.
- 갱신 직후 `verifyRuntime(version)` 재검증.
- 실패 시 이전 포인터로 롤백.

## 앱 시작 플로우

```text
app start
  -> active runtime version resolve
  -> verifyRuntime(active)
     -> success: status=healthy
     -> fail: status=repairing (사용자에게 표시)
         -> repairRuntime(active)
             -> success: status=repaired (사용자에게 표시)
             -> fail: status=failed (재시도/문의 안내)
```

### 사용자 상태 표시 권장 문구
- `런타임 점검 중...`
- `런타임 복구 중...`
- `런타임 복구 완료. 작업을 계속할 수 있습니다.`
- `런타임 복구 실패. 네트워크 상태를 확인하고 다시 시도해 주세요.`

## 표준 상태 머신 정의

런타임/세션 부트스트랩 상태는 아래 열거형으로 고정한다.

```ts
type RuntimeState =
  | 'IDLE'
  | 'PREPARING_RUNTIME'
  | 'AUTH_REQUIRED'
  | 'READY'
  | 'RUNNING'
  | 'PATCH_PENDING'
  | 'FAILED';
```

### 상태 의미
- `IDLE`: 초기 상태. 아직 런타임 준비/인증/실행을 시작하지 않은 상태.
- `PREPARING_RUNTIME`: 런타임 다운로드/검증/복구를 수행 중인 상태.
- `AUTH_REQUIRED`: API 키 만료, 로그인 만료 등으로 사용자 인증/재인증이 필요한 상태.
- `READY`: 실행 가능한 정상 대기 상태(명령 시작 가능).
- `RUNNING`: 명령 또는 작업이 현재 실행 중인 상태.
- `PATCH_PENDING`: diff/patch가 생성되어 사용자 승인 또는 충돌 해결을 기다리는 상태.
- `FAILED`: 자동 복구가 실패했거나, 즉시 사용자 개입이 필요한 치명 상태.

### 권장 전이

```text
IDLE -> PREPARING_RUNTIME
PREPARING_RUNTIME -> READY | AUTH_REQUIRED | FAILED
AUTH_REQUIRED -> PREPARING_RUNTIME | READY | FAILED
READY -> RUNNING | PATCH_PENDING
RUNNING -> READY | PATCH_PENDING | FAILED
PATCH_PENDING -> RUNNING | READY | FAILED
FAILED -> PREPARING_RUNTIME
```

## 오류 코드 표준화

오류 코드는 문자열 열거형으로 고정하고, UI/로깅/분석 전 구간에서 동일 값을 사용한다.

```ts
type RuntimeErrorCode =
  | 'RUNTIME_BROKEN'
  | 'AUTH_INVALID'
  | 'MODEL_UNAVAILABLE'
  | 'PATCH_CONFLICT'
  | 'PERMISSION_DENIED';
```

### 코드별 의미/처리 가이드
- `RUNTIME_BROKEN`: 런타임 파일 손상/누락/버전 불일치. 기본 액션은 `repairRuntime()`.
- `AUTH_INVALID`: 토큰 만료/무효 API 키/권한 철회. 기본 액션은 재인증.
- `MODEL_UNAVAILABLE`: 선택 모델이 비활성/쿼터 초과/배포 중단. 모델 재선택 또는 대체 모델 폴백.
- `PATCH_CONFLICT`: patch 적용 충돌 발생. 충돌 뷰 제공 후 수동 머지 유도.
- `PERMISSION_DENIED`: 파일/디렉터리/URI 접근 권한 거부. 권한 재요청 플로우로 이동.

## 상태별 UI 액션 노출 규칙

UI는 현재 상태에서 허용된 액션만 노출한다(비허용 액션은 숨김 또는 disabled 처리).

| 상태 | 노출 액션(예시) | 비고 |
|---|---|---|
| `IDLE` | `prepare` | 최초 진입 액션만 노출 |
| `PREPARING_RUNTIME` | `cancel`(선택) | 중복 실행/추가 입력 차단 |
| `AUTH_REQUIRED` | `reauth` | 인증 완료 전 실행 버튼 숨김 |
| `READY` | `run`, `change_model` | 정상 대기 상태 |
| `RUNNING` | `stop` | 설정 변경/중복 실행 차단 |
| `PATCH_PENDING` | `review_patch`, `apply_patch`, `reject_patch` | 승인 전 자동 적용 금지 |
| `FAILED` | `repair` | 실패 상태에서는 복구 버튼만 기본 노출 |

> 최소 규칙: `FAILED` 상태에서 `repair` 외 실행 액션은 노출하지 않는다.

## stderr 파싱 및 사용자 친화 메시지 규칙

stderr 원문을 그대로 노출하지 않고, 패턴 기반으로 표준 오류 코드 + 사용자 메시지로 변환한다.

### 파싱 파이프라인
1. stderr를 줄 단위로 정규화(트림, ANSI escape 제거).
2. 규칙 테이블을 위에서 아래 순서로 매칭(첫 매치 우선).
3. 매칭되면 `{ code, userMessage, rawSnippet }` 생성.
4. 매칭 실패 시 `RUNTIME_BROKEN` + 일반 안내 문구로 폴백.

### 규칙 테이블(초안)

| stderr 패턴(정규식 예시) | 매핑 코드 | 사용자 메시지 |
|---|---|---|
| `/(invalid api key|unauthorized|token expired)/i` | `AUTH_INVALID` | `인증 정보가 유효하지 않습니다. 다시 로그인해 주세요.` |
| `/(model .* not found|model unavailable|quota exceeded)/i` | `MODEL_UNAVAILABLE` | `요청한 모델을 사용할 수 없습니다. 다른 모델을 선택해 주세요.` |
| `/(patch.*conflict|merge conflict|hunk failed)/i` | `PATCH_CONFLICT` | `코드 변경 적용 중 충돌이 발생했습니다. 변경 내용을 검토해 주세요.` |
| `/(permission denied|eacces|operation not permitted)/i` | `PERMISSION_DENIED` | `파일 또는 리소스 접근 권한이 없습니다. 권한을 확인해 주세요.` |
| `/(checksum mismatch|runtime missing|binary not found|exec format error)/i` | `RUNTIME_BROKEN` | `런타임이 손상되었거나 누락되었습니다. 복구를 시도해 주세요.` |

### 구현 권장사항
- 원문(stderr) 전체는 개발자 로그에만 저장하고, 사용자 UI에는 `userMessage` 중심으로 노출.
- 동일 오류 반복 시(예: 30초 내 동일 코드 3회) 토스트 중복 노출을 억제.
- 로깅 이벤트 스키마에 `state`, `code`, `matchedPatternId`, `commandId`를 포함해 장애 분석 가능성을 확보.
