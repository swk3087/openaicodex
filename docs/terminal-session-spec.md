# Terminal Session 사양

## 목표
- 포그라운드 실행 책임(`TerminalSessionService`)과 세션 생명주기 책임(`TerminalSessionManager`)을 분리한다.
- 세션 API를 단일 계약으로 고정해 상위 계층(UI/IPC)과 하위 실행 계층을 느슨하게 결합한다.
- 명령 입력은 allowlist 기반 파서로 선검증하여 금지 명령을 즉시 차단한다.
- 모든 세션 작업 디렉터리를 `files/sessions/{sessionId}/workspace`로 강제한다.
- 로그 스트림은 ring buffer로 유지해 메모리 상한을 보장하고 OOM 위험을 줄인다.

## 디렉터리 구조

```text
files/
  sessions/
    {sessionId}/
      workspace/
      logs/
```

- 세션별 실제 실행 CWD는 반드시 `files/sessions/{sessionId}/workspace`.
- 상위에서 전달한 임의 경로는 무시하고, 매니저가 계산한 경로만 사용.

## 컴포넌트 분리

### `TerminalSessionManager` (control plane)
- 세션 생성/조회/종료를 담당.
- 세션 메타데이터와 상태(`idle`, `running`, `closed`, `blocked`)를 관리.
- `TerminalSessionService` 인스턴스를 세션 단위로 연결/해제.
- API 진입점에서 `sessionId` 유효성, 상태 전이, 디렉터리 준비를 수행.

### `TerminalSessionService` (foreground execution plane)
- 실제 프로세스 실행 및 stdin 전달 담당.
- 명령 파서 결과(허용/차단)를 바탕으로 실행 또는 즉시 거부.
- stdout/stderr 이벤트를 ring buffer에 append.
- 프로세스 종료 코드, 시그널, 마지막 실행 시각을 매니저에 보고.

## 세션 API 계약

```ts
interface TerminalSessionApi {
  openSession(sessionId: string): Promise<void>;
  execute(sessionId: string, command: string): Promise<ExecuteResult>;
  sendInput(sessionId: string, text: string): Promise<void>;
  closeSession(sessionId: string): Promise<void>;
}

interface ExecuteResult {
  accepted: boolean;
  blockedReason?: string;
  commandId?: string;
}
```

### `openSession(sessionId)`
- `sessionId` 형식 검증(예: `^[a-zA-Z0-9_-]{1,64}$`).
- `files/sessions/{sessionId}/workspace` 생성 보장.
- 이미 열린 세션이면 멱등 처리.

### `execute(sessionId, command)`
- 세션 존재/상태 확인 (`closed`면 오류).
- allowlist 파서로 1차 검증.
- 금지 명령/패턴 탐지 시 실행하지 않고 `accepted=false` 반환 + 보안 이벤트 기록.
- 허용 시 foreground 프로세스로 실행하고 `commandId` 반환.

### `sendInput(sessionId, text)`
- 활성 foreground 프로세스가 있는 경우 해당 stdin으로 전달.
- 실행 중 프로세스가 없으면 no-op 또는 명시적 오류(구현 정책 택1, 일관성 유지).

### `closeSession(sessionId)`
- 실행 중인 foreground 프로세스에 종료 시그널 전송(SIGTERM → timeout → SIGKILL).
- 버퍼 flush/핸들 정리 후 상태를 `closed`로 전이.

## 명령 파서(allowlist)

### 원칙
- 기본 거부(deny-by-default).
- "허용된 실행 파일 + 허용된 인자 규칙"을 모두 통과해야 실행.
- 셸 메타문자(`;`, `&&`, `||`, `|`, '`', `$()`, `>`, `<`) 포함 시 즉시 차단.

### 예시 정책

```ts
const COMMAND_ALLOWLIST = {
  ls: { args: [/^[\w./-]+$/] },
  cat: { args: [/^[\w./-]+$/] },
  pwd: { args: [] },
  echo: { args: [/^[\w\s._-]+$/] }
};
```

- 명령 토큰화 후 첫 토큰(executable)을 allowlist에서 조회.
- 인자 개수/정규식 규칙 위반 시 차단.
- 차단 시점은 파싱 직후(프로세스 spawn 이전)여야 함.

## 작업 디렉터리 강제 정책
- `resolveSessionWorkspace(sessionId)`로 CWD를 단일 계산.
- `realpath` 기준으로 `files/sessions/{sessionId}/workspace` 경로 이탈 여부 확인.
- 경로 순회(`..`) 또는 심볼릭 링크 탈출 탐지 시 세션 차단(`blocked`).

## 파일 접근 게이트웨이(`FileGateway`)

세션 워크스페이스 파일 접근은 직접 파일 API 호출 대신 `FileGateway` 인터페이스를 통해서만 수행한다.
이는 Android URI 권한, 트리 범위 검증, I/O 감사 로그를 단일 지점에서 일관되게 적용하기 위함이다.

```ts
interface FileGateway {
  grantTree(treeUri: string): Promise<void>;
  list(path: string): Promise<FileEntry[]>;
  read(path: string): Promise<Uint8Array>;
  write(path: string, data: Uint8Array): Promise<void>;
  stat(path: string): Promise<FileStat>;
}
```

### 메서드 역할
- `grantTree()`: 세션 루트 트리 URI 권한 확보 및 내부 캐시 갱신.
- `list()`: 디렉터리 엔트리 열람(권한 범위 밖 경로는 즉시 거부).
- `read()`: 파일 바이트 읽기(사전 `stat` 검증과 감사 이벤트 기록 포함).
- `write()`: 파일 쓰기(원자적 쓰기 권장: temp 파일 -> rename).
- `stat()`: 파일 타입/크기/mtime 조회.

### Android Persistable URI 권한 정책
- `grantTree()` 구현 시 `takePersistableUriPermission`을 반드시 호출해 앱 재시작 후에도 접근 권한을 유지한다.
- 앱 시작 시 persisted permission 목록과 실제 접근 가능 여부를 대조한다.
- 권한 만료/철회가 감지되면 아래 복구 루틴을 수행한다.
  1. 해당 트리 접근을 잠시 차단하고 세션 상태를 `blocked`로 전환.
  2. 재승인 UI를 노출해 사용자가 동일 트리를 다시 선택하도록 유도.
  3. 재승인 성공 시 `grantTree()` 재호출 후 보류 작업 재시도.
  4. 실패 시 명시적 오류 코드(`E_TREE_PERMISSION_EXPIRED`)를 상위 계층에 반환.

## diff 적용 파이프라인

파일 변경(diff) 적용은 아래 고정 순서로만 진행한다.

1. **패치 검증**
   - diff 포맷/헤더/허용 경로/파일 타입을 점검한다.
   - 적용 대상 파일의 존재 여부와 기본 전제(해시/사이즈)를 확인한다.
2. **충돌 탐지**
   - 대상 파일의 현재 상태와 patch base를 비교해 충돌 여부를 계산한다.
   - 충돌 발생 시 자동 적용을 중단하고 충돌 리포트를 생성한다.
3. **사용자 승인**
   - 변경 파일 목록, 충돌 여부, 위험 플래그(권한/삭제/대량 수정)를 UI에 표시한다.
   - 명시적 승인 이벤트 없이는 실제 쓰기를 수행하지 않는다.
4. **적용 + 롤백 포인트 기록**
   - 승인 후 patch를 적용하고, 적용 전 스냅샷(또는 역패치)을 롤백 포인트로 저장한다.
   - 적용 실패 시 롤백 포인트를 사용해 즉시 원복한다.

## 바이너리/대용량 파일 제외 규칙

diff 적용 대상에서 바이너리 및 대용량 파일을 기본 제외한다.

### 제외 기준(권장)
- 바이너리 파일: null byte 포함 또는 텍스트 인코딩 판별 실패.
- 대용량 파일: 단일 파일 `> 5 MiB` 또는 총 변경량 `> 20 MiB`.
- 생성 산출물/압축 아카이브: `*.apk`, `*.aab`, `*.so`, `*.zip`, `*.tar`, `*.jar`, `*.png`, `*.jpg` 등.

### UI 사전 고지
- diff 미리보기 단계에서 제외 규칙을 먼저 고지한다.
- 제외된 파일은 "자동 적용 제외"로 표기하고 이유(바이너리/용량 초과)를 함께 표시한다.
- 사용자가 제외 항목을 포함하려는 경우, 별도 위험 확인 다이얼로그(2차 확인)를 요구한다.

## 로그 스트림 ring buffer

### 요구사항
- 세션별 stdout/stderr를 메모리 ring buffer에 저장.
- 고정 용량 초과 시 오래된 로그부터 덮어쓰기.
- 버퍼는 UTF-8 텍스트 기준으로 운영하되, 구현에서는 byte 단위 제한을 권장.

### 인터페이스 예시

```ts
interface LogRingBuffer {
  push(chunk: string): void;
  snapshot(): string[];
  clear(): void;
}
```

### 권장 기본값
- `maxChunks`: 2,000
- `maxBytes`: 4 MiB
- 초과 시 FIFO 방식으로 `head` 이동.

## 상태 전이

```text
openSession -> idle
idle --execute(allowed)--> running
idle --execute(blocked)--> blocked
running --processExit--> idle
running --closeSession--> closed
blocked --closeSession--> closed
```

- `blocked` 상태에서는 `execute` 재시도 금지(운영 정책에 따라 `openSession` 재호출로 초기화 가능).

## 영속성 모델 (DB 엔티티)

세션 관련 데이터는 반드시 `SessionEntity`를 루트로 하는 1:N 구조로 설계한다.

### 엔티티 목록

```ts
interface SessionEntity {
  id: string; // PK (= sessionId)
  status: 'idle' | 'running' | 'closed' | 'blocked';
  createdAt: string;
  updatedAt: string;
}

interface SessionConfigEntity {
  id: string;
  sessionId: string; // FK -> SessionEntity.id
  cwd: string;
  allowlistVersion: string;
  createdAt: string;
  updatedAt: string;
}

interface SessionModelEntity {
  id: string;
  sessionId: string; // FK -> SessionEntity.id
  model: string;
  provider: string;
  temperature: number;
  createdAt: string;
  updatedAt: string;
}

interface PatchEntity {
  id: string;
  sessionId: string; // FK -> SessionEntity.id
  commandId: string;
  patchText: string;
  applyStatus: 'pending' | 'applied' | 'rejected' | 'rolled_back';
  createdAt: string;
  updatedAt: string;
}

interface CommandHistoryEntity {
  id: string;
  sessionId: string; // FK -> SessionEntity.id
  command: string;
  accepted: boolean;
  blockedReason?: string;
  exitCode?: number;
  createdAt: string;
}
```

### 핵심 제약
- `SessionConfigEntity`, `SessionModelEntity`, `PatchEntity`, `CommandHistoryEntity`는 모두 `sessionId` FK를 **필수(`NOT NULL`)**로 가진다.
- FK는 모두 `REFERENCES session(id) ON UPDATE CASCADE ON DELETE CASCADE`로 선언한다.
- `PatchEntity.commandId`는 세션 내 중복 방지를 위해 `(sessionId, commandId)` unique 인덱스를 권장한다.
- 조회 성능을 위해 모든 하위 엔티티에 `INDEX(sessionId, createdAt)`를 둔다.

### 세션 삭제(cascade) 정책
- `SessionEntity` 삭제 시 하위 엔티티(`SessionConfigEntity`, `SessionModelEntity`, `PatchEntity`, `CommandHistoryEntity`)는 DB FK cascade로 즉시 제거한다.
- 애플리케이션 레벨에서도 soft delete를 사용하지 않는 한, 별도 수동 정리 로직을 두지 않는다(이중 삭제 방지).
- 단, 감사 규정으로 command 이력 장기 보관이 필요하면 cascade 대신 아카이빙 테이블로 이관 후 삭제하는 별도 배치 정책을 채택한다.

## 리포지토리 계층 cross-session 접근 차단

모든 하위 엔티티 저장소는 `sessionId`를 입력으로 강제하고, PK 단독 조회를 금지한다.

```ts
interface PatchRepository {
  findById(sessionId: string, patchId: string): Promise<PatchEntity | null>;
  listBySession(sessionId: string): Promise<PatchEntity[]>;
  save(sessionId: string, patch: PatchEntity): Promise<void>;
}
```

### 가드 규칙
1. 저장소 메서드 시그니처에서 `sessionId`를 첫 번째 필수 인자로 받는다.
2. SQL/ORM 조건절은 항상 `WHERE id = :id AND session_id = :sessionId` 형태를 강제한다.
3. `affectedRows === 0`일 때는 "존재하지 않음"과 "타 세션 데이터"를 구분하지 않고 동일한 not-found 오류를 반환한다(정보 노출 차단).
4. 서비스 계층은 컨텍스트의 활성 `sessionId`와 요청 파라미터의 `sessionId`가 다르면 즉시 `E_SESSION_SCOPE_MISMATCH`를 반환한다.

## DB 버전업 및 마이그레이션 안정성 검증

마이그레이션 테스트는 최소 아래 3단계 시나리오를 CI에서 고정 실행한다.

1. **Fresh install 경로**
   - 빈 DB에서 최신 스키마까지 일괄 마이그레이션.
   - 모든 테이블/인덱스/FK 존재 여부와 `ON DELETE CASCADE` 설정을 검증.
2. **N-1 -> N 업그레이드 경로**
   - 직전 버전 스냅샷 DB를 준비하고 최신으로 마이그레이션.
   - 기존 데이터 보존, 신규 nullable/default 컬럼 채움, FK 제약 충돌 여부를 검증.
3. **회귀 안정성 경로**
   - 업그레이드 후 세션 생성/명령 기록/패치 저장/세션 삭제를 실행.
   - 세션 삭제 직후 하위 테이블 row count가 0인지 확인해 cascade 동작을 검증.

### 테스트 예시(의사코드)

```ts
it('deletes child rows when a session is deleted', async () => {
  const sessionId = 's1';
  await insertSession(sessionId);
  await insertSessionConfig(sessionId);
  await insertSessionModel(sessionId);
  await insertPatch(sessionId, 'cmd-1');
  await insertCommandHistory(sessionId, 'pwd');

  await deleteSession(sessionId);

  expect(await count('session_config', sessionId)).toBe(0);
  expect(await count('session_model', sessionId)).toBe(0);
  expect(await count('patch', sessionId)).toBe(0);
  expect(await count('command_history', sessionId)).toBe(0);
});
```

## 관측성/감사
- 차단 이벤트: `sessionId`, `rawCommand`, `reason`, `timestamp` 필수 기록.
- 실행 이벤트: `commandId`, `exitCode`, `durationMs`, `truncatedLogBytes` 기록.
- 보안 로그와 일반 로그는 분리 저장 권장.

## 테스트 체크리스트
- allowlist 외 명령(`rm -rf /`, `curl ... | sh`)이 spawn 이전 차단되는지.
- CWD가 항상 `files/sessions/{sessionId}/workspace`인지.
- `sendInput`이 활성 프로세스에만 전달되는지.
- ring buffer가 용량 초과 시 메모리 증가 없이 덮어쓰기 동작하는지.
- `closeSession` 시 프로세스/핸들이 누수 없이 정리되는지.
