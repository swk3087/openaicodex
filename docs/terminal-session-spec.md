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
