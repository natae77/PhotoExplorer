# 12. 동영상 뷰어 구현 계획서

[11번 기획서](11-video-viewer-spec.md)를 어떻게 만들 것인가.
**10단계만은 [11a번 기획서](11a-viewer-ui-cleanup-spec.md)** 를 따른다.

- 작성일: 2026-08-28
- 프로젝트: **PhotoExplorer** (`natae77/PhotoExplorer`, `zhanghai/MaterialFiles` fork)
- 브랜치: `feature/video-viewer` — 기준 커밋 `19d8771f` (`feature/media-view-mode` 끝)
- 전제: 기획서의 D1~D12가 모두 확정된 상태

**표기** — 09번과 같은 규칙을 쓴다.

- `§`는 **문서의 절**이다. 다른 문서를 가리킬 때는 `11번 §5.2`처럼 문서 번호를 앞에 붙인다.
- `단계`는 **작업 순서**다. 절 번호와 무관하다.

> ⚠️ **커밋은 사용자만 한다.** 이 계획서의 각 단계 끝에는 **커밋 메시지 초안**만 적혀 있다.
> 작업자(사람이든 Claude든)는 파일 변경까지만 하고 멈춘다. `git add`도 하지 않는다.
> [프로젝트 CLAUDE.md](../CLAUDE.md) 참고.

> ⚠️ **이 프로젝트에는 테스트 소스셋이 없다.** `app/src` 아래에 `main` 뿐이고
> `test`/`androidTest` 디렉터리도, JUnit·Espresso 의존성도 없다. 이 기능은 실제 재생과
> 터치가 대상이라 계측 테스트가 맞지만, 그 기반을 새로 까는 것은 이 작업 범위 밖이다
> ([10번 §5](10-image-viewer-swipe-down.md)에서 같은 판단을 했다).
> **검증은 단계마다 "빌드 통과 + 에뮬레이터/실기기 수동 확인"으로 한다.**
> 그래서 아래 단계들은 "실패하는 테스트를 먼저 쓴다"가 아니라 **"눈으로 확인할 수 있는
> 가장 작은 덩어리"** 로 잘라 놓았다.

## 0. 원칙

- **단계마다 빌드되고 눈으로 확인 가능해야 한다.** 각 단계 끝에 앱을 설치해 확인한다.
- **위험한 것부터 확인한다.** 0단계에서 라이브러리가 붙는지, 1단계 검증에서 아이폰 HEVC `.mov`가
  실제로 재생되는지부터 본다. 여기서 막히면 설계가 아니라 코덱 문제이므로 방향이 달라진다.
- **이름 정리를 먼저 한다.** 새로 만드는 파일이 `MediaViewer*` 이름 위에 얹히도록,
  이름 바꾸기(2단계)를 기능 구현 앞에 둔다. 나중에 하면 새 파일까지 두 번 이름을 바꾸게 된다.
- **되돌리기 쉽게 쪼갠다.** 단계마다 커밋 하나가 되도록 경계를 잡았다.

## 1. 만드는 순서

**"단계"는 작업 순서다.** 아래로 갈수록 앞 단계 위에 얹힌다.

| 단계 | 만드는 것 | 화면에서 보이는 것 | 기획서 | 상태 |
|---|---|---|---|---|
| 0 | Media3 의존성 추가 | (없음 — 빌드와 APK 크기만 확인) | §10 | ✅ 2026-08-28 |
| 1 | 이름 정리 `ImageViewer*` → `MediaViewer*` | 뷰어 제목이 "미디어 뷰어"로 바뀜 | §4.1, D11 | ✅ 2026-08-28 |
| 2 | **동영상이 뷰어에 들어온다** | 사진↔동영상이 좌우로 이어짐. 동영상은 썸네일 정지화면 | §3, §4.2, §4.3 | ✅ 2026-08-28 |
| 3 | **재생** | 페이지가 멈추면 자동 재생 | §5.1, §5.2 | ✅ 2026-08-28 |
| 4 | 컨트롤 ↔ 앱 바 묶기 | 탭하면 앱 바와 슬라이더가 같이 나타남 | §6.1, §6.2 | ✅ 2026-08-28 |
| 5 | 수명 · 재생 위치 · 오디오 · 화면 | 나갔다 와도 이어짐 | §5.3, §5.4, §5.5 | ✅ 2026-08-28 |
| 6 | 속도 조절 | `⋮` → 재생 속도 | §6.3 | ✅ 2026-08-28 |
| 7 | 세부 정보 오버레이 | `⋮` → 세부 정보 | §7 | ✅ 2026-08-28 |
| 8 | 오류 처리 · 공유 MIME · 삭제 안전 | 못 여는 코덱에서 안내 문구 | §8, §9 | ✅ 2026-08-28 |
| 9 | 검증 · 실기기 측정 | — | §11, §12 | ✅ 2026-08-28 — 에뮬레이터 + 실기기(SM-F971N). 11번 §11의 2·5번만 남음 |
| 10 | **화면 정리** — 검정 판 제거 · 컨트롤 손보기 | 사진/영상이 화면을 다 쓰고, 조작 요소만 얹힌다 | **11a 전체** | ✅ 2026-08-29 — 에뮬레이터 + 실기기(SM-F971N, release/R8) |

**구현하면서 계획과 달라진 곳** — 전부 에뮬레이터에서 돌려 보고 고친 것이다.
계획 본문은 그대로 두고(끝난 단계는 고치지 않는다) 여기에만 적는다.

| # | 계획 | 실제 |
|---|---|---|
| 1 | 3단계 §3.3 — 떠난 페이지 되돌리기를 `bindVideo()`에 맡긴다 | **떠난 페이지를 직접 되돌린다.** `offscreenPageLimit = 1`이라 바로 옆 페이지는 다시 바인딩되지 않아, 되돌아왔을 때 **검은 화면**이 그대로 보였다 |
| 2 | 4단계 §4.1 — `PlayerControlView`를 `wrap_content` 높이로 | **`minHeight="220dp"`를 준다.** `wrap_content`면 Media3가 "minimal mode"로 내려가 **시간 표시(현재/전체)와 10초 버튼이 사라진다** — 수용 기준 5가 깨진다 |
| 3 | 6단계 §6.3 — `menu.findItem(id).isChecked = i == index` | **맞는 항목에만 `isChecked = true`.** `checkableBehavior="single"` 그룹에서는 `setChecked(false)`도 **그 항목을 체크된 것으로 만든다**(`MenuItemImpl.setChecked()`). 그래서 항상 마지막 항목(2×)에 표시가 찍혔다 |
| 4 | 4단계 §4.3 — 컨트롤 표시를 `SystemUiHelper` 콜백과 `onPageSelected`에서 갱신 | **`onViewStateRestored()`에서도 한 번 부른다.** 콜백을 `setCurrentItem()` **뒤에** 등록하므로 첫 페이지에는 `onPageSelected()`가 오지 않는다 — 기존 코드가 `updateTitle()`을 여기서 부르는 이유와 같다. 없으면 **동영상을 눌러 들어간 첫 화면에 슬라이더가 안 뜬다** |
| 5 | 6단계 — 배속은 `⋮` 메뉴로만 바뀐다 | **`onPlaybackParametersChanged`로 되받는다.** `PlayerControlView`의 기본 컨트롤에는 톱니바퀴(설정) → 속도 메뉴가 딸려 있고, 거기에는 우리가 안 쓰는 1.25×도 있다. 되받지 않으면 부제목과 라디오 표시가 실제 속도와 어긋난다. 목록에 없는 값은 `media_viewer_speed_format`(`%1$s×`)로 표시한다 |
| 6 | 2단계 §2.4 — `bindVideo()`가 썸네일을 `isVisible = true`로 되돌린다 | **`animate().cancel()`과 `alpha = 1f`도 같이.** 첫 프레임에서 건 페이드아웃 애니메이션이 아직 돌고 있을 수 있다 |
| 7 | 10단계 §10.3.1 — 되감기/빨리감기를 `ImageButton` + `ExoStyledControls.Button.Center.Rewind`/`.Ffwd`로 | **`Button` + `.RewWithAmount`/`.FfwdWithAmount`, id도 `exo_rew_with_amount`/`exo_ffwd_with_amount`.** aar을 열어 보니 계획서에 적은 이름이 1.11.0에 없다. 실제 스타일은 `TextView` 계열이고 초 수(`10`)를 글자로 그린다 |
| 8 | 10단계 §10.1 — 버튼 배경으로 `media_viewer_control_background.xml`(원 + 리플)을 준다 | **드로어블을 만들지 않고, 버튼마다 `FrameLayout` 래퍼에 원 스크림을 깔았다.** `RewWithAmount`/`FfwdWithAmount`는 **아이콘을 `android:background`에, 리플을 `android:foreground`에** 갖고 있다. 배경을 덮어쓰면 아이콘이 지워진다 |
| 9 | 10단계 §10.3.1 — `paddingHorizontal` · `layout_marginHorizontal` | **`paddingStart/End` · `layout_marginStart/End`.** 앞엣것들은 API 26+인데 이 앱은 `minSdk 23`이다 |
| 10 | 10단계 §10.6 — `image_viewer_subtitle_format`을 지운다 | **지우지 않았다.** upstream 문자열이고 **31개 로케일에 번역이 들어 있다.** 지우면 번역 파일 31개를 건드리게 되고 upstream 병합만 어려워진다. 우리가 만든 `media_viewer_speed_format`만 지웠다 |

**상태 칸은 착수하면서 만든다.** 09번처럼 단계마다 갱신하고, **끝난 단계는 나중에 기획이
바뀌어도 본문을 고치지 않는다** — 대신 뒤에 새 단계를 붙인다.

2단계까지만 해도 확인할 것이 생긴다 — 동영상이 뷰어 페이지로 들어오는지, 사진과 섞여
좌우로 넘어가는지. 3단계부터가 실제 재생이다.

## 2. 만들 파일 · 고칠 파일

**신규**

| 파일 | 책임 |
|---|---|
| `viewer/media/PlayableVideo.kt` | "이 경로를 앱 안에서 재생할 수 있는가" 판정 하나만 (§3) |
| `viewer/media/VideoPlayerHolder.kt` | ExoPlayer 인스턴스 하나의 수명·부착·재생 위치 (§5) |
| `viewer/media/MediaViewerViewModel.kt` | 회전을 넘겨 사는 세션 상태 — 재생 위치·배속·파일 정보 (§5.1) |
| `viewer/media/VideoDetailsDialogFragment.kt` | 세부 정보 오버레이 시트 (§7) |
| `viewer/media/VideoDetails.kt` | 오버레이에 뿌릴 값 묶음 + 만드는 함수 (§7.2) |
| `res/layout/media_viewer_video_item.xml` | 동영상 페이지 (§4.3) |
| `res/layout/video_details_dialog.xml` | 세부 정보 시트 |
| `viewer/media/ScrimmedIcon.kt` | 앱 바 아이콘 뒤에 원 스크림을 씌우는 확장 함수 하나 (10단계, 11a §3.3) |
| `viewer/media/DelayedProgress.kt` | 페이지 하나의 로딩 표시 — 두 이유를 모아 0.5초 지연 (10단계, 11a §6.1) |
| `res/layout/media_viewer_player_control.xml` | 하단 컨트롤 레이아웃 (10단계, 11a §4) |
| `res/drawable/media_viewer_scrim_circle.xml` | 아이콘 뒤 원 (10단계) |
| `res/drawable/media_viewer_scrim_rect.xml` | 시간 텍스트 뒤 라운드 사각 (10단계) |
| `res/drawable/media_viewer_control_background.xml` | 재생 버튼 배경 — 원 스크림 + 리플 (10단계) |

**이름만 바뀜 (1단계)**

| 지금 | 바꾼 뒤 |
|---|---|
| `viewer/image/` (패키지) | `viewer/media/` |
| `ImageViewerActivity.kt` | `MediaViewerActivity.kt` |
| `ImageViewerFragment.kt` | `MediaViewerFragment.kt` |
| `ImageViewerAdapter.kt` | `MediaViewerAdapter.kt` |
| `res/layout/image_viewer_fragment.xml` | `media_viewer_fragment.xml` |
| `res/layout/image_viewer_item.xml` | `media_viewer_image_item.xml` |
| `res/menu/image_viewer.xml` | `media_viewer.xml` |

`viewer/image/ConfirmDeleteDialogFragment.kt`와 `viewer/saveas/`는 **이름을 바꾸지 않는다.**
앞엣것은 패키지만 따라 옮겨지고, 뒤엣것은 이 작업과 무관하다.

**수정**

| 파일 | 무엇을 |
|---|---|
| `app/build.gradle` | Media3 의존성 (0단계) |
| `AndroidManifest.xml:339` | 액티비티 이름. **인텐트 필터는 그대로** (1단계) |
| `filelist/FileListFragment.kt` | 뷰어 목록에 동영상 포함, 진입 분기 (2단계) |
| `res/values/strings.xml`, `res/values-ko/strings.xml` | 제목 변경 + 새 문자열 |
| `util/IntentExtensions.kt` 사용처 | 공유 MIME (8단계) |
| `res/layout/media_viewer_fragment.xml` | 앱 바 배경 투명, 컨트롤 레이아웃 교체 (10단계) |
| `res/layout/media_viewer_video_item.xml` | `show_buffering="never"` (10단계) |
| `viewer/media/MediaViewerFragment.kt` | 제목 제거, 아이콘 스크림, 버퍼링 표시 연결 (10단계) |
| `viewer/media/MediaViewerAdapter.kt` | 썸네일 로딩 표시를 `DelayedProgress`로 (10단계) |

## 3. 전역 제약

기획서에서 그대로 가져온 값들이다. **모든 단계에 항상 적용된다.**

| 제약 | 값 | 근거 |
|---|---|---|
| Media3 버전 | **1.11.0** | 로컬 Gradle 캐시에 이미 있음. `minSdkVersion 23`으로 이 프로젝트(`minSdk 23`)와 정확히 맞는다 |
| 재생 대상 | `MimeType.isVideo` **그리고** (`isLinuxPath` 또는 `isDocumentPath`) | 11번 §3 |
| 배속 값 | `0.25` `0.5` `0.75` `1.0` `1.5` `2.0` — 여섯 개 고정 | 11번 §6.3 |
| 촬영 시각 | **`MediaCreatedTime.read()` 만 쓴다.** 직접 다시 읽지 않는다 | 11번 §7.2 |
| 매니페스트 | `video/*` 인텐트 필터를 **넣지 않는다** | 11번 D4 |
| 아래로 스와이프 닫기 | **이번 범위 아님.** 동영상 페이지에 제스처를 붙이지 않는다 | 11번 D12 |
| 문자열 | 새 문자열은 `values/`(영어)와 `values-ko/`(한국어) **둘 다** | 11번 §10 |
| 커밋 | **하지 않는다.** 메시지 초안만 남긴다 | 프로젝트 CLAUDE.md |

**빌드 명령** (README와 동일):

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```

---

## 0단계. Media3 의존성 추가

화면 변화는 없다. 라이브러리가 붙는지와 APK가 얼마나 커지는지만 본다.

### 0.1 크기 기준값 먼저 재 둔다

- [ ] **의존성을 넣기 전** APK 크기를 잰다. 나중에 비교할 값이다.

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
ls -l app/build/outputs/apk/debug/*.apk
```

### 0.2 의존성 추가

- [ ] [app/build.gradle](../app/build.gradle)의 androidx 블록에 두 줄을 넣는다.
      **알파벳 순서를 지킨다** — `lifecycle` 다음, `preference` 앞이다.

```gradle
    implementation "androidx.lifecycle:lifecycle-viewmodel-ktx:$androidx_lifecycle_version"
    def androidx_media3_version = '1.11.0'
    implementation "androidx.media3:media3-exoplayer:$androidx_media3_version"
    implementation "androidx.media3:media3-ui:$androidx_media3_version"
    implementation 'androidx.preference:preference-ktx:1.2.1'
```

`media3-common`, `media3-datasource`, `media3-extractor`, `media3-decoder`, `media3-container`,
`media3-database`는 위 둘이 알아서 끌고 온다. **직접 적지 않는다.**
`media3-session`, `media3-exoplayer-dash/hls/rtsp`는 **넣지 않는다** — 각각 백그라운드 재생과
스트리밍용이고 둘 다 기획서 비목표다.

### 0.3 `@UnstableApi` — 이걸 모르면 컴파일이 안 된다

Media3 1.x의 UI 클래스는 전부 `@androidx.media3.common.util.UnstableApi`로 표시돼 있다
(`PlayerView`, `PlayerControlView` 모두 1.11.0 바이트코드에서 확인함).
그냥 쓰면 **경고가 아니라 오류**로 막힌다.

쓰는 쪽 클래스나 함수마다 이걸 붙인다.

```kotlin
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
class MediaViewerFragment : Fragment() { /* ... */ }
```

`kotlin.OptIn`이 **아니라** `androidx.annotation.OptIn`이다. 둘 다 이름이 `OptIn`이라
자동 임포트가 엉뚱한 쪽을 잡기 쉽다. 잡히면 "This declaration is experimental" 오류가
사라지지 않는다.

`androidx.media3.exoplayer.ExoPlayer`와 `androidx.media3.common.*`(`Player`, `MediaItem`,
`Format`, `PlaybackException`, `AudioAttributes`)는 안정 API라 표시가 필요 없다.

### 0.4 0단계 검증

- [ ] 빌드가 통과한다.
- [ ] APK 크기를 다시 재서 0.1의 값과 비교한다. **늘어난 양을 [11번 §11의 6번 칸에 적는다.**

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
ls -l app/build/outputs/apk/debug/*.apk
```

- [ ] **release 빌드도 한 번 해 본다.** 이 프로젝트는 release에서 `minifyEnabled true`,
      `shrinkResources true`다([app/build.gradle:76](../app/build.gradle#L76)).
      Media3는 자체 consumer ProGuard 규칙을 들고 오지만, 확인은 여기서 해 두는 편이
      나중에 원인을 찾는 것보다 싸다.

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleRelease
```

> **커밋 메시지 초안**
> `동영상 재생을 위해 Media3(ExoPlayer) 1.11.0 의존성 추가`

---

## 1단계. 이름 정리 — `ImageViewer*` → `MediaViewer*`

**동작은 하나도 바뀌지 않는다.** 화면 제목만 "이미지 뷰어" → "미디어 뷰어"가 된다.
기획서 §4.1과 D11.

### 1.1 왜 지금 하나

이 뷰어가 사진만 다룬다는 전제로 지어진 이름이 코드 곳곳에 남아 있는데,
2단계부터는 사실이 아니게 된다. 그리고 새로 만들 레이아웃·클래스가 `image_viewer_*` 옆에
`media_viewer_*`로 섞여 앉으면 나중에 두 번 이름을 바꾸게 된다.

### 1.2 밖으로 새는 곳은 두 군데뿐이다

패키지 밖에서 이 심볼들을 참조하는 곳을 전부 훑으면 이렇다.

| 파일 | 줄 | 내용 |
|---|---|---|
| [AndroidManifest.xml:339](../app/src/main/AndroidManifest.xml#L339) | 1줄 | `android:name` |
| [FileListFragment.kt:133](../app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt#L133) | import | |
| [FileListFragment.kt:1375](../app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt#L1375) | 호출 | `maybeAddImageViewerActivityExtras(...)` |
| [FileListFragment.kt:1393](../app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt#L1393) | 정의 | 같은 함수 |
| [FileListFragment.kt:1420](../app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt#L1420) | 호출 | `ImageViewerActivity.putExtras(...)` |

그래서 기계적 변경이고 위험이 낮다. **Android Studio의 Refactor > Rename을 쓰는 편이
가장 안전하다** — 레이아웃 이름을 바꾸면 뷰 바인딩 클래스 이름(`ImageViewerFragmentBinding`
→ `MediaViewerFragmentBinding`)까지 따라 바뀌는데, 이건 손으로 고치면 빠뜨리기 쉽다.

### 1.3 ⚠️ 인텐트 extra 키가 클래스 이름에서 나온다

[ImageViewerActivity.kt](../app/src/main/java/me/zhanghai/android/files/viewer/image/ImageViewerActivity.kt)의
extra 키는 클래스 이름을 문자열로 쓴다.

```kotlin
private val EXTRA_POSITION = "${ImageViewerActivity::class.java.name}.extra.POSITION"
```

이름을 바꾸면 이 문자열 값도 같이 바뀐다. **넣는 쪽과 꺼내는 쪽이 같은 상수를 쓰므로
문제가 없다.** 다만 이 키가 저장돼 있거나 외부와 주고받는 값이 **아니라는 점**을 확인하고
넘어간다 — 확인했다. `putExtras()`를 부르는 곳은 `FileListFragment` 하나뿐이다.

### 1.4 바꿀 것 목록

- [ ] 패키지 `viewer/image/` → `viewer/media/` (`ConfirmDeleteDialogFragment.kt`도 같이 옮긴다)
- [ ] `ImageViewerActivity` → `MediaViewerActivity`
- [ ] `ImageViewerFragment` → `MediaViewerFragment`
- [ ] `ImageViewerAdapter` → `MediaViewerAdapter`
- [ ] `res/layout/image_viewer_fragment.xml` → `media_viewer_fragment.xml`
- [ ] `res/layout/image_viewer_item.xml` → **`media_viewer_image_item.xml`**
      (2단계에서 `media_viewer_video_item.xml`이 옆에 생기므로 `image`를 남긴다)
- [ ] `res/menu/image_viewer.xml` → `media_viewer.xml`
- [ ] `FileListFragment.maybeAddImageViewerActivityExtras` → `maybeAddMediaViewerExtras`
- [ ] `FileListFragment.IMAGE_VIEWER_ACTIVITY_PATH_LIST_SIZE_MAX` → `MEDIA_VIEWER_PATH_LIST_SIZE_MAX`
- [ ] `AndroidManifest.xml`의 `android:name` — **`<intent-filter>`의 `image/*`는 그대로 둔다**

### 1.5 문자열 — **키 이름은 바꾸지 않는다. 값만 바꾼다**

⚠️ `image_viewer_`로 시작하는 키는 **32개 파일**에 들어 있다 — `values/`, `values-ko/`,
그리고 번역 30개.

```bash
grep -rl "image_viewer_" app/src/main/res/ | wc -l   # 32
```

이 번역 파일들은 upstream(`zhanghai/MaterialFiles`)에서 내려오는 것이라 키를 바꾸면
**다음 병합 때 30개 파일이 한꺼번에 충돌한다.** 얻는 것은 키 이름의 일관성뿐이다.
그래서 **기존 키 세 개는 이름을 그대로 두고, 사용자에게 보이는 값만 바꾼다.**
2단계부터 새로 만드는 문자열은 `media_viewer_` 접두사를 쓴다 — 새 키라 충돌할 곳이 없다.

- [ ] `res/values/strings.xml:699` — **키는 그대로**, 값만.

```xml
    <string name="image_viewer_title">Media viewer</string>
```

- [ ] `res/values-ko/strings.xml:554` — 같은 키의 값만.

```xml
    <string name="image_viewer_title">미디어 뷰어</string>
```

- [ ] `image_viewer_subtitle_format`은 **손대지 않는다.** 값이 `%1$,d/%2$,d`라
      사용자에게 "이미지"라는 말이 보이지 않는다.
- [ ] `image_viewer_delete_message_format`도 **손대지 않는다.** 이건 리터럴이 아니라
      `translatable="false"`가 붙은 **별칭**이다
      ([values/strings.xml:701](../app/src/main/res/values/strings.xml#L701) →
      `@string/file_delete_message_file_format`). `values-ko`에는 애초에 이 키가 없다 —
      번역할 것이 없기 때문이다. **새로 넣으면 안 된다.**
- [ ] 나머지 30개 번역 파일은 **건드리지 않는다.** 그쪽 `image_viewer_title`은 각 언어의
      "이미지 뷰어"로 남지만, 번역은 원래 upstream을 따라오는 것이고 기능에는 영향이 없다.

**1단계가 끝났는지는 문자열이 아니라 코드·리소스 이름으로 판정한다.**

```bash
ls app/src/main/res/layout/image_viewer_* app/src/main/res/menu/image_viewer.xml 2>/dev/null
grep -rn "ImageViewer" --include=*.kt --include=*.xml app/src/main
```

둘 다 아무것도 뱉지 않아야 1단계가 끝난 것이다.
`R.string.image_viewer_*`가 코드에 남아 있는 것은 **정상이다.**

### 1.6 1단계 검증

- [ ] 빌드 통과
- [ ] 사진을 눌러 뷰어에 들어간다. **동작이 이전과 완전히 같다** — 좌우 넘김, 탭으로 앱 바 토글,
      더블탭 확대, 삭제, 공유
- [ ] 앱 바 제목이 파일 이름, 부제목이 `3/20` 형태로 나온다
- [ ] 다른 앱(예: 갤러리)에서 사진을 "다른 앱으로 열기" 했을 때 후보 목록에
      **PhotoExplorer가 여전히 뜬다** (인텐트 필터를 건드리지 않았는지 확인)
- [ ] 최근 앱 목록이나 앱 정보에서 뷰어 라벨이 "미디어 뷰어"로 보인다

> **커밋 메시지 초안**
> `이미지 뷰어가 동영상도 다루게 되므로 MediaViewer 로 이름 정리`

---

## 2단계. 동영상이 뷰어에 들어온다

여기까지 하면 **사진과 동영상이 좌우로 이어서 넘어간다.** 동영상은 아직 재생되지 않고
첫 프레임 썸네일이 정지화면으로 보인다. 재생은 3단계다.

### 2.1 재생 가능 판정 (신규)

`app/src/main/java/me/zhanghai/android/files/viewer/media/PlayableVideo.kt`

```kotlin
/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import java8.nio.file.Path
import me.zhanghai.android.files.file.MimeType
import me.zhanghai.android.files.file.guessFromPath
import me.zhanghai.android.files.file.isVideo
import me.zhanghai.android.files.provider.document.isDocumentPath
import me.zhanghai.android.files.provider.linux.isLinuxPath

/**
 * Whether this path is a video we can play inside the app, see spec 11 section 3.
 *
 * The MIME type is guessed from the file name only. getItemViewType() calls this on the main
 * thread for every page, so it must never touch the file system.
 */
val Path.isPlayableVideo: Boolean
    get() = MimeType.guessFromPath(toString()).isVideo && (isLinuxPath || isDocumentPath)
```

#### ⚠️ 왜 `AndroidFileTypeDetector`를 쓰지 않는가

뷰어의 기존 코드는 MIME 타입을 이렇게 알아낸다
([MediaViewerAdapter.loadImageInfo()](../app/src/main/java/me/zhanghai/android/files/viewer/image/ImageViewerAdapter.kt)).

```kotlin
val attributes = readAttributes(BasicFileAttributes::class.java)   // ← 파일 시스템을 읽는다
val mimeType = AndroidFileTypeDetector.getMimeType(this, attributes).asMimeType()
```

이건 **IO 디스패처 안에서** 도는 코드다. 그런데 `getItemViewType()`은 `RecyclerView`가
**메인 스레드에서** 부르고, 페이지마다 부른다. 여기서 파일을 읽으면 스크롤이 끊기고
원격 경로에서는 ANR까지 간다.

`MimeType.guessFromPath()`는 파일 이름의 확장자를 메모리 안의 맵에서 찾는 순수 문자열 연산이다
([MimeTypeConversionExtensions.kt:14](../app/src/main/java/me/zhanghai/android/files/file/MimeTypeConversionExtensions.kt#L14)).
`.mov` → `video/quicktime`, `.mp4` → `video/mp4`가 이미 등록돼 있다.
확장자가 없거나 엉뚱하면 사진 페이지로 취급되고, 그 페이지는 8단계의 오류 표시로 떨어진다 —
크래시가 아니다.

### 2.2 뷰어 목록에 동영상 포함

[FileListFragment.kt:1393](../app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt#L1393)
(1단계에서 `maybeAddMediaViewerExtras`로 이름이 바뀌었다.)

- [ ] 이른 반환을 사진과 재생 가능한 동영상 둘 다 통과하게 바꾸고, **목록을 실제로 붙였는지를
      반환하게** 한다. 반환값이 왜 필요한지는 바로 아래 §2.2.1에 있다.

```kotlin
    /** Returns whether the viewer got a page list, see plan 12 2.2.1. */
    private fun maybeAddMediaViewerExtras(intent: Intent, path: Path, mimeType: MimeType): Boolean {
        // Videos we cannot play in-app keep going to an external player, see spec 11 section 3.
        if (!(mimeType.isImage || path.isPlayableVideo)) {
            return false
        }
        var paths = mutableListOf<Path>()
        // We need the ordered list from our adapter instead of the list from FileListLiveData.
        for (index in 0..<adapter.itemCount) {
            // Date tiles are not files. Missing this crashes when opening a photo, and this loop
            // lives outside the adapter so it is easy to overlook. See plan 09 4.1.
            val item = adapter.getItem(index) as? FileListItem.File ?: continue
            val file = item.file
            val filePath = file.path
            if (file.mimeType.isImage || filePath.isPlayableVideo || filePath == path) {
                paths.add(filePath)
            }
        }
        var position = paths.indexOf(path)
        if (position == -1) {
            return false
        }
        // ... 상한 자르기는 기존 코드 그대로
        MediaViewerActivity.putExtras(intent, paths, position)
        return true
    }
```

세 곳을 고쳤다. 첫 반환은 **동영상을 눌렀을 때도 뷰어로 보내려고**, 반복문 안은 **사진을 눌러
들어갔을 때 동영상도 목록에 담으려고**, 반환값은 §2.2.1 때문이다.

`filePath == path` 조건은 기존 코드에 있던 것이고 그대로 둔다 — 누른 파일 자신은 종류와
무관하게 목록에 들어가야 `position`이 `-1`이 되지 않는다.

기존 호출부인 `openFileWithIntent()` 안에서는 **반환값을 쓰지 않는다.** 사진은 지금처럼
암시적 인텐트가 처리하고, 목록이 안 붙으면 뷰어가 한 장짜리로 뜰 뿐이다 — 지금과 같다.

#### ⚠️ 이것만으로는 외부 앱으로 계속 나간다

`maybeAddMediaViewerExtras()`는 **인텐트에 extra를 붙일 뿐**이다. 실제로 어느 앱이 뜰지는
`path.fileProviderUri.createViewIntent(mimeType)`이 만든 암시적 인텐트가 정한다
([FileListFragment.kt:1370](../app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt#L1370)).
사진이 우리 뷰어로 오는 건 매니페스트에 `image/*` 필터가 있어서인데,
**기획서 D4에 따라 `video/*` 필터는 넣지 않기로 했다.**

그래서 동영상은 **암시적 인텐트를 쓰지 않고 뷰어 액티비티를 명시적으로 띄운다.**

- [ ] `openFile()`에 분기를 추가한다. `openApk` 분기 바로 아래가 자리다.

```kotlin
        if (file.mimeType.isApk) {
            openApk(file)
            return
        }
        // Playable videos open in our own viewer. There is no video/* intent filter (spec 11 D4),
        // so the implicit view intent below would always land in another app.
        if (file.path.isPlayableVideo) {
            openMediaViewer(file)
            return
        }
        if (file.isListable) {
```

- [ ] 그 액티비티를 명시적으로 띄우는 함수를 `FileListFragment`에 추가한다.
      MIME 타입은 새로 알아내지 말고 **호출부가 이미 들고 있는 `file.mimeType`을 쓴다.**

```kotlin
    private fun openMediaViewer(file: FileItem) {
        val intent = MediaViewerActivity::class.createIntent()
            .apply { extraPath = file.path }
        if (!maybeAddMediaViewerExtras(intent, file.path, file.mimeType)) {
            // See plan 12 2.2.1: without a page list the viewer would close itself immediately,
            // and an explicit intent has no other app to fall back to.
            openFileWithIntent(file, false)
            return
        }
        startActivitySafe(intent)
    }
```

`createIntent()`, `startActivitySafe()`, `extraPath`는 이 파일이 이미 쓰고 있는 확장 함수다
(`EditFileActivity::class.createIntent()` 참고).

#### 2.2.1 ⚠️ 목록을 못 만들면 화면이 열렸다 바로 닫힌다

`maybeAddMediaViewerExtras()`는 `position == -1`이면 조용히 반환한다. 그러면 인텐트에
`extraPathList`가 없고, 프래그먼트는 `paths.isEmpty()`에서 곧바로 `finish()`한다
([ImageViewerFragment.kt](../app/src/main/java/me/zhanghai/android/files/viewer/image/ImageViewerFragment.kt)
`onActivityCreated`). 사용자에게는 **누른 동영상이 아무 데서도 안 열리는** 것으로 보인다.

지금까지는 이 경우가 문제가 아니었다. extra가 안 붙어도 암시적 인텐트가 남아 있어서
다른 앱이 떴기 때문이다. 명시적 인텐트에는 그 안전망이 없다. 그래서 **실패하면 예전 경로
(`openFileWithIntent(file, false)`)로 떨어뜨린다** — 최악이라도 이 기능이 생기기 전과 같다.

- [ ] **"다른 앱으로 열기"(`openFileWith`)는 건드리지 않는다.** 거기서는 지금처럼 선택지가 떠야 한다.
      8단계의 오류 화면도 이 경로를 재사용한다.

### 2.3 동영상 페이지 레이아웃 (신규)

`app/src/main/res/layout/media_viewer_video_item.xml`

```xml
<?xml version="1.0" encoding="utf-8"?>

<!--
  ~ Copyright (c) 2026 PhotoExplorer
  ~ All Rights Reserved.
  -->

<FrameLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="match_parent">

    <androidx.media3.ui.PlayerView
        android:id="@+id/playerView"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:visibility="gone"
        app:use_controller="false"
        app:surface_type="texture_view"
        app:show_buffering="when_playing"
        app:resize_mode="fit" />

    <!-- Above the player on purpose, see 2.3.1. -->
    <ImageView
        android:id="@+id/thumbnailImage"
        android:layout_width="match_parent"
        android:layout_height="match_parent"
        android:scaleType="fitCenter"
        android:contentDescription="@null" />

    <ProgressBar
        android:id="@+id/progress"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:indeterminate="true"
        android:visibility="gone"
        style="@style/Widget.AppCompat.ProgressBar" />

    <LinearLayout
        android:id="@+id/errorLayout"
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center"
        android:orientation="vertical"
        android:gravity="center_horizontal"
        android:visibility="gone">

        <TextView
            android:id="@+id/errorText"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:gravity="center_horizontal"
            android:textAppearance="@style/TextAppearance.AppCompat.Title" />

        <Button
            android:id="@+id/openWithButton"
            android:layout_width="wrap_content"
            android:layout_height="wrap_content"
            android:layout_marginTop="16dp"
            android:text="@string/media_viewer_open_with" />
    </LinearLayout>
</FrameLayout>
```

#### 2.3.1 ⚠️ 썸네일이 `PlayerView` **위**에 있어야 한다

`FrameLayout`은 나중에 적힌 자식이 위에 그려진다. 순서를 뒤집어 썸네일을 아래에 두면
**검은 화면이 썸네일을 덮는다.**

`PlayerView`의 레이아웃(`exo_player_view.xml`)에는 `exo_shutter`라는 뷰가 들어 있고,
`android:background="@android:color/black"`으로 화면 전체를 덮은 채 **첫 프레임이 그려질
때까지 보인다** (1.11.0 aar에서 확인). 그래서 `playerView.isVisible = true`를 하는 순간
그 아래 썸네일은 아무 소용이 없어지고, 기획서 §10이 막으려던 "검은 번쩍임"이 그대로 난다.
그 상태에서는 §3.3의 "첫 프레임에 썸네일을 페이드아웃한다"도 **보이지 않는 뷰를 지우는
헛일**이 된다.

썸네일을 위에 두면 순서가 이렇게 된다.

1. 페이지에 도착 — 썸네일이 보인다 (그 아래에서 플레이어가 준비된다)
2. 첫 프레임이 그려짐 — 썸네일을 페이드아웃한다 (§3.3)
3. 그 아래에서 이미 영상이 돌고 있다. 검은 화면은 한 번도 보이지 않는다

`app:shutter_background_color="@android:color/transparent"`로 셔터를 투명하게 만드는
방법도 있지만, 그러면 준비되는 동안 뒤에 있는 것이 비쳐 보인다. **순서로 푸는 쪽을 쓴다.**

#### ⚠️ `surface_type="texture_view"` — 이걸 `surface_view`로 두면 페이지 전환이 깨진다

이 뷰어는 `DepthPageTransformer`를 쓴다
([MediaViewerFragment](../app/src/main/java/me/zhanghai/android/files/viewer/image/ImageViewerFragment.kt),
[ViewPagerTransformers.kt](../app/src/main/java/me/zhanghai/android/files/ui/ViewPagerTransformers.kt)).
이 트랜스포머는 페이지에 **`alpha`, `scaleX/Y`, `translationX`, `translationZ`** 를 건다.

`SurfaceView`는 윈도우에 구멍을 뚫고 그 뒤에서 따로 그리는 뷰라 **`alpha`도 `scale`도 먹지 않는다.**
그대로 두면 페이지를 넘기는 내내 동영상만 불투명하게, 원래 크기로 남아서 옆 페이지 위를 덮는다.
`TextureView`는 일반 뷰처럼 그려지므로 트랜스포머가 그대로 적용된다.

Media3의 기본값은 `surface_view`다. **반드시 명시적으로 바꿔야 한다.**

대가도 적어 둔다 — `TextureView`는 전력과 지연이 조금 더 들고, 보호 콘텐츠(DRM) 출력을 못 한다.
폰으로 찍은 로컬 파일이 대상이라 둘 다 해당하지 않는다.

### 2.4 어댑터에 뷰 타입 도입

[MediaViewerAdapter.kt](../app/src/main/java/me/zhanghai/android/files/viewer/image/ImageViewerAdapter.kt)

지금은 `SimpleAdapter<Path, MediaViewerAdapter.ViewHolder>`이고 ViewHolder가 하나뿐이다.
`FileListAdapter`가 날짜 타일을 넣을 때 쓴 것과 **같은 방식**으로 두 종류로 나눈다
([FileListAdapter.kt:252](../app/src/main/java/me/zhanghai/android/files/filelist/FileListAdapter.kt#L252)).

- [ ] 제네릭의 ViewHolder 타입을 `RecyclerView.ViewHolder`로 올린다.

```kotlin
class MediaViewerAdapter(
    private val lifecycleOwner: LifecycleOwner,
    private val listener: (View) -> Unit
) : SimpleAdapter<Path, RecyclerView.ViewHolder>() {
```

- [ ] 뷰 타입을 붙인다.

```kotlin
    override fun getItemViewType(position: Int): Int =
        if (getItem(position).isPlayableVideo) VIEW_TYPE_VIDEO else VIEW_TYPE_IMAGE

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = parent.context.layoutInflater
        return when (viewType) {
            VIEW_TYPE_VIDEO ->
                VideoViewHolder(MediaViewerVideoItemBinding.inflate(inflater, parent, false))
            else -> ImageViewHolder(MediaViewerImageItemBinding.inflate(inflater, parent, false))
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        val path = getItem(position)
        when (holder) {
            is ImageViewHolder -> bindImage(holder.binding, path)
            is VideoViewHolder -> bindVideo(holder.binding, path)
            else -> throw IllegalStateException(holder.toString())
        }
    }

    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        super.onViewRecycled(holder)

        when (holder) {
            is ImageViewHolder -> {
                holder.binding.image.dispose()
                holder.binding.largeImage.recycle()
            }
            is VideoViewHolder -> {
                holder.binding.thumbnailImage.dispose()
                // The player is detached in phase 3, not here.
            }
        }
    }

    companion object {
        private const val VIEW_TYPE_IMAGE = 0
        private const val VIEW_TYPE_VIDEO = 1

        // @see android.graphics.RecordingCanvas#MAX_BITMAP_SIZE
        private const val MAX_BITMAP_SIZE = 100 * 1024 * 1024
    }

    class ImageViewHolder(val binding: MediaViewerImageItemBinding) :
        RecyclerView.ViewHolder(binding.root)

    class VideoViewHolder(val binding: MediaViewerVideoItemBinding) :
        RecyclerView.ViewHolder(binding.root)
```

- [ ] 기존 `onBindViewHolder`의 몸통을 `bindImage(binding, path)`로 옮긴다. 내용은 그대로다.

```kotlin
    private fun bindImage(binding: MediaViewerImageItemBinding, path: Path) {
        binding.image.setOnPhotoTapListener { view, _, _ -> listener(view) }
        binding.largeImage.setOnClickListener(listener)
        loadImage(binding, path)
    }
```

동영상 바인딩은 **2단계에서는 썸네일과 탭 리스너뿐이다.** 코드는 아래 ⚠️ 항목을 읽고
그 아래 최종본을 쓴다.

#### ⚠️ 썸네일 요청에 `BasicFileAttributes`가 필요하다

목록의 썸네일은 `load(path to attributes)`로 요청한다
([FileListAdapter.kt:439](../app/src/main/java/me/zhanghai/android/files/filelist/FileListAdapter.kt#L439)).
Coil의 `PathAttributesFetcher`가 `Pair<Path, BasicFileAttributes>`를 받게 돼 있어서,
**`Path` 하나만 넘기면 fetcher가 붙지 않고 조용히 아무것도 안 나온다.**

목록 어댑터는 `FileItem`이 속성을 이미 들고 있어서 공짜인데, 뷰어는 `Path`만 갖고 있다.
속성 읽기는 파일 시스템 접근이므로 **메인 스레드에서 하면 안 된다.** 사진 쪽이 쓰는 방식을
그대로 따른다 — 코루틴을 띄워 `Dispatchers.IO`에서 읽고 돌아와서 요청한다.

- [ ] 동영상 바인딩을 만든다.

```kotlin
    private fun bindVideo(binding: MediaViewerVideoItemBinding, path: Path) {
        binding.root.setOnClickListener(listener)
        binding.playerView.isVisible = false
        binding.errorLayout.isVisible = false
        binding.thumbnailImage.isVisible = true
        binding.progress.fadeInUnsafe(true)
        lifecycleOwner.lifecycleScope.launch {
            val attributes = try {
                withContext(Dispatchers.IO) {
                    path.readAttributes(BasicFileAttributes::class.java)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                binding.progress.fadeOutUnsafe()
                return@launch
            }
            binding.thumbnailImage.load(path to attributes) {
                size(Size.ORIGINAL)
                fadeIn(binding.thumbnailImage.context.shortAnimTime)
                listener(
                    onSuccess = { _, _ -> binding.progress.fadeOutUnsafe() },
                    onError = { _, _ -> binding.progress.fadeOutUnsafe() }
                )
            }
        }
    }
```

썸네일이 실패해도 오류 화면을 띄우지 않는다 — 3단계에서 재생이 붙으면 썸네일은 어차피
잠깐 보이는 자리표시다. **재생 실패만 오류로 다룬다**(8단계).

### 2.5 문자열

- [ ] `values/strings.xml` · `values-ko/strings.xml` 둘 다에 넣는다. 8단계에서 쓴다.

```xml
<!-- values/strings.xml -->
    <string name="media_viewer_open_with">Open with another app</string>
```

```xml
<!-- values-ko/strings.xml -->
    <string name="media_viewer_open_with">다른 앱으로 열기</string>
```

### 2.6 2단계 검증

에뮬레이터(Pixel, API 36)에 `/sdcard/SwingTestData`를 올려 둔 상태로 확인한다
([05번 문서](05-test-data-pipeline.md)).

- [ ] 빌드 통과
- [ ] 사진과 동영상이 섞인 폴더에서 **사진을 눌러 들어간 뒤 좌우로 넘기면 동영상 페이지가 나온다.**
      첫 프레임 썸네일이 보이고, 앱 바 부제목의 전체 개수가 사진만 셌을 때보다 늘어나 있다
- [ ] **동영상을 직접 누르면** 외부 앱이 아니라 우리 뷰어가 뜬다
- [ ] 그 페이지에서 **화면을 탭하면 앱 바가 토글**된다
- [ ] **페이지를 넘기는 동안 동영상 썸네일이 옆 페이지처럼 흐려지고 작아진다** —
      §2.3의 `texture_view` 확인. 불투명하게 남아 있으면 `surface_type`을 확인한다
- [ ] 사진만 있는 폴더의 동작이 1단계와 같다
- [ ] 원격(SFTP/SMB) 폴더의 동영상을 누르면 **여전히 외부 앱**이 뜬다 (11번 수용 기준 19)
- [ ] 아카이브 안의 동영상도 마찬가지다

> **커밋 메시지 초안**
> `동영상이 미디어 뷰어 목록에 들어오고 전용 페이지에 썸네일이 뜨게`

---

## 3단계. 재생

### 3.1 플레이어 소유자 (신규)

`app/src/main/java/me/zhanghai/android/files/viewer/media/VideoPlayerHolder.kt`

플레이어 인스턴스 **하나**의 수명과 부착을 맡는다. 프래그먼트가 소유한다.
기획서 §5.2 — 페이지마다 만들지 않는 이유가 여기 있다.

```kotlin
/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java8.nio.file.Path
import me.zhanghai.android.files.file.fileProviderUri

/**
 * Owns the single ExoPlayer instance of the viewer, see spec 11 section 5.2.
 *
 * ViewPager2 keeps up to three pages alive (offscreenPageLimit = 1) and hardware decoders are a
 * handful per device, so one instance is attached to whichever page is current instead of one
 * instance per page.
 */
@OptIn(UnstableApi::class)
class VideoPlayerHolder(context: Context, listener: Player.Listener) {
    private val player: ExoPlayer =
        ExoPlayer.Builder(context.applicationContext)
            // Pauses when a phone call or another app takes the focus, see spec 11 section 5.5.
            .setAudioAttributes(AudioAttributes.DEFAULT, true)
            // Pauses when headphones are unplugged.
            .setHandleAudioBecomingNoisy(true)
            // Media3 defaults to 5s back and 15s forward. Spec 11 section 6.1 says 10s both ways.
            .setSeekBackIncrementMs(10_000)
            .setSeekForwardIncrementMs(10_000)
            .build()
            .apply {
                repeatMode = Player.REPEAT_MODE_OFF
                addListener(listener)
            }

    /** The path currently loaded, or null when nothing is. */
    var currentPath: Path? = null
        private set

    private var attachedView: PlayerView? = null

    val exoPlayer: ExoPlayer
        get() = player

    /**
     * Attaches the player to [view] and starts [path] at [positionMillis].
     *
     * Passing the path that is already attached does nothing at all, not even resume: see plan 12
     * 3.2.1. This is safe to call from page callbacks that fire more than once.
     */
    fun play(path: Path, view: PlayerView, positionMillis: Long) {
        if (currentPath == path && attachedView === view) {
            return
        }
        detach()
        attachedView = view
        view.player = player
        currentPath = path
        player.setMediaItem(MediaItem.fromUri(path.fileProviderUri), positionMillis)
        player.prepare()
        player.play()
    }

    /** Detaches from the current page without releasing the player. */
    fun detach() {
        player.stop()
        attachedView?.player = null
        attachedView = null
        currentPath = null
    }

    /** Position of what is playing now, or [C.TIME_UNSET] when there is nothing. */
    val currentPositionMillis: Long
        get() = if (currentPath != null) player.currentPosition else C.TIME_UNSET

    fun pause() {
        player.pause()
    }

    fun release() {
        attachedView?.player = null
        attachedView = null
        currentPath = null
        player.release()
    }
}
```

#### ⚠️ `fileProviderUri`인가 `Uri.fromFile()`인가

이 앱은 자체 `FileProvider`를 갖고 있어서 어떤 `Path`든 `content://` URI를 준다
([FileProvider.kt](../app/src/main/java/me/zhanghai/android/files/file/FileProvider.kt)).
ExoPlayer의 기본 `DataSource`는 `content://`와 `file://`을 모두 연다.

**`fileProviderUri`로 통일한다.** 로컬 파일은 `Uri.fromFile()`이 프로바이더를 거치지 않아
조금 빠르지만, 그러면 로컬과 SAF에서 경로가 갈리고 SAF 쪽만 따로 검증해야 한다.
§3의 재생 대상이 로컬과 SAF 둘뿐이라 한쪽으로 맞추는 값어치가 더 크다.
9단계의 실기기 측정에서 큰 파일 시크가 느리면 그때 로컬만 `file://`로 갈라도 된다.

### 3.2 프래그먼트에 붙이기

[MediaViewerFragment.kt](../app/src/main/java/me/zhanghai/android/files/viewer/image/ImageViewerFragment.kt)

- [ ] 클래스에 `@OptIn(UnstableApi::class)`를 붙이고 필드를 추가한다.

```kotlin
    private var playerHolder: VideoPlayerHolder? = null
```

- [ ] 어댑터를 만드는 곳(현재 104행) 바로 뒤에서 페이지 정착을 감시한다.
      **기존 `registerOnPageChangeCallback`에 `onPageScrollStateChanged`를 더한다.**

```kotlin
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    updateTitle()
                    // Do not start here. Fast flinging fires this for every page passed, and each
                    // one would briefly play sound. See spec 11 section 5.1.
                    stopPlaybackIfPageChanged()
                }

                override fun onPageScrollStateChanged(state: Int) {
                    if (state == ViewPager2.SCROLL_STATE_IDLE) {
                        startPlaybackIfVideoPage()
                    }
                }
            })
```

- [ ] 두 함수를 만든다.

```kotlin
    private fun stopPlaybackIfPageChanged() {
        val holder = playerHolder ?: return
        if (holder.currentPath != currentPath) {
            holder.detach()
        }
    }

    private fun startPlaybackIfVideoPage() {
        val path = currentPath
        if (!path.isPlayableVideo) {
            playerHolder?.detach()
            return
        }
        val playerView = currentVideoBinding?.playerView ?: return
        val holder = playerHolder ?: VideoPlayerHolder(requireContext(), playerListener)
            .also { playerHolder = it }
        // Already on this page: leave it alone, see 3.2.1.
        if (holder.currentPath == path) {
            return
        }
        playerView.isVisible = true
        holder.play(path, playerView, 0L)
    }
```

재생 위치 `0L`은 5단계에서 기억된 값으로 바뀐다. 3단계에서는 항상 처음부터다.

#### 3.2.1 ⚠️ 페이지가 안 바뀌어도 `SCROLL_STATE_IDLE`은 다시 온다

살짝 끌었다 놓기, 튕겼다가 같은 페이지에 도로 정착하기 — 이때도 `IDLE`이 온다.
`startPlaybackIfVideoPage()`가 그때마다 `play()`를 부르면 **사용자가 방금 일시정지한 영상이
스스로 다시 재생된다.** 그래서 위의 `holder.currentPath == path` 가드와 §3.1의
"같은 경로면 아무것도 하지 않는다"가 **둘 다** 필요하다. 한쪽만 있으면 다른 쪽에서 샌다.

기획서가 말하는 자동 재생은 "**새 동영상 페이지에 도착했을 때**"이지 "화면을 건드릴 때마다"가
아니다 (§5.1). 자동 재개의 유일한 예외는 화면으로 돌아왔을 때이고, 그건 5단계에서 다룬다.

#### 3.2.2 ⚠️ 첫 페이지에는 `IDLE`이 오지 않는다

`setCurrentItem(args.position, false)`는 스크롤을 만들지 않는다. 그래서 **뷰어에 들어온
직후의 페이지에 대해서는 `onPageScrollStateChanged()`가 한 번도 불리지 않는다.**
동영상을 눌러 뷰어에 들어갔는데 재생이 시작되지 않는다 — 수용 기준 1과 3이 바로 이 경로다.

- [ ] `binding.viewPager.apply { ... }` 블록 끝에 한 줄 더한다.

```kotlin
            // The initial page never scrolls, so SCROLL_STATE_IDLE never arrives for it. See 3.2.2.
            doOnPreDraw { startPlaybackIfVideoPage() }
```

`doOnPreDraw`인 이유는 이 시점에 페이지 뷰가 아직 붙지 않아 `currentVideoBinding`이
`null`이기 때문이다. `doOnPreDraw`는 이 파일이 이미 쓰고 있다(`delete()` 안의 blank screen 우회).
5단계의 `onResume`도 같은 이유로 같은 모양을 쓴다.

#### ⚠️ 현재 페이지의 뷰를 어떻게 꺼내는가

`ViewPager2`는 내부에 `RecyclerView`를 감추고 있고 `getChildAt(currentItem)`이 통하지 않는다.
페이지가 화면 밖에 있으면 뷰 자체가 없을 수도 있다. **`findViewHolderForAdapterPosition()`을
쓰고, `null`이면 조용히 포기한다** — `SCROLL_STATE_IDLE`은 다시 온다.

- [ ] 접근자를 **하나만** 만든다. `playerView` 하나가 아니라 바인딩 전체를 돌려준다 —
      3.3·7·8단계에서 썸네일과 오류 표시에도 쓴다.

```kotlin
    private val currentVideoBinding: MediaViewerVideoItemBinding?
        get() {
            val recyclerView = binding.viewPager.getChildAt(0) as? RecyclerView ?: return null
            val holder = recyclerView
                .findViewHolderForAdapterPosition(binding.viewPager.currentItem)
            return (holder as? MediaViewerAdapter.VideoViewHolder)?.binding
        }
```

`ViewPager2`의 0번 자식이 내부 `RecyclerView`라는 것은 구현 세부지만, `ViewPager2`가
공개 API를 주지 않아 널리 쓰이는 방법이다. **`as?`로 받아 `null`을 흘려보내므로**
구현이 바뀌어도 크래시하지 않고 "재생이 안 되는" 정도로 끝난다.

- [ ] 리스너를 만든다. 3단계에서는 로그만 찍고, 8단계에서 오류 화면을 붙인다.

```kotlin
    private val playerListener = object : Player.Listener {
        override fun onPlayerError(error: PlaybackException) {
            error.printStackTrace()
        }
    }
```

- [ ] 프래그먼트가 없어질 때 반드시 놓아준다. 5단계에서 더 다듬는다.

```kotlin
    override fun onDestroyView() {
        super.onDestroyView()

        playerHolder?.release()
        playerHolder = null
    }
```

### 3.3 썸네일과 재생 화면 겹치기

재생이 시작되기 전까지는 썸네일이 보이고, 첫 프레임이 그려지면 **썸네일이 걷힌다.**
검게 번쩍이지 않게 하는 것이 목적이다 (기획서 §10).

**썸네일이 `PlayerView` 위에 있다는 것이 전제다** — §2.3.1. 순서가 반대면 아래 코드는
보이지도 않는 뷰를 페이드아웃하는 헛일이 되고, 화면은 검게 번쩍인다.

- [ ] `playerListener`에 첫 프레임 신호를 받는다.

```kotlin
        override fun onRenderedFirstFrame() {
            currentVideoBinding?.thumbnailImage?.fadeOutUnsafe()
        }
```

`onRenderedFirstFrame()`은 `Player.Listener`의 기본 구현이 있는 콜백이다 (1.11.0에서 확인).
`currentVideoBinding`은 §3.2에서 이미 만들었다.

- [ ] 페이지를 떠나면 썸네일을 되돌린다 (기획서 §5.2 — 마지막 프레임이 아니라 첫 프레임으로).

```kotlin
    private fun stopPlaybackIfPageChanged() {
        val holder = playerHolder ?: return
        if (holder.currentPath != currentPath) {
            currentVideoBinding?.let {
                it.playerView.isVisible = false
                it.thumbnailImage.fadeInUnsafe(true)
            }
            holder.detach()
        }
    }
```

⚠️ 여기서 `currentVideoBinding`은 **이미 새 페이지**를 가리킨다는 점에 주의한다.
떠나는 페이지를 되돌리려면 `holder.currentPath`로 그 페이지의 위치를 찾아야 한다.
간단하게 가려면 **되돌리기를 어댑터의 `onViewRecycled`와 `bindVideo`에 맡긴다** —
`bindVideo`가 이미 `playerView.isVisible = false`, `thumbnailImage.isVisible = true`로
시작하므로, 페이지가 재사용될 때 저절로 제자리로 온다. **이 방식을 쓴다.**
`stopPlaybackIfPageChanged()`는 `holder.detach()`만 부른다.

### 3.4 3단계 검증

- [ ] 빌드 통과
- [ ] 동영상 페이지로 넘어가 **손가락을 떼면 재생이 시작된다** (11번 수용 기준 3)
- [ ] **파일 목록에서 동영상을 눌러 들어가면 바로 재생된다** — §3.2.2 (수용 기준 1·3)
- [ ] **빠르게 여러 페이지를 훑을 때 스쳐 지나가는 동영상의 소리가 나지 않는다** (수용 기준 4)
- [ ] 다른 페이지로 넘어가면 소리가 멈춘다
- [ ] 되돌아오면 처음부터 다시 재생된다 (위치 기억은 5단계)
- [ ] **일시정지한 뒤 화면을 살짝 끌었다 놓아도 다시 재생되지 않는다** — §3.2.1
      (컨트롤은 4단계에 생기므로, 3단계에서는 `⋮`가 아니라 `adb shell input` 이나
      로그로 확인해도 된다. 4단계 검증에서 다시 본다)
- [ ] 재생이 시작될 때 화면이 검게 번쩍이지 않는다. 준비되는 동안 **썸네일이 보인다** — §2.3.1
- [ ] **여기서 [11번 §11의 1~3번을 확인한다** — 아이폰 HEVC `.mov`, 갤럭시 HDR, 세로 영상.
      실기기(SM-F971N)로 한다. 에뮬레이터는 코덱 지원이 다르다.
      **이 셋이 안 되면 뒤 단계를 진행하기 전에 멈추고 원인을 본다.**

> **커밋 메시지 초안**
> `미디어 뷰어에서 동영상을 재생 — 플레이어 인스턴스 하나를 현재 페이지에 붙인다`

---

## 4단계. 컨트롤 ↔ 앱 바 묶기

### 4.1 컨트롤은 페이지가 아니라 **프래그먼트**에 둔다

기획서 §6.2는 "앱 바와 컨트롤이 같이 나타나고 같이 사라진다"고 정했다.
그러려면 컨트롤이 앱 바와 **같은 층**에 있어야 한다. 페이지 안에 넣으면 페이지가
재사용될 때마다 상태를 다시 맞춰야 하고, 페이지 전환 애니메이션에 컨트롤까지 딸려 간다.

그래서 `PlayerView`는 §2.3에서 이미 `use_controller="false"`로 두었고 — **화면을 그리는
표면일 뿐이다** — 컨트롤은 `PlayerControlView`를 프래그먼트 레이아웃에 따로 둔다.

- [ ] `res/layout/media_viewer_fragment.xml`에 앱 바와 나란히 넣는다.

```xml
    <androidx.media3.ui.PlayerControlView
        android:id="@+id/playerControlView"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:visibility="gone"
        app:show_timeout="0"
        app:show_previous_button="false"
        app:show_next_button="false"
        app:show_shuffle_button="false"
        app:show_subtitle_button="false"
        app:show_vr_button="false" />
```

`show_timeout="0"`이 **자동 숨김을 끈다.** 기획서 §6.2가 요구하는 것이다.
`show_previous/next_button="false"`인 이유는 재생 목록이 하나뿐이라 이전/다음이
할 일이 없기 때문이다. 좌우 이동은 `ViewPager2`가 한다.

- [ ] 내비게이션 바에 가리지 않게 인셋을 적용한다. 앱 바가 쓰는 것과 같은 확장 함수다.

```kotlin
        binding.playerControlView.applySystemWindowInsetsToPadding(
            left = true, bottom = true, right = true
        )
```

### 4.2 플레이어 붙이고 스크러빙 켜기

- [ ] `VideoPlayerHolder`를 만들 때 컨트롤에도 같은 플레이어를 준다.

```kotlin
        val holder = playerHolder ?: VideoPlayerHolder(requireContext(), playerListener)
            .also {
                playerHolder = it
                binding.playerControlView.player = it.exoPlayer
                binding.playerControlView.setTimeBarScrubbingEnabled(true)
            }
```

`setTimeBarScrubbingEnabled(true)`는 슬라이더를 끄는 동안 프레임이 따라오게 한다.
끄면 손을 뗄 때까지 화면이 멈춰 있어서 원하는 지점을 찾기 어렵다.

### 4.3 앱 바와 같이 여닫기

- [ ] `SystemUiHelper` 콜백(현재 92행)에 컨트롤을 더한다.

```kotlin
        systemUiHelper = SystemUiHelper(
            activity, SystemUiHelper.LEVEL_IMMERSIVE, SystemUiHelper.FLAG_IMMERSIVE_STICKY
        ) { visible: Boolean ->
            binding.appBarLayout.animate()
                .alpha(if (visible) 1f else 0f)
                .translationY(if (visible) 0f else -binding.appBarLayout.bottom.toFloat())
                .setDuration(mediumAnimTime.toLong())
                .setInterpolator(FastOutSlowInInterpolator())
                .start()
            // The controls ride with the app bar, see spec 11 section 6.2.
            updatePlayerControlVisibility(visible)
        }
```

- [ ] 표시 규칙을 한 곳에 모은다. **사진 페이지에서는 무조건 숨긴다.**

```kotlin
    private fun updatePlayerControlVisibility(systemUiVisible: Boolean) {
        val shouldShow = systemUiVisible && currentPath.isPlayableVideo
        binding.playerControlView.apply {
            if (shouldShow) {
                isVisible = true
                show()
            } else {
                hide()
                isVisible = false
            }
        }
    }
```

- [ ] 페이지가 바뀔 때도 다시 판단한다. `onPageSelected`에 한 줄 더한다.

```kotlin
                override fun onPageSelected(position: Int) {
                    updateTitle()
                    stopPlaybackIfPageChanged()
                    updatePlayerControlVisibility(systemUiHelper.isShowing)
                }
```

`SystemUiHelper`에 `isShowing`에 해당하는 게 없다면 프래그먼트가 마지막 값을 필드에
들고 있게 한다 — 콜백에서 받은 `visible`을 그대로 저장하면 된다.

```kotlin
    private var isSystemUiVisible = true
```

### 4.4 4단계 검증

- [ ] 빌드 통과
- [ ] 동영상 페이지에서 화면을 탭하면 **앱 바와 슬라이더가 같이 나타나고, 다시 탭하면 같이 사라진다**
      (11번 수용 기준 6)
- [ ] **슬라이더를 드래그하면 그 지점으로 이동**하고 현재 시간/전체 길이가 맞다 (수용 기준 5)
- [ ] 드래그하는 동안 화면이 따라 움직인다
- [ ] 재생/일시정지 버튼이 동작한다
- [ ] **일시정지한 뒤 화면을 살짝 끌었다 놓아도 다시 재생되지 않는다** — §3.2.1
- [ ] 10초 뒤로/앞으로 버튼이 **각각 10초씩** 움직인다 — §3.1 (Media3 기본값은 5초/15초다)
- [ ] **사진 페이지로 넘어가면 슬라이더가 사라진다.** 앱 바만 오르내린다
- [ ] 슬라이더가 내비게이션 바에 가리지 않는다
- [ ] 가로로 눕혀도 슬라이더가 화면 안에 있다

> **커밋 메시지 초안**
> `재생 컨트롤을 앱 바와 함께 여닫히게`

---

## 5단계. 수명 · 재생 위치 · 오디오 · 화면

### 5.1 세션 상태는 `ViewModel`에 둔다

#### ⚠️ 프래그먼트 필드에 두면 화면을 돌릴 때 사라진다

[AndroidManifest.xml:339](../app/src/main/AndroidManifest.xml#L339)의 이 액티비티에는
`android:configChanges`가 **없다.** 화면을 돌리면 액티비티와 프래그먼트가 새로 만들어지고,
평범한 필드에 담긴 재생 위치와 배속은 초기화된다. 기획서 §5.3("회전해도 위치 유지")과
§6.3("뷰어를 닫을 때까지 배속 유지")이 그대로 깨지고, 아래 5.5 검증의
"회전해도 위치가 유지된다"는 통과할 수 없다.

`ViewModel`은 회전을 넘겨 살아남고 **뷰어를 닫으면 함께 사라진다.** 기획서가 요구하는
"뷰어 세션 동안만 기억한다"(D8)와 수명이 정확히 같다. `lifecycle-viewmodel-ktx`는
이미 의존성이다([app/build.gradle:142](../app/build.gradle#L142)).

- [ ] `app/src/main/java/me/zhanghai/android/files/viewer/media/MediaViewerViewModel.kt`

```kotlin
/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import androidx.lifecycle.ViewModel
import java8.nio.file.Path

/**
 * State that outlives a configuration change but dies with the viewer, see spec 11 sections 5.4
 * and 6.3. Nothing here is persisted, see decision D8.
 */
class MediaViewerViewModel : ViewModel() {
    /** Playback position per video, in milliseconds. */
    val playbackPositions = mutableMapOf<Path, Long>()

    /** Shared by every video in this session, back to 1x when the viewer is closed. */
    var playbackSpeed = 1f

    /** The file half of the details sheet, see plan 12 7.3. */
    val videoFileDetails = mutableMapOf<Path, VideoFileDetails>()
}
```

- [ ] 프래그먼트에서 잡는다. 이 저장소가 쓰는 확장 함수다
      ([FileListFragment.kt:169](../app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt#L169) 참고 —
      `androidx.fragment.app.viewModels`가 아니라
      [util/FragmentViewModelLazy.kt](../app/src/main/java/me/zhanghai/android/files/util/FragmentViewModelLazy.kt)의 것이다).

```kotlin
    private val viewModel by viewModels { { MediaViewerViewModel() } }
```

`playbackSpeed`는 6단계, `videoFileDetails`는 7단계에서 쓴다. 세 개를 한 파일에 미리 적어 두는
이유는, 셋 다 "뷰어 세션 동안만 사는 상태"라는 같은 성질이고 나중에 하나씩 옮기면
같은 고민을 세 번 하게 되기 때문이다.

### 5.2 재생 위치 기억 (기획서 §5.4)

- [ ] 페이지를 떠날 때 적어 둔다.

```kotlin
    private fun stopPlaybackIfPageChanged() {
        val holder = playerHolder ?: return
        val playingPath = holder.currentPath ?: return
        if (playingPath != currentPath) {
            rememberPosition(holder, playingPath)
            holder.detach()
        }
    }

    private fun rememberPosition(holder: VideoPlayerHolder, path: Path) {
        val position = holder.currentPositionMillis
        if (position != C.TIME_UNSET && position > 0) {
            viewModel.playbackPositions[path] = position
        }
    }
```

- [ ] 시작할 때 꺼내 쓴다. 3단계에서 `0L`이던 자리다.

```kotlin
        holder.play(path, playerView, viewModel.playbackPositions[path] ?: 0L)
```

- [ ] 끝까지 재생된 동영상은 기억을 지운다. 안 지우면 다시 들어갔을 때 끝에서 시작한다.

```kotlin
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_ENDED) {
                playerHolder?.currentPath?.let { viewModel.playbackPositions.remove(it) }
            }
        }
```

`REPEAT_MODE_OFF`는 §3.1에서 이미 걸어 뒀으므로 끝나면 마지막 프레임에서 멈춘다
(기획서 §5.5, 수용 기준 15).

### 5.3 백그라운드 (기획서 §5.3)

- [ ] `onPause`에서 위치를 적고 놓아준다.

```kotlin
    override fun onPause() {
        super.onPause()

        val holder = playerHolder ?: return
        holder.currentPath?.let { rememberPosition(holder, it) }
        holder.release()
        playerHolder = null
        binding.playerControlView.player = null
    }
```

- [ ] `onResume`에서 다시 만든다. `SCROLL_STATE_IDLE`이 다시 오지 않으므로 **직접 부른다.**

```kotlin
    override fun onResume() {
        super.onResume()

        startPlaybackIfVideoPage()
    }
```

`startPlaybackIfVideoPage()`가 `viewModel.playbackPositions`에서 위치를 꺼내므로
이어서 재생된다. `onPause`에서 플레이어를 놓았으므로 §3.2.1의 "같은 경로면 아무것도 안 한다"
가드에 걸리지 않는다 — `detach`/`release`가 `currentPath`를 `null`로 만들어 두기 때문이다.

#### ⚠️ `onResume` 시점에는 페이지 뷰가 아직 없을 수 있다

`currentVideoBinding`이 `null`이면 `startPlaybackIfVideoPage()`가 조용히 돌아간다.
그러면 **돌아왔는데 재생이 안 되는** 상태가 된다. 뷰가 붙은 뒤 한 번 더 시도한다.
§3.2.2에서 첫 페이지에 쓴 것과 같은 방법이다.

```kotlin
    override fun onResume() {
        super.onResume()

        binding.viewPager.doOnPreDraw { startPlaybackIfVideoPage() }
    }
```

`doOnPreDraw`는 이 파일이 이미 쓰고 있다(`delete()` 안의 blank screen 우회).

- [ ] 3단계에서 넣은 `onDestroyView()`의 `release()`는 그대로 둔다. `onPause`에서 이미
      놓았으면 `playerHolder`가 `null`이라 두 번 부르지 않는다.

#### ⚠️ 포커스를 잃었다 되찾을 때 자동 재개하지 않는다

기획서 §5.3의 마지막 줄이다. `AudioAttributes`와 함께 `handleAudioFocus = true`를 주면
Media3가 포커스를 잃을 때 멈추고 **되찾으면 다시 재생한다.** 기획서는 "화면으로 돌아왔는가"만
자동 재개의 기준으로 삼는다.

이걸 맞추려면 포커스 복귀 재개를 막아야 하는데, Media3는 그 지점을 직접 열어 주지 않는다.
**9단계 검증에서 실제 동작을 확인하고, 기획서와 다르면 그때 기획서 §5.3을 Media3 동작에
맞춰 개정한다.** 여기서 억지로 우회 코드를 넣지 않는다 — 자동 재개는 대부분 사용자가
원하는 동작이기도 하다.

### 5.4 화면 꺼짐 방지 (기획서 §5.5)

- [ ] 재생 중일 때만 켠다.

```kotlin
        override fun onIsPlayingChanged(isPlaying: Boolean) {
            binding.root.keepScreenOn = isPlaying
        }
```

`View.keepScreenOn`은 뷰가 창에서 떨어지면 저절로 풀린다. 창 플래그를 직접 만지는 것보다
안전하다.

### 5.5 5단계 검증

- [ ] 빌드 통과
- [ ] 동영상 중간까지 보고 **옆 사진으로 갔다 돌아오면 그 위치에서 이어진다** (수용 기준 12)
- [ ] **홈으로 나갔다 돌아오면 그 위치에서 이어진다** (수용 기준 11)
- [ ] **화면을 회전해도 위치가 유지된다** — §5.1의 `ViewModel`이 있어야 통과한다.
      프래그먼트 필드에 두면 여기서 처음부터 다시 시작한다
- [ ] 재생 중 **전화가 오면 멈춘다** (수용 기준 13)
- [ ] 이어폰을 뽑으면 멈춘다
- [ ] **재생 중에는 화면이 꺼지지 않고**, 일시정지하면 다시 꺼진다 (수용 기준 14)
- [ ] 끝까지 재생되면 **마지막 프레임에서 멈춘다.** 반복하지 않고 페이지도 안 넘어간다 (수용 기준 15)
- [ ] 끝까지 본 동영상을 다시 열면 **처음부터** 시작한다
- [ ] 뷰어를 닫았다 다시 들어가면 처음부터 시작한다 (세션 안에서만 기억, D8)
- [ ] 홈으로 나간 뒤 `adb shell dumpsys media.player`나 로그로 **디코더가 남아 있지 않은지** 본다

> **커밋 메시지 초안**
> `동영상 재생 위치를 기억하고, 백그라운드에서 플레이어 자원을 놓게`

---

## 6단계. 속도 조절

### 6.1 값과 상태

- [ ] 값은 **§5.1의 `MediaViewerViewModel.playbackSpeed`에 이미 있다.** 프래그먼트 필드로
      두면 화면을 돌릴 때 1×로 돌아가 기획서 §6.3("뷰어를 닫을 때까지 유지")이 깨진다.
      프래그먼트에는 값 목록만 둔다.

```kotlin
    companion object {
        // Spec 11 section 6.3. 0.25 is there to slow fast motion down, e.g. a golf swing.
        private val PLAYBACK_SPEEDS = floatArrayOf(0.25f, 0.5f, 0.75f, 1f, 1.5f, 2f)
    }
```

### 6.2 메뉴

- [ ] `res/menu/media_viewer.xml`에 하위 메뉴를 넣는다. 삭제·공유 앞이다.

```xml
    <item
        android:id="@+id/action_playback_speed"
        android:orderInCategory="50"
        android:title="@string/media_viewer_playback_speed"
        app:showAsAction="never">
        <menu>
            <group android:checkableBehavior="single">
                <item android:id="@+id/action_speed_0_25" android:title="@string/media_viewer_speed_0_25" />
                <item android:id="@+id/action_speed_0_5" android:title="@string/media_viewer_speed_0_5" />
                <item android:id="@+id/action_speed_0_75" android:title="@string/media_viewer_speed_0_75" />
                <item android:id="@+id/action_speed_1" android:title="@string/media_viewer_speed_1" />
                <item android:id="@+id/action_speed_1_5" android:title="@string/media_viewer_speed_1_5" />
                <item android:id="@+id/action_speed_2" android:title="@string/media_viewer_speed_2" />
            </group>
        </menu>
    </item>
```

`checkableBehavior="single"`이 라디오 표시를 만든다 (기획서 §6.3).

- [ ] 문자열. **배속 값은 숫자라 번역이 같지만, 두 파일 다 넣는다** (전역 제약).

```xml
<!-- values/strings.xml -->
    <string name="media_viewer_playback_speed">Playback speed</string>
    <string name="media_viewer_speed_0_25">0.25×</string>
    <string name="media_viewer_speed_0_5">0.5×</string>
    <string name="media_viewer_speed_0_75">0.75×</string>
    <string name="media_viewer_speed_1">1×</string>
    <string name="media_viewer_speed_1_5">1.5×</string>
    <string name="media_viewer_speed_2">2×</string>
    <string name="media_viewer_speed_subtitle_format">%1$s</string>
```

```xml
<!-- values-ko/strings.xml -->
    <string name="media_viewer_playback_speed">재생 속도</string>
```

배속 값 여섯 개와 `media_viewer_speed_subtitle_format`은 `values-ko`에 **넣지 않는다** —
값이 같아서 기본값으로 떨어지면 된다. 번역이 다른 것만 `values-ko`에 둔다.

### 6.3 메뉴 동작

- [ ] 동영상 페이지일 때만 보이게 한다.

```kotlin
    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        val isVideo = currentPath.isPlayableVideo
        menu.findItem(R.id.action_playback_speed).isVisible = isVideo
        if (isVideo) {
            val index = PLAYBACK_SPEEDS.indexOf(viewModel.playbackSpeed)
            val itemIds = intArrayOf(
                R.id.action_speed_0_25, R.id.action_speed_0_5, R.id.action_speed_0_75,
                R.id.action_speed_1, R.id.action_speed_1_5, R.id.action_speed_2
            )
            itemIds.forEachIndexed { i, id ->
                menu.findItem(id).isChecked = i == index
            }
        }
    }
```

- [ ] 페이지가 바뀌면 메뉴를 다시 만들게 한다. `onPageSelected`에 한 줄.

```kotlin
                    requireActivity().invalidateOptionsMenu()
```

- [ ] 고르면 즉시 반영한다.

```kotlin
            R.id.action_speed_0_25 -> { setPlaybackSpeed(0.25f); true }
            R.id.action_speed_0_5 -> { setPlaybackSpeed(0.5f); true }
            R.id.action_speed_0_75 -> { setPlaybackSpeed(0.75f); true }
            R.id.action_speed_1 -> { setPlaybackSpeed(1f); true }
            R.id.action_speed_1_5 -> { setPlaybackSpeed(1.5f); true }
            R.id.action_speed_2 -> { setPlaybackSpeed(2f); true }
```

```kotlin
    private fun setPlaybackSpeed(speed: Float) {
        viewModel.playbackSpeed = speed
        playerHolder?.exoPlayer?.setPlaybackSpeed(speed)
        updateTitle()
    }
```

- [ ] 새 동영상을 시작할 때도 배속을 물려준다 (기획서 §6.3 — 넘어가도 유지된다).
      `startPlaybackIfVideoPage()`의 `holder.play(...)` 바로 뒤다.

```kotlin
        holder.exoPlayer.setPlaybackSpeed(viewModel.playbackSpeed)
```

### 6.4 앱 바 부제목에 배속 표시

- [ ] `updateTitle()`을 고친다. 기존 `n/m` 표시를 유지하면서 배속을 덧붙인다.
      부제목 서식 키가 `image_viewer_subtitle_format` 그대로인 것은 오타가 아니다 — §1.5.

```kotlin
    private fun updateTitle() {
        val path = currentPath
        requireActivity().title = path.fileName.toString()
        val size = paths.size
        val countText = if (size > 1) {
            getString(
                R.string.image_viewer_subtitle_format, binding.viewPager.currentItem + 1, size
            )
        } else {
            null
        }
        // Show the speed only when it is not 1x, see spec 11 section 6.3.
        val speedText = if (path.isPlayableVideo && viewModel.playbackSpeed != 1f) {
            formatPlaybackSpeed(viewModel.playbackSpeed)
        } else {
            null
        }
        binding.toolbar.subtitle = listOfNotNull(countText, speedText).joinToString("  ")
            .ifEmpty { null }
    }

    private fun formatPlaybackSpeed(speed: Float): String =
        getString(
            when (speed) {
                0.25f -> R.string.media_viewer_speed_0_25
                0.5f -> R.string.media_viewer_speed_0_5
                0.75f -> R.string.media_viewer_speed_0_75
                1.5f -> R.string.media_viewer_speed_1_5
                2f -> R.string.media_viewer_speed_2
                else -> R.string.media_viewer_speed_1
            }
        )
```

### 6.5 6단계 검증

- [ ] 빌드 통과
- [ ] `⋮` → 재생 속도에 **여섯 개 값**이 나오고 현재 값에 표시가 있다 (수용 기준 7)
- [ ] 고르면 **즉시** 속도가 바뀌고 재생이 끊기지 않는다
- [ ] 1×가 아니면 **앱 바 부제목에 배속이 보인다** (수용 기준 8)
- [ ] 다음 동영상으로 넘어가도 **배속이 유지**된다
- [ ] **사진 페이지에서는 재생 속도 메뉴가 안 보인다**
- [ ] **화면을 회전해도 배속이 유지된다** (§5.1의 `ViewModel`)
- [ ] 뷰어를 닫았다 열면 1×다
- [ ] 0.25×에서 소리가 심하게 깨지지 않는다 (Media3가 음높이를 보정한다)

> **커밋 메시지 초안**
> `재생 속도 조절 추가 (0.25×~2×)`

---

## 7단계. 세부 정보 오버레이

### 7.1 값 묶음 (신규)

`app/src/main/java/me/zhanghai/android/files/viewer/media/VideoDetails.kt`

```kotlin
/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

/**
 * What the details sheet shows, see spec 11 section 7.1.
 *
 * Every field is nullable: the format ones are unknown until playback is ready, and the metadata
 * ones may simply not be in the file.
 *
 * Not Parcelable on purpose: this is handed to the sheet through a listener, not through
 * arguments, because it changes while the sheet is open. See plan 12 7.3.1.
 */
class VideoDetails(
    val fileName: String,
    val path: String,
    val sizeBytes: Long,
    val createdTimeMillis: Long?,
    val width: Int?,
    val height: Int?,
    val durationMillis: Long?,
    val frameRate: Float?,
    val codec: String?,
    val bitRate: Int?,
    val rotationDegrees: Int?,
    val location: Pair<Float, Float>?
)
```

### 7.2 값을 어디서 읽는가

기획서 §7.2의 표를 그대로 코드로 옮긴다.

| 값 | 코드 |
|---|---|
| 해상도 | `player.videoFormat?.width` / `.height` |
| 코덱 | `player.videoFormat?.sampleMimeType` |
| 프레임 레이트 | `player.videoFormat?.frameRate` |
| 회전 | `player.videoFormat?.rotationDegrees` |
| 비트레이트 | `player.videoFormat?.averageBitrate`, 없으면 `.peakBitrate` |
| 길이 | `player.duration` |
| 촬영 시각 | `MediaCreatedTime.read(path, attributes, mimeType)` |
| 위치 | `MediaMetadataRetriever` — 기존 `retriever.location` |
| 파일 크기 · 경로 | `attributes.size()`, `path.toString()` |

`Format`의 필드 이름은 1.11.0 바이트코드로 확인했다 —
`width`, `height`, `frameRate`, `rotationDegrees`, `averageBitrate`, `peakBitrate`,
`sampleMimeType`, `codecs` 모두 `public final`이다.

#### ⚠️ `Format`의 "값 없음"은 `null`이 아니라 상수다

`Format.width`는 `Int`이고, 알 수 없을 때 `null`이 아니라 **`Format.NO_VALUE`(= `-1`)** 다.
`frameRate`도 `Float`이고 `Format.NO_VALUE.toFloat()`이 들어온다.
그냥 쓰면 화면에 `-1 × -1`, `-1.0 fps`가 나온다.

```kotlin
private fun Int.orNullIfNoValue(): Int? = if (this == Format.NO_VALUE) null else this
private fun Float.orNullIfNoValue(): Float? =
    if (this == Format.NO_VALUE.toFloat() || isNaN()) null else this
```

#### ⚠️ 코덱 이름은 MIME 타입 그대로 보여주지 않는다

`sampleMimeType`은 `video/hevc`, `video/avc` 같은 값이다. 기획서 §7.1의 예시는
`HEVC (H.265)`이므로 아는 값만 사람이 읽는 이름으로 바꾸고, 모르면 원래 값을 그대로 쓴다.

```kotlin
private fun codecDisplayName(sampleMimeType: String?): String? =
    when (sampleMimeType) {
        null -> null
        MimeTypes.VIDEO_H265 -> "HEVC (H.265)"
        MimeTypes.VIDEO_H264 -> "H.264 (AVC)"
        MimeTypes.VIDEO_AV1 -> "AV1"
        MimeTypes.VIDEO_VP9 -> "VP9"
        MimeTypes.VIDEO_MPEG -> "MPEG"
        else -> sampleMimeType
    }
```

`androidx.media3.common.MimeTypes`의 상수다.

- [ ] 값을 모으는 코드를 `VideoDetails.kt`에 같이 둔다. **파일을 읽는 쪽과 플레이어에서
      읽는 쪽을 나눈다** — 앞은 `Dispatchers.IO`에서, 뒤는 메인 스레드에서만 부를 수 있다.

```kotlin
/** The half of [VideoDetails] that needs the file system. */
class VideoFileDetails(
    val sizeBytes: Long,
    val createdTimeMillis: Long?,
    val location: Pair<Float, Float>?
)

/** Reads the file half. Call from [Dispatchers.IO]. */
@WorkerThread
fun readVideoFileDetails(path: Path): VideoFileDetails {
    val attributes = path.readAttributes(BasicFileAttributes::class.java)
    val mimeType = MimeType.guessFromPath(path.toString())
    // Spec 11 section 7.2: the same rule the date tiles use, so the two never disagree.
    val createdTimeMillis = MediaCreatedTime.read(path, attributes, mimeType)
    val location = try {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(path)
            retriever.location
        }
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
    return VideoFileDetails(attributes.size(), createdTimeMillis, location)
}

/** Combines the file half with what the player already knows. */
fun buildVideoDetails(
    path: Path,
    fileDetails: VideoFileDetails?,
    format: Format?,
    durationMillis: Long?
): VideoDetails =
    VideoDetails(
        fileName = path.fileName.toString(),
        path = path.toString(),
        sizeBytes = fileDetails?.sizeBytes ?: -1L,
        createdTimeMillis = fileDetails?.createdTimeMillis,
        width = format?.width?.orNullIfNoValue(),
        height = format?.height?.orNullIfNoValue(),
        durationMillis = durationMillis?.takeIf { it != C.TIME_UNSET && it > 0 },
        frameRate = format?.frameRate?.orNullIfNoValue(),
        codec = codecDisplayName(format?.sampleMimeType),
        bitRate = format?.averageBitrate?.orNullIfNoValue()
            ?: format?.peakBitrate?.orNullIfNoValue(),
        rotationDegrees = format?.rotationDegrees?.orNullIfNoValue()?.takeIf { it != 0 },
        location = fileDetails?.location
    )
```

`use`는 [compat/MediaMetadataRetrieverCompat.kt](../app/src/main/java/me/zhanghai/android/files/compat/MediaMetadataRetrieverCompat.kt),
`setDataSource(path)`는 [util/MediaMetadataRetrieverPathExtensions.kt](../app/src/main/java/me/zhanghai/android/files/util/MediaMetadataRetrieverPathExtensions.kt),
`retriever.location`은 [fileproperties/MediaMetadataRetrieverExtensions.kt](../app/src/main/java/me/zhanghai/android/files/fileproperties/MediaMetadataRetrieverExtensions.kt)에
이미 있다. **새로 만들지 않는다.**

`sizeBytes`가 `-1`이면 화면에서 `—`로 나가게 한다 (§7.3).

### 7.3 시트

- [ ] 레이아웃 `res/layout/video_details_dialog.xml` — `ScrollView` 안의 세로 `LinearLayout`
      하나면 된다. 항목은 코드로 붙인다.

```xml
<?xml version="1.0" encoding="utf-8"?>

<!--
  ~ Copyright (c) 2026 PhotoExplorer
  ~ All Rights Reserved.
  -->

<ScrollView
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content">

    <LinearLayout
        android:id="@+id/itemLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:orientation="vertical"
        android:paddingTop="16dp"
        android:paddingBottom="16dp" />
</ScrollView>
```

#### 7.3.1 시트는 값을 만들지 않는다. 받아서 그리기만 한다

값의 출처가 둘이다 — 플레이어(§7.2 위쪽 절반)와 파일 시스템(아래쪽 절반). **둘 다 들고 있는
것은 프래그먼트**이고, 플레이어 쪽 값은 **재생이 준비된 뒤에야 생긴다.** 그래서 역할을 이렇게
가른다.

| 누가 | 무엇을 |
|---|---|
| `MediaViewerFragment` | 파일 절반을 `Dispatchers.IO`에서 읽어 `ViewModel`에 캐시하고, 플레이어 절반과 합쳐 `VideoDetails`를 만든다. 값이 새로 생기면 시트를 다시 그리게 한다 |
| `VideoDetailsDialogFragment` | 받은 `VideoDetails`를 그린다. 그것만 한다 |

⚠️ **`VideoDetails`를 인자로 넘기지 않는다.** 두 가지 이유가 있다.
`ParcelableArgs`로 넘기려면 `@Parcelize`가 필요한데 `Pair<Float, Float>` 필드가 그대로는
Parcelable이 아니고, 무엇보다 **값이 나중에 바뀐다** — 한 번 넣고 끝나는 인자와 맞지 않는다.
인자는 `Path` 하나면 된다.

- [ ] `VideoDetailsDialogFragment`를 만든다. `BottomSheetDialogFragment`를 상속한다
      (`com.google.android.material:material:1.13.0`이 이미 있다. 이 저장소에서
      `BottomSheetDialogFragment`를 쓰는 것은 이 클래스가 처음이다).
      항목은 **`file_properties_tab_item.xml`을 그대로 인플레이트해서 쌓는다** —
      [FilePropertiesTabFragment.addItemView()](../app/src/main/java/me/zhanghai/android/files/fileproperties/FilePropertiesTabFragment.kt#L90)가
      쓰는 것과 같은 레이아웃(`FilePropertiesTabItemBinding`)이라 속성 대화상자와 생김새가
      어긋나지 않는다. 정확한 뷰 아이디는 그 함수를 열어 확인하고 쓴다.

```kotlin
/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

class VideoDetailsDialogFragment : BottomSheetDialogFragment() {
    private val args by args<Args>()

    private val listener: Listener
        get() = requireParentFragment() as Listener

    private lateinit var binding: VideoDetailsDialogBinding

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View =
        VideoDetailsDialogBinding.inflate(inflater, container, false)
            .also { binding = it }
            .root

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        bind(listener.getVideoDetails(args.path))
    }

    /** Called by the fragment when playback becomes ready or the file half arrives. */
    fun updateDetails() {
        if (view != null) {
            bind(listener.getVideoDetails(args.path))
        }
    }

    private fun bind(details: VideoDetails) {
        binding.itemLayout.removeAllViews()
        addItem(R.string.media_viewer_details_file_name, details.fileName)
        addItem(R.string.media_viewer_details_created_time, details.createdTimeMillis?.let {
            Instant.ofEpochMilli(it).formatLong()
        })
        addItem(
            R.string.media_viewer_details_dimensions,
            if (details.width != null && details.height != null) {
                getString(
                    R.string.file_properties_media_dimensions_format,
                    details.width, details.height
                )
            } else {
                null
            }
        )
        addItem(R.string.media_viewer_details_duration, details.durationMillis?.let {
            Duration.ofMillis(it).format()
        })
        addItem(R.string.media_viewer_details_frame_rate, details.frameRate?.let {
            getString(R.string.media_viewer_details_frame_rate_format, formatFrameRate(it))
        })
        addItem(R.string.media_viewer_details_codec, details.codec)
        addItem(R.string.media_viewer_details_bit_rate, details.bitRate?.let {
            getString(R.string.file_properties_media_bit_rate_format, it / 1000)
        })
        // Only when it is not 0, see spec 11 section 7.1.
        details.rotationDegrees?.let {
            addItem(
                R.string.media_viewer_details_rotation,
                getString(R.string.media_viewer_details_rotation_format, it)
            )
        }
        addItem(
            R.string.media_viewer_details_size,
            details.sizeBytes.takeIf { it >= 0 }?.let { FileSize(it).formatHumanReadable(requireContext()) }
        )
        // Only when the file has one, see spec 11 section 7.1.
        details.location?.let {
            addItem(
                R.string.media_viewer_details_location,
                getString(
                    R.string.file_properties_media_coordinates_format, it.first, it.second
                )
            )
        }
        addItem(R.string.media_viewer_details_path, details.path)
    }

    /** A null value keeps the row and shows an em dash, see spec 11 section 7.1. */
    private fun addItem(@StringRes titleRes: Int, value: String?) {
        // Inflate file_properties_tab_item.xml into binding.itemLayout and set the two views.
        // See FilePropertiesTabFragment.addItemView() for the exact ids.
    }

    companion object {
        const val TAG = "VideoDetailsDialogFragment"

        fun show(path: Path, fragment: Fragment) {
            // A tag, unlike DialogFragmentExtensions.show(), so the fragment can find us again
            // when the player gets ready. See plan 12 7.3.1.
            VideoDetailsDialogFragment().putArgs(Args(path))
                .show(fragment.childFragmentManager, TAG)
        }
    }

    @Parcelize
    class Args(val path: @WriteWith<ParcelableParceler> Path) : ParcelableArgs

    interface Listener {
        fun getVideoDetails(path: Path): VideoDetails
    }
}
```

`args`·`putArgs`·`ParcelableParceler`·`requireParentFragment() as Listener`는 같은 패키지의
[ConfirmDeleteDialogFragment.kt](../app/src/main/java/me/zhanghai/android/files/viewer/image/ConfirmDeleteDialogFragment.kt)와
같은 형태다. `show()`만 공용 확장 함수를 쓰지 않는데, 그쪽은 태그를 `null`로 넘겨
([DialogFragmentExtensions.kt:11](../app/src/main/java/me/zhanghai/android/files/util/DialogFragmentExtensions.kt#L11))
나중에 시트를 찾을 수 없기 때문이다.

⚠️ `addItem()`의 몸통과 `formatFrameRate()`, `FileSize`의 정확한 서식 함수 이름은
**기존 코드를 열어 확인하고 채운다.** 이 계획서가 이름을 지어내면 컴파일이 안 된다.
확인할 곳은 두 군데다.

```bash
grep -n "addItemView" app/src/main/java/me/zhanghai/android/files/fileproperties/FilePropertiesTabFragment.kt
grep -rn "class FileSize\|fun format" app/src/main/java/me/zhanghai/android/files/file/FileSize.kt
```

- [ ] 아직 모르는 값은 **줄을 없애지 말고 `—`(`media_viewer_details_unknown`)를 넣는다**
      (기획서 §7.1).

#### 7.3.2 프래그먼트 쪽 — 값을 만들고 밀어 넣는다

- [ ] `MediaViewerFragment`가 `VideoDetailsDialogFragment.Listener`를 구현한다.

```kotlin
class MediaViewerFragment :
    Fragment(), ConfirmDeleteDialogFragment.Listener, VideoDetailsDialogFragment.Listener {

    override fun getVideoDetails(path: Path): VideoDetails {
        // The player only knows about the page it is attached to.
        val player = playerHolder?.takeIf { it.currentPath == path }?.exoPlayer
        return buildVideoDetails(
            path, viewModel.videoFileDetails[path], player?.videoFormat, player?.duration
        )
    }

    private fun showVideoDetails() {
        val path = currentPath
        VideoDetailsDialogFragment.show(path, this)
        loadVideoFileDetails(path)
    }

    private fun loadVideoFileDetails(path: Path) {
        if (path in viewModel.videoFileDetails) {
            return
        }
        viewLifecycleOwner.lifecycleScope.launch {
            val fileDetails = try {
                withContext(Dispatchers.IO) { readVideoFileDetails(path) }
            } catch (e: Exception) {
                e.printStackTrace()
                return@launch
            }
            viewModel.videoFileDetails[path] = fileDetails
            updateVideoDetailsSheet()
        }
    }

    private fun updateVideoDetailsSheet() {
        (childFragmentManager.findFragmentByTag(VideoDetailsDialogFragment.TAG)
            as? VideoDetailsDialogFragment)?.updateDetails()
    }
}
```

`viewModel.videoFileDetails`는 §5.1에서 이미 만들어 뒀다. `playbackPositions`와 수명이
같다 — 회전을 넘기고, 뷰어를 닫으면 사라진다.

- [ ] ⚠️ **재생이 준비되면 시트를 다시 그린다.** 이것이 없으면 기획서 §7.1이 요구한
      "자리표시 `—`에 값이 들어온다"가 **영원히 일어나지 않는다.** 해상도·코덱·프레임 레이트·
      길이는 전부 재생 준비 후에 생기는 값이라, 시트를 먼저 열어 둔 사용자는 `—`만 보게 된다.
      7.4 검증이 보는 항목이 이것이다. `playerListener`에 더한다.

```kotlin
        override fun onPlaybackStateChanged(playbackState: Int) {
            if (playbackState == Player.STATE_READY) {
                // videoFormat and duration are known only now, see spec 11 section 7.1.
                updateVideoDetailsSheet()
            }
            if (playbackState == Player.STATE_ENDED) {
                // 5단계에서 넣은 코드 그대로
                playerHolder?.currentPath?.let { viewModel.playbackPositions.remove(it) }
            }
        }

        override fun onVideoSizeChanged(videoSize: VideoSize) {
            updateVideoDetailsSheet()
        }
```

- [ ] 메뉴에 항목을 넣는다. 배속 바로 뒤다.

```xml
    <item
        android:id="@+id/action_video_details"
        android:orderInCategory="60"
        android:title="@string/media_viewer_details"
        app:showAsAction="never" />
```

```xml
<!-- values/strings.xml -->
    <string name="media_viewer_details">Details</string>
    <string name="media_viewer_details_file_name">File name</string>
    <string name="media_viewer_details_created_time">Date taken</string>
    <string name="media_viewer_details_dimensions">Dimensions</string>
    <string name="media_viewer_details_duration">Duration</string>
    <string name="media_viewer_details_frame_rate">Frame rate</string>
    <string name="media_viewer_details_frame_rate_format">%1$s fps</string>
    <string name="media_viewer_details_codec">Codec</string>
    <string name="media_viewer_details_bit_rate">Bit rate</string>
    <string name="media_viewer_details_rotation">Rotation</string>
    <string name="media_viewer_details_rotation_format">%1$d°</string>
    <string name="media_viewer_details_size">File size</string>
    <string name="media_viewer_details_location">Location</string>
    <string name="media_viewer_details_path">Path</string>
    <string name="media_viewer_details_unknown">—</string>
```

```xml
<!-- values-ko/strings.xml -->
    <string name="media_viewer_details">세부 정보</string>
    <string name="media_viewer_details_file_name">파일 이름</string>
    <string name="media_viewer_details_created_time">촬영 시각</string>
    <string name="media_viewer_details_dimensions">해상도</string>
    <string name="media_viewer_details_duration">길이</string>
    <string name="media_viewer_details_frame_rate">프레임 레이트</string>
    <string name="media_viewer_details_codec">코덱</string>
    <string name="media_viewer_details_bit_rate">비트레이트</string>
    <string name="media_viewer_details_rotation">회전</string>
    <string name="media_viewer_details_size">파일 크기</string>
    <string name="media_viewer_details_location">위치</string>
    <string name="media_viewer_details_path">경로</string>
```

- [ ] 값 서식은 이미 있는 것을 재사용한다 — 해상도는
      `R.string.file_properties_media_dimensions_format`, 비트레이트는
      `R.string.file_properties_media_bit_rate_format`(kbps로 나눠 넣는다),
      파일 크기는 `FileSize`의 서식, 촬영 시각은 `Instant.formatLong()`
      ([FilePropertiesVideoTabFragment.kt](../app/src/main/java/me/zhanghai/android/files/fileproperties/video/FilePropertiesVideoTabFragment.kt) 참고).
      **새 서식 문자열을 만들기 전에 그쪽을 먼저 본다.**

- [ ] 항목도 동영상 페이지일 때만 보이게 한다. `onPrepareOptionsMenu`에 한 줄.

```kotlin
        menu.findItem(R.id.action_video_details).isVisible = isVideo
```

### 7.4 7단계 검증

- [ ] 빌드 통과
- [ ] `⋮` → 세부 정보에서 **해상도·길이·프레임 레이트·코덱·촬영 시각**이 나온다 (수용 기준 9)
- [ ] **시트를 여는 동안 재생이 계속된다**
- [ ] **촬영 시각이 파일 목록 미디어 모드의 날짜 타일과 같은 날짜**를 가리킨다 (수용 기준 10).
      `MediaCreatedTime`을 그대로 쓰는지 확인하는 항목이다
- [ ] **재생이 시작되기 전에 열면** 아직 모르는 값에 `—` 가 있고, **재생이 시작되면 그 자리가
      채워진다** — §7.3.2의 `STATE_READY` 연결이 있어야 통과한다. 시트를 닫았다 다시 열어야
      값이 보인다면 그 연결이 빠진 것이다
- [ ] 세로로 찍은 영상에서 **회전 90°가 표시되고, 해상도는 원본 값**이다
- [ ] 위치 정보가 없는 영상에서는 위치 줄이 아예 없다
- [ ] **사진 페이지에서는 세부 정보 메뉴가 안 보인다**
- [ ] 240fps 슬로모 영상의 fps 표기가 맞다 ([11번 §11의 4번)

> **커밋 메시지 초안**
> `재생 중에 볼 수 있는 동영상 세부 정보 시트 추가`

---

## 8단계. 오류 처리 · 공유 MIME · 삭제 안전

### 8.1 재생 실패 (기획서 §8)

- [ ] `playerListener`의 `onPlayerError`를 채운다.

```kotlin
        override fun onPlayerError(error: PlaybackException) {
            error.printStackTrace()
            val path = playerHolder?.currentPath ?: return
            // The page that failed may not be the page on screen any more, see 8.1.1.
            if (path != currentPath) {
                return
            }
            showPlaybackError(path, error)
        }
```

```kotlin
    private fun showPlaybackError(path: Path, error: PlaybackException) {
        val binding = currentVideoBinding ?: return
        binding.playerView.isVisible = false
        binding.thumbnailImage.isVisible = false
        binding.progress.fadeOutUnsafe()
        binding.errorText.text = getString(
            R.string.media_viewer_playback_error_format, error.errorCodeName
        )
        // Another app cannot open a file that is missing or unreadable either, so the button is
        // only for codec failures. Spec 11 section 8.
        val isFileError = error.errorCode == PlaybackException.ERROR_CODE_IO_FILE_NOT_FOUND
            || error.errorCode == PlaybackException.ERROR_CODE_IO_NO_PERMISSION
        binding.openWithButton.isVisible = !isFileError
        binding.openWithButton.setOnClickListener(
            if (isFileError) null else View.OnClickListener { openWithAnotherApp(path) }
        )
        binding.errorLayout.fadeInUnsafe(true)
    }
```

`PlaybackException.errorCodeName`은 1.11.0에 있는 `public final String getErrorCodeName()`이다.
`ERROR_CODE_DECODING_FORMAT_UNSUPPORTED` 같은 값이 나온다. 사람에게 보여줄 문구는 아니지만
**원인을 알 수 있는 유일한 단서**이므로 문구 안에 넣는다.

`ERROR_CODE_IO_FILE_NOT_FOUND`(2005)와 `ERROR_CODE_IO_NO_PERMISSION`(2006)은
`PlaybackException`의 상수다(1.11.0에서 확인). **기획서 §8의 마지막 줄** — "파일이 없거나
권한이 없을 때도 같은 자리에 오류 문구를 띄운다(이때는 열기 버튼 없음)" — 이 갈래가 그것이다.
버튼을 남겨 두면 눌러 봐야 다른 앱에서도 똑같이 실패한다.

#### 8.1.1 ⚠️ 실패한 페이지가 지금 보이는 페이지가 아닐 수 있다

`onPlayerError()`는 경로를 `playerHolder.currentPath`에서, 뷰를 `currentVideoBinding`
(=**현재 페이지**)에서 가져온다. 준비 도중에 사용자가 페이지를 넘겨 버리면 이 둘이 어긋나서
**멀쩡한 페이지에 남의 오류 문구가 찍힌다.** §3.3에서 짚은 것과 같은 종류의 어긋남이다.
위의 `path != currentPath` 가드가 그것을 막는다 — 떠난 페이지는 어차피 `detach()`되었고,
돌아오면 다시 시도해서 다시 실패하므로 오류를 놓치지도 않는다.

- [ ] "다른 앱으로 열기"는 **파일 목록의 기존 경로를 그대로 탄다.**

```kotlin
    private fun openWithAnotherApp(path: Path) {
        val mimeType = MimeType.guessFromPath(path.toString())
        val intent = path.fileProviderUri.createViewIntent(mimeType)
            .apply { extraPath = path }
            .withChooser()
        startActivitySafe(intent)
    }
```

`withChooser()`를 붙이는 이유는, 우리 앱이 이미 못 연 파일이므로 **다른 앱을 고를 기회를
주는 것이 목적**이기 때문이다.

- [ ] 문자열.

```xml
<!-- values/strings.xml -->
    <string name="media_viewer_playback_error_format">Cannot play this video (%1$s)</string>
```

```xml
<!-- values-ko/strings.xml -->
    <string name="media_viewer_playback_error_format">이 동영상을 재생할 수 없습니다 (%1$s)</string>
```

- [ ] 다른 페이지로 갔다 돌아왔을 때 오류 화면이 남지 않게 한다.
      `bindVideo()`가 이미 `errorLayout.isVisible = false`로 시작하므로 **재사용될 때는 저절로
      지워진다.** 같은 페이지에 머문 채로 다시 시도할 방법은 두지 않는다 (기획서 비목표 아님,
      단순히 필요 없다 — 페이지를 떠났다 오면 다시 시도된다).

### 8.2 공유 MIME (기획서 §9)

지금 `share()`는 타입이 `image/*`로 고정돼 있다
([ImageViewerFragment.kt:204](../app/src/main/java/me/zhanghai/android/files/viewer/image/ImageViewerFragment.kt#L204)).

- [ ] 실제 MIME 타입을 쓰게 바꾼다.

```kotlin
    private fun share() {
        val path = currentPath
        val mimeType = MimeType.guessFromPath(path.toString())
        val intent = path.fileProviderUri.createSendStreamIntent(mimeType)
            .apply { extraPath = path }
            .withChooser()
        startActivitySafe(intent)
    }
```

`createSendStreamIntent(mimeType)`은
[IntentExtensions.kt:138](../app/src/main/java/me/zhanghai/android/files/util/IntentExtensions.kt#L138)에 이미 있다.
`createSendImageIntent`가 `text` 인자로 받던 것은 이 호출부에서 안 쓰고 있었으므로 잃는 것이 없다.

⚠️ **사진에도 영향이 간다.** 사진의 MIME이 `image/jpeg`처럼 더 구체적으로 나가게 되는데,
`image/*`보다 정확하므로 문제가 아니다. 다만 **8단계 검증에서 사진 공유도 확인한다.**

### 8.3 재생 중인 파일 삭제 (기획서 §9)

- [ ] `delete(path)` 맨 앞에서 그 파일이 재생 중이면 먼저 놓아준다.

```kotlin
    override fun delete(path: Path) {
        // Let go of the file before unlinking it, see spec 11 section 9.
        playerHolder?.let { if (it.currentPath == path) it.detach() }
        viewModel.playbackPositions.remove(path)
        try {
            path.delete()
        } catch (e: IOException) {
            // ... 이하 기존 코드 그대로
```

`detach()`는 `stop()`을 부르고 `PlayerView`에서 떼어낸다. 인스턴스는 살아 있으므로
다음 페이지에서 그대로 재사용된다.

- [ ] 삭제 뒤 목록이 줄면서 현재 페이지가 동영상이 될 수 있다. 기존 코드가
      `binding.viewPager.doOnPreDraw { requestTransform() }`을 부르는데, **그 자리에서
      재생도 다시 시작시킨다.**

```kotlin
        binding.viewPager.doOnPreDraw {
            binding.viewPager.requestTransform()
            startPlaybackIfVideoPage()
        }
```

### 8.4 8단계 검증

- [ ] 빌드 통과
- [ ] **일부러 못 여는 파일을 만들어 확인한다.** `.mp4` 확장자를 붙인 텍스트 파일이면 된다

```bash
adb shell "echo not-a-video > /sdcard/SwingTestData/broken.mp4"
```

- [ ] 그 페이지에서 **오류 문구와 "다른 앱으로 열기"** 가 나온다 (수용 기준 18)
- [ ] 버튼을 누르면 다른 앱 선택지가 뜬다
- [ ] **파일을 지운 뒤**(뷰어를 띄운 채 `adb shell rm`) 그 페이지로 가면 오류 문구는 나오되
      **"다른 앱으로 열기" 버튼은 없다** — 기획서 §8, §8.1
- [ ] 오류가 나는 파일에 도착하자마자 **바로 옆 페이지로 넘겨도** 그 페이지에 오류 문구가
      찍히지 않는다 — §8.1.1
- [ ] 오류가 난 페이지에서 좌우로 넘어가면 다음 항목은 정상 재생된다
- [ ] 다시 돌아오면 오류 화면이 그대로 나온다 (지워지고 빈 화면이 되면 안 된다)
- [ ] **동영상을 공유**하면 받는 앱에 동영상으로 전달된다 (수용 기준 16)
- [ ] **사진을 공유**해도 이전처럼 동작한다
- [ ] **재생 중인 동영상을 삭제**해도 크래시하지 않고, 목록에서 빠지며 다음 항목으로 넘어간다 (수용 기준 17)
- [ ] 삭제 후 다음 항목이 동영상이면 자동 재생된다
- [ ] 마지막 항목을 삭제하면 뷰어가 닫힌다

> **커밋 메시지 초안**
> `재생 실패 안내와 다른 앱으로 열기, 공유 MIME 수정, 재생 중 파일 삭제 처리`

---

## 9단계. 검증 · 실기기 측정

### 9.1 수용 기준

- [ ] [11번 §12의 20개 항목을 처음부터 끝까지 확인한다. 각 단계 검증에서 이미 본 것들이지만,
      **전체를 이어서 한 번 더** 한다. 단계별로는 통과했는데 합쳐 놓으면 어긋나는 것이 나온다.

특히 이 셋은 단계 검증에서 다루지 않았다.

- [ ] 수용 기준 19 — **원격(SFTP/SMB) 폴더의 동영상**이 지금처럼 외부 앱으로 열리고,
      사진을 넘길 때 건너뛴다
- [ ] 수용 기준 20 — **사진만 있는 폴더의 동작이 이전과 완전히 같다.**
      확대, 좌우 넘김, 탭 토글, 삭제·공유. 1단계 이전 빌드와 나란히 놓고 비교한다
      ([03번 문서](03-debug-variant-side-by-side.md)의 방법)
- [ ] `.nomedia`가 있는 폴더에서도 똑같이 동작한다

### 9.2 실기기 측정 (최우선 리스크)

[11번 §11의 표를 채운다. 실기기 SM-F971N(Galaxy Z Fold 7, Android 17)에서 한다.

| # | 확인할 것 | 결과 |
|---|---|---|
| 1 | 아이폰 `.mov`(HEVC) 재생 | |
| 2 | 갤럭시 HDR 영상의 색 | |
| 3 | 세로 영상이 바로 서는가 | |
| 4 | 240fps 슬로모의 fps 표기 | |
| 5 | SAF 문서 경로(`content://`) 재생 | |
| 6 | APK 크기 증가 | (0단계에서 잼) |
| 7 | 수 GB 파일의 슬라이더 드래그 | |

- [ ] 결과를 **11번 기획서 §11에 적어 넣고**, 기획서의 개정 이력에 한 줄 남긴다.

### 9.3 그 밖에 봐야 할 것

- [ ] **폴더를 열 때 느려지지 않았는지.** `getItemViewType()`이 페이지마다
      `MimeType.guessFromPath()`를 부르는데 문자열 연산이라 무시할 만해야 한다.
      의심되면 사진 100장 폴더에서 스크롤을 재 본다
- [ ] **접근성** (기획서 §10). TalkBack을 켜고 확인한다.
      재생/일시정지 버튼과 슬라이더는 Media3가 설명을 갖고 있어야 하고, 없으면 붙인다.
      `⋮` 메뉴의 재생 속도·세부 정보는 제목이 곧 설명이다.
      **세부 정보 시트가 "이름, 값" 순서로 읽히는지**가 실제로 확인할 것이다
- [ ] **메모리.** 여러 동영상을 오가며 20분쯤 쓴 뒤 `adb shell dumpsys meminfo`로 누수를 본다.
      `MediaViewerViewModel.playbackPositions` 맵은 뷰어 세션 동안 계속 커지는데, 항목이 `Path`와 `Long`이라
      수백 개여도 문제가 없다
- [ ] **release 빌드.** `minifyEnabled true`에서도 재생이 되는지

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleRelease
```

- [ ] 0단계에서 release 빌드가 통과했더라도 **여기서 실제로 설치해 재생까지** 해 본다.
      ProGuard 문제는 컴파일이 아니라 실행할 때 드러난다

> **커밋 메시지 초안**
> `동영상 뷰어 실기기 검증 결과를 기획서에 반영`

---

## 10단계. 화면 정리 — 검정 판 제거 · 컨트롤 손보기

기준은 [11a번 기획서](11a-viewer-ui-cleanup-spec.md)다. 9단계까지와 달리 **기능을 더하지 않고 덜어낸다.**

### 10.0 이 단계가 되돌리는 것

이 단계는 앞 단계에서 만든 것 셋을 **의도적으로 되돌린다.** 끝난 단계의 본문은 고치지 않는 것이
이 문서의 규칙이므로(§1), 무엇이 뒤집히는지 여기에 모아 둔다.

| 되돌리는 것 | 어디서 만들었나 | 왜 |
|---|---|---|
| 앱 바 부제목의 배속 표시 | 6단계 §6.4 | 부제목이 통째로 없어진다 (11a §3.2, D15) |
| `PlayerControlView`의 `minHeight="220dp"` | §1 "달라진 곳" 2번 | 우리 레이아웃에는 minimal mode가 없으므로 이 방어가 필요 없다 (11a §4.1) |
| Media3 기본 컨트롤 레이아웃을 그대로 쓰기 | 4단계 §4.1 | 스크림과 톱니바퀴가 레이아웃에 박혀 있어 속성으로 못 뗀다 (11a D17) |

**뒤집히지 않는 것**: 컨트롤의 동작 로직은 여전히 Media3 것이다. 우리는 레이아웃만 갈아끼운다.
`show_timeout="0"`(자동 숨김 끄기), 앱 바와 함께 여닫기, 스크러빙은 그대로다.

### 10.1 스크림 드로어블부터 만든다

10.2와 10.3이 둘 다 이걸 쓴다. 색은 **이미 있는 `@color/dark_50_percent`를 그대로 쓴다.**
새 색을 만들지 않는다 (11a §3.3).

- [ ] `res/drawable/media_viewer_scrim_circle.xml` — 아이콘 뒤에 깔 원

```xml
<?xml version="1.0" encoding="utf-8"?>

<shape
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">

    <solid android:color="@color/dark_50_percent" />
</shape>
```

- [ ] `res/drawable/media_viewer_scrim_rect.xml` — 시간 텍스트 뒤에 깔 라운드 사각

```xml
<?xml version="1.0" encoding="utf-8"?>

<shape
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">

    <solid android:color="@color/dark_50_percent" />
    <corners android:radius="4dp" />
</shape>
```

- [ ] `res/drawable/media_viewer_control_background.xml` — 재생 버튼 배경. 원 스크림 **위에**
      리플이 도는 형태다. 배경을 그냥 원으로 주면 눌린 느낌이 사라진다.

```xml
<?xml version="1.0" encoding="utf-8"?>

<ripple
    xmlns:android="http://schemas.android.com/apk/res/android"
    android:color="?attr/colorControlHighlight">

    <item android:id="@android:id/mask">
        <shape android:shape="oval">
            <solid android:color="@android:color/white" />
        </shape>
    </item>

    <item android:drawable="@drawable/media_viewer_scrim_circle" />
</ripple>
```

### 10.2 상단 앱 바 — 배경과 글자를 없애고 아이콘에 원을 깐다

#### 10.2.1 배경 지우기

- [ ] `res/layout/media_viewer_fragment.xml`의 `appBarLayout` 배경을 투명으로.

```xml
    <FrameLayout
        android:id="@+id/appBarLayout"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:background="@android:color/transparent">
```

이 배경이 상태바 자리까지 그리고 있었다(`MediaViewerFragment.onActivityCreated()`의
"Our app bar will draw the status bar background" 주석). `statusBarColor`는 이미
`TRANSPARENT`이므로 **이제 상태바 자리까지 사진이 올라온다.** 의도한 결과다.

⚠️ **`applySystemWindowInsetsToPadding()` 호출은 지우지 않는다.** 지우면 아이콘이 상태바와
노치 밑으로 들어간다. 인셋은 그대로, 배경만 없앤다.

#### 10.2.2 제목·부제목 없애기

- [ ] `onActivityCreated()`에서 액션바가 제목을 그리지 않게 한다. **이 줄이 없으면
      매니페스트의 액티비티 라벨("미디어 뷰어")이 제목 자리에 그대로 뜬다.**

```kotlin
        val activity = activity as AppCompatActivity
        activity.setSupportActionBar(binding.toolbar)
        activity.supportActionBar!!.apply {
            setDisplayHomeAsUpEnabled(true)
            // The app bar has no background any more, so it shows nothing but its two icons.
            // See spec 11a section 3.2.
            setDisplayShowTitleEnabled(false)
        }
```

- [ ] `updateTitle()` 함수를 **통째로 지운다** (현재 509~529행).

- [ ] 호출처를 전부 지운다. 다섯 곳이다.

| 위치 | 지금 | 뒤 |
|---|---|---|
| `onPageSelected()` (153행) | `updateTitle()` | 줄 삭제 |
| `onPlaybackParametersChanged()` (322행) | `updateTitle()` | 줄 삭제 |
| `onViewStateRestored()` (346행) | `updateTitle()` | 줄 삭제 |
| 삭제 처리 (431행) | `updateTitle()` | 줄 삭제 |
| `setPlaybackSpeed()` (506행) | `updateTitle()` | 줄 삭제 |

- [ ] 431행 위의 주석은 **`updateTitle()`을 가리키므로 함께 고친다.** 인덱스 보정 자체는
      남겨 둔다 — 지우면 `currentPath`가 범위를 벗어난다.

```kotlin
        // ViewPager only asynchronously sets current item to 0, which isn't a desirable behavior
        // for us and would leave currentItem out of bounds for currentPath.
        if (binding.viewPager.currentItem > paths.lastIndex) {
            binding.viewPager.currentItem = paths.lastIndex
        }
```

- [ ] `onViewStateRestored()`에 남은 주석도 손본다. `updatePlayerControlVisibility()`는
      **그대로 둔다** — 첫 페이지에 슬라이더가 안 뜨는 문제(§1 "달라진 곳" 4번)는 여전하다.

```kotlin
        // onPageSelected() never fires for the initial page because the callback is registered
        // after setCurrentItem().
        updatePlayerControlVisibility()
```

#### 10.2.3 아이콘에 원 스크림 씌우기

배경 판이 없어졌으니 흰 아이콘이 밝은 사진 위에서 사라진다. **테마가 고른 아이콘을 그대로 두고
뒤에 원만 깐다.** 아이콘 파일을 새로 만들지 않는 이유는, 뒤로가기 아이콘이 테마의
`homeAsUpIndicator`에서 오기 때문이다 — 우리가 어떤 벡터가 쓰이는지 정하지 않는다.

- [ ] `app/src/main/java/me/zhanghai/android/files/viewer/media/ScrimmedIcon.kt` (신규)

```kotlin
/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.content.Context
import android.graphics.drawable.Drawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import me.zhanghai.android.files.R
import me.zhanghai.android.files.util.dpToDimensionPixelSize

private const val SCRIM_SIZE_DP = 40
private const val ICON_SIZE_DP = 24

/**
 * Puts a translucent disc behind an app bar icon, see spec 11a section 3.3.
 *
 * The app bar has no background of its own any more, so a white icon disappears over a bright
 * photo. Wrapping keeps whatever icon the theme picked instead of hard coding one of ours.
 */
fun Drawable.withCircleScrim(context: Context): Drawable {
    val scrimSize = context.dpToDimensionPixelSize(SCRIM_SIZE_DP)
    val inset = (scrimSize - context.dpToDimensionPixelSize(ICON_SIZE_DP)) / 2
    val scrim = GradientDrawable().apply {
        shape = GradientDrawable.OVAL
        setColor(context.getColor(R.color.dark_50_percent))
        setSize(scrimSize, scrimSize)
    }
    return LayerDrawable(arrayOf(scrim, this)).apply { setLayerInset(1, inset, inset, inset, inset) }
}
```

`LayerDrawable`의 고유 크기는 각 층의 고유 크기에 인셋을 더한 것 중 **가장 큰 값**이다.
원이 40dp, 아이콘이 24dp + 좌우 8dp = 40dp라서 결과도 40dp가 된다.

- [ ] 뒤로가기 아이콘에 씌운다. `setDisplayHomeAsUpEnabled(true)` **뒤에** 놓아야 한다.
      그 전에는 `navigationIcon`이 아직 `null`이다.

```kotlin
        binding.toolbar.navigationIcon =
            binding.toolbar.navigationIcon?.withCircleScrim(requireContext())
```

- [ ] `⋮` 아이콘은 `onPrepareOptionsMenu()`(현재 364행)에서 씌운다. **메뉴가 만들어진 뒤에야
      `overflowIcon`이 생긴다.** 이 콜백은 `invalidateOptionsMenu()`마다 다시 불리므로,
      **한 번만 씌우도록 막지 않으면 원이 겹겹이 쌓인다.**

```kotlin
    private var isOverflowIconScrimmed = false

    override fun onPrepareOptionsMenu(menu: Menu) {
        super.onPrepareOptionsMenu(menu)

        if (!isOverflowIconScrimmed) {
            val overflowIcon = binding.toolbar.overflowIcon
            if (overflowIcon != null) {
                binding.toolbar.overflowIcon = overflowIcon.withCircleScrim(requireContext())
                isOverflowIconScrimmed = true
            }
        }
        // ... 기존 내용은 그대로
    }
```

`isOverflowIconScrimmed`는 뷰가 아니라 프래그먼트가 들고 있으므로 화면 회전 뒤에도
살아 있다. 회전하면 뷰가 새로 만들어지면서 아이콘도 원래대로 돌아오므로,
`onDestroyView()`에서 `false`로 되돌린다.

- [ ] `onDestroyView()`에 한 줄 더한다.

```kotlin
    override fun onDestroyView() {
        super.onDestroyView()

        playerHolder?.release()
        playerHolder = null
        // The new view will get a fresh, unwrapped overflow icon.
        isOverflowIconScrimmed = false
    }
```

### 10.3 하단 컨트롤 — 우리 레이아웃으로 갈아끼운다

#### 10.3.1 컨트롤 레이아웃 (신규)

`PlayerControlView`는 **뷰를 id로 찾는다.** 그래서 우리 레이아웃도 Media3의 id를 그대로 써야
한다. `@+id`가 아니라 **`@id`** 로 참조한다 — 새로 만드는 id가 아니라 Media3의 것이다.

- [ ] `res/layout/media_viewer_player_control.xml` (신규)

```xml
<?xml version="1.0" encoding="utf-8"?>

<!--
  ~ Copyright (c) 2026 PhotoExplorer
  ~ All Rights Reserved.
  -->

<!--
  ~ Replaces the Media3 default layout, which bakes in a full width scrim and a settings button.
  ~ See spec 11a section 4. The behaviour still comes from PlayerControlView; only the views are
  ~ ours, and they keep Media3's ids so it can find them.
  -->
<LinearLayout
    xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:app="http://schemas.android.com/apk/res-auto"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:paddingHorizontal="8dp"
    android:paddingVertical="8dp">

    <LinearLayout
        android:layout_width="wrap_content"
        android:layout_height="wrap_content"
        android:layout_gravity="center_horizontal"
        android:orientation="horizontal">

        <ImageButton
            android:id="@id/exo_rew"
            style="@style/ExoStyledControls.Button.Center.Rewind"
            android:background="@drawable/media_viewer_control_background" />

        <ImageButton
            android:id="@id/exo_play_pause"
            style="@style/ExoStyledControls.Button.Center.PlayPause"
            android:layout_marginHorizontal="16dp"
            android:background="@drawable/media_viewer_control_background" />

        <ImageButton
            android:id="@id/exo_ffwd"
            style="@style/ExoStyledControls.Button.Center.FfwdWithAmount"
            android:background="@drawable/media_viewer_control_background" />
    </LinearLayout>

    <LinearLayout
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_marginTop="8dp"
        android:orientation="horizontal"
        android:gravity="center_vertical">

        <TextView
            android:id="@id/exo_position"
            style="@style/ExoStyledControls.TimeText.Position"
            android:background="@drawable/media_viewer_scrim_rect"
            android:paddingHorizontal="6dp"
            android:paddingVertical="2dp" />

        <androidx.media3.ui.DefaultTimeBar
            android:id="@id/exo_progress"
            android:layout_width="0dp"
            android:layout_height="wrap_content"
            android:layout_weight="1"
            android:layout_marginHorizontal="8dp"
            app:touch_target_height="39dp" />

        <TextView
            android:id="@id/exo_duration"
            style="@style/ExoStyledControls.TimeText.Duration"
            android:background="@drawable/media_viewer_scrim_rect"
            android:paddingHorizontal="6dp"
            android:paddingVertical="2dp" />
    </LinearLayout>
</LinearLayout>
```

**여기 없는 것이 곧 없어지는 것이다** — 스크림 배경 뷰, 톱니바퀴(`exo_settings`),
이전/다음, 셔플·반복·자막·VR·전체화면. `PlayerControlView`는 없는 id를 조용히 건너뛴다.

**오디오는 이 레이아웃에서 톱니바퀴가 빠지는 것으로 끝난다 (11a §4.3).** 트랙 선택 UI가
없어질 뿐이고, ExoPlayer의 기본 트랙 선택이 그대로 동작한다. **코드로 할 일이 없다** —
`TrackSelectionParameters`를 건드리지 않는다.

⚠️ **`@style/ExoStyledControls.*` 이름이 1.11.0에서 안 맞으면 빌드가 바로 깨진다.**
그때는 스타일을 빼고 아이콘을 직접 준다 — `exo_rew`에 `android:src="@drawable/exo_icon_rewind"`,
`exo_ffwd`에 `@drawable/exo_icon_fastforward`. `exo_play_pause`는 **아이콘을 주지 않아도 된다**:
`PlayerControlView`가 재생/일시정지 상태에 맞춰 직접 넣는다.

#### 10.3.2 프래그먼트 레이아웃에서 갈아끼우기

- [ ] `res/layout/media_viewer_fragment.xml`의 `PlayerControlView`를 이렇게 바꾼다.

```xml
    <androidx.media3.ui.PlayerControlView
        android:id="@+id/playerControlView"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:layout_gravity="bottom"
        android:visibility="gone"
        app:controller_layout_id="@layout/media_viewer_player_control"
        app:show_timeout="0" />
```

바뀐 곳은 셋이다.

- `app:controller_layout_id` **추가** — 우리 레이아웃을 쓴다
- `android:minHeight="220dp"` **삭제** — minimal mode 방어였는데, 우리 레이아웃에는
  minimal mode 자체가 없다 (§10.0)
- `show_previous_button` 등 다섯 개 **삭제** — 그 뷰들이 레이아웃에 아예 없으므로 끌 것이 없다

`show_timeout="0"`은 **남긴다.** 자동 숨김을 끄는 것이고, 앱 바와 함께 여닫는 규칙(11번 §6.2)이
여기에 걸려 있다.

`applySystemWindowInsetsToPadding()` 호출도 **그대로 둔다.** 제스처 바 위로 올라와야 한다.

### 10.4 슬라이더 터치 판정 1.5배

10.3.1 레이아웃의 `app:touch_target_height="39dp"` 한 줄이 전부다.

| 항목 | 값 | 왜 |
|---|---|---|
| `touch_target_height` | **39dp** | Media3 기본값 26dp × 1.5 (11a §5) |
| `bar_height` | 주지 않는다 | 막대 두께는 그대로 |
| `scrubber_enabled_size` / `scrubber_dragged_size` | 주지 않는다 | **동그라미 크기는 그대로** |

`android:layout_height`를 `wrap_content`로 둔 것이 중요하다. `DefaultTimeBar`는
`touch_target_height`를 자기 높이로 잡는다. 고정 높이를 주면 판정 영역이 잘린다.

### 10.5 로딩 표시를 하나로 합치고 지연시킨다

#### 10.5.1 ⚠️ 지금 스피너는 **버퍼링용이 아니다**

`media_viewer_video_item.xml`의 `progress`는 **썸네일 로딩용**이다
(`MediaViewerAdapter.bindVideo()`가 켜고 끈다). 버퍼링 표시는 `PlayerView`가
`show_buffering="when_playing"`으로 **따로 그린다.** 즉 넘길 때 도는 원의 출처가 둘이다.
하나만 지연시키면 다른 하나가 그대로 깜빡인다 (11a §6.1).

**둘을 한 뷰로 모으고, 그 뷰를 지연시킨다.**

⚠️ **사진 페이지는 손대지 않는다 (11a §6.2, D18).** `bindImage()`, `loadImage()`,
`loadImageWithInfo()`, `showError()`의 `binding.progress` 호출은 **지금 그대로 둔다.**
`media_viewer_image_item.xml`도 건드리지 않는다. 이 절의 변경은 전부 동영상 쪽이다.

#### 10.5.2 지연 표시기 (신규)

- [ ] `app/src/main/java/me/zhanghai/android/files/viewer/media/DelayedProgress.kt` (신규)

```kotlin
/*
 * Copyright (c) 2026 PhotoExplorer
 * All Rights Reserved.
 */

package me.zhanghai.android.files.viewer.media

import android.os.Handler
import android.os.Looper
import android.view.View
import me.zhanghai.android.files.util.fadeInUnsafe
import me.zhanghai.android.files.util.fadeOutUnsafe

private const val DELAY_MILLIS = 500L

/**
 * Shows [view] only when the wait actually lasts, see spec 11a section 6.1.
 *
 * A video page waits for two things that overlap: its thumbnail and the player's buffering. Both
 * report here, and the indicator stays up until both are done, so a finished thumbnail cannot hide
 * a still buffering player.
 */
class DelayedProgress(private val view: View) {
    enum class Reason { THUMBNAIL, BUFFERING }

    private val handler = Handler(Looper.getMainLooper())
    private val reasons = mutableSetOf<Reason>()
    private val showRunnable = Runnable { view.fadeInUnsafe(true) }

    fun begin(reason: Reason) {
        // Already waiting: the pending show still covers this reason.
        if (!reasons.add(reason) || reasons.size > 1) {
            return
        }
        handler.postDelayed(showRunnable, DELAY_MILLIS)
    }

    fun end(reason: Reason) {
        if (!reasons.remove(reason) || reasons.isNotEmpty()) {
            return
        }
        hide()
    }

    /** For recycling and errors, when neither reason is worth tracking any more. */
    fun endAll() {
        reasons.clear()
        hide()
    }

    private fun hide() {
        handler.removeCallbacks(showRunnable)
        view.fadeOutUnsafe()
    }
}
```

#### 10.5.3 어댑터 — 썸네일 쪽을 옮긴다

- [ ] `VideoViewHolder`가 표시기를 들게 한다.

```kotlin
    class VideoViewHolder(val binding: MediaViewerVideoItemBinding) :
        RecyclerView.ViewHolder(binding.root) {
        val progress = DelayedProgress(binding.progress)
    }
```

- [ ] `onBindViewHolder()`가 `binding`이 아니라 **홀더**를 넘기게 한다.

```kotlin
            is VideoViewHolder -> bindVideo(holder, path)
```

- [ ] `bindVideo()`의 시그니처와 세 군데 호출을 바꾼다.

```kotlin
    private fun bindVideo(holder: VideoViewHolder, path: Path) {
        val binding = holder.binding
        binding.root.reset()
        binding.root.setOnClickListener(listener)
        binding.playerView.isVisible = false
        binding.errorLayout.isVisible = false
        // The fragment fades the thumbnail out once the first frame is rendered, and that
        // animation may still be running when this page comes back. See plan 12 3.3.
        binding.thumbnailImage.animate().cancel()
        binding.thumbnailImage.isVisible = true
        binding.thumbnailImage.alpha = 1f
        holder.progress.begin(DelayedProgress.Reason.THUMBNAIL)
        lifecycleOwner.lifecycleScope.launch {
            val attributes = try {
                withContext(Dispatchers.IO) {
                    path.readAttributes(BasicFileAttributes::class.java)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                holder.progress.end(DelayedProgress.Reason.THUMBNAIL)
                return@launch
            }
            binding.thumbnailImage.load(path to attributes) {
                size(Size.ORIGINAL)
                fadeIn(binding.thumbnailImage.context.shortAnimTime)
                listener(
                    onSuccess = { _, _ ->
                        holder.progress.end(DelayedProgress.Reason.THUMBNAIL)
                    },
                    onError = { _, _ ->
                        holder.progress.end(DelayedProgress.Reason.THUMBNAIL)
                    }
                )
            }
        }
    }
```

- [ ] `onViewRecycled()`에서 예약을 취소한다. **없으면 재활용된 페이지에서 스피너가 뒤늦게 뜬다.**

```kotlin
    override fun onViewRecycled(holder: RecyclerView.ViewHolder) {
        ...
        when (holder) {
            is VideoViewHolder -> {
                holder.progress.endAll()
                holder.binding.thumbnailImage.dispose()
            }
            ...
        }
    }
```

#### 10.5.4 재생 뷰의 자체 버퍼링 표시를 끈다

- [ ] `res/layout/media_viewer_video_item.xml`의 `PlayerView`에서 한 줄을 바꾼다.

```xml
        app:show_buffering="never"
```

#### 10.5.5 프래그먼트 — 버퍼링 쪽을 표시기에 연결한다

프래그먼트는 지금 `videoBindingAt()`으로 **바인딩만** 꺼낸다. 표시기는 홀더에 있으므로
**홀더를 꺼내도록 바꾸고, 바인딩은 홀더에서 얻는다.**

- [ ] 275~290행의 두 접근자를 이렇게 바꾼다.

```kotlin
    private val currentVideoHolder: MediaViewerAdapter.VideoViewHolder?
        get() = videoHolderAt(binding.viewPager.currentItem)

    private val currentVideoBinding: MediaViewerVideoItemBinding?
        get() = currentVideoHolder?.binding

    private fun videoHolderAt(position: Int): MediaViewerAdapter.VideoViewHolder? {
        val recyclerView = binding.viewPager.getChildAt(0) as? RecyclerView ?: return null
        val holder = recyclerView.findViewHolderForAdapterPosition(position)
        return holder as? MediaViewerAdapter.VideoViewHolder
    }
```

`videoBindingAt(position)`을 쓰던 `restoreVideoPage()`(229행)도 홀더로 바꾼다. 떠나는 페이지의
**버퍼링 예약도 여기서 취소해야 한다** — 그러지 않으면 이미 떠난 페이지에 스피너가 뜬다.

- [ ] `restoreVideoPage()`를 이렇게 바꾼다.

```kotlin
    private fun restoreVideoPage(path: Path) {
        val position = paths.indexOf(path)
        if (position == -1) {
            return
        }
        val holder = videoHolderAt(position) ?: return
        holder.progress.end(DelayedProgress.Reason.BUFFERING)
        val videoBinding = holder.binding
        videoBinding.playerView.isVisible = false
        videoBinding.thumbnailImage.animate().cancel()
        videoBinding.thumbnailImage.alpha = 1f
        videoBinding.thumbnailImage.isVisible = true
    }
```

- [ ] `playerListener`의 `onPlaybackStateChanged()`에 버퍼링 처리를 넣는다.

```kotlin
        override fun onPlaybackStateChanged(playbackState: Int) {
            updateBufferingProgress(playbackState)
            if (playbackState == Player.STATE_READY) {
                // videoFormat and duration are known only now, see spec 11 section 7.1.
                updateVideoDetailsSheet()
            }
            if (playbackState == Player.STATE_ENDED) {
                // Otherwise coming back to this video would start it at its last frame.
                playerHolder?.currentPath?.let { viewModel.playbackPositions.remove(it) }
            }
        }
```

- [ ] 그 아래에 함수를 더한다. **화면에 있는 페이지가 실제로 재생 중인 그 페이지일 때만** 손댄다
      — `onPlayerError()`가 쓰는 것과 같은 방어다 (§8.1.1).

```kotlin
    /** Only the page that owns the player may show its buffering, see spec 11a section 6.1. */
    private fun updateBufferingProgress(playbackState: Int) {
        if (playerHolder?.currentPath != currentPath) {
            return
        }
        val holder = currentVideoHolder ?: return
        if (playbackState == Player.STATE_BUFFERING) {
            holder.progress.begin(DelayedProgress.Reason.BUFFERING)
        } else {
            holder.progress.end(DelayedProgress.Reason.BUFFERING)
        }
    }
```

- [ ] `showPlaybackError()`(현재 442행)에서 `videoBinding.progress.fadeOutUnsafe()`를
      표시기 쪽으로 바꾼다. **직접 숨기면 예약된 표시가 살아 있어 오류 문구 위에 스피너가 뜬다.**

```kotlin
    private fun showPlaybackError(path: Path, error: PlaybackException) {
        val holder = currentVideoHolder ?: return
        holder.progress.endAll()
        val videoBinding = holder.binding
        videoBinding.playerView.isVisible = false
        videoBinding.thumbnailImage.isVisible = false
        // ... 나머지는 그대로
```

### 10.6 안 쓰게 되는 코드와 문자열 지우기

`updateTitle()`을 지우면 딸린 것들이 함께 죽는다. **남겨 두면 다음 사람이 "왜 안 쓰지?"를
다시 조사한다.**

- [ ] `formatPlaybackSpeed()`(531~545행)를 지운다. `updateTitle()`이 유일한 호출처였다.
- [ ] `res/values/strings.xml`과 `res/values-ko/strings.xml`에서 두 문자열을 지운다.

| 문자열 | 쓰이던 곳 |
|---|---|
| `image_viewer_subtitle_format` | `updateTitle()`의 `3 / 20` |
| `media_viewer_speed_format` | `formatPlaybackSpeed()`의 "목록에 없는 배속" |

`media_viewer_speed_0_25` ~ `media_viewer_speed_2`는 **지우지 않는다.** `⋮` 메뉴가 쓴다.

- [ ] `onPlaybackParametersChanged()`의 주석을 고친다. 톱니바퀴가 없어졌으니 "PlayerControlView에
      자체 속도 메뉴가 있다"는 이유가 더는 맞지 않는다. **콜백 자체는 남긴다** — `ViewModel`의
      값과 실제 속도를 맞춰 두는 값이 있다.

```kotlin
        override fun onPlaybackParametersChanged(playbackParameters: PlaybackParameters) {
            // Our own menu is the only way to change this now, but keeping our copy in sync keeps
            // the checked menu item honest and stops the next video from reverting the speed.
            if (playbackParameters.speed != viewModel.playbackSpeed) {
                viewModel.playbackSpeed = playbackParameters.speed
                requireActivity().invalidateOptionsMenu()
            }
        }
```

- [ ] 안 쓰는 import를 정리한다. `updateTitle()`과 `formatPlaybackSpeed()`가 사라지면서
      `R.string.image_viewer_subtitle_format`·`R.string.media_viewer_speed_format` 참조가 없어진다.
      **Android Studio의 "Optimize Imports"를 돌리지 말고**(파일 전체가 흔들린다)
      빌드 경고에 뜬 것만 지운다.

`MediaViewerVideoItemBinding` import는 **남긴다** — `currentVideoBinding`이 계속 쓴다.

### 10.7 10단계 검증

**빌드**

- [x] `./gradlew assembleDebug` 통과
- [x] `./gradlew assembleRelease` — **R8 난독화·리소스 축소는 통과.** `packageRelease`는
      release 키스토어가 이 기계에 없어서 실패하는데, 10단계와 무관한 기존 환경 문제다

**앱 바** (11a 수용 기준 1~4)

- [x] 뷰어를 열고 탭했을 때 **위쪽에 반투명 검정 판이 없다**
- [x] **상태바 자리까지 사진이 올라온다.** 아이콘은 상태바를 침범하지 않는다
- [x] **파일 이름·`3 / 20`·`1.5×` 가 어디에도 없다.** 매니페스트 라벨("미디어 뷰어")도 안 뜬다
- [x] 뒤로가기와 `⋮` 뒤에 **원형 반투명 디스크**가 있고, **흰 사진 위에서도 보인다**
- [x] `⋮`를 여러 번 여닫아도 **원이 겹쳐서 진해지지 않는다** (§10.2.3의 중복 방지)
- [x] **화면을 돌린 뒤에도** 두 아이콘에 원이 그대로 있다

**하단 컨트롤** (수용 기준 5~8)

- [x] **아래쪽에 반투명 검정 판이 없다.** 컨트롤이 차지하는 높이가 눈에 띄게 줄었다
- [x] **톱니바퀴가 없다**
- [ ] 10초 뒤로 · 재생/일시정지 · 10초 앞으로 · 현재 위치 · 슬라이더 · 전체 길이가 **모두 있다**
      (§1 "달라진 곳" 2번에서 한 번 사라졌던 것들이다)
- [ ] 10초 뒤로/앞으로가 **각각 10초씩** 움직인다
- [ ] 재생 버튼에 **원**, 시간 텍스트에 **라운드 사각**이 깔려 있다
- [ ] 버튼을 누르면 리플이 돈다
- [x] `⋮` → 재생 속도가 동작하고, 고른 값에 라디오 표시가 붙는다
- [x] 컨트롤이 제스처 바에 가리지 않는다. **가로로 눕혀도** 화면 안에 있다

**슬라이더** (수용 기준 9, 10)

- [x] 지금보다 **눈에 띄게 잡기 쉽다.** 동그라미 크기는 그대로다
- [ ] 드래그하면 그 지점으로 이동하고, 끄는 동안 화면이 따라 움직인다
- [x] ⚠️ **슬라이더 근처에서 좌우로 스와이프했을 때 페이지가 넘어가는지.** 판정을 넓힌 만큼
      `ViewPager2`가 못 받는 영역이 커졌다. 컨트롤이 떠 있을 때만 해당한다

**로딩 표시** (수용 기준 11, 13)

- [x] 로컬 동영상으로 넘길 때 **가운데 스피너가 뜨지 않는다.** 썸네일·버퍼링 어느 쪽으로도
- [ ] 동영상 페이지를 빠르게 여러 번 오갈 때도 스피너가 깜빡이지 않는다
- [ ] **큰 4K 파일이나 SAF 경로**에서는 스피너가 제대로 뜨고, 재생이 시작되면 사라진다
- [ ] **사진 페이지의 스피너는 지금과 같다** — 바로 뜬다
- [x] 재생할 수 없는 동영상에서 안내 문구가 뜨고, **그 위에 스피너가 남지 않는다** (수용 기준 12)

**회귀** (수용 기준 14, 15)

- [x] 탭하면 앱 바와 컨트롤이 **같이** 나타나고 **같이** 사라진다
- [x] 아래로 스와이프해 닫기가 동작한다. **슬라이더를 아래로 끌 때는 닫히지 않는다**
- [ ] 좌우 넘김, 사진 확대, `⋮` → 삭제·공유·세부 정보가 모두 그대로다
- [x] 백그라운드에 갔다 와도 재생 위치가 유지된다

#### 10.7.1 에뮬레이터 검증 결과 (2026-08-29, Pixel_8 / API 36 / 420dpi)

테스트 데이터는 `/sdcard/MixTest` — 사진 7, 동영상 4, 깨진 파일 1.

**확인된 것**

- 슬라이더 판정 확대가 **실측으로 확인됐다.** 막대 중심에서 **45px 아래**를 눌렀더니
  위치가 00:36 → 00:12로 이동했다. 420dpi에서 예전 판정(26dp = ±34px)이면 빗나갔을 자리다
- 큰 파일(`IMG_6907.mov`, 138 MB)로 넘기는 **전환 도중 프레임을 잡았는데 스피너가 없다**
- 동영상 화면 위아래의 검은 띠는 **레터박스지 우리 스크림이 아니다.**
  1080×1920 영상이 1080×2400 화면에 들어가면 위아래 240px씩 남는데, 화면에서 잰 값과 정확히 맞는다

**안 본 것** — 10초 버튼이 정확히 10초씩 가는지, 버튼 리플, 사진 페이지 스피너,
큰 4K/SAF 파일에서 스피너가 제때 뜨는지, 사진 확대·삭제·공유.

**새로 나온 문제** — 11a §7의 6번으로 적었다.
**상태바의 시계·아이콘이 밝은 사진 위에서 흐리다.** 앱 바 배경이 상태바 자리까지
어둡게 깔아 주던 것이 없어져서다. 몰입 모드로 들어가면 상태바도 같이 사라지므로
**앱 바가 떠 있는 동안에만** 생긴다.

#### 10.7.2 실기기 검증 결과 (2026-08-29, SM-F971N / Android 17 / 420dpi)

**release 빌드를 이 저장소에서 처음 만들었다.** `signing.gradle`이 찾는 `signing.properties`가
없어서 `packageRelease`가 막히는데, **debug 키로 서명해서 통과시켰다.** debug 키의 비밀번호는
안드로이드가 공개해 둔 고정값이라 비밀이 아니고, 파일을 만들지 않고 환경변수로만 넘기면
워킹트리도 그대로다.

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" STORE_FILE="C:/Users/<사용자>/.android/debug.keystore" STORE_PASSWORD="android" KEY_ALIAS="androiddebugkey" KEY_PASSWORD="android" ./gradlew assembleRelease
```

> ⚠️ **배포용이 아니다.** debug 키로 서명한 APK는 스토어에 올릴 수 없다.
> 난독화·리소스 축소가 들어간 빌드를 기기에서 검증하는 용도다.

결과: `app-release.apk` **10.9 MB**. 기존 설치(같은 debug 키)에 덮어쓰기로 들어갔다.

**확인된 것** — 테스트 데이터는 기기의 `스윙` 폴더(644개).

- **R8 난독화 뒤에도 하단 컨트롤이 온전하다.** 10초 뒤/앞·재생/일시정지·현재 위치·슬라이더·
  전체 길이가 모두 살아 있다. 이번 단계에서 가장 위험했던 곳인데 통과했다
- 상·하단 검정 판 없음, 톱니바퀴 없음, 파일 이름·개수 없음
- **슬라이더 판정 확대가 실기기에서도 확인됐다.** 막대 중심에서 **45px 위**를 눌러
  00:05 → 00:20으로 이동했다 (예전 판정 ±34px 밖)
- **넘기는 전환 프레임에 스피너가 없다**
- 시간 텍스트의 **라운드 사각 칩**이 밝은 배경 위에서 또렷하다

**에뮬레이터에서 나온 상태바 문제(11a §7의 6번)는 이 기기에서 재현되지 않았다.**
One UI가 상태바 아이콘 색을 배경에 맞춰 바꾼다 — 밝은 천장 위에서는 검은 글자,
검은 레터박스 위에서는 흰 글자로 나온다. **AOSP 에뮬레이터 쪽 동작으로 보인다.**

**안 본 것** — 아이폰 `.mov` 재생, 사진 페이지, 10초 버튼의 정확한 이동량, 버튼 리플,
접었다 펼 때의 화면 전환.

**10단계 이후에 나온 것** — 이 단계를 실기기에서 쓰다가 성능 문제가 드러났다.
조사와 수정은 [13번](13-viewer-performance.md)에 있다. 원인은 10단계가 아니라
**사진을 원본 해상도로 디코딩하던 기존 동작**이었다.

> **커밋 메시지 초안**
> `뷰어에서 검은 배경과 안 쓰는 표시를 걷어내기`

---

## 10. 예상 작업량

| 단계 | 신규 파일 | 수정 파일 | 난이도 |
|---|---|---|---|
| 0 | 0 | 1 | 하 |
| 1 | 0 | 10 (이름 변경) | 하 (기계적) |
| 2 | 2 | 4 | **중상** (어댑터 뷰 타입 분리 + 진입 분기) |
| 3 | 1 | 1 | **상** (플레이어 수명과 페이지 전환) |
| 4 | 0 | 2 | 중 |
| 5 | 1 | 1 | 중 |
| 6 | 0 | 3 | 하 |
| 7 | 2 | 3 | **중상** (값 출처가 두 곳) |
| 8 | 0 | 3 | 중 |
| 9 | 0 | 1 (문서) | — |
| 10 | 6 | 4 | **중** (Media3 컨트롤 레이아웃 교체가 유일한 위험) |

3단계가 가장 어렵다. `ViewPager2` 안에서 현재 페이지의 뷰를 꺼내고, 페이지 전환과 생명주기가
겹치는 순간마다 플레이어를 정확히 붙였다 떼야 한다. **버그가 나면 대부분 여기다.**

2단계가 그 다음인데, 어려워서가 아니라 **건드리는 곳이 흩어져 있어서**다 —
어댑터, 프래그먼트, 파일 목록의 진입 분기가 각각 다른 파일이다.

## 11. 이 계획에서 의도적으로 미룬 것

- **아래로 스와이프 닫기** — `feature/swipe-down`과 합친 뒤에 정한다 (11번 D12)
- **원격·아카이브 경로 재생** — 커스텀 `DataSource`가 필요하다 (11번 D3)
- **오디오 포커스를 되찾을 때 자동 재개를 막는 것** — 9단계에서 실제 동작을 보고,
  기획서와 다르면 기획서를 고친다 (§5.3)
- **로컬 파일에 `file://`를 직접 쓰는 최적화** — `fileProviderUri`로 통일했다.
  9단계의 7번에서 느리면 그때 (§3.1)
- **프레임 단위 이동·구간 반복** — 11번 D10
- **자막, 음소거, 반복 재생, PIP** — 11번 §2 비목표
- **테스트 소스셋 도입** — 이 작업 범위 밖. 검증은 수동으로 한다
- **사진에서 파일 이름을 볼 수단** — 10단계가 앱 바 제목을 없애면서 사라진다.
  실제로 불편한지 써 보고 별건으로 정한다 (11a §7의 1번)
