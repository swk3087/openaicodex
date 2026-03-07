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
