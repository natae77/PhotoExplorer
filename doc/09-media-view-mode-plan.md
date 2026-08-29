# 09. 미디어 보기 모드 구현 계획서

[08번 기획서](08-media-view-mode-spec.md)를 어떻게 만들 것인가.

- 작성일: 2026-08-25
- 프로젝트: **PhotoExplorer** (`natae77/PhotoExplorer`, `zhanghai/MaterialFiles` fork)
- 기준 커밋: `fc12500` + 로컬 수정(dav4jvm 해시 고정, 앱 식별자 변경, Firebase 제거)
- 전제: 기획서의 D1~D22가 모두 확정된 상태 (5차 개정 반영)

> ⚠️ **[미결 항목 문서](media-view-mode-open-items.md)를 같이 봐야 한다.**
> 이 계획서를 코드와 대조해서 나온 것들 중, **기획서에 있는데 여기 없는 것**(주로 3단계)과
> **아직 안 정한 것**이 그 문서에 모여 있다. 단계에 착수하기 전에 해당 항목을 먼저 처리한다.

**표기** — 번호 두 가지가 섞이므로 규칙을 정해 둔다.

- `§`는 **문서의 절**이다. 다른 문서를 가리킬 때는 `08번 §4.3`처럼 문서 번호를 앞에 붙인다.
- `단계`는 **작업 순서**다. 절 번호와 무관하다.

## 0. 원칙

- **단계마다 빌드되고 검증 가능해야 한다.** 각 Phase 끝에 앱을 설치해 눈으로 확인할 수 있다.
- **UI 없는 것부터 만든다.** 데이터 계층(생성 시각)이 먼저 서야 정렬을 붙이고, 정렬이 서야 보기 모드가 의미 있다.
- **되돌리기 쉽게 쪼갠다.** 특히 1단계는 실기기 성능에 따라 설계가 바뀔 수 있다(§8-1번).

## 1. 만드는 순서

**"단계"는 작업 순서다.** 아래로 갈수록 앞 단계 위에 얹힌다.

| 단계 | 만드는 것 | 화면에서 보이는 것 | 상태 | 커밋 |
|---|---|---|---|---|
| 0 | 준비 (브랜치) | (없음) | ✅ 완료 | `feature/media-view-mode` |
| 1 | **사진에서 찍힌 날짜 꺼내오기** | (없음 — 다른 기능의 재료) | ✅ 완료 | |
| 2 | **찍힌 날짜순으로 정렬하기** | 정렬 메뉴에 "미디어 생성 시각"이 생김 | ✅ 완료 | |
| 3 | **미디어 보기 모드** | 정사각형 격자로 사진이 보임 | ✅ 완료 | |
| 4 | **날짜 타일** | 날짜가 바뀌는 자리에 날짜 칸이 끼어듦 | ✅ 완료 | |
| 5 | **최신 사진부터 보기** | 폴더를 열면 맨 아래(최신)에서 시작 | ✅ 완료 | |
| 6 | **폴더마다 보기 모드 기억하기** | 사진 폴더는 미디어, 문서 폴더는 목록 | ✅ 완료 | |
| 7 | 검증 · 실제 폰에서 측정 | — | 🟡 에뮬레이터만 | 실기기(Fold 7) 측정 남음 |
| 8 | **날짜 타일 요일 색** | 토요일 파랑 · 일요일 빨강 | ✅ 완료 | 기획서 6차 개정(D23) |

> **2026-08-27 구현 완료.** 에뮬레이터(Pixel 8, API 36) 검증까지 끝냈다. 구현하면서 계획서에
> 없던 함정이 다섯 개 더 나왔다 — [미결 항목 §E](media-view-mode-open-items.md)에 적어 뒀다.
> 계획서 본문은 **고치지 않는다**(§1의 원칙). 남은 것은 실기기 측정(7.2)과 커버리지(7.3)다.

1단계만 화면에 아무것도 안 보인다. 사진 파일 안에서 찍힌 날짜를 꺼내오는 코드인데,
2단계(정렬)와 3단계(미디어 모드)가 그 값을 쓰기 때문에 먼저 만든다.

2단계까지만 해도 쓸 만하다 — 평소 목록 화면에서 찍힌 날짜순 정렬이 된다.
3단계부터가 새로운 화면이다.

**상태 칸을 단계마다 갱신한다.** 계획서는 기획서와 달리 "어디까지 했나"에 따라 읽는 법이
달라진다. 그리고 **끝난 단계는 나중에 기획이 바뀌어도 본문을 고치지 않는다** — 대신 뒤에
새 단계를 붙인다. 고쳐 놓으면 "그 단계는 했지" 하고 넘어가서 변경이 통째로 누락된다.

---

## 0단계. 준비

### 0.1 브랜치

기본 브랜치는 **`main`** 이다(2026-08-25에 `master`에서 이름을 바꿨다).

`main`에서 작업 브랜치를 딴다. upstream(`zhanghai/MaterialFiles`)과 충돌 가능성이 있는
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
[08번 §5.7](08-media-view-mode-spec.md) 실측대로 파일당 약 300바이트만 읽는다.

#### ⚠️ 파일을 여는 방법 — **시크**해야 한다. 스트림 skip이 아니다

`path.newByteChannel()`로 열고 `position(offset)`으로 **건너뛴다.**
(`provider/common`의 `newByteChannel` 확장을 쓴다. `newInputStream()`은 쓰지 않는다.)

이건 취향 문제가 아니라 **이 기능의 성능 전제 그 자체**다.

08번 §5.7 실측에서 50개 파일이 **전부 `moov`가 파일 뒤쪽**에 있었다 — 파일 끝에서
13,804 ~ 287,309 바이트 지점. 즉 파서는 항상 거대한 `mdat` 박스를 통째로 뛰어넘는다.

`newInputStream().skip(n)`으로 짜면 provider에 따라 그 구간을 **실제로 읽어서** 넘긴다.
"파일당 300바이트"가 "파일당 수십~수백 KB"가 된다. 원격·SAF 경로에서 특히 그렇다.
§5.7의 `dd` 측정도 시크 전제였으므로, skip으로 구현하면 §8-1번 리스크가
실기기에서 재보기도 전에 이미 현실이 된다.

```
읽기 절차 (SeekableByteChannel)
1. position(offset) → 16바이트 읽기 → size(4) + type(4) [+ largesize(8) if size==1]
2. type != 'moov' 이면 offset += size, 1로 돌아감   ← position()으로 점프. 읽지 않는다
3. 'moov' 도달 → 그 안에서 'mvhd' 찾기 (앞 8KB 이내)
4. mvhd: version(1) + flags(3) + creation_time(version==0 ? 4 : 8 바이트)
5. 1904-01-01 UTC 기준 경과 초 → Instant
```

**1단계 검증에 넣을 것**: 파싱 1회당 실제로 읽은 바이트 수를 세어 로그로 찍는다.
파일당 수백 바이트여야 한다. 수십 KB가 나오면 어딘가에서 시크가 아니라 읽고 있는 것이다.

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

> `supportsThumbnail`([FileItemExtensions.kt:59](../app/src/main/java/me/zhanghai/android/files/filelist/FileItemExtensions.kt#L59))이
> 이미 비슷한 판정을 하고 있으므로, 조건 로직을 참고하거나 일부 재사용한다.

### 1.3 캐시 — **메모리 LRU만, 디스크는 나중에**

기획서에서는 "앱을 껐다 켜도 유지되는 캐시"를 적었지만, **1차 구현은 메모리 LRU만** 만든다.

이유:

- 캐시가 실제로 필요한 상황은 `PathObserver`가 폴더 변경을 감지해 `loadValue()`가
  **같은 프로세스 안에서** 반복 호출될 때다. 메모리 캐시로 충분히 막힌다.
- 앱 재시작 후 첫 진입 비용은 §5.7 기준 파일당 300바이트 — 디스크 캐시를 만들 만큼 크지 않다.
- 디스크 캐시는 저장 형식·상한·정리·무효화까지 딸려와 범위가 커진다.
  **실기기 측정 후 필요하다고 판명되면** 그때 붙인다(§8-1번).

```
LruCache<CacheKey, Long>    (예: 최대 4096항목)
CacheKey = (path, size, lastModifiedTime)   ← 내용이 바뀌면 자동 무효화
```

#### ⚠️ "값 없음"도 캐시해야 한다 — `LruCache`는 null을 담지 못한다

`android.util.LruCache`는 **null 값을 허용하지 않는다.** `put()`에 null을 주면 NPE고,
`get()`이 돌려주는 null은 "캐시에 없음"이라는 뜻이다. 그래서 타입을 `LruCache<K, Long?>`로
잡으면 **"값을 못 구했다"는 사실 자체를 기록할 수 없다.**

그런데 §1.2의 `readMediaCreatedTime()`은 파싱 실패 시 null을 반환한다. 그대로 두면
**값을 못 구한 파일만 정확히 캐시가 안 걸려서**, `PathObserver`가 돌 때마다 매번
다시 파싱한다 — 그것도 `MediaMetadataRetriever` 폴백까지 다 태우고서. 캐시를 만든 이유
(§8-4번의 폴백 비용)가 하필 가장 비싼 경우에만 무효화된다.

**센티널을 쓴다.**

```kotlin
private const val NO_VALUE = Long.MIN_VALUE

cache[key]?.let { return if (it == NO_VALUE) null else it }   // 조회
cache.put(key, result ?: NO_VALUE)                            // 기록
```

값 클래스로 감싸도 되지만 항목마다 객체가 하나씩 더 생긴다. 센티널이면 충분하다.

#### 캐시 객체는 어디에 두나

**전역 `object`로 둔다.** `loadFileItem()`이 `Path`의 확장 함수라 주입할 자리가 없다.
프로세스 수명과 같이 가고, 스레드 안전해야 한다 — `loadValue()`는 백그라운드 풀에서
돌고 폴더가 여럿 열려 있으면 동시에 불린다. `LruCache` 자체는 동기화되어 있다.

### 1.4 1단계 검증

앱 UI 변화가 없으므로 로그로 확인한다.

- `/sdcard/SwingTestData`의 50개(현재 20개 복사됨)에 대해 파싱 결과를 로그로 찍는다
- **기대값**([08번 §5.3](08-media-view-mode-spec.md)):

| 파일 | 기대 결과 |
|---|---|
| `20260819_062806_1.mp4` | 2026-08-18 21:29:48 UTC (메타데이터 채택) |
| `IMG_6429.mov` | 2026-04-11 01:53:20 UTC (mtime 채택 — `min()` 규칙 동작) |
| `IMG_6457.mov` | 2026-04-18 00:46 UTC |

`.mov`들이 전부 08-18로 나오면 **`min()` 규칙이 안 걸린 것**이다.

---

## 2단계. `FileItem` 확장 + 정렬 기준 추가

### 2.1 `FileItem`에 필드 추가

**파일**: [`file/FileItem.kt`](../app/src/main/java/me/zhanghai/android/files/file/FileItem.kt)

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

**파일**: [`filelist/FileSortOptions.kt`](../app/src/main/java/me/zhanghai/android/files/filelist/FileSortOptions.kt)

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

**파일**: [`res/menu/file_list.xml`](../app/src/main/res/menu/file_list.xml) — `group_sort`에 항목 추가

```xml
<item android:id="@+id/action_sort_by_media_created"
      android:title="@string/file_list_action_sort_by_media_created" />
```

**파일**: `FileListFragment.kt`
- `MenuBinding`에 `sortByMediaCreatedItem` 추가 (클래스 [1705행](../app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt#L1705))
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

**파일**: [`filelist/FileViewType.kt`](../app/src/main/java/me/zhanghai/android/files/filelist/FileViewType.kt)

```kotlin
enum class FileViewType { LIST, GRID, MEDIA }    // ← MEDIA 맨 뒤
```

⚠️ **반드시 맨 뒤.** SharedPreferences에 **ordinal(정수)** 로 저장된다
([SettingLiveDatas.kt:239](../app/src/main/java/me/zhanghai/android/files/settings/SettingLiveDatas.kt#L239)).

`FileListAdapter.getItemViewType()`이 `viewType.ordinal`을 쓰고
`onCreateViewHolder`가 `FileViewType.entries[viewType]`로 되돌리므로, 추가만 하면 자동으로 이어진다.

### 3.2 타일 레이아웃 신설

**파일**: `res/layout/file_item_media.xml`

기존 [`file_item_grid.xml`](../app/src/main/res/layout/file_item_grid.xml)을 참고하되 다음이 다르다:

| 항목 | grid | media |
|---|---|---|
| 종횡비 | `app:aspectRatio="1.78"` | **`1.0`** (`AspectRatioFrameLayout` 그대로 사용) |
| 바깥 여백 | `screen_edge_margin` | **없음** |
| 이름 줄 | 있음(아래 별도 줄) | **없음** (폴더 타일만 오버레이 1줄) |
| `⋮` 버튼 | 이름 줄 안 | **썸네일 위 우상단 오버레이** |
| 선택 아이콘 영역 | 별도 `iconLayout` | 없음 — 롱프레스로 선택 |
| 동영상 표시 | 없음 | **좌하단 ▶** |

- `⋮` 가시성: 반투명 스크림 또는 상단 그라데이션을 깐다. 아이콘 위치에 맞춰 모서리 쪽에 둔다.
- **`⋮` 위치 — 우상단 모서리에 바짝 붙인다** (08번 §4.1). 터치 영역 48dp를 모서리에 붙이고
  그 안에서 아이콘(24dp)만 민다. 여백을 비대칭으로 주는 것이 요점이다.

  ```xml
  <me.zhanghai.android.foregroundcompat.ForegroundImageButton
      android:id="@+id/menuButton"
      android:layout_width="@dimen/touch_target_size"
      android:layout_height="@dimen/touch_target_size"
      android:layout_gravity="top|end"
      android:paddingTop="4dp"
      android:paddingEnd="4dp"
      android:paddingBottom="20dp"
      android:paddingStart="20dp"
      ... />
  ```

  여백 합이 `24 + 4 + 20 = 48`이라 터치 영역 48dp가 유지되고, 아이콘은 모서리에서 4dp
  안쪽에 놓인다. 음수 마진을 쓰지 않으므로 부모의 `clipChildren` 설정과 무관하다.
  기존 목록·바둑판 레이아웃은 건드리지 않는다.
- `ViewHolder`가 `FileItemListBinding`/`FileItemGridBinding` 두 생성자를 갖고 있으므로
  **세 번째 생성자**(`FileItemMediaBinding`)를 추가한다. 없는 뷰는 `null`로 넘긴다
  (이미 `directoryThumbnailImage`, `descriptionText` 등이 nullable).

### 3.3 표시 대상 필터

**파일**: `FileListFragment.updateAdapterFileList()` ([734행](../app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt#L734))

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

**파일**: [`filelist/FileListAdapter.kt`](../app/src/main/java/me/zhanghai/android/files/filelist/FileListAdapter.kt)

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

**파일**: `FileListFragment.updateSpanCount()` ([656행](../app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt#L656))

```kotlin
FileViewType.MEDIA -> (widthDp / 112).coerceAtLeast(1)
```

- 기준 폭 112dp는 90dp를 25% 키운 값이다(08번 §4).
- `coerceAtLeast(1)`을 빼면 안 된다. `GridLayoutManager`는 `spanCount`가 0 이하일 때
  `IllegalArgumentException`을 던진다. 화면 폭이 112dp 미만인 상황(자유 창)이 실제로 있다.
- 드로어 폭 차감은 기존 GRID 분기와 동일하게 처리한다(코드 공유). GRID 분기 자체는 손대지 않는다.

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
- Pixel 8 세로 **3열**, 가로 회전 시 열 수 증가
- 분할 화면으로 좁히면 2열, 더 좁히면 1열. 어느 폭에서도 죽지 않는다
- `⋮` 아이콘이 모서리에서 4dp 안쪽에 있고, 눌러도 48dp 영역이 그대로 먹는다
- 사진·동영상·폴더만 보이고, 툴바 부제는 전체 개수 유지
- `⋮` 눌러 기존 메뉴 동작
- 동영상 타일에 ▶ 표시
- 정렬 메뉴에서 이름·종류·크기·수정시각이 흐리게

---

## 4단계. 날짜 타일

08번 §4.3을 만든다. 네 조각으로 나눈다.
**§4.1은 화면에 아무 변화가 없다.** 그런데 이 준비를 안 하고 §4.2부터 하면 반드시 꼬인다.

| 조각 | 하는 일 | 화면 변화 |
|---|---|---|
| §4.1 | 어댑터가 다루는 항목을 파일/날짜 두 갈래로 나눈다 | 없음 (기존과 똑같이 동작해야 함) |
| §4.2 | 날짜 타일을 만들어 목록에 끼운다 | 날짜 타일이 보이기 시작 |
| §4.3 | 날짜 타일 레이아웃 | 모양이 제대로 나옴 |
| §4.4 | 조작 차단 · 개수 · 접근성 | 눌러도 아무 일 없음 |

### 4.1 어댑터 항목 타입 분리 — 화면 변화 없는 준비 작업

**파일**: `filelist/FileListItem.kt` (신규)

```kotlin
sealed interface FileListItem {
    data class File(val file: FileItem) : FileListItem
    data class Date(val epochMillis: Long) : FileListItem
}
```

`Date`가 들고 있는 값은 **그날 00:00:00**이다. 화면에 찍을 날짜 문자열의 재료이자
항목 비교(DiffUtil)의 키다. **정렬 비교자에는 들어가지 않는다** — 이유는 §4.2.

실재하지 않는 경로를 가진 가짜 `FileItem`으로 날짜 타일을 표현하는 방법도 있지만 쓰지 않는다.
파일 속성(크기·권한 등)이 없는 물건이 파일 취급 경로 전체를 돌아다니게 되고,
열기·선택·메뉴·정렬 어디 한 군데에서 분기를 빠뜨리면 크래시다.
타입을 나누면 컴파일러가 빠뜨린 곳을 잡아 준다.

**파일**: [`filelist/FileListAdapter.kt`](../app/src/main/java/me/zhanghai/android/files/filelist/FileListAdapter.kt)

제네릭 타입을 바꾼다. 뷰홀더도 두 종류가 되므로 상위 타입으로 올린다.

```kotlin
// 이전
class FileListAdapter(...) : AnimatedListAdapter<FileItem, FileListAdapter.ViewHolder>(CALLBACK)

// 이후
class FileListAdapter(...) : AnimatedListAdapter<FileListItem, RecyclerView.ViewHolder>(CALLBACK)
```

`AnimatedListAdapter`의 `bindViewHolderAnimation(holder: VH)`는 `itemView`만 쓰므로
`RecyclerView.ViewHolder`로 올려도 그대로 동작한다.
`AnimatedListAdapter`·`ListAdapter`는 **고치지 않는다.**

#### ⚠️ 가장 중요한 함정 — 원본 파일 목록을 따로 들고 있어야 한다

지금 어댑터는 **"화면에 보이는 목록"을 재료로 다시 정렬한다.** 두 군데다.

```kotlin
var viewType: FileViewType
    set(value) { ...; if (!isSearching) { super.replace(list, true) } }      // 51행

var sortOptions: FileSortOptions
    set(value) { ...; val sortedList = list.sortedWith(value.createComparator()) }  // 60행
```

`list`는 `ListAdapter`가 들고 있는 **현재 어댑터 목록**이다.

목록에 파일만 들어 있을 때는 이래도 문제가 없다. 파일들을 다시 정렬하면 파일들이 나온다.
그런데 그 목록에 날짜 타일이 섞이면, **결과물을 다시 재료로 쓰는 셈**이 된다.

**정렬 방향을 바꿨을 때 벌어지는 일:**

```
지금 화면 (오름차순)   [8/5] [사진] [사진] [8/7] [사진]
                         ↓  이걸 통째로 재료로 삼아 다시 정렬하고
                         ↓  거기에 날짜 타일을 또 끼운다
결과 (내림차순)        [8/7] [8/7] [사진] [8/5] [8/5] [사진] [사진]
                        ↑ 두 개    ↑ 두 개
```

정렬을 토글할 때마다 날짜 타일이 한 벌씩 계속 불어난다.

**보기 모드를 바꿨을 때 벌어지는 일:**

`viewType` 세터는 정렬조차 하지 않고 현재 목록을 그대로 되돌려 놓는다.
미디어 → 바둑판으로 바꾸면 **바둑판 화면에 날짜 타일이 그대로 남는다.**

**고치는 법**: 어댑터가 **원본 파일 목록을 따로** 들고, 화면에 뿌릴 목록은 매번
그 원본에서 **처음부터 다시 만든다.** 화면에 있는 것을 재료로 쓰지 않는다.

```kotlin
private var files: List<FileItem> = emptyList()

private fun rebuildItems(clear: Boolean) {
    val items = if (isSearching) {
        files.map { FileListItem.File(it) }          // 검색 중: 정렬도 날짜 타일도 없음
    } else {
        buildItems(files)                             // 정렬 + 날짜 타일 삽입 (§4.2)
    }
    super.replace(items, clear)
    rebuildFilePositionMap()
}
```

`replaceListAndIsSearching()`, `viewType` 세터, `sortOptions` 세터 **세 곳 모두**가
`rebuildItems()`를 부르게 바꾼다. 이 세 곳이 각자 정렬하던 코드는 지운다.

#### 같이 고쳐야 하는 곳 — 빠뜨리면 크래시

`getItem(position)`이 이제 `FileListItem`을 준다. 파일이라고 가정하고 쓰던 곳을 전부 찾는다.

| 위치 | 무엇을 | 빠뜨리면 |
|---|---|---|
| `CALLBACK` (409행) | 종류가 다르면 `false`. 파일은 경로 비교, 날짜는 `epochMillis` 비교 | 목록 애니메이션이 엉킨다 |
| `rebuildFilePositionMap()` (164행) | 날짜 항목은 표에 넣지 않는다. **위치 값은 날짜 타일을 포함한 어댑터 위치 그대로** | 선택 표시가 엉뚱한 칸에 뜬다 |
| `selectAllFiles()` (124행) | 날짜 항목 건너뛰기 | 캐스트 예외 |
| `getItemViewType()` (172행) | 날짜면 별도 값. 지금은 `viewType.ordinal`을 그대로 쓰고 `onCreateViewHolder`가 `FileViewType.entries[viewType]`로 되돌리므로, 날짜용 상수를 `FileViewType.entries.size`(=3)로 잡는다 | `onCreateViewHolder`에서 배열 범위 초과 |
| `onBindViewHolder(payloads)` (206행) | 날짜 뷰홀더면 바로 반환 | `pickOptions`·`nameEllipsize`가 바뀔 때 전체 재바인딩이 돌면서 크래시 |
| `getPopupText()` (395행) | 날짜면 그 날짜 문자열 | 빠른 스크롤 팝업에서 크래시. *팝업에 날짜를 띄우는 기능을 만드는 게 아니라, 반환할 값이 있어야 해서다* |
| **`FileListFragment.maybeAddImageViewerActivityExtras()`** ([1294행](../app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt#L1294)) | 어댑터를 훑어 이미지 경로를 모은다. 날짜 항목 건너뛰기 | **사진을 열 때 크래시.** 어댑터 밖에 있어서 놓치기 쉽다 |

**§4.1 검증**: 여기까지 하고 앱을 돌렸을 때 **아무것도 달라지지 않아야 한다.**
목록·바둑판 모드, 선택, 정렬 변경, 검색, 사진 열기가 전부 이전과 똑같으면 통과다.

### 4.2 날짜 타일 만들어 끼우기

**위치**: `FileListAdapter` 안. 정렬이 어댑터에서 일어나므로 삽입도 같은 자리가 맞다.

```kotlin
private fun buildItems(files: List<FileItem>): List<FileListItem> {
    val sorted = files.sortedWith(sortOptions.createComparator())
    if (viewType != FileViewType.MEDIA) {
        return sorted.map { FileListItem.File(it) }
    }
    // 정렬된 순서대로 훑으면서, 앞 항목과 날짜가 다르면 그 앞에 날짜 타일을 끼운다
}
```

#### 이 방식이면 08번 §4.3의 "언제나 그 날 맨 앞"이 저절로 지켜진다

**정렬을 먼저 끝내고 훑으면서 끼우면** 오름차순이든 내림차순이든 날짜 타일이 언제나
구간의 맨 앞에 놓인다. 이 규칙을 위해 따로 할 일이 없다.

00:00:00 값을 정렬 비교자에 태우지 않는 이유가 이것이다. 그 값은 **어느 날짜 구간에
속하는지**를 정할 뿐이고, 구간 안에서의 자리는 끼워 넣는 시점에 정해진다.

#### 날짜를 나누는 기준

```kotlin
Instant.ofEpochMilli(mediaCreatedMillis)
    .atZone(ZoneId.systemDefault())     // 기기 시간대 (08번 §4.3)
    .toLocalDate()
```

값은 2단계에서 만든 **미디어 생성 시각**(08번 §5.4의 `min(메타데이터, 수정시각)` 결과)을 쓴다.
`FileItem`에 이미 들어 있으므로 파일을 다시 읽지 않는다.

#### 폴더 구간 건너뛰기

- `sortOptions.isDirectoriesFirst`가 **참**이면: 목록 앞쪽의 연속된 폴더 구간을 건너뛰고
  그 다음 항목부터 날짜를 매긴다. 첫 날짜 타일은 마지막 폴더 **바로 다음 칸**에 온다.
  줄을 새로 시작하지 않는다 — 격자가 알아서 이어 붙인다.
- **거짓**이면: 폴더도 그냥 대상에 넣는다. 폴더는 08번 §5.4에 따라 수정 시각으로
  폴백된 값을 이미 갖고 있으므로 특별 처리가 필요 없다.

#### 검색 중에는 넣지 않는다

`rebuildItems()`에서 `isSearching`이면 `buildItems()`를 아예 안 부른다(§4.1).
검색 중에는 어댑터가 정렬 자체를 건너뛰므로 날짜 구간이 성립하지 않는다.

### 4.3 레이아웃

**파일**: `res/layout/file_item_media_date.xml` (신규)

```
AspectRatioFrameLayout (app:aspectRatio="1.0", background=?colorSurfaceVariant)
└ LinearLayout (vertical, gravity=center)
  ├ TextView  연도    — 작게, ?android:textColorSecondary
  └ TextView  월·일   — 크게, textStyle=bold
```

- **바깥 여백 0, 모서리 라운드 없음.** 미디어 타일과 맞닿아야 한다 (08번 §4).
- **글자 줄이기**: 열이 많으면 타일이 좁아진다. 08번 §4.3은 "잘라내지 않고 줄인다"이므로
  월·일 `TextView`에 `app:autoSizeTextType="uniform"`을 준다 (`AppCompatTextView` 기능).
- **날짜 문자열**: 로케일을 따른다. 하드코딩하지 않는다.
  연도 `yyyy년` / `yyyy`, 월·일은 `DateFormat.getBestDateTimePattern(locale, "MMMd")` 계열을 쓴다.
- **선택 사항**: 일요일 빨강 / 토요일 파랑 (08번 §4.3). 안 넣어도 기능 손실 없다.
  넣을 거면 색상 리소스로 빼서 다크 테마에서도 읽히는지 확인한다.

**뷰홀더**: `FileListAdapter`에 `DateViewHolder`를 추가한다.
기존 `ViewHolder`에 nullable 필드를 더 붙이는 방식은 쓰지 않는다 — 날짜 타일에는
썸네일·아이콘·이름·`⋮` 중 어느 것도 없어서 전부 null이 된다.

### 4.4 조작 차단 · 개수 · 접근성

08번 §4.3 그대로. 날짜 타일은 **누를 수 없는 표시물**이다.

| 항목 | 어떻게 |
|---|---|
| 탭·롱프레스 | `DateViewHolder`에 리스너를 아예 달지 않는다. 루트에 `android:clickable="false"`, `android:focusable="false"` |
| 눌림 효과 | `foreground`에 `?selectableItemBackground`를 주지 않는다 (미디어 타일과 다른 점) |
| 선택 | `selectAllFiles()`와 `filePositionMap`에서 이미 빠져 있다 (§4.1). 추가 작업 없음 |
| `⋮` | 레이아웃에 없다 |
| 파일 개수 | 툴바 부제는 어댑터가 아니라 `getSubtitle()`이 원본 목록으로 만든다. **자동으로 안 세어진다** |
| 접근성 | 루트 `contentDescription`에 "2026년 8월 5일" 전체. 하위 `TextView` 둘은 `importantForAccessibility="no"`로 묶어서 한 번만 읽히게 한다 |

**5단계(최신 항목으로 스크롤)와의 관계**: 5단계는 `scrollToPosition(itemCount - 1)`을 쓴다.
날짜 타일은 항상 구간의 **맨 앞**에 오므로 목록의 마지막 항목은 여전히 미디어다.
**고칠 것이 없다.**

### 4단계 검증

- 날짜가 바뀌는 자리마다 날짜 타일이 **1칸**으로 들어가고, 다음 미디어가 **같은 줄 옆 칸**에 이어진다
- 오름차순·내림차순 **둘 다** 날짜 타일이 그 날 미디어들보다 앞에 온다
- 날짜 타일을 탭·롱프레스해도 아무 일이 없고, 눌림 효과도 안 뜬다
- 전체 선택했을 때 날짜 타일이 선택되지 않는다
- 툴바 부제의 파일 개수에 날짜 타일이 포함되지 않는다
- **정렬 방향을 여러 번 바꿔도 날짜 타일이 늘어나지 않는다** (§4.1의 함정)
- **미디어 → 바둑판으로 바꾸면 날짜 타일이 사라진다** (같은 함정)
- **검색어를 입력하면 날짜 타일이 사라지고, 지우면 다시 나온다**
- 사진을 탭해서 뷰어를 열었을 때 크래시하지 않고, 좌우로 넘겨도 정상이다
- 폴더가 섞인 폴더에서, 첫 날짜 타일이 마지막 폴더 바로 다음 칸에 온다

---

## 5단계. 최신 항목으로 스크롤

**파일**: `FileListFragment.onFileListChanged()` ([589행](../app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt#L589))

현재 `Success` 시점에 `viewModel.pendingState`로 스크롤을 복원한다.
그 **뒤에** 규칙을 하나 더 얹는다.

이 단계는 코드량이 제일 적지만 **함정이 둘 있다.** 둘 다 `pendingState`의 성질에서 온다.
먼저 그것부터 본다.

### ⚠️ `pendingState`는 **읽으면 사라진다**

[TrailData.kt:52](../app/src/main/java/me/zhanghai/android/files/filelist/TrailData.kt#L52):

```kotlin
val pendingState: Parcelable?
    get() = states.set(currentIndex, null)   // 이전 값을 돌려주면서 null로 밀어 넣는다
```

`MutableList.set()`은 **이전 값을 반환하고 그 자리를 덮어쓴다.** 이름은 게터지만 실제로는
**꺼내 가면 없어지는** 물건이다. 여기서 두 가지가 따라 나온다.

#### 함정 1 — `!= null` 검사만으로도 소비된다

```kotlin
if (viewModel.pendingState != null) {          // ← 여기서 이미 값이 빠져나가 버림
    layoutManager.onRestoreInstanceState(viewModel.pendingState)   // ← null이 들어간다
}
```

**뒤로가기 스크롤 복원이 통째로 죽는다.** 반드시 지역 변수로 **한 번만** 읽는다.

#### 함정 2 — 두 번째 호출부터는 항상 null이다

`onFileListChanged()`는 폴더 진입 때 한 번만 불리는 게 아니다. `PathObserver`가 폴더 변화를
감지할 때마다 `loadValue()`가 돌고, 그때마다 다시 불린다. 첫 호출에서 `pendingState`가
소비됐으므로 **그 뒤로는 계속 null**이다.

그래서 `else` 가지를 조건 없이 두면 이렇게 된다:

```
미디어 모드로 폴더를 열고 위쪽으로 스크롤해서 옛날 사진을 보는 중
  → 그 폴더에 파일이 하나 추가되거나 이름이 바뀜
  → PathObserver 발동 → onFileListChanged 재호출 → pendingState == null
  → 보던 자리에서 맨 아래로 튄다
```

**폴더 진입당 한 번만** 하도록 플래그가 필요하다.

### 정리된 규칙

```kotlin
if (stateful is Success) {
    val pendingState = viewModel.pendingState      // ← 한 번만 읽는다 (함정 1)
    when {
        pendingState != null -> {
            layoutManager.onRestoreInstanceState(pendingState)   // 뒤로가기 복귀: 최우선
            hasScrolledToLatest = true                           // 복원했으면 자동 스크롤 안 함
        }
        !hasScrolledToLatest                                     // ← 진입당 1회 (함정 2)
            && viewModel.viewType == FileViewType.MEDIA
            && sortOptions.order == ASCENDING -> {
            recyclerView.scrollToPosition(adapter.itemCount - 1)  // 최신(맨 아래)
            hasScrolledToLatest = true
        }
    }
}
```

`hasScrolledToLatest`는 **현재 경로가 바뀔 때 false로 되돌린다**(`onCurrentPathChanged()`).
보기 모드를 미디어로 **전환**할 때도 한 번 적용해야 하므로(`onViewTypeChanged`)
그 자리에서도 false로 되돌리고 스크롤을 다시 태운다.

### 그 밖

- `scrollToPosition(itemCount - 1)`의 **타이밍은 안전하다.** `ListDiffer`는 동기 구현이라
  ([ListDiffer.kt](../app/src/main/java/me/zhanghai/android/files/ui/ListDiffer.kt))
  바로 앞의 `updateAdapterFileList()`가 끝난 시점에 `itemCount`가 이미 갱신돼 있다.
  `AsyncListDiffer`였다면 한 프레임 미뤄야 했을 자리다.
- 재정렬로 인한 튐은 없다 — 1단계~2로 목록이 처음 그려질 때 이미 정렬이 끝나 있다.
- 내림차순이면 아무것도 하지 않는다(맨 위가 이미 최신).

### 5단계 검증

- 미디어 모드로 폴더를 열면 맨 아래에서 시작
- 하위 폴더 갔다 뒤로 → **원래 보던 위치** 복원(맨 아래로 튀지 않음) ← 함정 1
- **위쪽으로 스크롤해 둔 상태에서 그 폴더에 파일을 하나 만든다**(다른 앱이나 adb로).
  목록이 갱신돼도 **보던 자리에 그대로 있어야 한다** ← 함정 2
- 내림차순으로 바꾸면 맨 위에서 시작
- 바둑판 → 미디어로 전환하면 맨 아래로 간다

---

## 6단계. 폴더별 보기 모드 + 체크박스 제거

### 6.1 저장 규칙 변경

**파일**: [`filelist/FileViewTypeLiveData.kt`](../app/src/main/java/me/zhanghai/android/files/filelist/FileViewTypeLiveData.kt)

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

### 6.2 체크박스 제거

- `res/menu/file_list.xml`에서 `action_view_sort_path_specific` 항목 삭제
- `MenuBinding`의 `viewSortPathSpecificItem` 및 관련 분기 삭제
- [`FileViewSortPathSpecificLiveData.kt`](../app/src/main/java/me/zhanghai/android/files/filelist/FileViewSortPathSpecificLiveData.kt) 및
  `onViewSortPathSpecificChanged()` 삭제
- 문자열 `file_list_action_view_sort_path_specific` 제거
  (번역 파일 다수에 존재 — 미사용 문자열이라 남겨둬도 무해하나, 기본 `values/strings.xml`에서는 제거)

### 6.3 설정 화면

**파일**: `res/xml/settings.xml`, `settings/SettingsFragment.kt`

네 항목을 추가한다.

| 항목 | 내용 |
|---|---|
| **기본 보기 모드** | 전역 기본값(`FILE_LIST_VIEW_TYPE`). 목록/바둑판/미디어 중 선택. `ListPreference` |
| **기본 정렬 기준** | 전역 기본값(`FILE_LIST_SORT_OPTIONS.by`). `ListPreference` |
| **기본 정렬 방향** | 전역 기본값(`FILE_LIST_SORT_OPTIONS.order`). 오름/내림 |
| **폴더별 보기 설정 모두 초기화** | 경로별 저장소를 통째로 비운다 |

#### ⚠️ 정렬 항목을 빼먹으면 **전역 기본 정렬을 바꿀 방법이 없어진다**

§6.1이 `FileSortOptionsLiveData.putValue()`를 항상 경로별 저장으로 바꾼다. 그런데 지금
전역 정렬값을 바꾸는 경로는 **"이 폴더에만 적용"이 꺼진 상태에서 정렬 메뉴를 쓰는 것** 하나뿐이고,
§6.2가 그 체크박스를 없앤다. 즉 6단계를 마치면 전역 기본 정렬은 **아무도 건드릴 수 없는
고정값**이 된다.

`res/xml/settings.xml`에는 현재 정렬 관련 항목이 **하나도 없다**(보기 모드도 없다). 그래서
보기 모드만 설정에 노출하고 정렬을 빠뜨리기 쉽다. 기획서 D13이 정렬도 보기 모드와 **같은 규칙**
이라고 확정했으므로, 설정 노출도 같이 가야 대칭이 맞는다.

초기화 구현이 간단하다 — 경로별 설정은 **별도 SharedPreferences 파일**에 들어 있다
([SettingLiveData.kt:38](../app/src/main/java/me/zhanghai/android/files/settings/SettingLiveData.kt#L38)):

```
파일명: "<기본_prefs_이름>_path"
키    : "<pref_key>_<경로문자열>"
```

→ 그 파일 하나를 `clear()` 하면 끝난다. 확인 다이얼로그를 띄운다.

> 주의: 초기화 직후 현재 화면에 반영되려면 관련 LiveData가 갱신돼야 한다.
> `SettingLiveData`는 `OnSharedPreferenceChangeListener`로 동작하므로 `clear()`에도 콜백이 온다
> (키가 `null`로 오는 경우가 있어 확인 필요 — 안 오면 화면 재진입 시 반영으로 타협).

### 6단계 검증

- 폴더 A에서 미디어 선택 → 폴더 B는 그대로
- 앱 재시작 후에도 폴더 A만 미디어
- 설정에서 기본 보기 모드 변경 → 지정한 적 없는 폴더들만 따라 바뀜
- **설정에서 기본 정렬 기준·방향 변경 → 지정한 적 없는 폴더들만 따라 바뀜**
- 초기화 실행 → 모든 폴더가 기본값으로

---

## 7단계. 검증 · 측정

### 7.1 수용 기준

[08번 §12](08-media-view-mode-spec.md)의 16개 항목을 순서대로 확인한다.

### 7.2 실기기 측정 (최우선 리스크)

**대상**: Fold 7 (UFS 저장소). 에뮬레이터는 호스트 SSD + 페이지 캐시라 낙관적이다.

| 측정 | 방법 | 판단 |
|---|---|---|
| 사진 수백 장 폴더 진입 시간 | 변경 전/후 비교 | 체감 차이가 없으면 통과 |
| 같은 폴더 재진입 | 캐시 효과 확인 | 첫 진입보다 확연히 빨라야 함 |
| 미디어가 없는 폴더 | 문서 폴더 등 | 변경 전과 동일해야 함(비용 0) |

**느리면**: 기획서 D15를 되돌려 2차 단계로 분리한다(§8-1번).

### 7.3 커버리지 확인

테스트 데이터가 전부 `.mov`/`.mp4`다. 다음을 추가로 확인한다.

- **이미지** — JPEG(EXIF 있음/없음), PNG, HEIC
- **다른 동영상 컨테이너** — mkv, webm, 3gp → `Mp4CreationTime` 파싱 실패 시
  `MediaMetadataRetriever` 폴백이 도는지, 그 비용이 얼마인지

---

## 8단계. 날짜 타일 요일 색

기획서 **6차 개정(D23)** 으로 들어온 요구다. 5차까지 "선택 사항(없어도 됨)"이던 것이
정식 요구가 됐다.

> **왜 4단계 본문을 안 고치고 단계를 새로 붙였나** — §1의 원칙 그대로다.
> 4단계는 이미 ✅로 끝나 있어서, 본문을 고쳐 놓으면 다음에 읽는 사람이
> "4단계는 했지" 하고 넘어가 이 변경을 통째로 놓친다.

### 8.1 색상 리소스

**파일**: `res/values/colors.xml`, `res/values-night/colors.xml` (필요하면 신설)

```
media_date_saturday   토요일 파랑
media_date_sunday     일요일 빨강
```

라이트/다크 값을 **따로** 둔다. 같은 값을 쓰면 어두운 배경에서 채도가 죽어 탁해진다.
다크 쪽은 더 밝고 옅게 간다.

### 8.2 바인딩

**파일**: [`filelist/FileListAdapter.kt`](../app/src/main/java/me/zhanghai/android/files/filelist/FileListAdapter.kt) — `bindDateViewHolder()`

- **월·일 줄(`dateText`)에만** 색을 준다. 연도 줄(`yearText`)은 그대로 흐린 보조색이다.
- 요일은 타일이 들고 있는 `epochMillis`를 **기기 시간대**로 해석해 구한다.
  날짜 구간을 정할 때 쓴 것과 같은 시간대여야 한다(§4.2).

```kotlin
val dayOfWeek = Instant.ofEpochMilli(item.epochMillis)
    .atZone(ZoneId.systemDefault())
    .dayOfWeek
```

#### ⚠️ 평일에는 **기본색으로 되돌려야 한다**

뷰홀더는 재사용된다. 토요일 타일에 파랑을 칠한 뷰홀더가 그대로 수요일 타일에 재활용되면
**수요일이 파랗게 나온다.** 색을 칠하는 코드만 넣고 되돌리는 코드를 빼먹기 딱 좋은 자리다.

기본색을 상수로 적어 두면 테마(라이트/다크)를 따라가지 못하므로,
**뷰홀더를 만들 때 원래 색을 기억해 뒀다가** 평일에 그 값으로 되돌린다.

```kotlin
class DateViewHolder(binding: ...) : RecyclerView.ViewHolder(binding.root) {
    ...
    val defaultDateTextColors: ColorStateList = dateText.textColors   // 만들 때 한 번
}
```

`currentTextColor`(Int)가 아니라 `textColors`(`ColorStateList`)를 기억한다. 전자는
현재 상태의 색 하나뿐이라 되돌릴 때 상태별 색을 잃는다.

### 8단계 검증

- 토요일 타일의 월·일이 **파랑**, 일요일이 **빨강**, 나머지는 기본색
- **연도 줄은 색이 안 변한다**
- **스크롤을 위아래로 여러 번 해도 평일 타일이 물들지 않는다** (재사용 함정)
- 다크 테마로 바꿔도 두 색이 읽힌다
- 기기 시간대를 바꾸면 요일 판정이 따라 바뀐다(날짜 구간과 같은 기준)

---

## 8. 걱정되는 것들

| # | 걱정되는 것 | 어떻게 알아채나 | 그러면 어떻게 |
|---|---|---|---|
| **1** | **폴더 여는 시간이 늘어남** — 촬영 시각 읽기를 파일 목록 읽기에 같이 넣었기 때문 | 실제 폰에서 사진 폴더를 열 때 기다리는 시간이 길어짐 | §8.1 참고 |
| 2 | 썸네일 부하 — 열이 4~10개라 동시에 보이는 썸네일이 2~3배 | 스크롤 버벅임 | Coil 요청 취소가 제대로 되는지 확인. 필요 시 동시 요청 수 제한 |
| 3 | 경로별 설정 누적 — 이제 항상 저장됨 | prefs 파일 비대 | **바꾼 폴더만** 저장하므로 실제로는 제한적. 초기화 수단(5.3)이 안전판 |
| 4 | MP4 파서 견고성 | 손상 파일에서 멈추거나 크래시 | 1.1의 방어 조건 6가지를 반드시 넣고, 파싱은 예외를 삼켜 null 반환 |
| 5 | `FileItem` 변경 여파 | 컴파일 오류 | 데이터 클래스 생성자 인자가 늘어 호출부가 깨진다. `loadFileItem()` 외 생성 지점을 전수 확인 |
| 6 | upstream 병합 충돌 | 추후 rebase 시 | `FileListFragment.kt`를 크게 건드리므로 브랜치 분리(0.1). 변경을 함수 단위로 국소화 |

### 8.1 1번(폴더 여는 시간)을 자세히

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

## 9. 예상 작업량

| 단계 | 신규 파일 | 수정 파일 | 난이도 |
|---|---|---|---|
| 1 | 2 | 0 | 중 (박스 파서) |
| 2 | 0 | 4 | 하 |
| 3 | 1 | 4 | **중상** (레이아웃·오버레이·선택 표시) |
| 4 | 2 | 2 | **중상** (어댑터 항목 타입 분리가 넓게 번진다) |
| 5 | 0 | 1 | 하 |
| 6 | 0 | 6 (+1 삭제) | 중 |
| 7 | 0 | 0 | — |

3·4단계가 가장 손이 많이 간다. 나머지는 기존 구조에 얹는 수준이다.

4단계에서 실제로 시간이 드는 건 §4.2의 삽입 로직이 아니라 **§4.1의 타입 분리**다.
로직 자체는 훑으면서 끼우는 스무 줄인데, 어댑터가 파일만 다룬다는 전제로 짜인 곳이
어댑터 안팎에 흩어져 있다.

## 10. 이 계획에서 의도적으로 미룬 것

- **디스크 영속 캐시** — 메모리 LRU로 시작(1.3). 실기기 측정 후 판단.
- **정렬 기준으로서의 "미디어 생성 시각"을 목록 모드 기본값으로** — 기본은 기존 그대로.
- **점진적 목록 표시** — 1차 로딩은 여전히 "전부 아니면 전무". 별건.
- **하위 폴더 보기 설정 상속** — 기획서에서 범위 제외(L3).
- **핀치 줌, 재생 시간 배지** — 기획서 비목표.
- **"오늘"·"지난 주" 같은 상대 날짜 묶음, 월·연 단위 접기** — 기획서 비목표.
  날짜 타일은 하루 단위로 한 칸씩만 넣는다(4단계).
- **빠른 스크롤 팝업에 날짜 띄우기** — 기획서 비목표. 4단계에서는 크래시를 막을 값만 반환한다.
