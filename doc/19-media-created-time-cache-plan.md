# 19. 폴더별 촬영 시각 캐시 구현 계획서

사진·영상이 많은 폴더를 빠르게 열면서도 촬영 시각 정렬이 화면에서 갑자기 바뀌지 않게 만드는
계획이다.

- 작성일: 2026-09-15
- 2차 개정: 2026-10-01 — 독립 리뷰 3건의 구조·정확성·화면 검토 반영
- 대상 브랜치: `bugfix/large-folder-initial-load`
- 기준 커밋: `4b610a3c`
- 관련 문서: [08번 기획서](08-media-view-mode-spec.md),
  [09번 구현 계획](09-media-view-mode-plan.md),
  [미결 항목](media-view-mode-open-items.md)

## 1. 확인된 문제

현재 `Path.loadFileItem()`은 화면 모드와 정렬 기준을 모른 채 모든 사진·영상에 대해
`MediaCreatedTime.read()`를 호출한다. 그래서 이름순 목록처럼 촬영 시각을 쓰지 않는 화면도
EXIF 또는 영상 컨테이너를 전부 읽은 뒤에야 나타난다.

현재 캐시는 프로세스 메모리의 `LruCache`이고 상한은 4,096개다. 5,000개 폴더를 같은 순서로
다시 읽으면 첫 904개가 빠진 상태에서 시작하고, 앞쪽 파일을 다시 넣는 동안 뒤쪽 캐시도
차례로 밀려난다. 결국 두 번째 진입도 거의 전부 다시 읽는다. 앱 프로세스가 끝나면 캐시는
모두 사라진다.

단순히 기본 정보로 임시 정렬한 목록을 먼저 보이고 촬영 시각을 다 읽은 뒤 재정렬하는 방법은
쓰지 않는다. 사용자가 보고 있던 항목과 스크롤 위치가 갑자기 이동하기 때문이다.

## 2. 목표와 범위

### 목표

1. 일반 목록·바둑판 모드는 촬영 시각 파일 I/O 없이 기존 수준의 속도로 연다.
2. 미디어 모드와 `촬영 시각순` 정렬은 정확한 순서가 준비된 뒤 한 번만 표시한다.
3. 촬영 시각 결과를 폴더당 파일 하나에 영구 저장해 두 번째 진입부터 원본 미디어를 열지 않는다.
4. 파일 추가·수정·삭제를 폴더 전체 재분석 없이 항목별로 반영한다.
5. 손상된 캐시, 저장 공간 부족, 읽기 전용 원본 폴더가 있어도 목록 기능은 계속 동작한다.

### 이번 범위에서 하지 않는 것

- 화면에 나타난 목록을 나중에 다시 정렬하는 점진적 표시
- 캐시 파일을 원본 사진 폴더 안에 생성하는 것
- 썸네일·폴더 항목 수 등 다른 종류의 캐시 통합
- 여러 기기 사이의 캐시 동기화
- 파일 내용이 바뀌었지만 크기와 수정 시각이 모두 같은 특수 상황의 완전한 감지

## 3. 확정할 설계

### 3.1 저장 위치

캐시는 원본 폴더가 아니라 앱 전용 영구 저장소 중 **백업 제외 영역**에 둔다.

```text
noBackupFilesDir/
└── media_created_time_cache/
    ├── 0f8c...a91.mctc
    ├── 17ab...e20.mctc
    └── ...                         폴더 하나당 파일 하나
```

`cacheDir`는 운영체제가 임의로 지울 수 있고, `filesDir`는 현재 앱 설정에서 Android 백업
대상이므로 쓰지 않는다. 캐시는 다른 기기로 복원할 가치가 없고 파일 이름·크기·촬영 시각을
포함하므로 `noBackupFilesDir`가 맞다. 원본 폴더 안의 숨김 파일도
다음 이유로 쓰지 않는다.

- 사용자의 폴더를 앱 내부 데이터로 오염시킨다.
- 읽기 전용, SAF, 압축 파일, 원격 경로에는 쓰지 못할 수 있다.
- 캐시 쓰기가 `PathObserver`를 다시 깨워 목록 재로딩을 반복시킬 수 있다.
- 다른 파일 관리자와 백업·동기화 앱에 불필요하게 노출된다.

캐시 파일명은 provider가 정의한 **완전한 폴더 식별자**를 길이 구분된 바이트로 직렬화한 뒤
구한 SHA-256 해시다.

```text
provider scheme
+ 파일시스템 authority 또는 인스턴스 식별자
+ 비밀번호를 제외한 host / port / user
+ Document provider의 authority + tree/document ID
+ 정규화한 절대 경로
```

가능하면 provider가 완전하게 구현한 `Path.toUri()`를 정규화해서 쓰고, 그렇지 않은 provider는
위 구성요소를 명시적으로 제공한다. `scheme + Path.toString()`만 쓰면 서로 다른 SMB·SFTP·WebDAV
서버의 `/Pictures`가 충돌하므로 금지한다. 식별자 원문과 자격 증명은 파일명이나 캐시 본문에
저장하지 않는다.

### 3.2 캐시 파일 형식

외부 라이브러리를 추가하지 않고 `DataInputStream`/`DataOutputStream` 기반의 버전 있는
이진 형식을 쓴다. JSON보다 읽기·쓰기 양과 객체 생성을 줄이고, 5,000개 항목도 한 번에 빠르게
읽을 수 있다.

```text
Header
  magic              8 bytes   "PEMCTC01"
  schemaVersion      Int       1
  extractorVersion   Int       촬영 시각 판독 규칙 버전
  payloadLength      Int
  entryCount         Int

Entry × entryCount
  fileNameLength     Int
  fileNameUtf8       ByteArray 현재 폴더 바로 아래의 이름
  validatorFlags     Byte      size/mtime/strong validator의 존재·신뢰 여부
  fileSize           Long
  lastModifiedMillis Long
  strongIdLength     Int
  strongIdUtf8       ByteArray provider가 제공할 때만(fileKey, ETag, document ID 등)
  resultType         Byte      0 = 촬영 시각 없음, 1 = 값 있음
  createdTimeMillis  Long      resultType == 1일 때만

Footer
  payloadCrc32       Int       Header 뒤 payload의 CRC32
```

경로 전체 대신 바로 아래 파일 이름만 저장한다. 폴더별 파일이므로 충분하며 크기도 줄어든다.
심볼릭 링크는 현재 `FileItem.attributes`가 가리키는 대상의 크기·수정 시각을 사용한다.

다음 제한을 넘거나 형식이 잘못된 파일은 손상된 캐시로 처리한다.

- 파일 크기: 16 MiB
- 항목 수: 200,000개
- 파일 이름 UTF-8 길이: 16 KiB
- 알 수 없는 magic, version, result type
- 음수 길이, 잘린 입력, 중복 파일 이름
- payload 길이·CRC 불일치, footer 뒤 추가 바이트
- 허용 범위를 벗어난 촬영 시각

`schemaVersion`은 파일 구조, `extractorVersion`은 지원 형식·날짜 보정·원격 읽기 정책처럼 결과에
영향을 주는 판독 규칙을 나타낸다. 판독 규칙이 바뀌면 과거의 값과 “값 없음”을 다시 분석한다.
손상되거나 버전이 맞지 않는 캐시는 사용하지 않고 다시 만든다. 캐시 문제 때문에 원본 폴더
열기가 실패해서는 안 된다.

### 3.3 항목이 유효한 조건

로컬 파일처럼 크기와 수정 시각이 신뢰 가능한 provider에서는 다음 값이 모두 일치할 때만
적중으로 본다.

```text
파일 이름 + 파일 크기 + 수정 시각
```

SAF `DocumentsProvider`는 크기와 수정 시각을 제공하지 않아 둘 다 0이 될 수 있고, WebDAV도
수정 시각이 없으면 epoch를 돌려줄 수 있다. 따라서 0을 실제 값과 “알 수 없음”으로 함께 쓰지
않는다. `validatorFlags`로 알려짐과 신뢰 여부를 별도로 보존하고 다음 순서로 판단한다.

1. provider가 ETag, 안정적인 `fileKey`, document ID와 같은 강한 식별자를 주면 이를 우선한다.
2. 크기와 수정 시각이 모두 신뢰 가능하면 기존 조합을 쓴다.
3. 하나라도 신뢰할 수 없고 강한 식별자도 없으면 **영구 적중을 허용하지 않는다.** 메모리에서
   같은 목록 요청 동안만 재사용하고 다음 진입에서는 다시 판독한다.

provider별로 어떤 validator를 신뢰하는지 표와 테스트를 코드 옆에 둔다. 최소 대상은 local,
Document/SAF, SMB, SFTP, FTP, WebDAV, archive다.

- 새 파일: 항목이 없으므로 해당 파일만 분석한다.
- 내용 또는 수정 시각 변경: 키가 달라져 해당 파일만 다시 분석한다.
- 이름 변경: 이전 이름을 제거하고 새 이름으로 한 번 분석한다.
- 삭제: 다음 폴더 스캔 때 캐시 파일에서 제거한다.
- 촬영 시각이 없는 파일: `resultType = 0`으로 저장해 비싼 실패 과정을 반복하지 않는다.

신뢰 가능한 provider에서도 크기와 수정 시각을 보존한 채 내용만 바꾼 파일은 알아채지 못할 수
있다. 해시 계산은 원본 전체를 읽어야 하므로 폴더 열기 속도 목표와 충돌한다. 이 제한은 문서와
테스트에 명시한다.

일시적인 I/O 실패와 “정상적으로 촬영 시각이 없음”은 구분한다. 이를 위해
`MediaCreatedTime.read()`의 내부 결과를 다음 세 상태로 바꾼다.

```kotlin
sealed interface MediaCreatedTimeResult {
    data class Available(val millis: Long) : MediaCreatedTimeResult
    data object UnsupportedOrNoMetadata : MediaCreatedTimeResult
    data class RetryableFailure(val throwable: Throwable) : MediaCreatedTimeResult
}
```

`Available`과 안정적으로 판정된 `UnsupportedOrNoMetadata`만 디스크에 저장한다. 권한 문제,
파일이 사라진 경우, 일시적인 provider 오류, 원격 파일 읽기 설정 때문에 건너뛴 경우는 저장하지
않아 다음 진입이나 설정 변경 뒤 다시 시도할 수 있게 한다. 파서별 예외 분류표를 만들고, 손상된
미디어처럼 반복되는 영구 실패는 제한된 재시도 횟수 또는 다음 재시도 시각을 두는 방안을 2단계
측정으로 결정한다.

`InterruptedException`, `InterruptedIOException`, `CancellationException`과 명시적인 중단 신호는
결과 타입으로 바꾸지 않고 반드시 다시 던진다. 각 파일 판독 전후와 MP4 탐색 루프에서도 중단을
검사한다. 외부 API는 기존처럼 `Long?`를 돌려줘도 되지만 저장 계층에는 세 상태가 전달되어야 한다.

### 3.4 언제 촬영 시각을 준비할지

경로·검색·촬영 시각 필요 여부·세대 번호를 하나의 불변 요청으로 묶는다.

```kotlin
data class FileListLoadRequest(
    val path: Path,
    val searchState: SearchState,
    val includeMediaCreatedTime: Boolean,
    val generation: Long
)
```

`includeMediaCreatedTime`은 다음 중 하나일 때만 `true`다.

- 보기 모드가 `FileViewType.MEDIA`
- 일반 목록·바둑판에서 정렬 기준이 `By.MEDIA_CREATED`

단, **검색 중에는 항상 `false`**다. 현재 검색 결과는 500ms 단위로 늘어나며 의도적으로 정렬과
날짜 묶음을 하지 않고, 보기·정렬 메뉴도 숨긴다. 검색 결과의 여러 부모 폴더 캐시를 여는 작업은
화면에 쓰이지 않으므로 하지 않는다.

그 외 이름·종류·크기·수정 시각 정렬에서는 캐시 파일도 열지 않는다. `loadFileItem()`은 빠른
기본 정보만 만드는 함수로 되돌리고, 촬영 시각은 목록 로더가 정책에 따라 별도로 채운다.
`FileJobs`와 `FileLiveData`가 불필요하게 미디어 정보를 읽는 문제도 함께 사라진다.

`FileListSwitchMapLiveData`는 `path`와 `searchState`만 따로 보지 않고 이 요청 하나만 source key로
사용한다. `includeMediaCreatedTime`이 실제로 바뀔 때만 새 목록 작업을 만든다. LIST↔GRID,
정렬 방향 변경처럼 정책이 같으면 다시 읽지 않는다. 모든 Loading/Success/Failure와 캐시 저장은
게시 직전에 현재 generation과 비교하며, 오래된 요청의 결과는 버린다.

### 3.5 화면에 전달하는 순서

#### 촬영 시각이 필요 없는 경우

```text
폴더 열거 → 기본 FileItem 생성 → 정렬 → Success 한 번 게시
```

#### 촬영 시각이 필요한 경우

```text
폴더 열거 → 기본 FileItem 생성
          → 폴더 캐시 파일 한 번 읽기
          → 적중 항목 적용
          → 빠진 미디어만 원본에서 분석
          → 촬영 시각 정렬
          → Success 한 번 게시
          → 같은 불변 snapshot을 캐시 파일에 비동기 원자 저장
```

차가운 캐시의 미디어 모드는 분석이 끝날 때까지 기존 LIST/GRID 화면을 그대로 유지하고 로딩을
표시한다. 기존 기본 목록을 MEDIA 어댑터에 전달하지 않는다. 따뜻한 캐시에서는 원본 미디어를
열지 않고 폴더 캐시 파일 하나만 읽는다. 캐시 저장 지연이나 실패는 이미 완성된 목록의 표시를
막지 않는다.

ViewModel은 사용자가 선택한 `requestedMode`와 실제 화면의 `renderedMode`를 분리한다. 완성된
Success가 현재 generation과 일치할 때만 모드와 목록을 함께 교체한다. 최신 항목 이동과 viewport
복원도 이 시점에 한 번만 실행한다. 준비 중 반대 방향으로 전환하면 이전 작업을 취소하고 새 요청만
유효하게 만든다.

이미 메모리에 완전한 결과가 있으면 재사용한다. MEDIA→LIST 촬영 시각순처럼 정책이 `true`로
유지되는 전환은 다시 읽지 않는다. 목록 모드로 돌아가도 현재 메모리의 촬영 시각을 버릴 필요는
없지만, 다음 새 폴더에서는 필요하지 않으면 읽지 않는다.

### 3.6 메모리와 디스크의 역할

메모리 캐시는 개별 파일 4,096개가 아니라 최근에 사용한 **폴더 snapshot**을 보관하되, 폴더
개수만으로 상한을 정하지 않는다.

- 메모리: 최대 8개 폴더이면서 총 40,000항목, 추정 24 MiB 이하의 가중 LRU
- 디스크: 앱 재시작과 대형 폴더 재진입에 사용

한 폴더의 캐시를 읽은 뒤 파일마다 디스크를 다시 열지 않는다. 목록 작업 하나가 폴더 캐시의
snapshot을 받아 메모리에서 조회하고, 변경분을 합쳐 마지막에 한 번 저장한다. 한 폴더만으로
상한을 넘으면 그 snapshot은 디스크에서 사용하되 작업 완료 뒤 메모리에 남기지 않는다.
`ComponentCallbacks2.onTrimMemory()`에서 메모리 캐시를 단계적으로 비운다.

### 3.7 원자적 저장과 동시 접근

- `android.util.AtomicFile`의 `startWrite()`/`finishWrite()`/`failWrite()` 계약을 그대로 사용한다.
- 취소, 예외, 저장 공간 부족이면 기존 파일을 보존한다.
- 폴더별 single-flight 작업과 generation을 둬 같은 요청의 중복 판독을 합친다.
- 디스크 snapshot 읽기와 최종 병합·교체만 폴더별 `Mutex` 안에서 수행하고, 느린 원본 분석은
  잠금 밖에서 수행한다.
- 서로 다른 폴더는 동시에 처리할 수 있다.
- 분석 작업이 취소되면 부분 결과를 화면이나 디스크에 게시하지 않는다.
- 쓰기 직전에 최신 디스크 snapshot과 조건부 병합한다. 완전한 폴더 열거 결과만 삭제 항목을
  제거할 수 있으며 부분 결과는 upsert만 가능하다.
- `.bak`는 고아 파일이 아니라 중단된 쓰기의 유일한 정상본일 수 있다. 시작할 때 먼저
  `AtomicFile.openRead()`로 복구한 뒤 정상 base가 확인된 경우에만 구현이 만든 임시 파일을 정리한다.

### 3.8 캐시 총량과 정리

폴더별 파일은 무한히 쌓이지 않게 다음 초기 상한을 둔다.

- 전체 크기 상한: 64 MiB
- 폴더 캐시 파일 수 상한: 512개
- 정리 목표: 48 MiB 이하, 384개 이하

캐시 본문은 항목 추가·변경·삭제가 있을 때만 다시 저장한다. 최근 사용 표시는 마지막 갱신에서
24시간 이상 지났을 때만 파일 최종 수정 시각을 갱신해 따뜻한 캐시 진입마다 쓰기가 발생하지
않게 한다. 새 캐시 저장 후 상한을 넘으면 오래 사용하지 않은 파일부터 정리 목표까지 삭제한다.
정리 실패는 기능 실패로 취급하지 않는다. 현재 사용 중인 파일과 `.bak`/임시 파일은 정리 대상에서
제외한다. 복구 절차를 통과한 뒤에만 고아 임시 파일을 정리한다.

### 3.9 차가운 캐시의 분석 병렬도

폴더 directory stream은 기본 `FileItem` snapshot을 만든 뒤 먼저 닫는다. 이후 미디어 분석은
전용 제한 executor에서 수행한다.

- 로컬 저장소: 동시 2개로 시작해 Fold 7 측정 후 최대 4개까지 검토
- 원격 provider: 동시 1개
- `MediaMetadataRetriever` 생성도 같은 제한을 적용
- 새 요청 취소가 250ms 안에 관찰되는 것을 목표로 각 항목 전후에 중단 확인

첫 MEDIA 진입은 원본 메타데이터 양에 따라 오래 걸릴 수 있으므로 호출 수뿐 아니라 시간도
기록한다. 병렬도는 에뮬레이터 수치가 아니라 실기기 발열·스토리지 지연을 보고 확정한다.

## 4. 코드 구조

### 새 파일

| 파일 | 역할 |
|---|---|
| `file/MediaCreatedTimeResult.kt` | 값 있음·값 없음·읽기 실패 구분 |
| `file/MediaCreatedTimeCache.kt` | CRC·버전이 있는 폴더별 형식, 적중 판정, 원자적 I/O |
| `file/MediaCreatedTimeRepository.kt` | 가중 LRU, single-flight, 누락 분석, generation, 정리 조정 |
| `file/MediaCreatedTimeCacheKey.kt` | provider별 완전한 폴더 식별자와 항목 validator 생성 |
| `filelist/FileListLoadRequest.kt` | 경로·검색·정책·generation을 묶은 불변 요청 |

### 수정 파일

| 파일 | 변경 |
|---|---|
| `file/MediaCreatedTime.kt` | 세 상태 결과 제공, 기존 `Long?` 호출 호환 |
| `file/FileItem.kt` | 기본 정보 생성과 촬영 시각 적용을 분리 |
| `filelist/FileListLiveData.kt` | 로드 정책에 따라 폴더 캐시를 한 번 조회 |
| `filelist/FileListViewModel.kt` | 통합 요청 생성, requested/rendered 모드, generation 소유 |
| `filelist/SearchFileListLiveData.kt` | 검색 중 촬영 시각 접근이 없음을 유지 |
| `filelist/FileListFragment.kt` | 완성 결과에서만 모드·목록·스크롤을 함께 반영 |
| `app/AppInitializers.kt` 또는 별도 초기화 파일 | 고아 임시 파일과 상한 초과 캐시 정리 예약 |

Repository는 Android 저장 위치를 생성자 인자로 받고, 캐시 코덱과 미디어 판독 함수를 주입할 수
있게 만든다. 단위 테스트에서 실제 Android 파일 시스템이나 EXIF 파서 없이 동작을 검증하기
위해서다.

## 5. 구현 단계

### 0단계. 기준 측정

- [ ] 실기기에서 미디어 5,000개 폴더 준비
- [ ] 일반 목록 첫 진입, 같은 폴더 재진입, 앱 재시작 후 재진입 시간을 각각 측정
- [ ] `MediaCreatedTime.read()` 호출 수와 캐시 적중 수를 임시 계측
- [ ] 캐시 본문 read/write 횟수와 요청 취소 반응 시간을 임시 계측
- [ ] 측정 로그에는 전체 경로나 파일 이름을 남기지 않음

### 1단계. 캐시 코덱과 파일 수명

- [ ] provider authority·계정·Document ID를 포함한 폴더 식별자와 캐시 경로 구현
- [ ] extractor version·validator flags·CRC를 포함한 v1 이진 읽기·쓰기 구현
- [ ] `AtomicFile` 저장, `.bak` 복구, 프로세스 중단 지점별 테스트 구현
- [ ] 크기·개수 상한과 오래된 파일 정리 구현
- [ ] `noBackupFilesDir` 사용과 백업 제외 확인
- [ ] 정상 왕복, 빈 캐시, 값 없음, 잘림, CRC·길이·버전 오류 단위 테스트

### 2단계. 촬영 시각 결과 타입 분리

- [ ] 파서별로 값 없음·재시도 실패·중단 예외 분류표 작성
- [ ] 현재 이미지·영상 판독 결과와 `min(metadata, mtime)` 규칙 유지
- [ ] 재시도 실패와 설정상 제외가 “값 없음”으로 저장되지 않는지 테스트
- [ ] 판독 전후와 MP4 탐색 루프의 중단 전파 테스트
- [ ] 기존 뷰어 세부 정보가 같은 촬영 시각을 표시하는지 테스트

### 3단계. 폴더 Repository

- [ ] 폴더 캐시를 한 번 읽고 여러 항목을 메모리에서 조회
- [ ] 신뢰 가능한 validator만 영구 적중시키고 빠진 항목만 분석
- [ ] 완전한 폴더 snapshot에서만 삭제 항목 제거
- [ ] 같은 폴더 single-flight, generation 병합, 다른 폴더 병렬 처리
- [ ] 8폴더·40,000항목·24 MiB 가중 LRU와 저메모리 처리
- [ ] 로컬 2개·원격 1개 분석 제한과 취소 처리
- [ ] 5,000개보다 작은 메모리 캐시 상한 때문에 전부 다시 읽는 현상이 없는지 테스트

### 4단계. 모드·정렬별 로드 정책

- [ ] `loadFileItem()`에서 무조건 실행되는 촬영 시각 판독 제거
- [ ] 일반 목록·바둑판은 디스크 캐시와 원본 미디어를 모두 열지 않음
- [ ] 미디어 모드와 `By.MEDIA_CREATED`만 Repository 사용
- [ ] `FileListLoadRequest`와 generation이 일치하는 결과만 게시
- [ ] requested/rendered 모드를 분리하고 완성 결과에서 목록과 함께 교체
- [ ] 모드 전환, 준비 중 반대 전환, 정렬 변경, 뒤로 가기에서 스크롤 상태 유지
- [ ] 검색 중에는 보기 모드와 무관하게 촬영 시각·폴더 캐시에 접근하지 않음
- [ ] 완성 목록을 먼저 게시하고 동일 snapshot의 저장은 비동기로 수행

### 5단계. 실제 데이터 검증

- [ ] JPEG(EXIF 있음·없음), HEIC, PNG, MP4, MOV, MKV, WebM 혼합 폴더
- [ ] 5,000개 일반 목록이 기존 MaterialFiles 수준으로 열림
- [ ] 차가운 캐시의 미디어 모드는 로딩 후 한 번에 정확한 순서로 나타남
- [ ] 같은 폴더 재진입과 앱 재시작 후 원본 미디어 판독이 0회임
- [ ] 같은 폴더 재진입에서 캐시 본문 쓰기가 0회임
- [ ] 파일 하나 추가·수정·삭제 시 그 항목만 처리됨
- [ ] 서로 다른 SMB/SFTP/WebDAV 계정의 같은 경로가 섞이지 않음
- [ ] size/mtime이 불명확한 SAF/WebDAV 항목이 영구 오적중하지 않음
- [ ] 캐시 파일 손상·저장 공간 부족에서도 앱이 멈추거나 목록을 잃지 않음
- [ ] 앱 데이터 삭제 후 첫 실행부터 정상적으로 캐시를 다시 만듦

## 6. 단위 테스트 목록

| 대상 | 반드시 확인할 것 |
|---|---|
| 폴더 해시 | 서버·포트·계정·Document tree가 다르면 분리, 자격 증명·경로 원문 미노출 |
| 코덱 | 5,000개 왕복, 값 없음, 유니코드 이름, CRC·잘림·상한·버전 거부 |
| 유효성 | 이름·크기·mtime·강한 ID 변경, unknown validator의 영구 hit 금지 |
| Repository | warm hit에서 판독 0회, 한 항목 변경 시 1회, 실패는 비저장 |
| 동시성 | single-flight, generation 병합, 오래 끝난 작업이 최신 캐시를 덮지 않음 |
| 취소 | 화면·디스크 미게시, 중단 예외 비저장, 기존 정상 캐시 보존 |
| 정리 | 64 MiB/512개 초과 시 오래된 것부터 48 MiB/384개까지 정리 |
| 메모리 | 8폴더·40,000항목·24 MiB 동시 준수, 초대형 한 폴더 비보존 |
| 로드 정책 | LIST/GRID 이름순과 검색은 접근 0회, MEDIA/촬영 시각순은 1회 |
| 화면 결과 | 잘못된 목록 프레임 0회, 완성 목록 1회, 최신 이동·복원 1회 |

코덱과 Repository는 일반 JVM 단위 테스트로 검증한다. 정책 결합과 generation coordinator도 Android
비의존 클래스로 분리해 결정적으로 테스트한다. `InstantTaskExecutorRule` 기반 ViewModel/LiveData
통합 테스트에서 observer 순서를 검증하고, Robolectric 또는 기기 UI 테스트에서 LIST→MEDIA,
GRID→MEDIA, 이름순→촬영 시각순, 준비 중 반대 전환, 회전·뒤로 가기의 실제 render sequence와
스크롤 위치를 확인한다.

## 7. 성능 측정 기준

시간만 재면 운영체제 파일 캐시의 영향을 구분하기 어려우므로 호출 수도 함께 기록한다.

| 시나리오 | 기대 결과 |
|---|---|
| 5,000개 일반 목록 첫 진입 | `MediaCreatedTime.read()` 0회, 캐시 파일 read 0회 |
| 미디어 모드 첫 진입 | 지원 미디어의 cache miss만 판독, 완료 시간 별도 기록 |
| 같은 폴더 재진입 | 원본 판독 0회, 캐시 read 최대 1회, 본문 write 0회 |
| 앱 재시작 후 재진입 | 원본 판독 0회, 캐시 read 1회, 본문 write 0회 |
| 파일 하나 수정 후 재진입 | 원본 미디어 판독 1회 |
| MEDIA 준비 중 LIST 전환 | 250ms 안에 취소 관찰, 늦은 화면·캐시 게시 0회 |

임시 계측 로그는 검증 후 제거한다. 에뮬레이터 결과는 참고만 하고 최종 판단은 Fold 7의 UFS
저장소에서 한다.

## 8. 완료 조건

- 일반 목록 모드에서 촬영 시각 판독과 캐시 접근이 발생하지 않는다.
- 검색 중에도 촬영 시각 판독과 캐시 접근이 발생하지 않는다.
- 5,000개 폴더를 다시 열거나 앱을 다시 실행해도 저장된 항목을 재분석하지 않는다.
- 미디어 모드 목록은 잘못된 임시 순서를 노출하지 않고 요청 세대당 한 번만 나타난다.
- 값이 없는 미디어도 반복 분석하지 않으며, 일시적인 읽기 실패는 나중에 다시 시도한다.
- provider가 신뢰할 validator를 주지 않으면 오래된 값을 영구 적중시키지 않는다.
- 캐시 저장 실패·지연이 완성 목록 표시를 막지 않는다.
- 캐시 총량이 정한 상한을 지키고 손상된 캐시를 자동 복구한다.
- 전체 단위 테스트와 실기기 수용 테스트가 통과한다.

## 9. 구현 전에 다시 확인할 한 가지

이 계획의 “폴더별 파일”은 **앱 전용 저장소에 폴더마다 하나씩**이라는 뜻이다. 원본 사진 폴더
안에 `.photoexplorer-cache` 같은 파일을 두는 요구라면 §3.1부터 다시 검토해야 한다. 그 방식은
원본 폴더 쓰기 권한, 감시 이벤트 반복, 백업·동기화 노출 문제를 별도로 해결해야 한다.
