# 17. 파일 목록 화면 후속 개선 구현 계획

[16번 기획서](16-file-list-ui-refinement-spec.md)를 실제 코드에 반영하기 위한 작업 순서와
검증 기준이다. 상단 표면색과 아이콘처럼 독립적인 시각 변경을 먼저 끝내고, I/O와 RecyclerView
수명 주기가 얽히는 폴더 항목 수를 별도 단계에서 구현한다.

- 작성일: 2026-09-11
- 기준 브랜치: `feature/file-list-ui-refresh`
- 기준 커밋: `32c96c7f`
- 대상 버전: 1.7.4 (versionCode 39)
- 선행 문서: [14번 기획서](14-file-list-ui-refresh-spec.md),
  [15번 구현 계획](15-file-list-ui-refresh-plan.md),
  [16번 후속 기획서](16-file-list-ui-refinement-spec.md)
- 상태: **구현 및 기본 검증 완료 — 추가 환경 검증 항목은 16.5 참고**

## 1. 구현 원칙

1. **보기 모드가 아니라 최종 화면을 기준으로 한다.** 상단 배경은 LIST·GRID·MEDIA와 스크롤
   상태에서 같은 픽셀값이어야 한다.
2. **기존 동작을 바꾸지 않는다.** 선택 작업은 아이콘만 바꾸고 cut/copy/delete 흐름과 메뉴
   ID, archive 예외, 읽기 전용 규칙을 유지한다.
3. **폴더 개수 I/O를 ViewHolder에 소유시키지 않는다.** ViewModel 범위의 loader/cache가
   작업을 관리하고 Adapter는 상태를 표시하고 요청만 전달한다. 다만 실제 요청 활성화는
   Fragment view lifecycle과 LIST 보기 상태를 함께 따른다.
4. **늦은 비동기 결과를 경로와 generation으로 검증한다.** adapter position이나 ViewHolder
   참조를 작업에 저장하지 않고, 취소·무효화 전 작업은 mtime이 같아도 결과를 폐기한다.
5. **부분 실패는 목록 실패가 아니다.** 개수를 셀 수 없는 폴더는 날짜만 표시한다.
6. **불필요한 전체 rebind를 피한다.** 개수 완료 시 해당 경로의 description만 payload로
   갱신하고 이미지·썸네일 로딩은 다시 시작하지 않는다.
7. **테마와 provider를 함께 고려한다.** M2/M3, 밝음·어두움·검정 야간과 로컬·SAF·archive·
   원격 경로의 fallback을 단계별로 확인한다.

## 2. 단계 요약

| 단계 | 작업 | 주요 산출물 | 상태 |
|---|---|---|---|
| 0 | 기준 상태·실측 고정 | 빌드, 스크린샷, 상단/경로 색 기준 | ⬜ |
| 1 | 상단 표면색과 경로 아래 간격 | 고정 app bar fill, 8dp 간격 | ⬜ |
| 2 | 폴더 벡터와 MEDIA 크기 | 새 폴더 실루엣, MEDIA 56dp | ⬜ |
| 3 | 선택 작업 아이콘 | 파일 목록 전용 이동·복사·삭제 벡터 | ⬜ |
| 4 | LIST 정적 보조 정보 | 폴더 날짜, 파일 날짜·크기, 가운데점 형식 | ⬜ |
| 5 | 폴더 항목 수 비동기 로딩 | 단위 테스트 가능한 loader/cache, ViewModel 연동, 행 단위 payload | ⬜ |
| 6 | 현재 폴더 툴바 제목 | breadcrumb 표시 이름 연동 | ⬜ |
| 7 | 통합·성능·접근성 검증 | emulator/실기기 결과와 문서 갱신 | ⬜ |

단계 1~4와 6은 서로 비교적 독립적이다. 단계 5는 목록 표시와 provider I/O에 영향을 주므로
앞 단계의 시각적 결과가 안정된 뒤 진행한다.

## 3. 예상 변경 파일

### 3.1 기존 파일

| 파일 | 변경 내용 | 단계 |
|---|---|---|
| `app/src/main/res/layout/file_list_fragment_app_bar_include.xml` | 앱 바·overlay 배경 연결, 경로 바 아래 8dp margin | 1 |
| `app/src/main/res/values/attrs.xml` | 다른 화면에 영향을 주지 않는 파일 목록 상단 전용 theme attribute | 1 |
| `app/src/main/res/values/themes.xml` | M2 상단 기준 surface 지정 | 1 |
| `app/src/main/res/values/themes_material3.xml` | M3 elevated surface 지정 | 1 |
| `app/src/main/res/values/colors.xml` | 밝은 fallback 상단색과 폴더 세부색 | 1~2 |
| `app/src/main/res/values-night/colors.xml` | 야간·검정 fallback 상단색과 폴더 세부색 | 1~2 |
| `app/src/main/res/values/dimens.xml` | 8dp 간격, MEDIA 폴더 56dp | 1~2 |
| `app/src/main/res/drawable/file_directory_icon.xml` | LIST용 폴더 앞판·탭·내부 선 | 2 |
| `app/src/main/res/drawable/file_directory_thumbnail.xml` | GRID·MEDIA용 같은 계열 폴더 | 2 |
| `app/src/main/res/layout/file_item_media.xml` | 폴더 ImageView 56dp 중앙 배치 | 2 |
| `app/src/main/res/menu/file_list_select.xml` | 세 전용 선택 아이콘 연결 | 3 |
| `app/src/main/res/layout/file_item_list.xml` | 보조 정보 말줄임·폭, 큰 글꼴 행 확장, `⋮` 영역 확인 | 4 |
| `app/src/main/res/values/strings.xml` | metadata 구분자, 항목 수 plural, 항목 메뉴 접근성 이름 | 3~5 |
| `app/src/main/res/values-ko/strings.xml` | 한국어 `N개` plural과 행 메뉴 접근성 이름 | 3~5 |
| `app/src/main/java/me/zhanghai/android/files/filelist/FileListAdapter.kt` | description 분리 bind, count 상태 표시·요청·payload | 4~5 |
| `app/src/main/java/me/zhanghai/android/files/filelist/FileListViewModel.kt` | loader 소유, cache 조회·요청·취소·무효화 API | 5 |
| `app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt` | 선택 아이콘 런타임 전환, count update·view lifecycle, 현재 폴더 제목 | 1, 3, 5~6 |
| `app/src/main/java/me/zhanghai/android/files/ui/CoordinatorAppBarLayout.kt` | 필요 시 fill 고정과 overlay toolbar 동기화 분리 | 1 |
| `app/build.gradle` | 현재 없는 loader 단위 테스트용 JUnit/coroutines-test 의존성 추가 | 5 |

### 3.2 새 파일 후보

| 파일 | 책임 |
|---|---|
| `DirectoryItemCountLoader.kt` | 직접 하위 항목 비동기 열거, 제한된 병렬 실행, cache와 stale 결과 차단 |
| `file_list_app_bar_background.xml` | elevation overlay의 영향을 받지 않는 불투명 상단 fill |
| `file_list_select_move_icon_24dp.xml` | 폴더+오른쪽 화살표 |
| `file_list_select_copy_icon_24dp.xml` | 겹친 둥근 사각형 |
| `file_list_select_delete_icon_24dp.xml` | 윤곽선 휴지통 |
| `app/src/test/.../DirectoryItemCountLoaderTest.kt` | cache·queue·취소·generation 상태 머신의 결정론적 단위 테스트 |

`DirectoryItemCountLoader`의 상태 모델이 작으면 같은 파일에 sealed interface를 둔다. 별도 파일을
여러 개로 쪼개지는 않는다.

## 4. 0단계 — 기준 상태와 실측 고정

### 4.1 워킹트리와 빌드 기준

- [ ] `git status --short`로 16·17번 문서 외의 예상하지 못한 변경이 없는지 확인한다.
- [ ] `assembleDebug`를 실행해 구현 전 빌드 기준을 확보한다.
- [ ] `git diff --check`를 실행한다.
- [ ] 기존 `lintDebug` 실패가 `VideoDetails.kt`의 Media3 opt-in 4건뿐인지 기록한다.

### 4.2 화면 기준

Pixel_8 API 36, M3 밝은 테마에서 다음 기준을 다시 캡처한다.

| 화면 | 상단 빈 지점 | 경로 바 중앙 |
|---|---|---|
| LIST 맨 위 | `#FAF8FF` | `#F1F3F4` |
| MEDIA 최신 위치 | `#EEEDF4` | `#F1F3F4` |
| MEDIA 맨 위 | `#FAF8FF` | `#F1F3F4` |

- [ ] 좌표는 텍스트, chip, ripple, 그림자에서 벗어난 동일 지점으로 고정하고, wallpaper,
  동적 색상 on/off, contrast, color mode를 캡처 메타데이터에 함께 기록한다.
- [ ] 스크린샷 압축이나 육안이 아니라 원본 PNG의 ARGB를 읽는다.
- [ ] 현재 LIST, GRID, MEDIA 폴더 크기와 LIST 행 높이를 캡처한다.
- [ ] 선택 모드의 cut/copy/delete 아이콘과 archive 선택 시 copy→extract 전환을 기록한다.

## 5. 1단계 — 상단 표면색과 경로 아래 간격

### 5.1 lifted 기준색 계산

현재 차이는 `CoordinatorAppBarLayout`의 `MaterialShapeDrawable`에 `liftOnScroll` elevation
overlay가 합성되면서 생긴다. 목표는 **현재 테마의 lifted 색을 한 번 결정한 뒤 fill에는 더 이상
elevation overlay를 중복 적용하지 않는 것**이다.

구현 전에 다음 두 방식을 작은 변경으로 비교한다.

1. Material의 `ElevationOverlayProvider`와 실제 `AppBarLayout`의 lifted elevation을 사용해
   `colorSurface`의 합성색을 계산한다. elevation은 임의 dp 상수가 아니라 inflate 뒤 app bar의
   `lifted` 상태 elevation/style에서 얻고 px 단위로 overlay provider에 전달한다.
2. M3의 elevated surface attribute와 M2 fallback 색을 theme attribute로 직접 제공한다.

선택 기준은 다음과 같다.

- M3 밝음에서 결과가 실측 `#EEEDF4`와 일치한다.
- 동적 색상 변경 뒤 해당 팔레트의 elevated surface를 사용한다.
- M2와 야간에서 기존 lifted 화면보다 명도가 역전되지 않는다.
- API 23~37에서 같은 방식으로 resolve된다.

계산 방식이 기존 Material overlay 결과와 정확히 일치하면 1안을 우선한다. 라이브러리 내부
elevation 값 의존성이 불안정하면 파일 목록 전용 attribute와 테마별 fallback을 쓰되, 한 테마에 고정된
`#EEEDF4`를 모든 테마에 재사용하지 않는다.

### 5.2 fill과 elevation 분리

- [ ] app bar에는 위에서 얻은 색을 가진 불투명 drawable을 적용한다.
- [ ] 해당 drawable에는 elevation overlay가 다시 적용되지 않게 한다.
- [ ] `liftOnScroll`과 view elevation은 유지해 스크롤 그림자 변화는 보존한다.
- [ ] 기본 toolbar, 즐겨찾기 주변, 경로 바 바깥, overlay toolbar가 같은 fill을 공유한다.
- [ ] `syncBackgroundColorTo()`가 lift callback으로 overlay toolbar 색을 다시 바꾸지 않도록
  고정색 동기화 경로를 추가하거나 해당 callback 동기화를 사용하지 않는다.
- [ ] `CoordinatorAppBarLayout` 생성자의 상태 표시줄 투명화가 `MaterialShapeDrawable` 배경을
  전제로 하므로, `ColorDrawable`로 바꾸지 않거나 같은 초기 fill 비교·투명화 동작을 명시적으로
  보존한다.
- [ ] 상태 표시줄 투명 처리와 아이콘 명암이 기존과 같은지 확인한다.
- [ ] immersive/translucent viewer 테마에는 파일 목록 전용 색을 확산시키지 않는다.

`CoordinatorAppBarLayout`을 전역 변경하면 다른 화면의 lift 동작까지 바뀔 수 있다. 공용 클래스에
API를 추가하더라도 file list가 명시적으로 opt-in할 때만 fill을 고정한다. 공용
`colorAppBarSurface` 자체를 바꾸지 않고 파일 목록 전용 attribute/drawable을 반드시 사용한다.

### 5.3 8dp 간격

- [ ] `file_list_path_bar_margin_bottom = 8dp`처럼 용도가 드러나는 dimen을 추가한다.
- [ ] `breadcrumbLayout`의 `layout_marginBottom`에 적용한다.
- [ ] 경로 바 내부 padding이나 RecyclerView padding으로 대신하지 않는다.
- [ ] 즐겨찾기 0개에서도 8dp가 남고, 앱 바가 접힐 때 함께 움직이는지 확인한다.

### 5.4 단계 검증

- [ ] LIST·GRID·MEDIA 각각 맨 위와 스크롤 후 상단 빈 지점의 ARGB가 같다.
- [ ] 기준 wallpaper에서 동적 색상을 끈 M3 밝음은 `#EEEDF4`와 일치한다.
- [ ] 동적 색상을 켠 M3 밝음은 해당 팔레트에서 계산한 lifted 기대값과 일치하고 스크롤 전후 같다.
- [ ] configuration change로 Activity가 재생성되면 새 팔레트에서 fill을 다시 resolve한다.
- [ ] 경로 바는 밝음 `#E9E7EE`, 야간 `#292A2D`를 유지한다.
- [ ] 경로 아래부터 첫 콘텐츠까지 8dp가 측정된다.
- [ ] 선택 toolbar를 열고 닫아도 색이 튀지 않는다.

## 6. 2단계 — 폴더 벡터와 MEDIA 표시 크기

### 6.1 자체 벡터 제작

참고 스크린샷을 눈으로만 참조해 다음 레이어를 새 path data로 그린다.

1. 뒤쪽 탭/후면: 시작 쪽이 높고 끝 쪽으로 낮아지는 얇은 노란 면
2. 앞판: 둥근 아래 모서리와 완만한 위 모서리를 가진 채운 면
3. 내부 선: 앞판 상단 안쪽의 짧은 밝은 선

- [ ] Samsung APK나 이미지에서 vector/bitmap을 추출하지 않는다.
- [ ] LIST 24dp에서 내부 선이 최소 한 물리 픽셀 이상 보이는지 확인한다.
- [ ] thumbnail 벡터도 같은 비율과 모서리 리듬을 사용한다.
- [ ] 기본 채움은 `file_icon_yellow`, 내부 선은 전용 색 또는 흰색 alpha로 표현한다.
- [ ] 야간에서 내부 선이 사라지거나 과도하게 흰색으로 뜨지 않는지 확인한다.
- [ ] `autoMirrored`를 켜지 않는다.

### 6.2 MEDIA만 56dp로 축소

- [ ] `media_directory_icon_size = 56dp`를 추가한다.
- [ ] `file_item_media.xml`의 `directoryThumbnailImage`만 56dp 정사각형과 `center` gravity로 바꾼다.
- [ ] `thumbnailImage`와 일반 미디어의 match-parent edge-to-edge는 건드리지 않는다.
- [ ] 하단 폴더 이름 scrim이 아이콘을 과도하게 덮지 않는지 확인한다.
- [ ] GRID의 넓은 folder thumbnail 크기는 유지한다.

### 6.3 단계 검증

- [ ] LIST·GRID·MEDIA에서 같은 폴더로 보이되 작은 아이콘의 선이 뭉개지지 않는다.
- [ ] MEDIA 아이콘 bounds가 정확히 56dp이고 타일 중앙에 있다.
- [ ] 선택 overlay, menu scrim, 폴더 이름이 기존 z-order를 유지한다.
- [ ] 밝음·어두움·검정 야간에서 폴더 앞판·탭·내부 선이 구분된다.

## 7. 3단계 — 선택 작업 아이콘

### 7.1 전용 자산

- [ ] 24dp viewport에 2dp 상당의 일정한 선 두께로 세 벡터를 직접 만든다.
- [ ] 이동은 폴더 윤곽과 오른쪽 화살표가 24dp 안에서 겹치지 않게 한다.
- [ ] 복사는 앞·뒤 둥근 사각형의 겹침이 작은 크기에서도 읽히게 한다.
- [ ] 삭제는 뚜껑, 손잡이, 몸체를 분리하고 지나치게 가는 내부선을 피한다.
- [ ] `?colorControlNormal` tint와 disabled alpha를 따른다.

### 7.2 메뉴 연결

- [ ] `file_list_select.xml`의 `action_cut`, `action_copy`, `action_delete`에만 연결한다.
- [ ] 전역 cut/copy/delete 아이콘은 변경하지 않는다.
- [ ] `FileListFragment.updateOverlayToolbar()`의 archive copy→extract 아이콘 교체를 유지한다.
- [ ] 위 메서드가 매 선택 갱신 때 아이콘을 다시 지정하므로 archive 전체 선택이면 기존 extract,
  그 외에는 새 `file_list_select_copy_icon_24dp`를 명시적으로 설정한다.
- [ ] 메뉴 title, alphabetic shortcut, visibility 조건을 바꾸지 않는다.

### 7.3 단계 검증

- [ ] 파일 하나, 폴더 하나, 혼합 다중 선택에서 세 아이콘을 확인한다.
- [ ] read-only 경로에서 이동·삭제 숨김이 유지된다.
- [ ] archive 파일 선택 시 추출 아이콘과 제목이 유지된다.
- [ ] 같은 선택 세션에서 일반 선택과 archive 선택 상태를 오갈 때 copy/extract 아이콘과 제목이
  각각 즉시 복원된다.
- [ ] TalkBack이 기존 작업 이름을 읽는다.
- [ ] LIST·GRID·MEDIA 행의 `⋮` 버튼이 실제 지역화된 content description과 버튼 역할을 읽는다.

## 8. 4단계 — LIST 정적 보조 정보

폴더 개수 없이도 먼저 날짜·크기 형식을 완성해 UI와 비동기 로직을 분리한다.

### 8.1 description bind 분리

`FileListAdapter.bindFileViewHolder()`에서 description 계산을 별도 함수로 추출한다.

```text
bindFileDescription(holder, file, directoryCountState)
```

- [ ] LIST에 `descriptionText`가 있을 때만 실행한다.
- [ ] 폴더와 파일 모두 `lastModifiedTime().toInstant().formatShort(context)`를 만든다.
- [ ] 일반 파일과 정상 파일 심볼릭 링크는 기존 `attributes.fileSize.formatHumanReadable(context)`를
  붙이고, 정상 디렉터리 링크는 폴더 규칙을 따른다.
- [ ] 깨졌거나 대상 attributes를 읽을 수 없는 심볼릭 링크는 크기나 count를 추정하지 않고
  `attributesNoFollowLinks`의 수정 시각만 표시한다.
- [ ] 현재 파일 설명에 쓰는 `file_item_description_separator`의 네 칸 공백 값을 ` · `로 바꾸고
  폴더와 파일 metadata 조합에 함께 사용한다.
- [ ] 이름과 description은 각각 한 줄 끝 말줄임을 유지한다.
- [ ] 우측 `menuButton`의 48dp 폭과 margin은 유지한다.
- [ ] 기본 글꼴에서는 기존 72dp 높이를 유지하고, 큰 글꼴에서는 `minHeight=72dp`와
  `wrap_content` 조합으로 두 텍스트가 잘리지 않게 행 확장을 허용한다.
- [ ] 현재 코드에는 행 `menuButton`의 content description이 없으므로 LIST·GRID·MEDIA에 공통
  지역화 문자열(가능하면 `{파일명} 옵션`)을 bind하고 button 역할을 유지한다.

### 8.2 전용 payload

현재 adapter는 payload가 하나라도 있으면 상태만 갱신하고 full bind를 중단한다. 폴더 개수 갱신이
썸네일을 다시 로드하지 않도록 payload 의미를 분리한다.

- [ ] 기존 `PAYLOAD_STATE_CHANGED`를 유지한다.
- [ ] `PAYLOAD_DESCRIPTION_CHANGED`를 새로 둔다.
- [ ] description payload면 description만 bind하고 즉시 반환한다.
- [ ] state payload면 checked/enabled/name ellipsize 등 기존 상태 갱신만 수행한다.
- [ ] 빈 payload에서만 listener·아이콘·Coil·popup menu까지 full bind한다.
- [ ] `payloads`에 두 종류가 함께 있으면 `contains()`로 각각 적용한 뒤 반환해 full bind와 같은
  최종 상태가 되게 한다. 모르는 payload가 있으면 안전하게 full bind한다.
- [ ] description-only bind는 cache 조회만 하고 요청을 다시 만들지 않는다. 요청은 full bind의
  `requestIfNeeded` 경로에서만 수행해 update loop를 막는다.

### 8.3 단계 검증

- [ ] 폴더는 우선 수정 시각만 보인다.
- [ ] 파일은 `수정 시각 · 크기`로 보인다.
- [ ] 0 byte, 매우 큰 파일, 긴 이름, 큰 글꼴을 확인한다.
- [ ] 정상 폴더 링크, 정상 파일 링크, 깨진 링크에서 위 표시 규칙을 확인한다.
- [ ] fontScale 1.3/2.0에서 이름·description이 겹치거나 잘리지 않고 메뉴 48dp 영역이 유지된다.
- [ ] description payload가 Coil 이미지 요청을 다시 시작하지 않는지 로그나 breakpoint로 확인한다.

## 9. 5단계 — 폴더 항목 수 비동기 로딩

### 9.1 상태와 cache key

새 loader는 UI 문자열이 아니라 정수 상태만 보관한다.

```text
DirectoryItemCountState
├─ NotRequested
├─ Loading
├─ Available(count: Int)
└─ Unavailable

DirectoryItemCountKey(path, lastModifiedMillis)
DirectoryItemCountRequestToken(key, listGeneration, requestId)
```

- `Available(0)`은 정상적인 빈 폴더다.
- `Unavailable`은 권한, provider 오류, 취소가 아닌 최종 실패다.
- `lastModifiedMillis`는 빠른 cache hit를 위한 힌트이지 정확성 경계가 아니다. mtime이 0이거나
  갱신되지 않는 SAF·archive·원격 provider도 있으므로 목록 generation과 명시적 invalidate를
  함께 사용한다.
- `listGeneration`은 current path, 검색 query, 명시적 새로고침, provider change로 현재 표시
  후보의 세대가 바뀔 때 증가한다. cache key와 별개인 request token에 넣어 완료 시 generation이
  다르면 같은 Path·mtime이어도 버린다. 보기 전환·재진입 때 유효한 완료 cache는 유지할 수 있다.
- 완료 cache는 `Path`별 최신 항목만 남기는 bounded LRU 구조로 제한한다. 초기 상한은 **256개**다.
- running은 최대 **4개**, pending queue는 초기 **64개**로 별도 제한한다. queue 초과 시 아직
  시작하지 않은 가장 오래된 요청을 제거하고 그 상태를 `NotRequested`로 되돌린다.
- 상태와 key를 `FileItem`이나 DiffUtil contents에 넣지 않는다.

### 9.2 ViewModel 소유 API

`FileListViewModel`이 loader를 소유하고 `viewModelScope`에서 관리한다.

```text
getDirectoryItemCount(file): DirectoryItemCountState
requestDirectoryItemCount(file)
setDirectoryItemCountLoadingEnabled(viewStarted, viewType)
setDirectoryItemCountCandidates(files, listGeneration)
invalidateDirectoryItemCounts(paths, advanceGeneration)
directoryItemCountUpdates: LiveData<DirectoryItemCountUpdate>
```

- [ ] Adapter가 Context나 CoroutineScope를 loader에 넘기지 않게 한다.
- [ ] Fragment/Adapter listener를 통해 cache 조회와 `requestIfNeeded`를 ViewModel로 전달한다.
- [ ] 결과 update에는 `Path`, key, 최종 상태를 넣는다.
- [ ] worker 결과는 main thread로 돌아온 뒤 cache·LRU·queue·in-flight map을 한 스레드에서만
  변경하고 `LiveData.value`로 순차 게시한다. worker에서 여러 `postValue()`를 호출하지 않는다.
- [ ] observer 비활성 중 완료 이벤트가 합쳐져도 복구되도록 `onStart`/새 observer 연결 시 현재
  adapter의 description을 한 번 payload rebind하고 cache에서 최신 상태를 읽는다.
- [ ] 새 observer가 붙을 때 이벤트 재전달 여부와 무관하게 full bind도 cache의 최신 상태를 읽는다.
- [ ] ViewModel이 정리될 때 모든 작업이 자동 취소된다.

### 9.3 제한된 I/O

- [ ] `runInterruptible(Dispatchers.IO)`에서 `path.newDirectoryStream().use`로 직접 하위 항목만 센다.
- [ ] cancellable한 FIFO queue와 semaphore/worker pool로 동시 directory stream을 **최대 4개**로
  제한한다. 같은 key는 queued와 running 양쪽에서 중복 합친다.
- [ ] 화면에 full bind되어 처음 요청된 순서를 처리 순서로 사용하고, 취소된 queued 요청은 즉시
  queue에서 제거한다.
- [ ] 전체 열거가 정상 완료된 경우에만 `Available(count)`를 게시한다.
- [ ] count는 `Long`/checked increment로 계산하고 `Int.MAX_VALUE`를 넘으면 `Unavailable`로 처리한다.
- [ ] `CancellationException`은 반드시 다시 던진다. 그 밖의 `IOException`, `SecurityException`,
  `DirectoryIteratorException` 및 provider가 던지는 일반 `Exception`은 `Unavailable`로 격리하되
  `Error`는 삼키지 않는다.
- [ ] 일부까지만 센 값을 표시하지 않는다.
- [ ] interrupt/coroutine cancellation은 실패 cache로 남기지 않고, 아직 동일 generation의 같은
  요청이면 in-flight와 `Loading`을 함께 제거해 `NotRequested`로 복원한다.
- [ ] 같은 key의 중복 in-flight 요청을 합친다.
- [ ] 완료 직전 현재 generation, 활성 scope, key, in-flight token을 모두 재검증하고 하나라도
  다르면 cache와 update를 변경하지 않는다.

원격 provider가 interrupt에 반응하지 않으면 실행 중 stream은 당장 끝나지 않을 수 있다. 이때도
UI와 경로 이동은 기다리지 않고 늦은 결과를 폐기한다. 전역 4개 hard cap을 넘겨 대체 worker를
계속 만들지는 않는다. 따라서 비협조적인 provider가 네 slot을 모두 점유하면 새 count는 진행되지
않을 수 있으며, 날짜-only fallback을 유지한다. 이 제한을 실기 검증 기록에 남긴다.

### 9.4 요청·표시 흐름

```text
LIST full bind
  → 폴더 날짜 즉시 표시
  → cache 조회
      Available  → 날짜 · N개 표시
      Unavailable → 날짜만 표시
      Loading     → 날짜만 표시
      NotRequested → 날짜 표시 + 비동기 요청
  → 완료 update(Path, key, generation)
  → filePositionMap에서 현재 position 조회
  → 같은 Path가 있는 경우 description payload
  → cache 재조회 후 날짜 · N개 표시
```

ViewHolder나 과거 position을 callback에 캡처하지 않는다. 결과 시점에 `filePositionMap`으로 현재
위치를 다시 찾고, 현재 generation/key가 일치할 때만 description payload를 보낸다. observer가
inactive였던 구간 뒤에는 전체 현재 행을 description payload로 한 번 재동기화한다.

### 9.5 활성화·취소·무효화

- [ ] `viewLifecycleOwner`가 STARTED이고 LIST일 때만 새 count 요청을 허용한다. Fragment의
  `onStart`/`onStop`(또는 동등한 view lifecycle observer)에서 활성 상태를 전달한다.
- [ ] GRID·MEDIA로 전환하면 아직 끝나지 않은 요청을 취소하고 cache는 유지한다.
- [ ] view가 STOPPED/파괴되거나 current path가 바뀌면 이전 화면의 queued/running 작업을 취소하고
  cache는 유지한다. 재생성된 view가 LIST이면 full bind에서 필요한 항목을 다시 요청한다.
- [ ] LIST 검색에서도 visible 결과 폴더의 count를 표시한다. query가 바뀌거나 검색이 끝나면
  generation을 올리고, 같은 query의 점진적 결과 갱신에서는 candidate 집합으로 사라진 요청만
  취소하되 이미 완료된 유효 cache를 버리지 않는다.
- [ ] Adapter에 새 목록을 제출할 때 directory candidate key 집합을 ViewModel에 전달하고 목록에서
  사라진 pending 요청을 취소한다. `Loading`은 실제 queued/running 항목에만 존재해야 한다.
- [ ] 명시적 새로고침에서는 reload 전에 현재 adapter 목록의 directory Path 집합을 캡처해
  invalidate하고 generation을 올린다. 실패한 `Unavailable`도 이때 재시도 가능해진다.
- [ ] 새 FileItem의 폴더 mtime이 달라지면 기존 key를 자동으로 폐기한다.
- [ ] PathObserver로 현재 목록의 새 `Success`가 게시되면 해당 화면의 directory cache를
  invalidate하거나 list generation을 올린다. mtime이 같거나 0이어도 이전 완료를 재사용하지 않는다.
- [ ] 앱이 시작한 create/copy/move/delete/rename/archive 작업은 결과가 목록에 반영되는 시점에
  영향받은 부모/표시 폴더 Path를 invalidate한다. 직접 완료 callback 연결이 없으면 PathObserver의
  새 `Success` generation을 정확성 경계로 사용한다.
- [ ] cache 상한을 넘으면 in-flight가 아닌 가장 오래 사용하지 않은 항목부터 제거한다.
- [ ] 완료된 `Unavailable`은 같은 generation에서 반복 요청하지 않고, reload/provider change/
  새 화면 generation에서만 다시 시도한다.

### 9.6 문자열과 접근성

- [ ] 기본 영어 plural은 `1 item`/`%d items`, 한국어는 `%d개`를 제공한다.
- [ ] `0개`도 정상 plural 결과로 표시한다.
- [ ] `수정 시각 · N개` 전체가 description TextView에 한 문자열로 들어간다.
- [ ] 실패를 `알 수 없음`으로 길게 노출하지 않고 날짜만 유지한다.
- [ ] TalkBack이 숫자 갱신 때 전체 화면을 방해하는 live region 알림을 만들지 않는다.

### 9.7 단계 검증

- [ ] 빈 폴더는 `0개`, 1개 폴더는 `1개`, 혼합 하위 항목은 합계 `N개`로 표시된다.
- [ ] 숨김 표시 설정과 관계없이 같은 직접 하위 항목 수를 표시한다.
- [ ] 권한이 없거나 깨진 경로는 날짜만 남고 목록 화면은 성공 상태를 유지한다.
- [ ] 빠른 fling과 보기 전환에서 다른 행의 개수가 붙지 않는다.
- [ ] 같은 화면 재스크롤은 cache hit이고 directory stream을 다시 열지 않는다.
- [ ] 새로고침과 폴더 내용 변경 뒤 count가 갱신된다.
- [ ] 300개 이상의 폴더에서도 cache가 상한을 지키고 UI가 멈추지 않는다.
- [ ] 느린 원격 provider에서 최대 동시 작업 수가 4를 넘지 않는다.

### 9.8 단계 내부 구현 순서

1. 열거 함수·dispatcher를 주입할 수 있는 loader와 11.2의 단위 테스트를 먼저 작성한다.
2. ViewModel의 cache/queue/generation API와 Adapter의 조회·payload 경로를 연결한다.
3. Fragment의 view lifecycle, 보기·검색 전환, 목록 candidate, reload/PathObserver 무효화를 연결한다.
4. 로컬·archive·SAF·원격 provider 순서로 통합 검증하고 비협조적 provider 제한을 기록한다.

## 10. 6단계 — 현재 폴더 툴바 제목

### 10.1 breadcrumb를 단일 이름 원천으로 사용

현재 `onActivityCreated()`와 `onPickOptionsChanged()`가 각각 Activity title을 바꾸므로 breadcrumb
observer에서 별도로 덮어쓰지 않는다. 제목 결정을 `updateToolbarTitle(breadcrumb, pickOptions)` 한
함수로 모으고 두 observer에서 호출한다.

```text
index = breadcrumb.selectedIndex
title = if (pickOptions != null) existingPickerOperationTitle(pickOptions)
    else breadcrumb.nameProducers[index](context)
    .takeIf { it.isNotBlank() }
    ?: getString(R.string.file_list_title)
```

- [ ] 일반 탐색에서는 index가 `paths`와 `nameProducers` 양쪽에 유효한지 검사하고, producer가
  일반 `Exception`을 던지거나 빈 값을 반환하면 `파일`로 fallback한다. `Error`는 삼키지 않는다.
- [ ] navigation root의 지역화 이름을 그대로 사용한다.
- [ ] 일반 폴더, archive root, SAF root에서 breadcrumb와 툴바 제목이 일치한다.
- [ ] picker에서는 기존 `파일 열기`/`파일 만들기`/`폴더 열기` 작업 제목을 유지해 기존 동작을
  바꾸지 않는다. 일반 탐색의 현재 폴더명 정책과 명시적으로 분리한다.
- [ ] `activity.title` 한 경로만 사용하고 toolbar title을 직접 중복 갱신하지 않아 깜박임을 막는다.
- [ ] 화면 회전과 프로세스 복원 후 observer가 제목을 다시 설정한다.

### 10.2 상태별 회귀

- [ ] 검색이 열려도 underlying title은 현재 폴더 이름이다.
- [ ] 선택 overlay는 `N개 선택됨`을 계속 표시한다.
- [ ] 선택 종료 후 아래의 기본 toolbar에 현재 폴더명이 남는다.
- [ ] Loading/Failure는 subtitle만 바꾸고 title을 `로드 중`/`오류`로 바꾸지 않는다.
- [ ] 즐겨찾기와 breadcrumb 이동 직후 새 이름이 반영된다.
- [ ] `ACTION_GET_CONTENT`, `ACTION_OPEN_DOCUMENT`, `ACTION_CREATE_DOCUMENT`,
  `ACTION_OPEN_DOCUMENT_TREE`에서 경로 이동·회전 뒤에도 picker 작업 제목이 유지된다.

## 11. 7단계 — 통합 검증

### 11.1 정적 검사와 빌드

- [ ] `git diff --check`
- [ ] `assembleDebug`
- [ ] `lintDebug`
- [ ] 기존 Media3 opt-in 외 새 lint 오류가 없는지 파일별 확인
- [ ] 디버그 키로 minified `assembleRelease`
- [ ] `apksigner verify --verbose --print-certs`

### 11.2 자동 테스트

loader의 directory enumerator와 dispatcher를 주입 가능한 경계로 만들고 실제 provider 없이 상태
머신을 결정론적으로 검사한다. 현재 저장소에 test 구성이 없으므로 최소 JUnit과
`kotlinx-coroutines-test` 구성을 추가한다.

- [ ] 0/1/N개와 `Int.MAX_VALUE` 초과 처리
- [ ] stream 중간 예외에서 부분 count를 게시하지 않고 `Unavailable`이 되는지 확인
- [ ] 같은 key의 queued/running 중복 요청 합치기
- [ ] FIFO, running 최대 4개, pending 최대 64개와 drop 시 `NotRequested` 복원
- [ ] 취소가 `Unavailable`을 만들지 않고 LIST 재진입 때 재요청되는지 확인
- [ ] invalidate 뒤 같은 mtime의 늦은 성공·실패가 generation/token 검사에서 폐기되는지 확인
- [ ] 완료 cache LRU 256과 running/pending 별도 상한 확인
- [ ] 빠른 다중 완료 및 observer STOPPED→STARTED 뒤 모든 현재 행이 최신 cache로 갱신되는지 확인
- [ ] state+description 복합 payload와 모르는 payload fallback이 full bind와 같은 결과인지 확인
- [ ] 정상 directory/file 심볼릭 링크와 깨진 링크 description 규칙 확인

### 11.3 에뮬레이터 매트릭스

| 항목 | 조합 |
|---|---|
| 테마 | M2/M3 × 밝음/어두움/검정 야간 |
| 보기 | LIST/GRID/MEDIA, 각 맨 위·중간·끝 |
| 즐겨찾기 | 0개, 1개, 가로 폭보다 다수 |
| 경로 | 저장소 루트, 일반 폴더, 긴 breadcrumb, archive |
| LIST | 빈 폴더, 1개, 다수, 긴 이름, 큰 파일, 0 byte |
| 선택 | 파일, 폴더, 혼합, read-only, archive |
| 화면 | 세로, 가로, 큰 글꼴, RTL |
| picker | GET_CONTENT, OPEN_DOCUMENT, CREATE_DOCUMENT, OPEN_DOCUMENT_TREE |
| 수명 주기 | LIST↔GRID/MEDIA, 검색 query 교체, 회전, background→foreground |

상단 빈 지점과 경로 바 중앙의 픽셀을 원본 PNG에서 읽는다. 같은 테마의 모든 보기·스크롤
상태에서 상단 fill은 같아야 하고, 경로 바는 별도 고정색을 유지해야 한다.

### 11.4 실기기

- [ ] SM-F971N에 디버그 키 서명 release APK를 `adb install -r`로 설치한다.
- [ ] 접힌 화면과 펼친 화면에서 8dp 간격, 제목, LIST 정보, MEDIA 56dp 아이콘을 확인한다.
- [ ] 실제 가로 스크롤·세로 fling 중 폴더 count 갱신이 끊김을 만들지 않는지 확인한다.
- [ ] 선택 작업 아이콘과 실제 이동·복사·삭제 확인 대화상자를 확인한다.
- [ ] 앱 프로세스의 `FATAL EXCEPTION`, ANR, provider 오류 전파 여부를 logcat으로 확인한다.
- [ ] 테스트 데이터와 즐겨찾기 설정을 변경했다면 원래 상태로 복원한다.

### 11.5 성능·수명 주기 확인

- [ ] count 기능 전후 첫 화면 표시 시간이 눈에 띄게 늘지 않는다.
- [ ] 메인 스레드에서 `newDirectoryStream()`이 호출되지 않는다.
- [ ] count 완료 payload가 thumbnail/image request를 재시작하지 않는다.
- [ ] 4개 동시성 제한과 256개 cache 상한을 계측 로그로 한 번 확인한 뒤 임시 로그를 제거한다.
- [ ] Fragment view가 STOPPED/파괴되면 queued 요청이 0이고 실행 중 요청에는 interrupt가 시도되며,
  View/Fragment 참조가 남지 않는다. ViewModel `onCleared()` 뒤에는 loader job이 0이다.
- [ ] observer가 비활성인 동안 10개 이상 완료된 뒤 복귀해도 모든 현재 행이 최신 값을 표시한다.
- [ ] interrupt를 무시하는 가짜/느린 provider에서는 전역 4개 cap과 UI 응답성·날짜 fallback을
  유지하며, 새 count가 진행되지 않을 수 있다는 제한을 검증 기록에 남긴다.

## 12. 구현 시 주의할 함정

| 함정 | 결과 | 방지책 |
|---|---|---|
| lifted 색을 base로 넣고 elevation overlay도 유지 | 스크롤 시 두 번 어두워짐 | fill drawable의 overlay 합성 차단, elevation은 그림자로만 사용 |
| `CoordinatorAppBarLayout` 전역 동작 변경 | 설정·서버 화면까지 색이 바뀜 | file list 전용 opt-in API/자원 |
| M3 밝은 `#EEEDF4`를 모든 테마에 하드코딩 | 동적 색·야간에서 부자연스러움 | theme별 lifted surface resolve |
| MEDIA의 directory ImageView만 줄이며 gravity 누락 | 타일 시작 쪽에 붙음 | 56dp + center gravity 함께 적용 |
| 전역 cut 아이콘 교체 | 다른 화면의 잘라내기 의미까지 변경 | file list selection 전용 drawable |
| 폴더 count를 bind에서 동기 실행 | 스크롤 멈춤·ANR | ViewModel worker loader |
| position/ViewHolder를 async callback에 저장 | 재활용된 다른 행에 count 표시 | Path+mtime key와 결과 시점 position 재조회 |
| 실패 중 센 일부 값을 게시 | 잘못된 개수 노출 | 완전 성공만 Available |
| count 변경에 full notify | Coil 재요청·깜박임 | description 전용 payload |
| 취소 뒤 `Loading`만 cache에 잔류 | LIST 재진입 뒤 영구 미요청 | in-flight와 Loading 원자적 제거, NotRequested 복원 |
| worker에서 LiveData `postValue()` 연속 호출 | 여러 완료 Path의 화면 갱신 유실 | main-thread 순차 게시 + observer 재활성화 시 재동기화 |
| Path+mtime만 stale 판정에 사용 | 같은/0 mtime provider에서 옛 count 재삽입 | list generation과 request token까지 검증 |
| pending 요청을 모두 in-flight로 간주 | cache/queue 상한 무력화 | completed 256, pending 64, running 4를 별도로 제한 |
| 취소 무시 provider마다 대체 worker 생성 | 실제 stream 동시성 폭주 | 전역 4개 hard cap과 날짜-only fallback 유지 |
| 검색 결과 교체를 current path 변경으로만 판단 | 사라진 원격 폴더 I/O 지속 | query/list generation과 candidate 집합으로 취소 |
| `Path.fileName`으로 title 결정 | 저장소 루트가 `/`로 표시 | breadcrumb nameProducer 재사용 |
| breadcrumb observer가 picker 제목을 덮음 | 파일 열기/만들기 작업 맥락 소실 | 통합 title 함수에서 picker 작업 제목 우선 |
| 선택 toolbar title까지 현재 폴더명으로 변경 | 선택 개수 소실 | 기본 toolbar만 경로 제목 적용 |
| 메뉴 버튼에 접근성 이름이 있다고 가정 | TalkBack에서 무명 버튼 | LIST·GRID·MEDIA에 지역화된 행 옵션 이름 bind |
| 72dp 고정 높이에서 큰 글꼴 검사만 수행 | 텍스트 수직 잘림 | 기본 72dp minHeight + 큰 글꼴 wrap_content |
| 메뉴 XML만 새 copy 아이콘으로 변경 | Fragment가 기존 copy 아이콘으로 덮음 | 일반/아카이브 분기에 새 자산을 명시적으로 설정 |

## 13. 완료 조건

다음을 모두 만족하면 16번 기획의 구현이 완료된 것으로 본다.

1. 상단 fill, 8dp 간격, 폴더 모양·MEDIA 크기, 선택 아이콘, LIST 보조 정보, 현재 폴더 제목이
   16번 수용 기준대로 구현되어 있다.
2. 폴더 count는 직접 하위 항목만 비동기로 세며 UI thread, provider 실패, ViewHolder 재사용에
   안전하다.
3. Debug와 디버그 키 서명 Release가 빌드되고 에뮬레이터·SM-F971N에 설치된다.
4. LIST·GRID·MEDIA 및 M2/M3·야간·폴더블 상태에서 새 회귀가 없다.
5. loader 자동 테스트가 중복·상한·취소·무효화·stale 결과·이벤트 유실을 통과한다.
6. 새 lint 오류와 `git diff --check` 오류가 없다.
7. 실제 검증 결과, 조정된 크기·색, provider 제한과 남은 미확인 항목을 16번 또는 이 문서에 기록한다.

## 14. 커밋 분리 권장안

사용자가 커밋을 요청할 때 다음 두 묶음이 리뷰하기 쉽다.

1. `Refine file list surfaces and icons`
   - 상단 배경, 간격, 폴더·선택 아이콘, MEDIA 크기, 현재 폴더 제목
2. `Show file metadata and directory item counts`
   - LIST description, loader/cache, 문자열, 비동기 검증

구현 중 변경이 강하게 결합되면 한 커밋으로 합칠 수 있지만, Codex가 임의로 staging하거나
커밋하지 않는다.

## 15. 사전 검토 반영 기록

구현 전 세 독립 검토(아키텍처·코드 정합성, UX·접근성·검증, 범위·리스크)를 수행했고 다음을
계획의 필수 계약으로 반영했다.

1. `updateOverlayToolbar()`의 런타임 copy 아이콘 덮어쓰기와 picker 제목 경합을 실제 변경 지점에
   포함했다.
2. count loader에 generation/token, 유실 없는 화면 갱신, 취소 시 `Loading` 복구, completed/
   pending/running 별도 상한과 view lifecycle을 추가했다.
3. mtime을 힌트로만 취급하고 reload·PathObserver·검색 결과 교체를 명시적 무효화 경계로 삼았다.
4. 심볼릭 링크 표시, 행 메뉴 접근성 이름, 큰 글꼴 행 확장, 동적 색상 재현 조건을 구체화했다.
5. loader 상태 머신과 payload 병합을 구현 전에 자동 테스트할 수 있도록 테스트 경계와 항목을
   추가했다.

## 16. 구현 결과 (2026-09-11)

### 16.1 반영 완료

- 파일 목록 app bar가 M3에서는 `colorSurfaceContainer`, M2에서는 기존 elevation overlay 계산값을
  고정 fill로 사용하게 했다. 공용 theme attribute는 바꾸지 않고 file list만 opt-in하므로 다른
  화면과 immersive/translucent 테마에는 확산되지 않는다.
- 경로 바 아래 8dp, 새 폴더 3-layer 벡터, MEDIA 56dp, 파일 목록 전용 이동·복사·삭제 아이콘과
  archive copy→extract 런타임 전환을 반영했다.
- LIST에 `수정 시각 · 파일 크기`와 비동기 `수정 시각 · N개`를 표시하고, 깨진 심볼릭 링크는
  날짜만 표시한다. 행 메뉴에는 파일명을 포함한 지역화된 접근성 이름을 부여했다.
- `DirectoryItemCountLoader`를 추가해 completed LRU 256, FIFO pending 64, running 4, Path+mtime
  cache, generation/request token, 중복 합치기, 취소·오류 격리와 stale 완료 폐기를 구현했다.
- Fragment STARTED/LIST 상태, 보기·경로·검색 candidate 전환, 명시적 새로고침과 정상 목록
  `Success`에 count 활성화·취소·무효화를 연결했다. 완료는 description payload만 갱신한다.
- 일반 탐색은 breadcrumb 표시 이름을 Activity title로 사용하고 picker는 기존 작업 제목을 유지한다.

### 16.2 자동 검증

- [x] `assembleDebug` 성공
- [x] `testDebugUnitTest` 성공 — `DirectoryItemCountLoaderTest` 7개, 실패·건너뜀 0개
- [x] 빠른 완료 이벤트 전부 전달, 중복 합치기, 취소 복구, stale generation 폐기, running/pending
  상한, completed LRU 상한을 단위 테스트로 확인
- [x] `git diff --check` 오류 없음
- [ ] `lintDebug`는 기존 `VideoDetails.kt` Media3 `UnsafeOptInUsageError` 4건 때문에 실패한다. 새 구현
  파일에서 추가된 lint error는 없다.

### 16.3 에뮬레이터 검증

- [x] Pixel 8 AVD(`sdk_gphone64_x86_64`, API 36)에 debug APK 설치 및 all-files access 허용
- [x] 앱 cold start와 기본 내부 저장소 LIST 표시 확인
- [x] 격리한 `Download/PhotoExplorerTest`에서 직접 하위 항목 수 0/1/17, 파일 크기 0 B/1.05 MB,
  긴 폴더명 말줄임, 현재 폴더 title과 breadcrumb를 확인
- [x] LIST·GRID·MEDIA 전환, MEDIA 폴더 아이콘의 중앙 정렬·56dp 크기, 새 3-layer 폴더 아이콘 확인
- [x] 폴더 선택 시 이동·복사·삭제 아이콘과 접근성 이름(`Cut`, `Copy`, `Delete`), 행 메뉴의
  파일명 포함 접근성 이름 확인. 실제 이동·복사·삭제는 수행하지 않음
- [x] 선택된 MEDIA 화면을 세로→가로 회전한 뒤 보기 모드·선택 수 1·현재 경로 유지 확인
- [x] system dark mode 재구성 뒤 폴더/작업 아이콘 대비와 화면 상태 유지 확인
- [x] 17개 항목 LIST 스크롤 전후 앱바 내부 3개 표본 픽셀이 모두 `ARGB FF1E1F25`로 동일해
  lifted 상태에서 fill 색이 변하지 않음을 확인
- [x] 수정 APK 실행 이후 `AndroidRuntime:E`/`ActivityManager:E` 로그 없음

첫 설치본에서는 Fragment `onStart()`가 초기 `viewType` LiveData 전달보다 먼저 실행되어
`FileListAdapter.refreshDirectoryDescriptions()`에서 `UninitializedPropertyAccessException`이
발생했다. `_viewType` 초기화 여부를 확인하도록 수정했고, 단위 테스트·debug 빌드·재설치 후 위
검증을 다시 수행했다.

### 16.4 shipping 설정 및 SM-F971N 검증

- [x] Minified release 빌드 — R8, resource shrinking, `lintVitalRelease`, `assembleRelease` 성공
- [x] 프로젝트 release keystore가 없는 개발 환경이므로 로컬 Android debug 인증서로 QA 서명
- [x] `apksigner verify --verbose --print-certs` 성공 — v1/v2 서명, signer 1개
- [x] APK SHA-256 `0E77873428305C374CCEF66F43A21F0CCEC609F1F495BA81419F4FD5E593E25D`
- [x] SM-F971N(API 37, 1248×1972)에 데이터 보존 설치 성공, cold start 188ms
- [x] 실제 내부 저장소 LIST에서 폴더 count 0/1/4/5/17/647과 한국어 단위·행 메뉴 접근성 이름 확인
- [x] LIST·GRID·MEDIA 전환과 새 폴더 아이콘·MEDIA 중앙 정렬 확인
- [x] MEDIA 상태에서 세로→가로 회전 후 현재 경로·보기 상태 유지 확인
- [x] 프로세스 강제 종료 후 cold restart에서도 경로·MEDIA 설정 복원 확인
- [x] 테스트 종료 시 세로·자동 회전·LIST 보기로 복원
- [x] 전체 shipping 실기기 테스트 구간에서 `AndroidRuntime:E`/`ActivityManager:E` 로그 없음

이 APK는 shipping과 같은 최적화 설정으로 빌드됐지만 Android Debug 인증서로 QA 서명됐으므로
스토어/외부 배포용 최종 산출물은 아니다. 실기기의 기존 파일은 열기·선택·이동·복사·삭제하지 않았다.

### 16.5 남은 기기 검증

- SM-F971N의 실제 접힘↔펼침 전환, M2/동적 색 조합, 큰 글꼴, TalkBack 순회, RTL은 미확인이다.
- SAF/SMB/SFTP/archive provider의 실패·지연·취소 무시 동작과 count 첫 화면 성능 계측은 남아 있다.
- 배포용 release keystore를 사용한 최종 서명과 `apksigner` 검증은 남아 있다.

### 16.6 경로 바 중간 톤 후속 조정

- 밝은 테마 경로 바를 `#F1F3F4`에서 `#E9E7EE`로 바꿨다.
- Pixel 8 API 36 원본 캡처에서 즐겨찾기 칩 `#E3E1E8` → 경로 바 `#E9E7EE` → 고정 상단 배경
  `#EEEDF4` 순서로 측정되어 의도한 중간 톤을 확인했다.
- 야간 경로 바는 `#303134`에서 `#292A2D`로 낮췄다. 같은 캡처에서 즐겨찾기 칩 `#27282D`와
  경로 바 `#292A2D`가 가까운 단계로 보이고 기존처럼 경로 바만 밝게 뜨지 않았다.
- `assembleDebug`, `testDebugUnitTest` 7개와 `git diff --check`가 통과했다.
