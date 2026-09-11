# 15. 파일 목록 화면 정리 구현 계획서

[14번 기획서](14-file-list-ui-refresh-spec.md)의 다섯 항목을 현재 코드에 어떻게 넣을 것인가.
폴더 구분선, 회색 경로 바, 경로 시작 정렬, 노란 폴더, 즐겨찾기 바로가기를 단계별로 구현한다.

- 작성일: 2026-09-10
- 프로젝트: **PhotoExplorer** (`natae77/PhotoExplorer`, `zhanghai/MaterialFiles` fork)
- 대상 버전: 1.7.4 (versionCode 39) 기준 소스
- 브랜치: `feature/file-list-ui-refresh`
- 기준 커밋: `6c0d74f1`
- 전제: 14번 기획서 2차 개정의 D1~D12가 확정된 상태
- 상태: **구현 완료, Pixel_8 에뮬레이터·SM-F971N 실기기 release 검증 완료**

**표기**

- `14번 §3.5`처럼 문서 번호가 붙은 `§`는 기획서의 절이다.
- `단계`는 이 문서의 구현 순서다.
- 파일 경로는 별도 표시가 없으면 `app/src/main/` 아래다.

> ⚠️ **커밋은 사용자만 한다.** 각 단계에는 작업 경계를 알아볼 수 있도록 커밋 메시지
> 초안만 적는다. 구현자는 `git add`, `git commit`, `git push`를 실행하지 않는다.

> ⚠️ 이 프로젝트에는 `test`/`androidTest` 소스셋이 없다. 이번 작업은 테마, 실제 치수,
> 가로·세로 제스처와 폴더블 레이아웃이 핵심이므로 단계마다 **빌드 + 에뮬레이터/실기기
> 수동 확인**으로 검증한다. 테스트 기반을 새로 만드는 일은 이번 범위에 넣지 않는다.

## 0. 구현 원칙

1. **기존 데이터가 정본이다.** 즐겨찾기 바는 `Settings.BOOKMARK_DIRECTORIES`를 직접 관찰하며
   별도 목록, 캐시, 저장 형식이나 마이그레이션을 만들지 않는다.
2. **행 크기를 바꾸지 않는다.** 폴더 divider는 레이아웃에 1dp View를 추가하지 않고
   `RecyclerView.ItemDecoration`의 `onDraw()`에서 기존 행 뒤에 그린다.
3. **기존 상태 표현을 보존한다.** 폴더 아이콘을 표시하는 ImageView와 선택/ripple 구조는
   그대로 두고 벡터의 모양과 색만 바꾼다.
4. **상단 두 바를 한 덩어리로 둔다.** 즐겨찾기 바와 경로 바를 같은 고정 상단 탐색 컨테이너에
   넣어 앱 바 스크롤 중 하나만 남거나 서로 겹치지 않게 한다.
5. **현재 경로 변경과 목록 변경을 분리한다.** 현재 경로만 바뀔 때 칩을 다시 만들지 않고
   checked 상태만 갱신한다. 그래야 가로 스크롤 위치와 TalkBack 포커스가 불필요하게 초기화되지 않는다.
6. **논리 방향을 쓴다.** 좌우 치수는 `start`/`end`, RTL 대응 API를 사용한다.
7. **단계마다 앱이 빌드되어야 한다.** 한 단계가 끝난 뒤 화면 확인을 마치고 다음 단계로 간다.

## 1. 단계 요약

| 단계 | 만드는 것 | 화면에서 보이는 결과 | 기획서 | 상태 |
|---|---|---|---|---|
| 0 | 기준 상태 기록 | 변경 전 화면과 빌드 기준 확보 | §7, §8 | ✅ |
| 1 | 노란색 채움 폴더 | LIST·GRID·MEDIA의 폴더가 노란 채움 모양 | §3.4 | ✅ |
| 2 | 폴더 행 divider | LIST의 폴더 행 아래에만 1dp 선 | §3.1 | ✅ |
| 3 | 회색 경로 바와 시작 정렬 | 16dp 바깥 여백의 둥근 회색 바, 12dp 시작 | §3.2, §3.3 | ✅ |
| 4 | 즐겨찾기 바 표시 | 툴바와 경로 바 사이에 한 줄 칩 목록 | §3.5 위치·모양 | ✅ |
| 5 | 즐겨찾기 동작과 상태 | 이동·선택·편집·즉시 갱신·선택 해제 | §3.5 동작, §4.1 | ✅ |
| 6 | 통합 검증과 조정 | 테마·폴더블·접근성·회귀 확인 | §4~§8 | 🟨 에뮬레이터·실기기 기본 검증 완료, 접기·펼치기·TalkBack 별도 확인 필요 |

## 2. 파일 지도

### 2.1 새로 만들 파일

| 파일 | 책임 | 단계 |
|---|---|---|
| `java/me/zhanghai/android/files/filelist/FileListDividerItemDecoration.kt` | LIST 폴더 행 아래 divider를 행 크기 변경 없이 그림 | 2 |
| `java/me/zhanghai/android/files/filelist/BookmarkBarLayout.kt` | 즐겨찾기 칩 생성·순서·선택·클릭·길게 누르기·가로 스크롤 | 4~5 |
| `res/layout/bookmark_directory_chip.xml` | 48dp 터치 높이, 200dp 최대 폭의 한 줄 체크 가능 Chip | 4 |
| `res/drawable/file_list_path_bar_background.xml` | 24dp 모서리의 경로 바 배경 | 3 |

### 2.2 수정할 파일

| 파일 | 변경 | 단계 |
|---|---|---|
| `res/drawable/file_directory_icon.xml` | 24dp 윤곽 폴더를 노란 채움 폴더 path로 변경 | 1 |
| `res/drawable/file_directory_thumbnail.xml` | GRID·MEDIA 폴더 벡터를 `file_icon_yellow`로 변경 | 1 |
| `java/me/zhanghai/android/files/filelist/FileListAdapter.kt` | decoration이 현재 child에 bind된 폴더 상태를 안전하게 조회하는 함수 추가 | 2 |
| `java/me/zhanghai/android/files/filelist/BreadcrumbLayout.kt` | 활성 breadcrumb에 테마 강조색을 적용하고 경로 바 배경과의 대비를 확인 | 3 |
| `res/layout/file_list_fragment_app_bar_include.xml` | 상단 탐색 컨테이너, 즐겨찾기 바, 경로 바 배경·여백·정렬 | 3~4 |
| `res/values/colors.xml` | 밝은 경로 바 색 `#F1F3F4` | 3 |
| `res/values-night/colors.xml` | 어두운·검정 야간 경로 바 색 `#303134` | 3 |
| `res/values/dimens.xml` | 경로 바와 칩의 확정 치수에 이름을 부여 | 3~4 |
| `java/me/zhanghai/android/files/filelist/FileListFragment.kt` | decoration 설치, bookmark 관찰·listener, 이동 전 상태 정리 | 2, 4~5 |

`FileListFragment.Binding`은 include의 ViewBinding을 그대로 보관하지 않고 필요한 View를 직접
나열한다. `bookmarkBarLayout`을 XML에 추가한 뒤 이 내부 Binding의 생성자 프로퍼티와
`inflate()` 인수에도 반드시 추가한다. 빠뜨리면 XML은 빌드되어도 Fragment에서 접근할 수 없다.

### 2.3 만들지 않을 것

- 즐겨찾기 전용 ViewModel 또는 저장소
- 즐겨찾기용 문자열 복사본
- 폴더별 전용 아이콘과 비트맵
- GRID·MEDIA divider
- 경로 바용 M2/M3 별도 레이아웃
- 선택 상태 저장용 SharedPreferences
- 새 테스트 소스셋과 UI 테스트 의존성

## 3. 확정 치수와 자원

| 이름 제안 | 값 | 쓰임 |
|---|---:|---|
| `file_list_path_bar_margin_horizontal` | 16dp | 경로 바 바깥 start/end 여백 |
| `file_list_path_bar_corner_radius` | 24dp | 높이 48dp인 경로 바의 완전한 캡슐 모양 |
| `bookmark_bar_padding_horizontal` | 16dp | 첫·마지막 칩과 화면 가장자리 사이 |
| `bookmark_chip_spacing` | 8dp | 칩 사이 간격 |
| `bookmark_chip_max_width` | 200dp | 텍스트와 좌우 내부 여백을 포함한 전체 칩 상한 |
| 기존 `tab_layout_height` | 48dp | 경로 바 높이 |
| 기존 `touch_target_size` | 48dp | 칩 최소 터치 높이 |
| 기존 `content_start_margin` | 기본 72dp, `sw600dp` 80dp | LIST 텍스트 시작점과 divider start |
| 기존 `screen_edge_margin` | 기본 16dp, `sw600dp` 24dp | divider end 여백 |
| 기존 `horizontal_divider_height` | 1dp | divider 두께 |

경로 바 색은 같은 이름의 day/night 자원으로 분리한다.

```xml
<!-- res/values/colors.xml -->
<color name="file_list_path_bar_background">#F1F3F4</color>

<!-- res/values-night/colors.xml -->
<color name="file_list_path_bar_background">#303134</color>
```

검정 야간 모드도 `values-night`의 `#303134`를 사용한다. 이 모드가 테마에서
`colorSurface=#000000`으로 덮어써도 경로 바 전용 색은 변하지 않는다. M3 동적 색상도 경로
바 배경은 덮어쓰지 않으며, 활성 breadcrumb와 선택된 칩에만 현재 테마의 강조색을 쓴다.

---

## 0단계. 기준 상태와 빌드 확보

기능을 넣기 전에 회귀 비교에 필요한 화면과 명령을 고정한다.

### 0.1 워킹트리와 기준 확인

- [ ] `git status --short --branch`로 기존 사용자 변경을 기록한다.
- [ ] 현재 브랜치가 `feature/file-list-ui-refresh`, HEAD가 `6c0d74f1` 계열인지 확인한다.
- [ ] 사용자 변경 파일은 되돌리거나 스테이징하지 않는다.

### 0.2 변경 전 화면 기록

같은 폴더를 아래 조합으로 캡처한다.

- [ ] LIST: 폴더 둘 이상과 파일 둘 이상, 폴더 우선 켬
- [ ] LIST: 폴더 우선 끔으로 폴더와 파일이 섞인 상태
- [ ] GRID와 MEDIA
- [ ] 짧은 breadcrumb와 화면 폭보다 긴 breadcrumb
- [ ] 즐겨찾기 0개, 1개, 화면 폭보다 많은 상태
- [ ] 밝은, 어두운, 검정 야간 테마
- [ ] 접힌 화면과 펼친 화면

### 0.3 빌드

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```

- [ ] 빌드가 통과한다.
- [ ] 설치한 APK의 버전과 대상 브랜치가 맞는지 확인한다.

**커밋 메시지 초안:** 없음 — 기준 기록만 한다.

---

## 1단계. 노란색 채움 폴더 아이콘

어댑터와 레이아웃은 건드리지 않고 두 벡터 자산만 바꾼다.

### 1.1 LIST 아이콘

`res/drawable/file_directory_icon.xml`은 현재 회색 윤곽 path다.

- [ ] `android:fillColor`를 `@color/file_icon_yellow`로 바꾼다.
- [ ] pathData를 Material의 24dp 채움 폴더 실루엣으로 교체한다.
- [ ] vector의 width/height/viewport는 24dp/24×24를 유지한다.

`MimeTypeIcon.DIRECTORY`가 이 drawable을 가리키고 `FileListAdapter`가 모든 파일 보기에서
`file.mimeType.iconRes`를 사용하므로 별도 Kotlin 분기는 만들지 않는다.

### 1.2 GRID·MEDIA 폴더 썸네일

`res/drawable/file_directory_thumbnail.xml`은 이미 채움 실루엣이다.

- [ ] path 모양은 유지하고 `fillColor`만 `@color/file_icon_yellow`로 바꾼다.
- [ ] GRID와 MEDIA가 같은 drawable을 계속 공유하게 한다.

### 1.3 상태 보존 확인

- [ ] LIST 폴더 아이콘은 24dp다.
- [ ] GRID·MEDIA 폴더 타일의 폴더 모양과 이름이 기존 위치에 남는다.
- [ ] 파일 아이콘과 미디어 썸네일 색은 바뀌지 않는다.
- [ ] 폴더 선택, 비활성화, 복사·이동 대기 상태에서 기존 alpha와 badge가 유지된다.
- [ ] 밝은 테마는 기존 `#F4B400`, 야간은 기존 `#F8CF5C`를 사용한다.

**커밋 메시지 초안:** `Use filled yellow folder icons in file lists`

---

## 2단계. LIST 폴더 행 divider

`file_item_list.xml`에 View를 넣으면 72dp 행의 측정이나 터치 범위를 잘못 바꾸기 쉽다.
offset을 추가하지 않는 `ItemDecoration`으로 선만 그린다.

### 2.1 bind된 child 상태 조회

`FileListAdapter`에 decoration 전용 읽기 함수를 둔다. adapter position으로
현재 목록을 다시 조회하지 않고, `ViewHolder`에 직전 bind 때의 폴더 여부를 보관한다.
DiffUtil pre-layout·삭제·이동 애니메이션 중에도 화면의 child와 새 adapter position이 엇갈리지
않게 하기 위해서다.

```kotlin
fun isDirectoryChild(recyclerView: RecyclerView, child: View): Boolean {
    val holder = recyclerView.getChildViewHolder(child) as? ViewHolder ?: return false
    return holder.isDirectory
}
```

- [ ] `ViewHolder.isDirectory`는 파일 항목을 bind할 때마다 현재 항목으로 갱신한다.
- [ ] date holder이거나 예상하지 못한 holder인 경우 false가 된다.
- [ ] 파일 목록 자체를 외부에 노출하지 않는다.
- [ ] 폴더 우선 설정과 무관하게 해당 child에 실제로 bind된 항목만 판정한다.

### 2.2 `FileListDividerItemDecoration`

- [ ] 생성자에서 divider drawable과 `horizontal_divider_height`, `content_start_margin`,
  `screen_edge_margin`을 자원으로 읽는다. 값을 72dp/16dp로 하드코딩하지 않는다.
- [ ] 기본 화면에서는 start 72dp/end 16dp이고, `sw600dp`에서는 기존 행 레이아웃과 같은
  start 80dp/end 24dp가 되어 텍스트 시작점과 화면 여백을 계속 맞춘다.
- [ ] `onDraw()`를 사용해 child보다 아래 계층에 그린다. `onDrawOver()`는 쓰지 않는다.
- [ ] `adapter.viewType != FileViewType.LIST`이면 아무것도 그리지 않는다.
- [ ] 각 visible child에 대해 `adapter.isDirectoryChild(recyclerView, child)`를 조회하고 false면
  건너뛴다.
- [ ] `y = round(child.bottom + child.translationY)`를 기준으로 세로 bounds를
  `[y - dividerHeight, y]`로 잡는다. offset이 없으므로 `[y, y + dividerHeight]`로
  다음 행 영역에 그리지 않는다.
- [ ] child에 bind된 상태와 `translationY`를 함께 사용해 DiffUtil 삭제·이동
  애니메이션 중에도 선이 해당 행과 함께 움직이게 한다.
- [ ] `getItemOffsets()`는 override하지 않거나 0을 반환해 행 높이를 늘리지 않는다.
- [ ] bounds는 LTR/RTL 모두 논리 start 72dp, end 16dp가 되게 계산한다.

divider 색은 테마의 표준 list divider drawable을 가져와 사용한다. drawable의 고유 높이에
의존하지 않고 bounds 높이를 `horizontal_divider_height` 1dp로 고정한다.

### 2.3 Fragment에 한 번만 설치

`FileListFragment.onActivityCreated()`에서 adapter를 RecyclerView에 붙인 직후 한 번 추가한다.

```kotlin
binding.recyclerView.addItemDecoration(FileListDividerItemDecoration(requireContext(), adapter))
```

보기 모드를 바꿀 때 decoration을 제거·재추가하지 않는다. decoration 자체가 현재 viewType을
확인하고 RecyclerView는 adapter 변경 시 다시 그린다.

### 2.4 검증

- [ ] LIST의 폴더 행 아래에만 선이 보인다.
- [ ] 마지막 폴더와 첫 파일 사이에도 보인다.
- [ ] 폴더 우선 끔에서 중간에 섞인 폴더마다 바로 아래에 보인다.
- [ ] 파일→파일 사이에는 새 선이 없다.
- [ ] GRID·MEDIA와 MEDIA 날짜 타일에는 선이 없다.
- [ ] 행 높이와 스크롤 위치가 변경 전과 같다.
- [ ] 선택 배경과 ripple이 divider 위에서 정상적으로 보인다.
- [ ] 추가·삭제 DiffUtil 애니메이션 중 선이 다른 행에 남지 않는다.

**커밋 메시지 초안:** `Add dividers below folder rows in list view`

---

## 3단계. 회색 경로 바와 논리 start 정렬

### 3.1 색과 배경 drawable

- [ ] 3절의 `file_list_path_bar_background` day/night 색을 추가한다.
- [ ] `file_list_path_bar_background.xml`을 shape drawable로 만든다.
- [ ] solid는 위 색 자원, corners는 24dp로 한다.
- [ ] stroke와 elevation은 추가하지 않는다.

### 3.2 앱 바 레이아웃 재구성

`file_list_fragment_app_bar_include.xml`에서 toolbar FrameLayout 다음에 세로
`LinearLayout`인 상단 탐색 컨테이너를 둔다. 이 컨테이너 안의 순서는 다음과 같다.

```text
BookmarkBarLayout       — 4단계에서 추가, 0개이면 GONE
BreadcrumbLayout        — 항상 표시
```

상단 탐색 컨테이너에는 `layout_scrollFlags`를 주지 않는다. 기존 toolbar FrameLayout만 현재의
`file_list_toolbar_scroll_flags`를 유지한다. 따라서 앱 바가 움직여도 즐겨찾기와 breadcrumb는
같은 고정 영역에 있고 둘 중 하나만 따로 이동하지 않는다.

### 3.3 breadcrumb 여백과 padding

`BreadcrumbLayout`에 다음을 적용한다.

- [ ] width `match_parent`, height `wrap_content` 유지
- [ ] start/end 바깥 margin 16dp
- [ ] background `@drawable/file_list_path_bar_background`
- [ ] `android:clipToOutline="true"`로 ripple이 둥근 바 밖으로 새지 않게 함
- [ ] 기존 XML의 start 60dp, end 4dp padding 제거

`breadcrumb_item.xml` 자체가 start 12dp와 최종 end 합계 12dp를 이미 제공한다. 따라서
`BreadcrumbLayout`에 12dp를 또 넣지 않는다. 중복 padding을 넣으면 첫 글자가 24dp에서
시작한다. XML 바깥 padding을 0으로 만들면 `BreadcrumbLayout.init`이 내부 `itemsLayout`에
0을 전달하고, 첫 item의 기존 12dp가 곧 경로 바의 안쪽 시작 여백이 된다.

현재 `scrollToSelectedItem()`은 선택 item의 left/right에 맞춰 스크롤한다. 긴 경로에서는
선택 item의 경계가 바 start로 오고 실제 텍스트는 item padding 12dp 뒤에 보여, 자동 스크롤과
12dp 규칙을 함께 만족한다. 이 로직은 바꾸지 않는다.

### 3.4 테마와 접근성 검증

현재 `BreadcrumbLayout.itemColor`의 activated 색은 `textColorPrimary`다. 14번 §3.2의
"활성 breadcrumb에 사용자의 강조색 반영"을 실제 구현하기 위해 activated 상태의 기준색을
현재 테마의 `colorPrimary`에서 가져온다. 비활성 상태는 기존 `textColorSecondary`를 유지하고,
텍스트와 화살표에는 같은 state color를 적용한다.

밝은 테마의 파랑·노랑·연두 등 여러 `colorPrimary`는 `#F1F3F4` 위에서 4.5:1을
충족하지 않으므로 대비 보정을 예외 처리가 아닌 기본 경로로 구현한다.

1. 실제 해상된 `colorPrimary`와 `file_list_path_bar_background`를
   `ColorUtils.calculateContrast()`로 계산한다.
2. 이미 4.5:1 이상이면 원색을 그대로 쓴다.
3. 미달이면 원색을 검정 또는 흰색 방향으로 `ColorUtils.blendARGB()`하며, 각 방향에서
   4.5:1을 처음 만족하는 최소 혼합 비율을 이진 탐색한다.
4. 두 결과 중 원색과의 혼합 비율이 더 작은 색을 접근성용 activated 색으로 쓴다.
   수치 오차로 둘 다 실패하면 배경과 대비가 더 큰 검정/흰색을 fallback으로 쓴다.

이 계산은 `BreadcrumbLayout`이 테마를 받아 생성될 때 한 번 수행하며, 별도의 고정 RGB
색 자원은 만들지 않는다. 실제 구현이 이 알고리즘과 달라진 경우에만 §6.7에 이유와
방식을 기록한다.

- [ ] 밝은 테마: 흰 본문과 `#F1F3F4` 바가 구분된다.
- [ ] 일반 야간: `#202124` 계열 본문과 `#303134` 바가 구분된다.
- [ ] 검정 야간: `#000000` 본문과 `#303134` 바가 구분된다.
- [ ] M2/M3 각각에서 활성 경로는 대비를 충족하는 테마 강조색 계열, 이전 경로는 기존
  `textColorSecondary`로 표시된다.
- [ ] 일반 크기 경로 글자는 배경과 4.5:1 이상의 대비를 가진다.
- [ ] 밝은 테마의 노랑·연두·시안 계열 사용자 색과 M3 동적 색에서도 보정 후
  activated 색의 대비가 4.5:1 이상이다.
- [ ] 짧은 경로는 가운데로 가지 않고 start 12dp에서 시작한다.
- [ ] 긴 경로 자동 스크롤, 탭, 길게 누르기 메뉴가 그대로다.
- [ ] RTL에서는 오른쪽 12dp에서 시작한다.

**커밋 메시지 초안:** `Style breadcrumbs as an inset path bar`

---

## 4단계. 즐겨찾기 바로가기 바 표시

### 4.1 `bookmark_directory_chip.xml`

Material `Chip` 하나를 정의한다.

- [ ] filter/checkable Chip 계열 스타일을 사용한다.
- [ ] `layout_width="wrap_content"`, 최소 높이 48dp
- [ ] `maxWidth="@dimen/bookmark_chip_max_width"` = 200dp
- [ ] `maxLines="1"`, `ellipsize="end"`
- [ ] checkable true, checked icon과 일반 chip icon은 표시하지 않음
- [ ] 텍스트는 코드에서 `BookmarkDirectory.name`을 넣음

선택 시 현재 테마의 filter Chip checked 표현을 사용하되, checked 배경/테두리에서 현재
`colorPrimary` 계열이 보이는지 M2/M3에서 확인한다. 기본 스타일이 강조색을 반영하지 않는
조합이 있으면 그때 state color list를 추가한다. 색을 고정 RGB로 만들지는 않는다.

### 4.2 `BookmarkBarLayout`

`BreadcrumbLayout`과 같은 package의 `HorizontalScrollView`로 만든다.

- [ ] horizontal scroll bar를 숨긴다.
- [ ] 내부에 horizontal `LinearLayout` 하나를 둔다.
- [ ] 첫·마지막 16dp padding과 칩 사이 8dp 간격을 논리 start/end로 적용한다.
- [ ] 외부 높이는 내용에 맞추되 각 칩의 터치 높이는 48dp다.
- [ ] `BookmarkDirectory.id`는 `Long` 그대로 `Map<Long, Chip>`의 안정 식별자로 사용한다.
  `Int`로 잘라 Android `View.id`에 넣지 않는다. 그렇게 하면 상위 32bit 손실,
  충돌, `View.NO_ID` 문제가 생길 수 있다.
- [ ] 목록이 바뀌지 않고 currentPath만 바뀌면 child를 재생성하지 않고 checked만 갱신한다.
- [ ] 목록 변경 시 저장 순서대로 child를 재배치하고 이름·listener를 다시 bind한다.
- [ ] 0개이면 View 자체를 `GONE`으로 바꿔 높이와 여백을 모두 없앤다.

공개 API의 형태는 다음 정도로 제한한다.

```kotlin
fun setListener(listener: Listener)
fun setBookmarkDirectories(bookmarkDirectories: List<BookmarkDirectory>)
fun setCurrentPath(path: Path)

interface Listener {
    fun navigateTo(bookmarkDirectory: BookmarkDirectory)
    fun edit(bookmarkDirectory: BookmarkDirectory)
}
```

currentPath는 초기 설정 순서상 아직 없을 수 있으므로 내부에는 nullable로 보관한다. 두 setter가
어느 순서로 호출되어도 크래시하거나 잘못된 칩을 선택하지 않아야 한다.

### 4.3 앱 바에 배치

- [ ] 3단계에서 만든 상단 탐색 컨테이너의 breadcrumb 바로 위에 `BookmarkBarLayout`을 둔다.
- [ ] 초기 visibility는 `gone`으로 둔다. Settings observer가 목록을 받으면 결정한다.
- [ ] 별도 배경이나 두 번째 경로 바 모양을 만들지 않는다.
- [ ] 부모 폭이 파일 콘텐츠 폭이므로 persistent drawer 아래로 뻗는 별도 폭 계산을 하지 않는다.

### 4.4 Fragment에 데이터 연결

`onActivityCreated()`에서 listener를 연결하고 다음 두 LiveData를 관찰한다.

```kotlin
Settings.BOOKMARK_DIRECTORIES.observe(viewLifecycleOwner) {
    binding.bookmarkBarLayout.setBookmarkDirectories(it)
}
viewModel.currentPathLiveData.observe(viewLifecycleOwner) {
    onCurrentPathChanged(it)
    binding.bookmarkBarLayout.setCurrentPath(it)
}
```

기존 currentPath observer를 두 개로 중복 등록하지 말고 한 observer 안에서 두 작업을 호출한다.
Settings는 이미 `SettingLiveData`이므로 편집·삭제·순서 변경 뒤 현재 화면에 즉시 새 값이 온다.

### 4.5 표시 검증

- [ ] 저장 순서와 칩 순서가 같다.
- [ ] 사용자 이름이 있으면 사용자 이름, 없으면 폴더 이름이다.
- [ ] 같은 경로·다른 이름의 중복 즐겨찾기도 각각 보인다.
- [ ] 200dp를 넘는 이름은 한 줄 끝 말줄임된다.
- [ ] 여러 칩은 두 줄로 감기지 않고 가로 스크롤된다.
- [ ] 0개이면 breadcrumb가 바로 위로 당겨진다.
- [ ] 회전 후 데이터가 달라지지 않는다.

**커밋 메시지 초안:** `Show bookmark shortcuts above the path bar`

---

## 5단계. 즐겨찾기 동작과 탐색 상태

### 5.1 선택 상태

각 칩을 bind하거나 currentPath가 바뀔 때 다음 값을 계산한다.

```kotlin
val checked = currentPath != null && currentPath == bookmarkDirectory.path
chip.isChecked = checked
```

- [ ] ID나 이름이 아닌 `Path.equals`에 해당하는 `==`를 쓴다.
- [ ] 같은 path의 중복 즐겨찾기는 모두 checked다.
- [ ] Chip의 checked 상태가 선택 의미로 TalkBack 접근성 정보에 노출된다.
- [ ] 선택된 칩은 현재 테마 강조색을 반영한다.

### 5.2 탭과 같은 경로 no-op

- [ ] 다른 경로 칩 탭은 listener로 Fragment에 전달한다.
- [ ] 현재 경로와 같은 칩 탭은 실제 탐색을 호출하지 않는다.
- [ ] 같은 경로 no-op에서는 검색과 파일 선택 상태도 바꾸지 않는다.

`Chip`은 checkable이면 클릭 자체로 checked 값을 먼저 토글한다. 따라서 현재 경로 칩을 눌러
탐색을 no-op 처리하기만 하면 선택 표시가 해제된 채 남는다. 각 칩의 click listener에서는
프레임워크 토글 결과를 믿지 않고 `currentPath == bookmarkDirectory.path`로 checked 상태를
즉시 다시 맞춘 뒤, 다른 경로일 때만 Fragment listener를 호출한다. 다른 경로의 observer가
도착하기 전에도 기존 칩과 누른 칩이 잘못된 선택 상태로 남지 않도록, click 처리 시 모든 칩의
checked 상태를 현재 경로 기준으로 한 번 갱신한다.

Fragment에서는 즐겨찾기만을 위한 별도 navigation 함수를 만들지 않고 기존 `navigateTo(Path)`로
들어오게 한다. breadcrumb와 즐겨찾기가 같은 정리 규칙을 쓰게 하기 위해 이 함수의 다른 경로
분기를 다음 순서로 보강한다.

```kotlin
collapseSearchView()
if (path != currentPath) {
    viewModel.clearSelectedFiles()
}
val state = layoutManager.onSaveInstanceState()
viewModel.navigateTo(state!!, path)
```

같은 경로 칩의 no-op은 `BookmarkBarLayout`이 listener를 호출하지 않는 곳에서 완결한다.
공용 `navigateTo()` 첫 줄에서 같은 경로를 return하면 탐색 서랍 등 기존 호출자의 검색 닫기와
탐색 흐름까지 바뀌므로 그렇게 하지 않는다. 이 `navigateTo()`는 breadcrumb와 탐색 서랍에서도
쓰이므로, **다른 경로일 때만** 이전 폴더의 선택 파일을 지우고 나머지 기존 동작은
보존한다. 폴더 목록에서 폴더를 여는 기존 흐름에도 적용되지만, 선택이 없는 일반 탭에는
동작 차이가 없다.

### 5.3 길게 누르기

칩의 long-click은 탐색 서랍의 `BookmarkDirectoryItem.onLongClick()`과 같은 intent를 만든다.

```kotlin
EditBookmarkDirectoryDialogActivity::class.createIntent()
    .putArgs(EditBookmarkDirectoryDialogFragment.Args(bookmarkDirectory))
```

- [ ] `FileListFragment`가 안전한 startActivity helper로 실행한다.
- [ ] long-click listener는 `true`를 반환해 뒤이은 click 이동을 막는다.
- [ ] 대화상자에서 이름·경로 변경 후 칩이 즉시 갱신된다.
- [ ] 삭제 후 칩이 즉시 사라지고 마지막 칩이었다면 바 전체가 GONE이 된다.
- [ ] 편집 중 기기 회전은 기존 dialog activity의 상태 복원에 맡긴다.

### 5.4 접근할 수 없는 경로

새로운 사전 존재 검사나 별도 오류 UI를 만들지 않는다. 기존 `viewModel.navigateTo()`와
file list loading의 Failure 처리를 그대로 거친다. 실패했다고 즐겨찾기 자체를 삭제하거나
순서를 바꾸지 않는다.

### 5.5 동작 검증

- [ ] 다른 칩 탭: 검색 닫힘 → 파일 선택 해제 → 같은 작업에서 경로 이동
- [ ] breadcrumb 상위 경로 탭도 같은 순서로 정리
- [ ] 현재 경로 칩 탭은 no-op이며 검색·선택 유지
- [ ] 현재 경로인 탐색 서랍 항목 탭은 공용 함수의 선행 return으로 새로 no-op되지
  않고 기준 화면의 검색 닫기·탐색·drawer 닫기 동작을 유지
- [ ] 중복 path 칩은 모두 선택 표시
- [ ] 칩 길게 누르기 뒤 경로 이동이 발생하지 않음
- [ ] 편집·삭제·재정렬 결과가 화면을 다시 열지 않아도 반영
- [ ] 접근 불가 경로에서 기존 오류가 보이고 앱이 종료되지 않음

**커밋 메시지 초안:** `Connect bookmark shortcuts to file navigation`

---

## 6단계. 통합 검증과 문서 갱신

### 6.1 빌드와 정적 검사

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew lintDebug
```

- [ ] debug 빌드 통과
- [ ] 새 lint 오류 없음
- [ ] 가능하면 release/R8 빌드도 통과. 서명 정보가 없으면 debug까지만 필수로 하고 이유 기록
- [ ] `git diff --check` 통과

### 6.2 테마 행렬

아래 6조합에서 같은 폴더와 긴 경로를 확인한다.

| Material 설정 | 테마 | 확인 |
|---|---|---|
| M2 | 밝음 | 경로 바 `#F1F3F4`, divider, 짙은 노란 폴더, 칩 선택 |
| M2 | 어두움 | 경로 바 `#303134`, divider, 밝은 노란 폴더, 글자 대비 |
| M2 | 검정 야간 | 검정 본문과 `#303134` 경로 바 구분 |
| M3 | 밝음 | 동적/사용자 강조색이 활성 breadcrumb·선택 칩에 반영 |
| M3 | 어두움 | 중성 경로 바는 고정, 활성 요소만 강조색 |
| M3 | 검정 야간 | 본문과 바 구분, ripple clipping |

### 6.3 크기와 폴더블

- [ ] 좁은 일반 폰에서 200dp 칩이 화면을 독점하지 않는다.
- [ ] Galaxy Z Fold8 일반형 접힌 화면의 약 360dp 논리 폭에서 200dp 칩은 화면의 약 56%,
  좌우 16dp를 제외한 영역의 약 61%다.
- [ ] 접기·펼치기 후 칩이 두 줄이 되거나 path bar가 drawer 아래로 뻗지 않는다.
- [ ] persistent drawer가 열린 넓은 화면에서 두 바는 파일 콘텐츠 폭만 사용한다.
- [ ] 회전·접기·펼치기 뒤 breadcrumb 선택 item 자동 스크롤이 동작한다.
- [ ] 즐겨찾기 가로 위치가 currentPath 갱신만으로 매번 처음으로 돌아가지 않는다.

### 6.4 제스처와 앱 바

- [ ] 즐겨찾기 위 가로 drag는 칩 목록만 움직인다.
- [ ] 경로 바 위 가로 drag는 breadcrumb만 움직인다.
- [ ] 파일 목록 세로 drag와 swipe-to-refresh는 기존처럼 동작한다.
- [ ] 양쪽 화면 가장자리에서 뒤로가기 제스처와 과도하게 충돌하지 않는다.
- [ ] toolbar가 스크롤될 때 즐겨찾기와 경로 바가 같이 고정되고 서로 겹치지 않는다.
- [ ] 즐겨찾기 0개가 되는 순간 앱 바 높이와 content bottom 보정이 정상 갱신된다.

### 6.5 접근성

- [ ] TalkBack 순서: 툴바 → 즐겨찾기 칩 → breadcrumb → 파일 목록
- [ ] 칩은 보이는 이름을 그대로 읽는다.
- [ ] 선택 칩은 checked 상태를 읽는다.
- [ ] 칩의 실제 터치 높이가 48dp 이상이다.
- [ ] 노란색을 구별하지 않아도 채움 폴더 실루엣과 파일 아이콘 모양이 다르다.
- [ ] RTL 테스트 로케일에서 두 가로 목록의 순서와 start/end 여백이 뒤집힌다.

### 6.6 회귀 확인

- [ ] 검색 시작·결과·닫기
- [ ] 다중 선택, 전체 선택, 선택 취소
- [ ] LIST·GRID·MEDIA 전환
- [ ] 정렬 기준·방향·폴더 우선 변경
- [ ] breadcrumb 탭·길게 누르기·복사·새 작업에서 열기
- [ ] 탐색 서랍의 즐겨찾기 탭·길게 누르기
- [ ] 탐색 서랍에서 현재 경로를 다시 눌렀을 때 기준 화면과 동일한 상태 정리·drawer
  닫기 동작
- [ ] 파일 선택기 인텐트의 파일/폴더 선택
- [ ] 복사·이동 대기 상태와 붙여넣기
- [ ] 빈 폴더·로딩·오류 화면

### 6.7 문서에 실제 결과 기록

- [ ] 이 문서 §1의 단계 상태를 갱신한다.
- [ ] 계획과 달라진 구현이 있으면 이유와 실제 방식을 별도 표로 남긴다.
- [ ] 14번 §8의 실기기 결과를 기획서 또는 이 문서에 기록한다.
- [ ] 다른 기계에서도 필요한 판단은 반드시 이 git 추적 문서에 남긴다.

### 6.8 2026-09-11 구현·검증 결과

| 항목 | 실제 결과 |
|---|---|
| 기준 | `feature/file-list-ui-refresh`, 작업 시작 HEAD `ee2e83e2`; 기존 워킹트리 변경 없음 |
| 빌드 | `assembleDebug` 성공. 생성 APK를 API 36 Pixel_8 AVD에 설치하고 cold launch 성공. 서명 설정 없이 실행한 최초 `assembleRelease`는 R8·리소스 최적화까지 통과한 뒤 로컬 release `storeFile` 부재로 `packageRelease`에서 중단했으며, 이후 디버그 키를 지정한 release 빌드와 실기 검증은 아래 §6.9에서 완료 |
| lint | `lintDebug` 실행 완료. 이번 변경 파일의 새 오류는 없으나 기존 `VideoDetails.kt`의 Media3 `UnsafeOptInUsageError` 4건(92~94, 111행) 때문에 전체 task는 실패 |
| LIST | 밝음·어두움·검정 야간에서 노란 채움 폴더와 폴더 행 divider 확인. 폴더 우선을 끈 혼합 목록에서 파일→파일에는 선이 없고 폴더 행 아래에만 선이 남는 것 확인 |
| GRID·MEDIA | 두 보기 모두 노란 폴더 썸네일 확인. MEDIA 날짜 타일과 미디어 썸네일에는 LIST divider가 생기지 않음 |
| 경로 바 | 16dp 논리 바깥 여백, 48dp 높이, 둥근 배경, 12dp 텍스트 시작, 긴 breadcrumb 가로 스크롤 확인 |
| 즐겨찾기 | `Screenshots` 칩 표시·48dp 터치 높이·탭 이동·현재 경로 checked 노출 확인. 현재 경로 칩을 다시 탭해도 펼친 검색이 유지되는 no-op 확인. 길게 누르면 편집 대화상자가 열리고 클릭 이동이 뒤따르지 않음 |
| 오류 경로 | 존재하지 않는 기존 `Screenshots` 즐겨찾기로 이동했을 때 기존 `NoSuchFileException` 오류 화면을 표시하고 앱은 종료되지 않음 |
| 테마 | M2/M3 각각 밝음·어두움·검정 야간의 6조합 확인. 밝은 바는 `#F1F3F4`, 두 야간 모드는 `#303134`; 검정 본문에서도 바가 구분됨 |
| 테스트 후 상태 | AVD 설정을 M3 켬, 시스템 밝음, 검정 야간 끔, 폴더 우선 켬으로 복원 |

Pixel_8 AVD에서는 실제 폴더블 동작과 실기기 제스처를 확인하지 않았다. 아래 §6.9에서 실기기
기본 화면과 탭·길게 누르기·보기 전환을 추가로 검증했다. 실제 접기·펼치기, `sw600dp`
persistent drawer, RTL, TalkBack 포커스 순서는 아직 확인하지 않았다. 0개/다수 즐겨찾기와
편집·삭제·재정렬의 즉시 반영도 기존 데이터를 훼손하지 않기 위해 이번 수동 실행에서는
변경하지 않았다. 해당 동작은 `SettingLiveData`를 `viewLifecycleOwner`로 직접 관찰하고 목록 변경
시에만 칩을 다시 만드는 구현으로 연결되어 있다.

### 6.9 2026-09-11 실기기 release 설치·검증 결과

| 항목 | 실제 결과 |
|---|---|
| 빌드 | 표준 Android 디버그 키를 release 서명 설정으로 지정해 `assembleRelease` 성공. R8, `lintVitalRelease`, 리소스 최적화, `packageRelease`까지 완료. APK 크기 10,940,607 bytes |
| 서명 | `apksigner verify --verbose --print-certs` 통과. v1·v2 서명 유효, 인증서 DN `C=US, O=Android, CN=Android Debug`, SHA-256 `a53f49a692d609f06c40875deeeb74a548fbb7d424242295d57f999a2c0f3585` |
| 기기 | Samsung `SM-F971N`, Android API 37, 1248×1972, 420 dpi |
| 설치·실행 | `adb install -r` 성공. `com.natae.photoexplorer/me.zhanghai.android.files.filelist.FileListActivity` cold launch 성공. 설치 버전 `1.7.4` (`versionCode=39`) |
| LIST | 밝은 M3 테마에서 노란 채움 폴더 아이콘, 폴더 행 divider, 16dp 바깥 여백의 회색 둥근 경로 바 확인 |
| 즐겨찾기 | 한 줄에 4개 칩 표시 확인. `Camera` 탭으로 이동하고 checked 상태 확인. 검색을 연 상태에서 현재 경로 칩을 다시 탭해 검색이 유지되는 no-op 확인. 길게 눌러 편집 대화상자가 열리는 것 확인 후 변경 없이 닫음 |
| GRID·MEDIA | GRID의 2열 노란 폴더 타일과 MEDIA의 4열 폴더 타일 확인. 테스트 후 LIST와 기기 저장공간 루트로 복원 |
| 안정성 | 테스트 구간 logcat에 `FATAL EXCEPTION`이나 앱 crash 없음. 확인된 오류 로그는 Samsung/Adreno의 비치명적 vendor 로그뿐 |
| 정리 | 즐겨찾기 데이터는 변경하지 않았고, 기기에 만든 테스트용 스크린샷 2개 삭제 완료 |

**커밋 메시지 초안:** `Verify file list UI refresh across layouts and themes`

## 7. 완료 조건

다음이 모두 참일 때 구현 완료로 본다.

1. 14번 §7의 수용 기준 18개가 모두 확인됐다.
2. debug build와 lint가 통과했다.
3. 밝은·어두운·검정 야간 및 M2/M3 조합을 확인했다.
4. LIST의 폴더 divider가 행 크기와 다른 보기 모드에 영향을 주지 않는다.
5. 즐겨찾기 변경이 현재 화면에 즉시 반영되고 별도 저장 데이터가 생기지 않았다.
6. 다른 경로 이동 전 검색과 이전 폴더의 파일 선택이 정리된다.
7. 접힌 화면·펼친 화면·persistent drawer에서 상단 두 바의 폭과 스크롤이 정상이다.
8. 구현 결과와 계획 차이가 이 문서에 기록됐다.

## 8. 구현 중 특히 조심할 함정

| 함정 | 결과 | 방지책 |
|---|---|---|
| divider를 item layout의 새 1dp 행으로 추가 | 72dp 행 높이 또는 터치 범위 변경 | offset 없는 `ItemDecoration.onDraw()` 사용 |
| divider start/end를 72dp/16dp로 하드코딩 | `sw600dp` 행의 80dp/24dp 정렬과 어긋남 | 구성별 `content_start_margin`·`screen_edge_margin` 자원을 읽음 |
| decoration에서 `onDrawOver()` 사용 | 선택 배경·ripple 위로 선이 올라옴 | `onDraw()`로 child 아래에 그림 |
| offset 없이 divider를 `child.bottom`부터 아래로 그림 | 다음 행 영역으로 넘어가 배경에 가려짐 | `[bottom - 1dp, bottom]` 범위로 현재 행 안에 그림 |
| 애니메이션 중 child의 adapter position으로 폴더를 재판정 | 이전 child와 새 목록 position이 엇갈려 선이 다른 행에 붙음 | holder에 직전 bind된 폴더 상태를 조회 |
| breadcrumb에 새 12dp와 item의 기존 12dp를 모두 적용 | 실제 시작이 24dp | 바 padding 0, item padding 12dp 유지 |
| 경로 바에 `?colorSurface` 사용 | 검정 야간에서 본문과 같은 검정 | day/night 전용 색 자원 사용 |
| currentPath가 바뀔 때 칩 전체 재생성 | 가로 스크롤·접근성 포커스 초기화 | checked 상태만 갱신 |
| checkable Chip의 자동 토글을 그대로 둠 | 현재 경로 칩 no-op 탭 뒤 선택 표시가 해제됨 | click에서 현재 경로 기준으로 모든 checked 상태를 즉시 복원 |
| 즐겨찾기 ID로 선택 판정 | 같은 경로 중복 칩 일부만 선택 | `currentPath == bookmark.path` 사용 |
| 활성 breadcrumb 색을 기존 코드 그대로 둠 | M3 동적색/사용자 강조색 수용 기준을 충족하지 못함 | activated state 색을 테마 강조색 계열로 만들고 4.5:1 대비 확인 |
| `colorPrimary`를 대비 계산 없이 breadcrumb에 적용 | 밝은 파랑·노랑·연두 테마에서 4.5:1 미달 | 검정/흰색 방향 최소 혼합으로 대비를 보정 |
| long-click에서 false 반환 | 편집 대화상자와 경로 이동이 연달아 발생 | 처리 후 true 반환 |
| XML에 bookmark bar만 추가 | 수동 `FileListFragment.Binding`에서 접근 불가 | Binding 프로퍼티와 inflate 인수 동시 수정 |
| `BookmarkDirectory.id`를 `View.id`용 `Int`로 변환 | 64bit ID 손실·충돌 또는 `NO_ID` 오인 | `Long` 그대로 `Map<Long, Chip>`의 키로 사용 |
| 목록 변경 observer를 `observeForever`로 연결 | Fragment 파괴 뒤 누수 | `viewLifecycleOwner`로 관찰 |
| 선택 해제 없이 경로 이동 | 이전 폴더 파일에 후속 작업 가능 | `navigateTo()`에서 다른 경로면 먼저 clear |
| 공용 `navigateTo()`에서 같은 경로를 즉시 return | 탐색 서랍 등 기존 호출자의 검색 닫기·탐색 동작까지 바뀔 | 칩의 no-op은 bar 내부에서 처리하고 공용 함수의 기존 흐름 보존 |
