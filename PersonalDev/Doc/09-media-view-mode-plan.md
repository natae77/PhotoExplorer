# 09. 미디어 보기 모드 구현 계획서

[08번 기획서](08-photo-view-mode-spec.md)를 어떻게 만들 것인가.

- 작성일: 2026-08-25
- 프로젝트: **PhotoExplorer** (`natae77/PhotoExplorer`, `zhanghai/MaterialFiles` fork)
- 기준 커밋: `fc12500` + 로컬 수정(dav4jvm 해시 고정, 앱 식별자 변경, Firebase 제거)
- 전제: 기획서의 D1~D15가 모두 확정된 상태

## 0. 원칙

- **단계마다 빌드되고 검증 가능해야 한다.** 각 Phase 끝에 앱을 설치해 눈으로 확인할 수 있다.
- **UI 없는 것부터 만든다.** 데이터 계층(생성 시각)이 먼저 서야 정렬을 붙이고, 정렬이 서야 보기 모드가 의미 있다.
- **되돌리기 쉽게 쪼갠다.** 특히 1단계은 실기기 성능에 따라 설계가 바뀔 수 있다(§7-1번).

## 1. 만드는 순서

**"단계"는 작업 순서다.** 아래로 갈수록 앞 단계 위에 얹힌다.

| 단계 | 만드는 것 | 화면에서 보이는 것 |
|---|---|---|
| 0 | 준비 (브랜치) | (없음) |
| 1 | **사진에서 찍힌 날짜 꺼내오기** | (없음 — 다른 기능의 재료) |
| 2 | **찍힌 날짜순으로 정렬하기** | 정렬 메뉴에 "미디어 생성 시각"이 생김 |
| 3 | **미디어 보기 모드** | 정사각형 격자로 사진이 보임 |
| 4 | **최신 사진부터 보기** | 폴더를 열면 맨 아래(최신)에서 시작 |
| 5 | **폴더마다 보기 모드 기억하기** | 사진 폴더는 미디어, 문서 폴더는 목록 |
| 6 | 검증 · 실제 폰에서 측정 | — |

1단계만 화면에 아무것도 안 보인다. 사진 파일 안에서 찍힌 날짜를 꺼내오는 코드인데,
2단계(정렬)와 3단계(미디어 모드)가 그 값을 쓰기 때문에 먼저 만든다.

2단계까지만 해도 쓸 만하다 — 평소 목록 화면에서 찍힌 날짜순 정렬이 된다.
3단계부터가 새로운 화면이다.

---

## 0단계. 준비

### 0.1 브랜치

`master`에서 작업 브랜치를 딴다. upstream(`zhanghai/MaterialFiles`)과 충돌 가능성이 있는
파일이 많으므로(특히 `FileListFragment.kt`) 브랜치를 분리한다.

### 0.2 앱 식별자 — 이미 해결됨

이 프로젝트는 **PhotoExplorer**로 분리되면서 앱 식별자가 `com.natae.photoexplorer`로 바뀌었다.
정품 Material Files(`me.zhanghai.android.files`)와 **패키지명이 다르므로 그냥 나란히 깔린다.**

따라서 [03번 문서](03-debug-variant-side-by-side.md)의 `.debug` 접미사 트릭은 **더 이상 필요 없다.**
그 문서는 히스토리로만 남겨둔다.

| 항목 | 값 |
|---|---|
| 앱 식별자 | `com.natae.photoexplorer` |
| 코드 패키지(namespace) | `me.zhanghai.android.files` (그대로 — 소스 전체를 옮기지 않으려고) |
| 앱 이름 | PhotoExplorer |
| Firebase | 제거 (Crashlytics·Analytics 모두) |

---

## 1단계. 미디어 생성 시각 조회 + 캐시

UI에 아무 변화가 없다. 데이터 계층만 만든다.

### 1.1 MP4 박스 파서 (신규)

**파일**: `app/src/main/java/me/zhanghai/android/files/file/Mp4CreationTime.kt` (가칭)

박스 체인을 따라가 `moov` → `mvhd`의 `creation_time`을 읽는다.
[08번 §5.7](08-photo-view-mode-spec.md) 실측대로 파일당 약 300바이트만 읽는다.

```
읽기 절차
1. offset 0에서 16바이트 → size(4) + type(4) [+ largesize(8) if size==1]
2. type != 'moov' 이면 offset += size, 1로 돌아감
3. 'moov' 도달 → 그 안에서 'mvhd' 찾기 (앞 8KB 이내)
4. mvhd: version(1) + flags(3) + creation_time(version==0 ? 4 : 8 바이트)
5. 1904-01-01 UTC 기준 경과 초 → Instant
```

**반드시 처리할 것 (견고성)**

| 상황 | 처리 |
|---|---|
| `size == 0` (마지막까지 뻗는 박스) | 중단, null 반환 |
| `size < 8` | 손상으로 보고 중단 (무한 루프 방지) |
| `offset`이 파일 크기를 넘음 | 중단 |
| 박스 반복 횟수 상한 | 예: 32회 초과 시 중단 |
| `creation_time == 0` | 값 없음으로 취급(null) |
| 1904 기준 → epoch 변환 시 음수/미래 | 무시(null) |

> 이 검증들은 **테스트 데이터로는 안 걸린다**(50개 전부 정상). 손상 파일·비표준 파일이
> 들어와도 앱이 멈추지 않게 하는 방어 코드다.

### 1.2 생성 시각 조회 진입점 (신규)

**파일**: `file/MediaCreatedTime.kt` (가칭)

```
fun readMediaCreatedTime(path, attributes, mimeType): Long?
  ├ mimeType.isImage → ExifInterface.inferDateTimeOriginal()
  │                     (기존: fileproperties/image/ExifInterfaceExtensions.kt:31)
  ├ mimeType.isVideo → Mp4CreationTime 파싱
  │                     실패 시 MediaMetadataRetriever (기존:
  │                     fileproperties/MediaMetadataRetrieverExtensions.kt)
  └ 그 외 → null

최종: min(구한 값, attributes.lastModifiedTime())   ← 기획서 §5.4
```

**제외 조건**

- 원격 경로(`path.isRemotePath`)이고 `READ_REMOTE_FILES_FOR_THUMBNAIL`이 꺼져 있으면 → null
- 압축 파일 내부 경로(`isArchivePath`) → null
- null이면 호출부에서 mtime을 쓴다

> `supportsThumbnail`([FileItemExtensions.kt:59](../../app/src/main/java/me/zhanghai/android/files/filelist/FileItemExtensions.kt#L59))이
> 이미 비슷한 판정을 하고 있으므로, 조건 로직을 참고하거나 일부 재사용한다.

### 1.3 캐시 — **메모리 LRU만, 디스크는 나중에**

기획서에서는 "앱을 껐다 켜도 유지되는 캐시"를 적었지만, **1차 구현은 메모리 LRU만** 만든다.

이유:

- 캐시가 실제로 필요한 상황은 `PathObserver`가 폴더 변경을 감지해 `loadValue()`가
  **같은 프로세스 안에서** 반복 호출될 때다. 메모리 캐시로 충분히 막힌다.
- 앱 재시작 후 첫 진입 비용은 §5.7 기준 파일당 300바이트 — 디스크 캐시를 만들 만큼 크지 않다.
- 디스크 캐시는 저장 형식·상한·정리·무효화까지 딸려와 범위가 커진다.
  **실기기 측정 후 필요하다고 판명되면** 그때 붙인다(§7-1번).

```
LruCache<CacheKey, Long?>   (예: 최대 4096항목)
CacheKey = (path, size, lastModifiedTime)   ← 내용이 바뀌면 자동 무효화
```

### 1.4 1단계 검증

앱 UI 변화가 없으므로 로그로 확인한다.

- `/sdcard/SwingTestData`의 50개(현재 20개 복사됨)에 대해 파싱 결과를 로그로 찍는다
- **기대값**([08번 §5.3](08-photo-view-mode-spec.md)):

| 파일 | 기대 결과 |
|---|---|
| `20260819_062806_1.mp4` | 2026-08-18 21:29:48 UTC (메타데이터 채택) |
| `IMG_6429.mov` | 2026-04-11 01:53:20 UTC (mtime 채택 — `min()` 규칙 동작) |
| `IMG_6457.mov` | 2026-04-18 00:46 UTC |

`.mov`들이 전부 08-18로 나오면 **`min()` 규칙이 안 걸린 것**이다.

---

## 2단계. `FileItem` 확장 + 정렬 기준 추가

### 2.1 `FileItem`에 필드 추가

**파일**: [`file/FileItem.kt`](../../app/src/main/java/me/zhanghai/android/files/file/FileItem.kt)

```kotlin
@Parcelize
data class FileItem(
    ...,
    val mimeType: MimeType,
    val mediaCreatedTimeMillis: Long?      // ← 추가 (맨 뒤)
) : Parcelable
```

- 타입을 `Long?`로 두면 **별도 Parceler가 필요 없다**(`Instant`/`FileTime`은 필요).
- `loadFileItem()`에서 채운다. **이미지·동영상일 때만** 호출하므로 다른 파일은 비용 0.
- 심볼릭 링크는 타깃 속성 기준으로 판단한다(기존 구조 그대로).

### 2.2 정렬 기준 추가

**파일**: [`filelist/FileSortOptions.kt`](../../app/src/main/java/me/zhanghai/android/files/filelist/FileSortOptions.kt)

```kotlin
enum class By { NAME, TYPE, SIZE, LAST_MODIFIED, MEDIA_CREATED }   // ← 맨 뒤에 추가
```

⚠️ **반드시 맨 뒤.** `@Parcelize`로 직렬화되어 저장되므로 순서가 바뀌면 기존 사용자의 정렬이 뒤바뀐다.

`createComparator()`에 분기 추가:

```kotlin
By.MEDIA_CREATED ->
    comparator = compareBy<FileItem> {
        it.mediaCreatedTimeMillis ?: it.attributes.lastModifiedTime().toMillis()
    }.then(comparator)
```

- 값이 없으면 mtime 폴백 — 미디어가 아닌 파일도 목록이 깨지지 않는다.
- 폴더 우선 옵션은 기존 로직이 그대로 처리한다(비교자 앞단에서 폴더를 분리).

### 2.3 메뉴 · 문자열

**파일**: [`res/menu/file_list.xml`](../../app/src/main/res/menu/file_list.xml) — `group_sort`에 항목 추가

```xml
<item android:id="@+id/action_sort_by_media_created"
      android:title="@string/file_list_action_sort_by_media_created" />
```

**파일**: `FileListFragment.kt`
- `MenuBinding`에 `sortByMediaCreatedItem` 추가 (클래스 [1705행](../../app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt#L1705))
- `onOptionsItemSelected`에 분기 추가
- `updateViewSortMenuItems()`의 `when(sortOptions.by)`에 분기 추가

**문자열**: `file_list_action_sort_by_media_created` — ko "미디어 생성 시각" / en "Media created"

### 2.4 2단계 검증

에뮬레이터 `/sdcard/SwingTestData`(20개, `.nomedia` 있음)에서:

1. 목록 모드 → 정렬 "수정 시각" → 순서 기록
2. 정렬 "미디어 생성 시각" → **순서가 달라져야 한다**
3. 촬영 시각 정렬 결과가 **2026-04-11 → 2026-08-19** 로 흩어져야 한다
   (전부 08-18로 뭉치면 `min()` 규칙 미동작 — 1단계로 돌아간다)

---

## 3단계. `MEDIA` 보기 모드 UI

### 3.1 enum 추가

**파일**: [`filelist/FileViewType.kt`](../../app/src/main/java/me/zhanghai/android/files/filelist/FileViewType.kt)

```kotlin
enum class FileViewType { LIST, GRID, MEDIA }    // ← MEDIA 맨 뒤
```

⚠️ **반드시 맨 뒤.** SharedPreferences에 **ordinal(정수)** 로 저장된다
([SettingLiveDatas.kt:239](../../app/src/main/java/me/zhanghai/android/files/settings/SettingLiveDatas.kt#L239)).

`FileListAdapter.getItemViewType()`이 `viewType.ordinal`을 쓰고
`onCreateViewHolder`가 `FileViewType.entries[viewType]`로 되돌리므로, 추가만 하면 자동으로 이어진다.

### 3.2 타일 레이아웃 신설

**파일**: `res/layout/file_item_media.xml`

기존 [`file_item_grid.xml`](../../app/src/main/res/layout/file_item_grid.xml)을 참고하되 다음이 다르다:

| 항목 | grid | media |
|---|---|---|
| 종횡비 | `app:aspectRatio="1.78"` | **`1.0`** (`AspectRatioFrameLayout` 그대로 사용) |
| 바깥 여백 | `screen_edge_margin` | **없음** |
| 이름 줄 | 있음(아래 별도 줄) | **없음** (폴더 타일만 오버레이 1줄) |
| `⋮` 버튼 | 이름 줄 안 | **썸네일 위 우상단 오버레이** |
| 선택 아이콘 영역 | 별도 `iconLayout` | 없음 — 롱프레스로 선택 |
| 동영상 표시 | 없음 | **좌하단 ▶** |

- `⋮` 가시성: 반투명 스크림 또는 상단 그라데이션을 깐다.
- `⋮` 터치 타깃 48dp 유지, 아이콘은 24dp.
- `ViewHolder`가 `FileItemListBinding`/`FileItemGridBinding` 두 생성자를 갖고 있으므로
  **세 번째 생성자**(`FileItemMediaBinding`)를 추가한다. 없는 뷰는 `null`로 넘긴다
  (이미 `directoryThumbnailImage`, `descriptionText` 등이 nullable).

### 3.3 표시 대상 필터

**파일**: `FileListFragment.updateAdapterFileList()` ([734행](../../app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt#L734))

숨김 파일 필터 **바로 옆**에 붙인다. 자연스러운 자리다.

```kotlin
if (viewModel.viewType == FileViewType.MEDIA) {
    files = files.filter {
        it.attributes.isDirectory || it.mimeType.isImage || it.mimeType.isVideo
    }
}
```

- 툴바 부제(개수)는 `getSubtitle()`이 **필터 전 목록**을 받으므로 전체 개수가 그대로 유지된다
  (기획서 D2 요구와 일치 — 별도 작업 불필요).
- 보기 모드가 바뀌면 이 함수를 다시 부르도록 `onViewTypeChanged()`에 호출을 추가한다.

### 3.4 어댑터 바인딩

**파일**: [`filelist/FileListAdapter.kt`](../../app/src/main/java/me/zhanghai/android/files/filelist/FileListAdapter.kt)

- `onCreateViewHolder`: `FileViewType.MEDIA` 분기 추가.
  grid 전용 Material3 배경/전경 처리는 **적용하지 않는다**(라운드 없음).
- `onBindViewHolder`:
  - 이름/설명 텍스트는 미디어 타일에서 `null` 처리
  - 폴더 타일만 이름 표시
  - 동영상이면 ▶ 표시
  - `contentDescription`에 파일 이름 설정 (접근성)
  - 배지(심볼릭 링크·암호화)는 미디어 모드에서 숨김
- 썸네일 로딩은 기존 `load(path to attributes)` 그대로. `centerCrop`은 레이아웃에서 지정.

### 3.5 열 수

**파일**: `FileListFragment.updateSpanCount()` ([656행](../../app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt#L656))

```kotlin
FileViewType.MEDIA -> (widthDp / 90).coerceAtLeast(4)
```

드로어 폭 차감은 기존 GRID 분기와 동일하게 처리한다(코드 공유).

### 3.6 메뉴 · 정렬 비활성

**파일**: `res/menu/file_list.xml` — `group_view`에 `action_view_media` 추가
**파일**: `FileListFragment.kt`
- `MenuBinding`에 `viewMediaItem` 추가
- `onOptionsItemSelected` 분기
- `updateViewSortMenuItems()`:
  - `when(viewType)`에 MEDIA 분기
  - **MEDIA일 때** 이름·종류·크기·수정시각 항목을 `isEnabled = false`로 흐리게,
    정렬 기준을 `MEDIA_CREATED`로 고정. 오름/내림차순·폴더 우선은 활성 유지.

**문자열**: `file_list_action_view_media` — ko "미디어" / en "Media"

### 3.7 3단계 검증

- 미디어 모드에서 정사각형 타일이 **간격 없이** 붙어 나오는가
- Pixel 8 세로 4열, 가로 회전 시 열 수 증가
- 사진·동영상·폴더만 보이고, 툴바 부제는 전체 개수 유지
- `⋮` 눌러 기존 메뉴 동작
- 동영상 타일에 ▶ 표시
- 정렬 메뉴에서 이름·종류·크기·수정시각이 흐리게

---

## 4단계. 최신 항목으로 스크롤

**파일**: `FileListFragment.onFileListChanged()` ([589행](../../app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt#L589))

현재 `Success` 시점에 `viewModel.pendingState`로 스크롤을 복원한다.
그 **뒤에** 규칙을 하나 더 얹는다.

```
if (stateful is Success) {
    if (pendingState != null) {
        복원 (기존)                      ← 뒤로가기 복귀: 최우선
    } else if (viewType == MEDIA && sortOrder == ASCENDING) {
        scrollToPosition(itemCount - 1)   ← 최신(맨 아래)
    }
}
```

- 재정렬로 인한 튐은 없다 — 1단계~2로 목록이 처음 그려질 때 이미 정렬이 끝나 있다.
- 보기 모드를 미디어로 **전환**할 때도 한 번 적용한다(`onViewTypeChanged`).
- 내림차순이면 아무것도 하지 않는다(맨 위가 이미 최신).

### 4단계 검증

- 미디어 모드로 폴더를 열면 맨 아래에서 시작
- 하위 폴더 갔다 뒤로 → **원래 보던 위치** 복원(맨 아래로 튀지 않음)
- 내림차순으로 바꾸면 맨 위에서 시작

---

## 5단계. 폴더별 보기 모드 + 체크박스 제거

### 5.1 저장 규칙 변경

**파일**: [`filelist/FileViewTypeLiveData.kt`](../../app/src/main/java/me/zhanghai/android/files/filelist/FileViewTypeLiveData.kt)

```kotlin
// 현재
fun putValue(value: FileViewType) {
    if (pathViewTypeLiveData.value != null) pathViewTypeLiveData.putValue(value)
    else Settings.FILE_LIST_VIEW_TYPE.putValue(value)     // ← 전역이 바뀜
}

// 변경 후
fun putValue(value: FileViewType) {
    pathViewTypeLiveData.putValue(value)                  // ← 항상 그 폴더에만
}
```

읽기(`loadValue`)는 이미 `경로별 값 ?: 전역 기본값` 구조라 **그대로 두면 된다.**

정렬(`FileSortOptionsLiveData`)도 동일하게 바꾼다.

### 5.2 체크박스 제거

- `res/menu/file_list.xml`에서 `action_view_sort_path_specific` 항목 삭제
- `MenuBinding`의 `viewSortPathSpecificItem` 및 관련 분기 삭제
- [`FileViewSortPathSpecificLiveData.kt`](../../app/src/main/java/me/zhanghai/android/files/filelist/FileViewSortPathSpecificLiveData.kt) 및
  `onViewSortPathSpecificChanged()` 삭제
- 문자열 `file_list_action_view_sort_path_specific` 제거
  (번역 파일 다수에 존재 — 미사용 문자열이라 남겨둬도 무해하나, 기본 `values/strings.xml`에서는 제거)

### 5.3 설정 화면

**파일**: `res/xml/settings.xml`, `settings/SettingsFragment.kt`

두 항목을 추가한다.

| 항목 | 내용 |
|---|---|
| **기본 보기 모드** | 전역 기본값(`FILE_LIST_VIEW_TYPE`). 목록/바둑판/미디어 중 선택. `ListPreference` |
| **폴더별 보기 설정 모두 초기화** | 경로별 저장소를 통째로 비운다 |

초기화 구현이 간단하다 — 경로별 설정은 **별도 SharedPreferences 파일**에 들어 있다
([SettingLiveData.kt:38](../../app/src/main/java/me/zhanghai/android/files/settings/SettingLiveData.kt#L38)):

```
파일명: "<기본_prefs_이름>_path"
키    : "<pref_key>_<경로문자열>"
```

→ 그 파일 하나를 `clear()` 하면 끝난다. 확인 다이얼로그를 띄운다.

> 주의: 초기화 직후 현재 화면에 반영되려면 관련 LiveData가 갱신돼야 한다.
> `SettingLiveData`는 `OnSharedPreferenceChangeListener`로 동작하므로 `clear()`에도 콜백이 온다
> (키가 `null`로 오는 경우가 있어 확인 필요 — 안 오면 화면 재진입 시 반영으로 타협).

### 5단계 검증

- 폴더 A에서 미디어 선택 → 폴더 B는 그대로
- 앱 재시작 후에도 폴더 A만 미디어
- 설정에서 기본 보기 모드 변경 → 지정한 적 없는 폴더들만 따라 바뀜
- 초기화 실행 → 모든 폴더가 기본값으로

---

## 6단계. 검증 · 측정

### 6.1 수용 기준

[08번 §12](08-photo-view-mode-spec.md)의 13개 항목을 순서대로 확인한다.

### 6.2 실기기 측정 (최우선 리스크)

**대상**: Fold 7 (UFS 저장소). 에뮬레이터는 호스트 SSD + 페이지 캐시라 낙관적이다.

| 측정 | 방법 | 판단 |
|---|---|---|
| 사진 수백 장 폴더 진입 시간 | 변경 전/후 비교 | 체감 차이가 없으면 통과 |
| 같은 폴더 재진입 | 캐시 효과 확인 | 첫 진입보다 확연히 빨라야 함 |
| 미디어가 없는 폴더 | 문서 폴더 등 | 변경 전과 동일해야 함(비용 0) |

**느리면**: 기획서 D15를 되돌려 2차 단계로 분리한다(§7-1번).

### 6.3 커버리지 확인

테스트 데이터가 전부 `.mov`/`.mp4`다. 다음을 추가로 확인한다.

- **이미지** — JPEG(EXIF 있음/없음), PNG, HEIC
- **다른 동영상 컨테이너** — mkv, webm, 3gp → `Mp4CreationTime` 파싱 실패 시
  `MediaMetadataRetriever` 폴백이 도는지, 그 비용이 얼마인지

---

## 7. 걱정되는 것들

| # | 걱정되는 것 | 어떻게 알아채나 | 그러면 어떻게 |
|---|---|---|---|
| **1** | **폴더 여는 시간이 늘어남** — 촬영 시각 읽기를 파일 목록 읽기에 같이 넣었기 때문 | 실제 폰에서 사진 폴더를 열 때 기다리는 시간이 길어짐 | §7.1 참고 |
| 2 | 썸네일 부하 — 열이 4~10개라 동시에 보이는 썸네일이 2~3배 | 스크롤 버벅임 | Coil 요청 취소가 제대로 되는지 확인. 필요 시 동시 요청 수 제한 |
| 3 | 경로별 설정 누적 — 이제 항상 저장됨 | prefs 파일 비대 | **바꾼 폴더만** 저장하므로 실제로는 제한적. 초기화 수단(5.3)이 안전판 |
| 4 | MP4 파서 견고성 | 손상 파일에서 멈추거나 크래시 | 1.1의 방어 조건 6가지를 반드시 넣고, 파싱은 예외를 삼켜 null 반환 |
| 5 | `FileItem` 변경 여파 | 컴파일 오류 | 데이터 클래스 생성자 인자가 늘어 호출부가 깨진다. `loadFileItem()` 외 생성 지점을 전수 확인 |
| 6 | upstream 병합 충돌 | 추후 rebase 시 | `FileListFragment.kt`를 크게 건드리므로 브랜치 분리(0.1). 변경을 함수 단위로 국소화 |

### 7.1 1번(폴더 여는 시간)을 자세히

**지금 정한 방식**

폴더를 열면 앱이 파일 목록을 읽는다. 그동안 화면에는 진행 표시가 돈다.
우리는 **촬영 시각 읽기를 이 목록 읽기 안에 같이 넣기로** 했다.
그래서 사진이 많은 폴더는 여는 데 걸리는 시간이 지금보다 조금 늘어난다.

**괜찮다고 본 근거**

에뮬레이터에서 재보니 파일 하나당 300바이트만 읽으면 됐다. 500장이어도 다 합쳐 150KB다.
사실상 공짜다.

**그런데 확신할 수 없는 이유**

에뮬레이터는 PC의 빠른 SSD 위에서 돈다. 진짜 폰의 저장장치는 그것보다 느리고,
파일을 여닫는 비용도 다르다. **폰에서 직접 재봐야 안다.**

**폰에서 느리면 어떻게 하나**

촬영 시각 읽기를 목록 읽기에서 빼낸다. 목록을 먼저 화면에 띄우고, 촬영 시각은 그 뒤에
따로 읽는다. 폴더는 빨리 열린다.

대신 이런 일이 생긴다:

1. 목록이 처음 나올 때는 촬영 시각을 아직 모르니 **순서가 틀린 채로** 보인다
   (수정 시각 순서로 임시 배치)
2. 촬영 시각을 다 읽고 나면 **순서가 갑자기 바뀐다**
3. 그 순간 사용자가 보고 있던 사진이 화면 밖으로 밀려난다

그래서 "사용자가 아직 스크롤을 안 건드렸으면 위치를 다시 맞추고, 이미 스크롤 중이면 그대로 둔다"
같은 판단 코드가 더 필요해진다. 지금 방식에는 이런 게 아예 없다.

**그래서 1단계을 따로 뺐다**

이 갈림길은 1단계(촬영 시각 읽기) 하나에만 걸려 있다.
방향을 바꾸게 되면 그 단계만 손보면 되고, 2단계~5는 그대로 둘 수 있다.

## 8. 예상 작업량

| 단계 | 신규 파일 | 수정 파일 | 난이도 |
|---|---|---|---|
| 1 | 2 | 0 | 중 (박스 파서) |
| 2 | 0 | 4 | 하 |
| 3 | 1 | 4 | **중상** (레이아웃·오버레이·선택 표시) |
| 4 | 0 | 1 | 하 |
| 5 | 0 | 6 (+1 삭제) | 중 |
| 6 | 0 | 0 | — |

3단계이 가장 손이 많이 간다. 나머지는 기존 구조에 얹는 수준이다.

## 9. 이 계획에서 의도적으로 미룬 것

- **디스크 영속 캐시** — 메모리 LRU로 시작(1.3). 실기기 측정 후 판단.
- **정렬 기준으로서의 "미디어 생성 시각"을 목록 모드 기본값으로** — 기본은 기존 그대로.
- **점진적 목록 표시** — 1차 로딩은 여전히 "전부 아니면 전무". 별건.
- **하위 폴더 보기 설정 상속** — 기획서에서 범위 제외(L3).
- **날짜 그룹 헤더, 핀치 줌, 재생 시간 배지** — 기획서 비목표.
