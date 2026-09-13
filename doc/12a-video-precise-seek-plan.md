# 12a. 동영상 정밀 이동 구현 계획서

[11b 동영상 정밀 이동 기획서](11b-video-precise-seek-spec.md)를 현재 미디어 뷰어 위에
어떻게 구현하고 검증할 것인가.

- 작성일: 2026-09-13
- 프로젝트: **PhotoExplorer** (`natae77/PhotoExplorer`, `zhanghai/MaterialFiles` fork)
- 브랜치: `feature/video-precise-seek` — 기준 커밋 `4b610a3c`
- 전제: 11b 기획서 2차의 D21~D29가 확정된 상태
- 상태: **구현 및 Pixel 8 AVD 검증 완료 — 실기기 항목 남음, 미커밋 워킹트리**

> ⚠️ **커밋과 스테이징은 하지 않는다.** 단계별 커밋 메시지도 만들지 않는다.
> 모든 변경은 사용자가 검토할 수 있도록 워킹트리에 그대로 둔다.

## 0. 구현 원칙

1. **실제 PTS가 기준이다.** 평균 fps와 `1 / fps` 계산은 프레임 이동에 사용하지 않는다.
2. **재생을 먼저 살린다.** PTS 준비는 비동기이며 실패해도 재생·슬라이더·1초 이동은 유지한다.
3. **한 번 고른 뒤에는 인덱스로 이동한다.** 플레이어의 밀리초 위치를 매 클릭마다 다시
   해석하지 않는다.
4. **Media3 기본 탐색 버튼과 분리한다.** 10초용 id와 자동 텍스트 갱신에 기대지 않고,
   앱이 정밀 이동 버튼의 동작·표시·활성 상태를 소유한다.
5. **터치 영역은 실제 접근성 노드인 세 셀이다.** 단순 TouchDelegate에 기대지 않는다.
6. **순수 계산을 먼저 테스트한다.** Android 디코더가 없어도 PTS 이웃 선택과 상태 전환을
   단위 테스트할 수 있게 분리한다.
7. **단계가 끝날 때마다 빌드 가능해야 한다.** 마지막에는 실제 에뮬레이터 동영상으로 확인한다.

## 1. 현재 구조와 바꿀 경계

### 1.1 현재 구조

| 자리 | 현재 책임 |
|---|---|
| `VideoPlayerHolder` | ExoPlayer 하나의 생성·부착·정지·해제, 10초 탐색 간격 |
| `MediaViewerFragment` | 현재 페이지와 플레이어 연결, 컨트롤·메뉴·스크럽 이벤트 |
| `MediaViewerViewModel` | 영상별 위치, 세션 배속, 세부 정보 캐시 |
| `media_viewer_player_control.xml` | Media3 id를 가진 32dp 버튼을 52dp 원형 래퍼 안에 배치 |
| `media_viewer.xml` | 재생 속도, 세부 정보, 삭제, 공유 |

Media3의 `PlayerControlView`는 `exo_rew_with_amount`와 `exo_ffwd_with_amount`를 찾으면
자체 클릭 리스너를 붙이고 플레이어의 탐색 간격을 숫자로 다시 표시한다. 프레임 모드의 `1`과
실제 PTS 이동은 이 계약과 다르므로 두 버튼만 앱 전용 id로 분리한다. 재생/일시정지는 기존
`exo_play_pause` id와 Media3 동작을 유지한다.

### 1.2 새 책임의 배치

| 책임 | 둘 곳 |
|---|---|
| PTS 목록 읽기 | 신규 `VideoFrameTimelineReader.kt` |
| 로딩·캐시·취소 상태 | 신규 `VideoFrameTimelineLoader.kt`, `MediaViewerViewModel` 소유 |
| 이전/다음 PTS 계산 | 신규 `VideoFrameTimeline.kt`의 순수 함수 |
| 프레임 커서·정확 탐색 | `VideoPlayerHolder` |
| 메뉴·버튼 표시·활성 상태 | `MediaViewerFragment` |
| 세 개의 비중첩 확장 터치 영역 | 신규 `PrimaryMediaControlButton.kt` |

## 2. 만들 파일과 고칠 파일

### 2.1 신규

| 파일 | 책임 |
|---|---|
| `viewer/media/VideoSeekUnit.kt` | `FRAME`, `SECOND` 두 세션 선택값 |
| `viewer/media/VideoFrameTimeline.kt` | 정렬·중복 제거된 PTS와 이웃 인덱스 계산 |
| `viewer/media/VideoFrameTimelineReader.kt` | 로컬/SAF 영상 트랙에서 PTS를 읽음 |
| `viewer/media/VideoFrameTimelineLoader.kt` | 현재 영상의 비동기 로딩, 중복 요청 합치기, 취소, 세션 캐시 |
| `viewer/media/PrimaryMediaControlButton.kt` | 68dp × 최소 64dp 셀 자체가 클릭·포커스·접근성 Button 노드가 됨 |
| `src/test/.../VideoFrameTimelineTest.kt` | CFR·VFR·경계·정확 일치·중복 PTS 계산 테스트 |
| `src/test/.../VideoFrameTimelineLoaderTest.kt` | 성공·실패·중복 요청·취소·캐시 상태 테스트 |

### 2.2 수정

| 파일 | 변경 |
|---|---|
| `MediaViewerViewModel.kt` | 기본 `FRAME` 선택값과 타임라인 로더를 세션 상태로 보관 |
| `VideoPlayerHolder.kt` | 10초 간격 제거, 프레임 커서, `EXACT` 프레임 탐색과 1초 탐색 |
| `MediaViewerFragment.kt` | 로딩 요청, 메뉴, 버튼 동작·표시·활성 상태, 커서 초기화, 터치 영역 설치 |
| `media_viewer_player_control.xml` | 뒤로/앞으로 앱 전용 id, 터치 셀 부모 id 부여 |
| `media_viewer.xml` | 재생 속도와 세부 정보 사이에 이동 단위 하위 메뉴 추가 |
| `strings.xml`, `values-ko/strings.xml` | 이동 단위·1프레임·1초·접근성 문자열 |
| `doc/11b-video-precise-seek-spec.md` | 구현 뒤 실제와 달라진 점과 검증 결과만 반영 |
| `doc/README.md` | 12a 계획서 링크 추가 |

새 drawable은 만들지 않는다. 현재 화살표, 재생 아이콘, 원형 스크림, 리플을 그대로 쓴다.

## 3. 데이터 모델

### 3.1 이동 단위

```kotlin
enum class VideoSeekUnit {
    FRAME,
    SECOND
}
```

`MediaViewerViewModel.videoSeekUnit`의 초기값은 `FRAME`이다. ViewModel에 두므로 화면 회전과
영상 페이지 전환에는 남고, 뷰어 Activity가 끝나면 사라진다.

### 3.2 타임라인 상태

경로마다 다음 상태 중 하나를 가진다.

```kotlin
sealed interface VideoFrameTimelineState {
    data object NotRequested : VideoFrameTimelineState
    data object Loading : VideoFrameTimelineState
    class Available(val timeline: VideoFrameTimeline) : VideoFrameTimelineState
    data object Unavailable : VideoFrameTimelineState
}
```

- `NotRequested`: 아직 현재 영상이 아니었거나 1초 모드라 읽지 않음
- `Loading`: 백그라운드에서 PTS를 읽는 중
- `Available`: 하나 이상의 PTS를 가진 정렬·중복 제거 타임라인
- `Unavailable`: 지원하지 않는 소스/트랙이거나 읽기 실패

상태는 `LiveData<Map<Path, VideoFrameTimelineState>>` 또는 현재 프로젝트의 관찰 가능한 상태
패턴으로 노출한다. Fragment가 결과를 폴링하지 않고 상태 변경 때 버튼을 갱신해야 한다.

### 3.3 타임라인 표현

PTS는 마이크로초 단위의 `LongArray`로 보관한다.

- `Long` 밀리초로 낮추지 않는다.
- 읽은 뒤 오름차순 정렬한다. 저장/디코딩 순서와 표시 순서가 다른 영상도 PTS 순서로 만든다.
- 같은 PTS는 하나만 남긴다. 한 번의 버튼 입력으로 같은 표시 시각을 반복하지 않는다.
- 박싱된 `MutableList<Long>`을 최종 캐시로 남기지 않는다. 긴 240fps 영상의 메모리 낭비를 피한다.
- 처음부터 primitive growable buffer에 모으고, 배열 안에서 정렬·중복 제거해 복사 피크를 제한한다.
- `SAMPLE_FLAG_PARTIAL_FRAME`, 음수 EOS 이외의 비정상 PTS, 단조화할 수 없는 값이 나오면
  정확한 프레임 목록이라고 가장하지 않고 `Unavailable`로 끝낸다.

## 4. 실제 PTS 읽기

### 4.1 입력 범위

11번 §3의 재생 가능 범위와 똑같이 **로컬 Linux 경로와 SAF 문서 경로만** 대상으로 한다.
원격·아카이브 경로를 새로 지원하지 않는다.

경로는 재생과 같은 `path.fileProviderUri`를 통해 연다. SAF 경로에서는 가능한 경우 원래
문서 URI가 사용되고, 로컬 경로에서는 앱 FileProvider가 파일을 연다.

### 4.2 읽는 값

플랫폼 미디어 추출기로 트랙을 열고 다음 순서로 읽는다.

1. 트랙 목록에서 MIME이 `video/`로 시작하는 영상 트랙을 찾는다. 영상 트랙이 하나가 아니면
   플레이어가 선택한 포맷과 MIME·코덱·해상도·track id를 대조하고, 유일하게 고를 수 없으면
   `Unavailable`로 끝낸다.
2. 영상 트랙만 선택한다.
3. 끝까지 전진하면서 각 샘플의 표시 시각(마이크로초)을 수집한다.
4. 오름차순 정렬하고 중복 PTS를 제거한다.
5. 결과가 비었으면 `Unavailable`, 하나 이상이면 `Available`로 만든다.
6. 성공·실패·취소 어느 경우에도 추출기를 `finally`에서 해제한다.

프레임 데이터 자체는 읽거나 보관하지 않는다. PTS와 다음 샘플로 전진하는 데 필요한 컨테이너
정보만 사용한다.

### 4.3 비동기·취소·캐시

- I/O dispatcher에서 실행한다. 메인 스레드에서 추출기를 열거나 전체 샘플을 훑지 않는다.
- 기본값이 프레임이지만 **플레이어가 `STATE_READY`가 된 뒤** 현재 동영상만 요청한다.
  재생 준비와 같은 소스를 동시에 훑지 않는다.
- 현재 페이지가 사진이거나 이동 단위가 1초면 새 요청을 시작하지 않는다.
- 같은 경로의 동시 요청은 하나로 합친다.
- 페이지를 떠나거나 1초 모드로 바꾸면 미완료 요청을 best-effort로 취소한다. 화면 회전만으로는
  ViewModel 작업을 취소하지 않는다. 반복문은 일정 샘플마다 취소를 확인한다.
- `setDataSource()`나 한 번의 `advance()`는 즉시 취소되지 않을 수 있으므로 요청마다 generation과
  request id를 두고 오래된 완료 결과를 절대 현재 상태에 반영하지 않는다.
- 완료된 타임라인은 **현재 영상 고정 + 최근 사용 LRU, 전체 32MiB 이하**로 캐시한다.
- 단일 타임라인이 **16MiB 또는 2,000,000 PTS** 중 먼저 닿는 상한을 넘으면 부분 결과를 버리고
  `Unavailable`로 끝낸다. 상한은 구현·실측 결과에 따라 낮추는 것은 허용하되 높일 때는 문서를 고친다.
- ViewModel이 정리되면 작업과 캐시도 함께 끝난다.
- loader는 `viewModelScope`만 사용하고 상태 map 변경은 main dispatcher에서 직렬화한다.
- 취소는 `NotRequested`, 읽기/상한 실패는 `Unavailable`이다. `CancellationException`은 다시 던진다.
- 캐시 키는 `Path`만 쓰지 않고 가능한 경우 크기·마지막 수정시각을 포함한 콘텐츠 버전 키로 만든다.
- reader는 application context와 주입 가능한 source opener만 사용한다. Activity/Fragment를 보관하지 않는다.
- extractor와 별도 PFD를 열었다면 스캔이 끝날 때까지 유지하고 모든 경로에서 둘 다 닫는다.

`IOException`, `SecurityException`, `IllegalArgumentException`과 extractor 런타임 실패는 요청 하나의
`Unavailable`로 격리하며 플레이어 상태를 건드리지 않는다.

## 5. PTS 이웃 선택

### 5.1 현재 위치에서 첫 클릭

정렬된 PTS 배열에서 이진 탐색한다.

- 이전: 현재 위치보다 **엄격히 작은** PTS 중 가장 큰 값
- 다음: 현재 위치보다 **엄격히 큰** PTS 중 가장 작은 값
- 현재 위치와 PTS가 같으면 같은 항목을 다시 고르지 않고 인덱스를 ±1 한다.
- 범위를 벗어나면 첫/마지막 인덱스에 머문다.

예:

```text
PTS(us) = [20_000, 52_000, 91_000, 140_000]
현재     = 100_000
이전     = 91_000
다음     = 140_000
```

### 5.2 연속 클릭용 프레임 커서

`VideoPlayerHolder`에 다음 값을 둔다.

- 커서가 속한 경로
- 선택한 PTS 인덱스
- 플레이어에 요청한 밀리초 위치

첫 클릭 뒤에는 플레이어의 위치를 다시 이진 탐색하지 않고 커서 인덱스를 ±1 한다. 다음 경우
커서를 무효화한다.

- 일반 재생이 시작됨
- 슬라이더의 `onScrubStart`
- 1초 이동
- 다른 경로를 재생하거나 플레이어를 detach/release
- 플레이어의 현재 위치가 마지막 요청 위치에서 허용 오차 이상 달라짐

마이크로초 PTS를 Media3 공개 탐색 API의 밀리초로 바꾸는 규칙은 미리 확정하지 않는다.
`EXACT`는 특정 PTS 샘플을 고르는 API가 아니라 허용 오차 0의 시간 탐색이므로, 1단계 기술
스파이크에서 목표 프레임을 실제로 남기는 정수 ms 선택 규칙을 결정한다. 후보는 목표 PTS가
표시되는 구간 안의 정수 ms이며, 그 구간에 표현 가능한 ms가 없으면 해당 프레임을 공개 Player
API로 구별할 수 없는 것으로 처리한다. 커서에는 원본 PTS 인덱스를 남긴다.

## 6. 플레이어 이동

### 6.1 프레임 모드

1. 현재 타임라인이 `Available`인지 확인한다.
2. 재생 중이면 일시정지한다.
3. §5에 따라 대상 인덱스를 구한다.
4. Media3 탐색 파라미터를 `EXACT`로 바꾼다.
5. 대상 PTS를 밀리초로 변환해 이동한다.
6. 경로·인덱스·요청 위치를 프레임 커서에 저장한다.
7. 이 탐색 요청이 접수되면 탐색 파라미터를 `DEFAULT`로 복원한다. 최소한 스크럽 시작,
   새 media item 설정, 위치 복원, detach에서도 DEFAULT를 보장한다.

`EXACT`라도 압축 영상은 앞선 키프레임부터 디코딩할 수 있다. 프레임 커서는
`path + index + generation + pending requested position`으로 두고, 자기 탐색의 position
discontinuity만 승인한다. 다른 seek, `onPlayWhenReadyChanged(true)`, 스크럽, media item 변경은
커서를 지운다. 빠른 연타는 마지막 generation만 유효하다.

### 6.2 1초 모드

- 뒤로는 `currentPosition - 1_000ms`, 앞으로는 `currentPosition + 1_000ms`다.
- 0과 영상 길이 사이로 제한한다.
- 탐색 파라미터는 기본값으로 돌린다.
- 재생/일시정지 상태를 바꾸지 않는다.
- 프레임 커서를 지운다.
- 하드웨어 미디어 키의 되감기/빨리감기 증가량도 1초로 맞춘다.

## 7. 메뉴와 세션 상태

`media_viewer.xml`에서 항목 순서를 다음처럼 둔다.

| order | 항목 |
|---:|---|
| 50 | 재생 속도 |
| 55 | 이동 단위 |
| 60 | 세부 정보 |
| 100 | 삭제, 공유 |

`이동 단위` 하위 그룹은 `checkableBehavior="single"`이고 항목은 `1프레임`, `1초`다.

- 동영상 페이지에서만 부모 항목을 보인다.
- `onPrepareOptionsMenu()`에서 ViewModel 값과 맞는 한 항목에만 `isChecked = true`를 준다.
  기존 배속 메뉴에서 확인한 AppCompat 단일 선택 동작 때문에 다른 항목에 `false`를 반복 설정하지 않는다.
- 선택 즉시 ViewModel을 갱신하고 버튼 텍스트·설명·활성 상태를 갱신한다.
- `FRAME` 선택 시 현재 영상 타임라인이 `NotRequested`면 로딩을 요청한다.

## 8. 버튼 표시와 Media3 기본 동작 분리

### 8.1 뒤로/앞으로

XML id를 Media3의 `exo_rew_with_amount`, `exo_ffwd_with_amount`에서 앱 전용 id로 바꾼다.
그러면 PlayerControlView가 10초 탐색 리스너와 숫자 갱신을 붙이지 않는다.

Fragment가 두 버튼에 다음을 직접 설정한다.

| 단위 | 텍스트 | 설명 |
|---|---|---|
| 프레임 | `1` | 1프레임 뒤로/앞으로 이동 |
| 초 | `1` | 1초 뒤로/앞으로 이동 |

프레임 모드에서는 `Available`일 때만 동작 가능 표시를 한다. `Loading`·`Unavailable`에서는
시각적으로 비활성화하되 §9의 터치 셀이 이벤트를 소비한다. 1초 모드에서는 플레이어가 붙어 있고
해당 탐색 명령이 가능할 때 활성화한다.

### 8.2 재생/일시정지

`exo_play_pause` id와 Media3 리스너를 유지한다. 프레임 탐색 뒤 재생 버튼을 누르면 Media3의
기존 동작으로 그 위치부터 재생한다. 재생 시작 이벤트에서 프레임 커서를 무효화한다.

## 9. 68dp × 최소 64dp 비중첩 터치 셀

### 9.1 레이아웃

보이는 52dp 원형 스크림과 32dp 아이콘/버튼은 그대로 둔다. 세 버튼이 놓인 행에는 식별 id를
추가하고 높이를 최소 64dp로 확보한다. 현재 버튼 중심 간격은 68dp다.

### 9.2 실제 셀을 접근성 버튼으로 사용

TouchDelegate만으로는 TalkBack 탐색 경계와 키보드/D-pad 포커스 경계까지 넓어진다고 보장할 수
없다. 따라서 신규 `PrimaryMediaControlButton` 셋이 실제 68dp × 최소 64dp 셀이자
클릭·포커스·접근성 노드가 된다.

```text
|<---- 68dp ---->|<---- 68dp ---->|<---- 68dp ---->|
|      뒤로      |    재생/정지    |      앞으로     |
```

- XML에서 세 셀을 연속 형제로 배치하므로 hit rect가 구조적으로 겹치지 않는다.
- 각 셀 안의 52dp 스크림·32dp 아이콘/텍스트는 장식/표시 역할만 하고 중복 접근성 노드가 되지 않는다.
- 셀의 접근성 class는 Button이고, content description·enabled·pressed 상태가 실제 동작과 일치한다.
- 가운데 셀은 Media3 play/pause 동작을 위임하되 아이콘과 설명을 Media3 상태에 맞춰 동기화한다.
- 활성 셀은 셀 전체 탭에서 같은 리플과 한 번의 click만 만든다.
- 비활성 프레임 셀은 클릭·리플 없이 이벤트만 소비하고 TalkBack에는 disabled로 노출한다.
- `ViewConfiguration.scaledTouchSlop` 밖으로 나가면 click을 취소한다. `UP/CANCEL`, detach,
  visibility 변경에서도 pressed 상태와 대상 gesture를 정리한다.
- RTL에서도 화면 왼쪽/오른쪽 셀과 시간상 뒤로/앞으로 의미가 뒤바뀌지 않는지 명시적으로 확인한다.
- 새 `1` 표시는 기존 스타일과 크기·배치를 유지하고 큰 글꼴에서도 잘리지 않게 한다.

## 10. 구현 단계

### 0단계 — 기준 상태와 문서 연결

- [ ] 워킹트리에 문서 외 코드 변경이 없는지 확인한다.
- [ ] 현재 `main` 기준 debug 빌드와 단위 테스트를 한 번 실행한다.
- [ ] `doc/README.md`에 12a 링크를 추가한다.

검증: `:app:assembleDebug :app:testDebugUnitTest`.

### 1단계 — 결정론적 fixture와 Media3 기술 스파이크 (구현 게이트)

- [ ] 각 프레임에 고유 번호와 PTS를 화면에 새긴 작은 CFR·VFR·B-frame 영상을 준비한다.
- [ ] 기대 PTS manifest를 함께 만든다. non-zero first PTS와 가능하면 edit-list fixture도 둔다.
- [ ] platform extractor가 읽은 PTS, Media3 currentPosition 원점, 화면에 남은 frame 번호를 대조한다.
- [ ] 목표 PTS가 표시되는 정수 밀리초 선택 규칙을 확정한다.
- [ ] `pause + EXACT seek + DEFAULT 복원`으로 이전/다음 프레임을 반복 없이 표시하는지 확인한다.

**게이트:** CFR·VFR·B-frame fixture에서 목표 프레임을 안정적으로 표시하지 못하거나 extractor와
Media3 시간축 offset을 보정할 수 없으면 이후 단계를 구현하지 않는다. MediaCodec/별도 프레임
표시 방식으로 계획을 다시 써야 한다.

### 2단계 — PTS 계산 모델과 단위 테스트

- [ ] `VideoSeekUnit`, `VideoFrameTimeline`을 만든다.
- [ ] 정렬·중복 제거와 이진 탐색을 구현한다.
- [ ] CFR, VFR, 첫 PTS 비0, 정확 일치, 시작/끝, 중복 PTS 테스트를 작성한다.
- [ ] 연속 인덱스 ±1 테스트를 작성한다.

화면 변화 없음. JVM 테스트만 통과시키고 다음 단계로 간다.

### 3단계 — PTS reader와 비동기 loader

- [ ] reader가 로컬/SAF URI의 영상 트랙 PTS를 읽게 한다.
- [ ] 모든 종료 경로에서 자원을 해제하고 취소를 주기적으로 확인한다.
- [ ] loader에 `NotRequested/Loading/Available/Unavailable` 상태와 경로별 캐시를 넣는다.
- [ ] 중복 요청 합치기, cancel→NotRequested, 늦은 완료 무시, A→B→A generation, 회전 유지,
  1초 전환 취소, 성공, 실패, 자원 해제 테스트를 작성한다.
- [ ] 16MiB/2,000,000 PTS 단일 상한과 32MiB LRU eviction 테스트를 작성한다.
- [ ] ViewModel이 loader와 기본 `FRAME` 값을 소유하게 한다.

검증: 빌드·JVM 테스트. 아직 버튼에는 연결하지 않는다.

### 4단계 — 메뉴와 버튼 외형

- [ ] `⋮`에서 재생 속도 바로 다음에 이동 단위를 넣는다.
- [ ] 기본 체크가 1프레임인지 확인한다.
- [ ] 뒤로/앞으로를 앱 전용 id로 바꾸고 두 모드 모두 `1` 표시를 연결한다.
- [ ] 영어·한국어 문자열과 content description을 추가한다.
- [ ] 사진 페이지에서는 메뉴를 숨긴다.

검증: 에뮬레이터에서 메뉴 순서·체크·표시 전환을 먼저 확인한다.

### 5단계 — 실제 프레임/1초 탐색

- [ ] 현재 동영상 진입 시 프레임 타임라인 로딩을 시작한다.
- [ ] 로딩 상태에 따라 프레임 버튼 활성 표시를 갱신한다.
- [ ] `VideoPlayerHolder`에 첫 PTS 이웃 탐색과 연속 프레임 커서를 연결한다.
- [ ] 프레임 클릭은 일시정지 + EXACT 탐색, 1초 클릭은 상태 유지 + ±1초로 연결한다.
- [ ] 재생·스크럽·페이지 변경·detach/release에서 커서를 초기화한다.

검증: 계산 테스트와 에뮬레이터 실제 동영상 프레임 변화를 함께 확인한다.

### 6단계 — 세 주요 버튼 터치 영역

- [ ] 세 버튼 행의 세로 공간과 id를 마련한다.
- [ ] 셀 자체를 68dp × 최소 64dp clickable/focusable Button 접근성 노드로 만든다.
- [ ] 내부 표시 뷰의 중복 접근성 포커스를 제거한다.
- [ ] 한 gesture가 다른 버튼으로 넘어가지 않고 touch slop 이탈 시 취소되게 한다.
- [ ] 비활성 프레임 셀도 탭을 소비하게 한다.
- [ ] 리플과 실제 버튼 click은 기존과 같이 보이게 한다.

검증: 경계 좌우를 반복 탭해 옆 버튼 오작동과 컨트롤 숨김이 없는지 확인한다.

### 7단계 — 회귀·성능·문서 결과

- [ ] 전체 debug 빌드와 단위 테스트를 실행한다.
- [ ] 에뮬레이터에서 §11 체크리스트를 실행한다.
- [ ] 실패·계획 변경이 있으면 이 계획서 상단에 “구현하면서 달라진 곳” 표를 추가한다.
- [ ] 11b §11 실측 결과와 상태를 갱신한다.
- [ ] 코드·문서 diff와 워킹트리 상태를 확인한다.

## 11. 에뮬레이터 검증 체크리스트

테스트 장치: Pixel 8 AVD. 임의의 `DCIM/Test` 영상뿐 아니라 1단계의 프레임 번호/PTS fixture를
사용한다. 기대 PTS와 화면 frame 번호가 없는 육안 비교만으로 정확성 통과를 판정하지 않는다.

### 11.1 메뉴·기본값

- [ ] 새 뷰어에서 기존 모양의 버튼 안에 `1`이 있고 `이동 단위 → 1프레임`에 체크가 있다.
- [ ] 메뉴 순서가 재생 속도 → 이동 단위 → 세부 정보다.
- [ ] 1초를 고르면 두 버튼이 `1`로 바뀌고 체크가 이동한다.
- [ ] 화면 회전과 다른 동영상 이동 뒤 선택값이 유지된다.
- [ ] 뷰어를 닫고 다시 열면 1프레임으로 돌아온다.

### 11.2 프레임 이동

- [ ] 재생 중 프레임 버튼을 누르면 일시정지하고 한 프레임만 이동한다.
- [ ] 앞으로 10번, 뒤로 10번 누르면 같은 화면으로 돌아온다.
- [ ] 슬라이더로 임의 위치로 간 뒤 이전/다음이 서로 다른 실제 이웃 프레임을 보인다.
- [ ] 영상 시작의 이전과 마지막의 다음에서 범위를 넘지 않는다.
- [ ] MP4와 MOV 모두 프레임 버튼이 준비 완료 후 활성화된다.
- [ ] 빠르게 연타해도 마지막으로 누른 방향과 횟수에 맞는 프레임이 남는다.
- [ ] extractor PTS와 화면에 새긴 frame 번호가 manifest의 기대값과 일치한다.

### 11.3 1초 이동

- [ ] 일시정지 상태에서 ±1초 뒤에도 일시정지다.
- [ ] 재생 상태에서 ±1초 뒤에도 재생한다.
- [ ] 시작/끝에서 범위를 넘지 않는다.

### 11.4 터치 영역

- [ ] 각 원의 상·하·좌·우 가장자리 바깥 투명 영역에서도 해당 버튼이 동작한다.
- [ ] 뒤로 셀의 오른쪽 경계에서 재생/일시정지가 반응하지 않는다.
- [ ] 앞으로 셀의 왼쪽 경계에서 재생/일시정지가 반응하지 않는다.
- [ ] 세 셀 사이를 탭해도 컨트롤이 사라지지 않는다.
- [ ] 프레임 로딩 중 비활성 셀을 탭해도 컨트롤이 사라지지 않는다.
- [ ] 세 셀 밖 영상 바탕 탭은 기존처럼 컨트롤을 숨긴다.
- [ ] TalkBack 탐색 bounds, D-pad 포커스, disabled 안내가 68×64dp 셀과 일치한다.
- [ ] 세로/가로, RTL, 큰 글꼴에서 셀 경계와 `1` 표시가 깨지지 않는다.

### 11.5 회귀와 오류

- [ ] 재생/일시정지, 슬라이더, 배속, 세부 정보, 공유, 삭제가 그대로다.
- [ ] 사진 페이지에서는 정밀 이동 메뉴와 재생 컨트롤이 보이지 않는다.
- [ ] 사진↔동영상 좌우 이동과 아래로 닫기가 그대로다.
- [ ] PTS 읽기 실패를 만들어도 영상 재생과 1초 이동은 가능하다.
- [ ] 로딩 중 페이지를 빠르게 넘기거나 뷰어를 닫아도 크래시·누수가 없다.
- [ ] 긴 240fps fixture/영상에서 PTS 개수·로딩 시간·heap 증가량을 기록하고 상한이 지켜진다.

## 12. 완료 조건

1. [11b 수용 기준](11b-video-precise-seek-spec.md#12-수용-기준) 20개가 코드 또는
   에뮬레이터 검증으로 확인된다.
2. `:app:assembleDebug`와 `:app:testDebugUnitTest`가 모두 통과한다.
3. PTS 기반 계산은 CFR·VFR·비0 시작·경계·연속 이동 단위 테스트를 가진다.
4. 타임코드 fixture와 실제 MP4/MOV에서 프레임 이동과 1초 이동을 확인한다.
5. 세 확장 터치 셀이 겹치지 않고, 버튼 탭이 영상 바탕 탭으로 새지 않는다.
6. 구현 결과와 남은 실기기 항목이 문서에 기록된다.
7. 커밋·스테이징·푸시 없이 워킹트리에 변경 사항이 남아 있다.

## 13. 구현 결과 (2026-09-13)

### 13.1 구현하면서 달라진 곳

| 계획 | 실제 구현 | 이유 |
|---|---|---|
| loader를 별도 파일로 둔다 | 비동기 요청·generation·32MiB LRU를 `MediaViewerViewModel`에 모았다 | 현재 뷰어에는 동시 활성 타임라인이 하나뿐이라 상태 소유자와 수명 주기를 한곳에서 확인하기 쉽다 |
| 프레임 커서를 `VideoPlayerHolder`에 둔다 | 세션 커서는 ViewModel, 탐색 명령은 Fragment가 맡는다 | 회전에는 유지하면서 player release에는 종속되지 않는 선택 상태이기 때문이다 |
| Media3 뒤로/앞으로 id를 없앤다 | 내부 32dp 표시 뷰에는 id를 유지하고, 68×64dp 바깥 셀이 클릭을 전부 가로챈다 | PlayerControlView의 기존 아이콘 갱신은 살리되 앱 동작과 접근성 노드는 완전히 분리한다 |

### 13.2 검증 결과

- `:app:assembleDebug`와 `:app:testDebugUnitTest` 통과: 총 11개 테스트, 실패 0개.
- 생성 스크립트 `tools/video-seek-fixtures/generate.ps1`로 CFR+B-frame 및
  VFR+B-frame MP4를 재현할 수 있다.
- VFR fixture의 demux PTS는 `0, 40, 120, 200, 450, 500, 850ms`로 확인했다.
  Pixel 8 AVD에서 끝 위치부터 이전 셀을 연속 탭했을 때 화면 frame id가
  `7 → 6 → 5`, 즉 실제 PTS `850 → 500 → 450ms` 순서로 이동했다. 350ms 다음 50ms라는
  서로 다른 간격을 그대로 따른 결과이므로 평균 fps 이동이 아니다.
- 메뉴는 `Playback speed → Move unit → Details` 순서이고 새 세션의 `Frame` 라디오가
  기본 선택됨을 화면에서 확인했다. 이후 표시 요구 변경에 따라 두 모드 모두 기존 버튼
  모양과 가운데 숫자 `1`을 사용한다.
- 초 모드에서 일시정지 상태를 유지한 채 약 1초 전으로 이동해 `frame 3`이 남는 것을
  확인했다.
- 에뮬레이터의 59MB 실제 MOV에서 음수 PTS를 발견했다. `-1`만 extractor EOF로
  판정하고 나머지 음수 PTS는 정렬·원점 보정하도록 고친 뒤 이전/다음 프레임 셀이 모두
  활성화됨을 확인했다.
- mdpi 환산 2.625 배율 AVD의 세 접근성 노드 경계는 각각 179×168px
  (약 68×64dp)이며 `x=271..450`, `450..629`, `629..808`로 빈틈·겹침이 없다.
  52dp 원 바깥이면서 셀 안인 좌표를 탭해 해당 이동/재생만 실행되고 컨트롤이 유지됨을
  확인했다.
- 로컬 경로의 속성 조회가 root provider에 의존하는 문제를 에뮬레이터에서 발견해
  `java.io.File`의 size/mtime을 사용하도록 보정했다. SAF 경로는 기존 NIO attributes를 쓴다.
- 사진 페이지에서는 재생 속도·이동 단위·세부 정보와 세 재생 셀이 숨고 기존
  `Delete`, `Share` 메뉴만 남는 것을 확인했다.

고프레임레이트 실파일, 제조사별 디코더, 실제 SAF 공급자와 TalkBack 낭독은 AVD만으로
동등하게 보장할 수 없으므로 11b §11의 실기기 확인 항목으로 남긴다.

## 부록 A. 세 에이전트 리뷰 취합 (2026-09-13)

| 관점 | 핵심 지적 | 반영 |
|---|---|---|
| PTS / Media3 | `EXACT`는 특정 sample 선택 API가 아니며 us→ms 올림은 목표 프레임을 건너뛸 수 있음 | 결정론적 fixture 기술 스파이크를 1단계 게이트로 추가. 변환 규칙은 실측 뒤 확정 |
| PTS / Media3 | EXACT가 전역 상태로 남으면 슬라이더·위치 복원도 바뀜 | 자기 frame seek 직후 DEFAULT 복원, 스크럽·media item·detach 방어 추가 |
| 상태 / 성능 | 무제한 PTS 캐시와 박싱 수집은 긴 240fps 영상에서 OOM 위험 | primitive buffer, 단일 16MiB/2M PTS, 전체 32MiB LRU 명시 |
| 상태 / 성능 | 취소 뒤 늦은 완료와 A→B→A가 새 상태를 덮을 수 있음 | viewModelScope, main 직렬화, generation/request id, cancellation 재throw 명시 |
| 상태 / 성능 | 재생 준비와 PTS 스캔이 같은 소스를 경쟁함 | 현재 영상이 STATE_READY가 된 뒤 단일 reader 시작 |
| UI / 접근성 | TouchDelegate만으로 TalkBack·D-pad 영역 확대를 보장할 수 없음 | 68×64dp 셀 자체를 Button 접근성 노드로 변경 |
| UI / 테스트 | 육안과 모호한 경계 탭으로는 정확성을 판정할 수 없음 | frame 번호/PTS fixture, 수치 대조, RTL·큰 글꼴·disabled 검증 추가 |
