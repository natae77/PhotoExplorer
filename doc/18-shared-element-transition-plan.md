# 18. 그리드 ↔ 뷰어 공유 요소 전환 구현 계획

미디어 모드에서 사진을 열면 **타일이 커지면서 뷰어가 되고**, 뷰어를 닫으면
**미디어가 원래 타일 자리로 줄어들며 들어간다.** 지금의 좌우 슬라이드를 대체한다.

- 작성일: 2026-09-04 / 최종 개정: **2026-09-12 (11차)**
- 프로젝트: **PhotoExplorer** (`natae77/PhotoExplorer`, `zhanghai/MaterialFiles` fork)
- 기준 소스: `feature/media-view-mode` (`8c3e67d2` 시점)
- 상태: **11차 최초 타일 빈칸·깜박임 핵심 경로 구현 및 3인 검토 보완 계획 반영**

**개정 이력** — 이 문서는 개정할 때 새 문서를 만들지 않고 **본문을 직접 고친다.**

| 개정 | 날짜 | 무엇이 바뀌었나 |
|---|---|---|
| 11차 | 2026-09-12 | A에서 열어 B로 넘긴 뒤 복귀하면 최초 타일 A가 빈칸으로 남거나 한 번 깜박이는 결함을 실제 구현에 맞춰 반영했다. 일반 `alpha`와 별개인 `transitionAlpha`를 포함해 프레임워크가 조작한 표시 상태를 정상화하고, B로 반환할 때 A의 `transitionName`을 잠시 떼어 후속 탐색·캡처에서 A가 다시 선택되지 않게 한다. 3인 검토 결과 반환 transition 종료 훅과 RecyclerView 재활용 정규화가 후속 구현으로 남았다 |
| 10차 | 2026-09-12 | 좌우 이동 후 swipe-down 첫 프레임에 최초 타일이 비어 있다가 반환 직전에 현재 타일과 빈칸이 교환되는 결함을 반영했다. 최신 타일 READY 전에 최초/직전 타일을 복원하고 현재 썸네일만 숨겨, 폴더를 드러내기 전에 빈칸을 현재 위치로 옮기도록 했다 |
| 9차 | 2026-09-11 | 에뮬레이터에서 최초 타일 A로 연 뒤 B로 넘겨 swipe-down하면 복귀 애니메이션이 끊기는 결함을 재현했다. 최초 `ActivityOptions`의 이름 A를 B로 바꿔 버린 caller callback이 원인이므로 **이름은 끝까지 A로 유지하고 대상 View만 B 타일로 교체**하도록 바로잡았다. 페이지 IDLE 때 아래 폴더를 현재 타일 위치로 미리 스크롤하는 세션·sequence number·pre-draw READY 계획과 레이스/폴백 검증을 추가했다 |
| 8차 | 2026-09-11 | 7차 접합 계획을 구현했다. 반투명 Immersive 창의 검정 배경 알파, 내부 폴더 진입 표식, 드래그 콜백, 종료 상태기·입력 잠금을 연결하고 `PixelCopy` 준비 중 뷰 재생성도 안전하게 초기화했다. 빌드·단위 테스트와 에뮬레이터 핵심 경로 결과를 §5.7에 기록했다 |
| 7차 | 2026-09-11 | 10번 5차 요구를 접합했다. 작은 이동량 임계값으로 아래 스와이프를 확정하면 뷰어 창의 검정 `windowBackground` 알파를 낮춰 **실제 폴더 화면을 사진 아래에 선노출**하고, 사진은 누적된 이동량을 따라잡은 뒤 손가락과 1:1로 움직인다. 드래그 중 배경 노출과 손을 놓은 뒤의 공유 요소 복귀를 분리했다(§3.6.1, D27) |
| 6차 | 2026-09-11 | 문서 번호를 14 → **18**로 바로잡았다. 파일 목록 UI와 통합하면서 동영상 출력이 `TextureView`에서 HDR용 `SurfaceView`로 바뀐 것을 반영해, 현재 프레임 획득을 **`PixelCopy` 비동기 복사**로 수정했다(§3.5·§3.6, D26). 별도 통합 검증 문서의 결과와 미검증 범위를 §5.5에 합쳤다 |
| 5차 | 2026-09-04 | **구현하면서 실기(에뮬레이터)에서 드러난 것들.** ⚠️ **§3.3이 지목한 전환 종료 훅이 틀렸다 — `onSharedElementEnd` 는 전환이 끝날 때가 아니라 시작하기 전에 불린다**(§3.3, D22). 진입에도 §3.6 (1)과 같은 **같은 그림 둘** 문제가 있다는 것을 빠뜨렸다(§3.3, D23). **Coil 이 하드웨어 비트맵을 주면 프레임워크 스냅샷이 앱을 죽인다**(§3.5, D24). 폴백에서 페이지를 감추면 안 된다는 것(§3.7 F1·F2, D25) |
| 4차 | 2026-09-04 | 3차 소스 대조 리뷰 반영. **폴백 가드가 진입에서 먼저 걸려 열기 전환을 죽이던 것**을 방향 구분으로 고쳤다(§3.2, D20). **enter 콜백 등록 자리가 한 박자 늦어** 진입에만 안 걸리던 것을 액티비티 `onCreate` 로 옮겼다(§3.2, D19 정정). 자리를 못 박지 않았던 셋 — 전환 종료 훅, 로딩 완료 신호, exit 콜백 등록 자리 — 을 정했다(§3.3·§3.5·§3.4) |
| 3차 | 2026-09-04 | **§0 표와 D11이 틀렸다 — 툴바 화살표는 `finish()` 로 가서 복귀 전환을 돌지 않는다**(D17). "종료 직전" 훅이 코드에 없어 자리를 만들었다(§3.6). F4·F5 폴백이 **빈 사각형을 날려 보내던 것**을 뷰어 쪽 차단으로 고쳤다(D18). 스냅샷을 `background` 로도 읽는다(§3.5) |
| 2차 | 2026-09-04 | **사진이 `openMediaViewer()` 를 타지 않는다는 것을 놓쳤다**(§3.0, D14). `transitionImage` 를 `GONE` 으로 두면 잡히지 않고(D16), 뷰어 쪽 `transitionName` 을 바꾸면 복귀가 깨진다(D15) |
| 1차 | 2026-09-04 | 최초 작성 |

이 문서는 10번처럼 기획과 계획을 함께 담는다. 새 파일 1개 + 고친 파일 8개 규모라
두 문서로 나누면 오가며 읽는 비용만 늘어난다(10번 §7과 같은 판단).

## 0. 지금은 어떻게 되어 있나

뷰어는 **별도 액티비티**다. 지금 보이는 좌우 슬라이드는 **테마 기본 액티비티 전환**이고,
공유 요소 전환은 프로젝트 어디에도 없다 — `transitionName` 도, `makeSceneTransitionAnimation` 도 없다.

⚠️ **닫는 경로 셋이 같은 길로 모이지 않는다.**

| 경로 | 지금 어디로 가나 | 복귀 전환이 도는가 |
|---|---|---|
| 아래로 끌기 | `SwipeDownDismissLayout.onDismiss` → `onBackPressedDispatcher.onBackPressed()` | ○ |
| 뒤로가기 | 시스템 → 같은 디스패처 | ○ |
| **툴바 위 화살표** | `onOptionsItemSelected` 에 `android.R.id.home` 분기가 **없다** → `super` → [AppActivity.onSupportNavigateUp():33](../app/src/main/java/me/zhanghai/android/files/app/AppActivity.kt#L33) 의 **`finish()`** | **✗** |

`Activity.onBackPressed()` 는 API 21부터 `finishAfterTransition()` 을 부르지만
**`finish()` 는 복귀 전환을 돌리지 않는다.** 그대로 두면 수용 기준 5가 화살표에서만 깨진다.
`MediaViewerActivity` 에 `onSupportNavigateUp()` 을 재정의해 디스패처로 보낸다(D17, 4단계).

⚠️ **"종료 직전"에 걸 훅도 지금은 없다.** 뷰어에는 `OnBackPressedCallback` 도
`onBackPressed` 재정의도 없다(`onBackPressedDispatcher` 는 아래로 끌기에서 **부르기만** 한다).
§3.6에서 그 자리를 만든다.

## 1. 목표 / 비목표

**목표**

1. 미디어 모드에서 타일을 누르면 **그 타일이 커지면서** 뷰어가 된다.
2. 뷰어를 닫으면 **지금 보고 있는 미디어**가 그 파일의 타일 자리로 줄어들며 들어간다.
   열 때와 다른 사진을 보고 있어도 그 사진의 타일로 간다.
3. 좌우로 넘겨 현재 타일이 화면 밖이 되면 **페이지가 정착한 시점에 아래 그리드를 미리 그 자리로
   스크롤한다.** 아래로 끌기 시작 전부터 현재 타일이 보여야 한다.
4. 사진(일반·대용량)과 동영상에서, **닫는 경로 셋 모두에서** 똑같이 동작한다.
5. 짝이 될 타일이 없는 경우에는 **조용히 지금까지의 전환으로 돌아간다**(§3.7).

**비목표 (이번 범위 아님)**

- 리스트·바둑판 모드. 작은 아이콘에서 전체 화면으로 퍼지는 것은 오히려 산만하다.
- 확대된 사진에서의 전환. 확대 중 뒤로가기는 폴백으로 간다(§3.7).
- 뷰어 안 페이지 전환 애니메이션(`DepthPageTransformer`) 변경.
- 전환 켜기/끄기 설정 항목.
- Android 13+ 예측형 뒤로가기 대응. §3.6의 항상 켜진 `OnBackPressedCallback` 이 그 애니메이션을
  끄지만, 지금도 쓰고 있지 않으므로 회귀는 아니다. **알고 간다.**

## 2. 동작 정의

| 상황 | 동작 |
|---|---|
| 미디어 모드에서 타일 탭 | 타일이 전체 화면으로 커지며 뷰어. 배경은 함께 어두워진다 |
| 뷰어에서 뒤로가기 / 위 화살표 / 아래로 끌기 | 현재 미디어가 그 파일의 타일로 줄어들며 들어간다 |
| 뷰어에서 좌우로 넘긴 뒤 닫기 | **넘긴 그 파일의 타일**로 들어간다. 화면 밖이면 페이지 IDLE 때 아래 그리드를 미리 스크롤 |
| 아래로 끌던 중에 놓아 닫힘 | 끌던 **그 자리·그 크기에서 이어서** 타일로 들어간다 (§3.6) |
| 사진을 확대한 채로 뒤로가기 | 폴백 — 지금까지의 전환 |
| 뷰어에서 파일을 삭제한 뒤 닫기 | 폴백 — 돌아갈 타일이 없다 |
| 다른 앱이 `VIEW image/*` 로 뷰어를 열었을 때 | 폴백 — 짝지을 그리드가 없다 |
| 리스트·바둑판 모드에서 (동영상을) 열었을 때 | 폴백 — 열 때도 닫을 때도 (§3.7 F1) |

## 3. 설계

### 3.0 ⚠️ 먼저 — **사진은 지금 우리 뷰어를 직접 열지 않는다**

[FileListFragment.kt:1320-1334](../app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt#L1320)
의 `openFile()` 은 이렇게 갈린다.

```
isPlayableVideo  → openMediaViewer()      ← 우리 액티비티, 명시적 인텐트. 모든 뷰 모드에서
그 밖(=사진)     → openFileWithIntent()   ← 암시적 VIEW 인텐트, 기본 앱이 받는다
```

사진은 `image/*` 암시적 인텐트로 나가고, `MediaViewerActivity` 의 인텐트 필터에 걸려
**되돌아 들어오는** 구조다. 그래서 §3.7 F2가 이론이 아니라 실제 경로이기도 하다.

이대로 두면 **동영상만 새 전환을 타고 사진은 예전 그대로**가 되어 수용 기준 2가 성립하지 않는다.
게다가 사진 쪽은 다른 앱이 기본 뷰어일 수 있어 `setResult` / `onActivityReenter` 계약 자체가 성립하지 않는다.

**결정(D14) — 미디어 모드에서는 사진도 `openMediaViewer()` 로 명시 라우팅한다.**
리스트·바둑판 모드는 지금 그대로 둔다.

```kotlin
// openFile() 안, 기존 isPlayableVideo 분기 자리
if (file.path.isPlayableVideo
    || (viewModel.viewType == FileViewType.MEDIA && file.mimeType.isImage)) {
    openMediaViewer(file)
    return
}
```

`maybeAddMediaViewerExtras` 는 이미 `mimeType.isImage` 를 받아 주므로 목록 구성은 그대로 된다.
목록을 못 만들었을 때 `openFileWithIntent` 로 떨어지는 폴백도 그대로 살린다.

⚠️ **동영상은 반대다 — 모든 뷰 모드에서 `openMediaViewer()` 를 탄다.** 그래서 열 때뿐 아니라
**닫을 때도** 미디어 모드인지 확인해야 한다(§3.7 F1).

**부작용을 알고 간다.** 다른 갤러리 앱을 `image/*` 기본으로 정해 둔 사용자도 **미디어 모드에서는**
PhotoExplorer 뷰어를 보게 된다. 미디어 모드가 갤러리 UX를 지향하므로 의도한 동작이다.

### 3.1 공유 요소는 **뷰어 쪽 전용 `ImageView` 한 장**

뷰어 페이지는 세 갈래다 — `PhotoView`(일반 사진), `SaveStateSubsamplingScaleImageView`(대용량 사진),
`PlayerView`(동영상). 이 셋을 그대로 공유 요소로 쓰면 안 된다.

⚠️ **`SubsamplingScaleImageView` 는 `ImageView` 가 아니다.** 그래서 그리드 타일의
`centerCrop` 을 뷰어의 `fitCenter` 로 이어 주는 `ChangeImageTransform` 이 통하지 않는다.
`@android:transition/move`(공유 요소 기본 전환)의 네 조각 중 하나가 통째로 빠지는 셈이라,
대용량 사진에서만 잘려 보이는 전환이 된다. `PlayerView` 도 마찬가지다.

그래서 **`media_viewer_fragment.xml` 에 전환 전용 `ImageView` 를 한 장 둔다.**

```
FrameLayout                       (media_viewer_fragment.xml)
├─ ViewPager2                     페이지들 — 손대지 않는다
├─ PlayerControlView
├─ appBarLayout
└─ transitionImage   ← 새로 추가. match_parent, fitCenter, 항상 VISIBLE
```

- `ImageView` ↔ `ImageView` 라 `ChangeImageTransform` 이 정상 동작한다. **이것이 이 설계의 요점이다.**
- 페이지 안이 아니라 **프래그먼트 레이아웃**에 두므로 페이지 재활용과 무관하다.
  페이지마다 두면 `offscreenPageLimit = 1` 때문에 한 화면에 셋이 생긴다.
- 세 갈래가 한 갈래로 줄어든다. 전환 로직을 한 곳에서만 본다.

⚠️ **`GONE` 으로 두면 안 된다.** `ChangeImageTransform.captureValues()` 와 `ChangeBounds` 는
`visibility != VISIBLE` 이면 그냥 빠져나온다. 공유 요소로 지도에 넣어도(§3.2)
**캡처가 비어 전환이 서지 않는다.**

> (`ViewGroup.findNamedViews()` 도 `VISIBLE` 만 훑지만, 뷰어 쪽은 `transitionName` 을 쓰지 않으므로
> (D15) 그 경로는 애초에 타지 않는다. 진짜 이유는 캡처 쪽이다.)

**대신 `drawable` 을 비워 둔다.** 항상 `VISIBLE` 이되 그릴 것이 없으면 아무것도 그리지 않는다.
클릭 가능하지 않은 `ImageView` 는 터치를 소비하지 않으므로 아래의 페이지·툴바 조작을 막지 않는다.

**⚠️ `appBarLayout` 과의 앞뒤는 5단계에서 눈으로 정한다.** 맨 뒤에 두면 진입 후 로딩이 끝날 때까지
툴바 아이콘이 그림 뒤에 가린다. 앱바가 투명하고 아이콘이 둘뿐이라 `appBarLayout` **앞**에
두는 선택지도 있다.

동영상 페이지의 `thumbnailImage` 와 역할이 겹쳐 보이지만 다르다. 그쪽은 페이지 안에 있고
첫 프레임이 나올 때까지 자리를 지키는 용도다(12번 2.3.1). 이 한 장은 프래그먼트 위에 있고
전환 중에만 그림을 갖는다.

### 3.2 짝짓기 — 이름은 **그리드 쪽에만**, 콜백은 **방향을 가려서**

`transitionName` 은 진입할 때 `ActivityOptions` 에 실린 것이 **끝까지 기준**이 된다.
복귀 시 `ActivityTransitionState.startExitBackTransition()` 도 그 이름으로 짝을 찾는다.
**그래서 뷰어 쪽 뷰의 이름을 도중에 바꾸면 복귀가 깨진다.**

9차에서 이것이 caller 쪽에도 똑같이 적용된다는 것을 실제 결함으로 확인했다. A 타일에서 열어 B로
넘겨도 프레임워크의 프로토콜 키는 **최초 이름 A**다. B는 복귀할 **대상 View를 고르는 경로**일 뿐,
이름을 B로 바꾸라는 뜻이 아니다. 양쪽 콜백의 성공 경로에서 `names`는 건드리지 않고
`sharedElements[A] = tileB`만 설정한다. 이름 목록과 map을 함께 비우는 것은 타일이 없는 폴백뿐이다.

**결론(D15) — 뷰어의 `transitionImage` 에는 `transitionName` 을 붙이지 않는다.**
대신 `onMapSharedElements` 로 지도에 직접 넣는다.

#### 3.2.1 ⚠️ 가드는 **복귀에만** 걸어야 한다

프레임워크가 `onMapSharedElements` 를 부르는 자리는 `ActivityTransitionCoordinator.viewsReady()` 이고,
진입 쪽에서는 `EnterTransitionCoordinator` 가 **데코뷰의 첫 pre-draw** 에서 그것을 돌린다.
`onSharedElementStart` 는 그보다 한참 뒤(`startSharedElementTransition`)다.

```
[열 때]
onMapSharedElements    ← 이때 transitionImage.drawable 은 당연히 null. 아직 아무도 안 채웠다
onSharedElementStart   ← §3.3이 그림을 채우는 자리
```

그래서 "**그림이 없으면 지도를 비운다**"(§3.7 F4·F5)를 방향 구분 없이 걸면
**진입에서 먼저 걸려 공유 요소가 하나도 없는 채로 열린다.** 수용 기준 1이 100% 실패하고,
증상은 §7 최상위 위험("스냅샷이 비어 온다")과 구분이 안 된다.

**종료 훅(§3.6)에서 `isReturning` 을 세우고, 그때만 가드를 적용한다.**
그리드 쪽이 `Opening / Returning / ReturnBlocked`로 방향을 가리는 것(§3.4.6)과 같은 문제이고 같은 해법이다.

```kotlin
override fun onMapSharedElements(names: MutableList<String>, sharedElements: MutableMap<String, View>) {
    val image = transitionImage ?: return
    if (isReturning && image.drawable == null) {
        // 보낼 그림이 없다. 빈 사각형이 타일로 날아가는 것을 막는다 (F4·F5)
        names.clear()
        sharedElements.clear()
        return
    }
    val name = names.firstOrNull() ?: return
    sharedElements[name] = image
}
```

#### 3.2.2 ⚠️ enter 콜백은 **액티비티 `onCreate` 에서** 등록한다

`EnterTransitionCoordinator` 는 **생성자에서** `activity.mEnterTransitionListener` 를 붙잡아 둔다.
나중에 `setEnterSharedElementCallback` 을 불러도 **이미 만들어진 코디네이터는 바뀌지 않는다.**
그 생성 시점은 `ActivityTransitionState.setEnterActivityOptions()` — `onCreate` 직후다.

반면 AndroidX 의 `Fragment.onActivityCreated` 는 `FragmentActivity.onStart()` 에서
디스패치된다(`fragment-1.8.9` `FragmentActivity.java:342`). **한 박자 늦다.**

⚠️ **고약한 것은 복귀만 우연히 동작한다는 점이다.** `startExitBackTransition()` 은
`finishAfterTransition()` 시점에 리스너를 **다시 읽어** return 코디네이터를 만들기 때문이다.
증상이 "닫기는 되는데 열기만 안 된다"로 나와 §3.2.1의 결함과 겹쳐 보인다.

그래서 **`MediaViewerActivity.onCreate` 의 `commit()` 직후에 등록하고, 뷰는 콜백 안에서 늦게 찾는다.**
콜백 본문은 첫 pre-draw 이후에 불리므로 그때는 프래그먼트 뷰가 이미 있다.

```kotlin
// MediaViewerActivity.onCreate
ActivityCompat.setEnterSharedElementCallback(this, object : SharedElementCallback() {
    private val transitionImage: ImageView?
        get() = fragment.transitionImageOrNull   // binding 미초기화면 null
    …
})
```

`FileListActivity` 처럼 `fragment` 필드를 둔다 —
지금 `MediaViewerActivity` 는 프래그먼트를 만들어 `commit` 하고 참조를 버린다
([MediaViewerActivity.kt:26](../app/src/main/java/me/zhanghai/android/files/viewer/media/MediaViewerActivity.kt#L26)).
**D19를 뒤집는다.** 3차의 "프래그먼트에서 늦게 등록하면 된다"는 결론이 틀렸다 —
등록을 늦출 것이 아니라 **뷰 찾기를 늦춰야** 한다.

#### 3.2.3 그리드 쪽 이름

**경로 문자열**이면 충분하다.

```kotlin
fun mediaTransitionName(path: Path): String = "media:$path"
```

`FileListAdapter.bindFileViewHolder` 에서 `holder.thumbnailImage.transitionName` 을
**`viewType == MEDIA` 일 때만** 설정하고, 아니면 `null` 로 둔다.

⚠️ **재활용 때문이 아니라 `bindFileViewHolder` 가 세 모드 공용이기 때문이다.**
(`getItemViewType` 이 `viewType.ordinal` 을 돌려주고 `RecycledViewPool` 은 viewType별로 나뉘므로
미디어 홀더가 리스트 모드에서 재활용되는 일은 없다. 조건 없이 쓰면 리스트·바둑판 타일에도
이름이 붙는 것이 문제다.)

### 3.3 열 때 (그리드 → 뷰어)

1. `FileListFragment` 가 지금 눌린 파일의 타일 `ImageView` 를 찾는다.
   **어댑터 위치는 이미 있는 것을 쓴다** — `FileListAdapter.filePositionMap` 이
   `Path → 전체 어댑터 위치` 를 들고 있다([FileListAdapter.kt:95](../app/src/main/java/me/zhanghai/android/files/filelist/FileListAdapter.kt#L95)).
   ⚠️ **미디어 모드 목록에는 `FileListItem.Date` 타일이 섞여 있어 파일 인덱스 ≠ 어댑터 위치**이므로
   직접 세면 안 된다. 지금은 `private` 이니 읽기용 접근자를 하나 연다.
   그 위치로 `recyclerView.findViewHolderForAdapterPosition()` → `thumbnailImage`.
   **닫을 때도 같은 조회가 필요하므로 헬퍼 하나로 만든다** — `mediaTileImageFor(path): ImageView?`.
   덕분에 `FileListAdapter.Listener` 인터페이스는 건드리지 않는다.
2. `openMediaViewer` 에서 미디어 모드일 때만
   `ActivityOptionsCompat.makeSceneTransitionAnimation(activity, tile, name)` 을 만든다.
   ⚠️ **결과를 받는 형태로 띄워야 한다**(§3.4). 지금의 `startActivitySafe` 대신
   `ActivityResultLauncher` 를 쓴다. 옵션을 받는 `launchSafe` 오버로드가 이미 있다
   ([ActivityResultLauncherExtensions.kt:31](../app/src/main/java/me/zhanghai/android/files/util/ActivityResultLauncherExtensions.kt#L31)).
3. 뷰어의 `onSharedElementStart` 에서 `transitionImage` 에 **떠나온 타일의 그림**을 채운다(§3.5).

#### 3.3.1 ⚠️ 들어올 때도 페이지를 감춰야 한다 (5차)

§3.6 (1)은 **닫을 때** 같은 그림이 둘 보이는 것을 막는다. **열 때도 똑같다** — 이것을 4차까지 빠뜨렸다.

뷰어 페이지는 **첫 프레임부터 최종 크기로, 불투명하게** 깔려 있다. 그래서 타일이 커지는 동안
그 아래에 이미 같은 사진이 최종 위치에 놓여 있고, 화면에는 **자라는 사각형 + 제자리의 사진**이
함께 보인다. 자라는 것이 눈에 띄지 않아 "전환이 안 도는 것처럼" 보이기까지 한다.
수용 기준 1과 9가 함께 깨진다.

**`onMapSharedElements`(진입) 에서 `viewPager.alpha = 0f`, 전환이 끝나면 되돌린다**(D23).

⚠️ **`GONE`·`INVISIBLE` 이 아니라 알파다.** 감춘 동안에도 페이지가 측정·레이아웃되고 로딩이
끝나야 `currentPageContent()` 가 `READY` 를 답한다. 그렇지 않으면 영영 되살리지 못한다.

#### 3.3.2 언제 비우는가 — ⚠️ **4차가 지목한 훅은 틀렸다** (5차)

"전환이 끝나고, 그 아래 페이지가 준비되면" 페이드아웃 후 `drawable` 을 비우고 pager 를 되살린다.
⚠️ **`GONE` 으로 바꾸지 않는다**(§3.1).

| 신호 | 무엇을 쓰나 |
|---|---|
| 전환 종료 | **`window.sharedElementEnterTransition` 에 건 `TransitionListener.onTransitionEnd`** (D22) |
| 페이지 준비 | **§3.5의 `currentPageContent()`.** 그림을 얻는 통로와 같은 것을 쓴다 |

⚠️ **`SharedElementCallback.onSharedElementEnd` 는 전환 종료가 아니다.**
프레임워크는 애니메이션을 **돌리기 전에**, 최종 상태를 배치해 놓고 "재어 보라"는 뜻으로 이것을
부른다(`onSharedElementStart` 와 한 쌍이다). 여기서 페이드아웃과 pager 복원을 하면
**비행이 시작되는 순간에 최종 크기의 사진이 드러나** §3.3.1의 결함이 그대로 재현된다.
실측: `onSharedElementStart` 03.466 → 실제 종료 03.793, **327 ms** 차이.

`PhoneWindow` 는 이 전환을 **창마다 새로 인플레이트**하므로 리스너가 액티비티보다 오래 살지 않는다.

⚠️ **`ViewPager2` 는 처음 연 페이지에 대해서도 `onPageSelected` 를 쏜다.** 그것도 전환 도중에.
"페이지가 바뀌었으니 진입은 끝났다"는 식의 안전망을 두려면 **진입 중인지 표시하는 플래그로
막아야 한다.**

**전환이 시작조차 하지 않을 수 있다.** 지도에 실렸다고 도는 것이 아니다. pager 를 감춘 채
`onSharedElementStart` 를 기다리는 동안은 **검은 화면**이므로 시간 제한을 짧게(0.3초쯤) 두고,
시작이 확인되면 그때 넉넉하게(1.5초쯤) 늘린다.

배경이 튀는 문제는 없다. 프레임워크(`EnterTransitionCoordinator`)가 들어오는 액티비티의
`windowBackground` 알파를 전환에 맞춰 올려 준다. 뷰어 테마의 배경은 `ColorDrawable`(검정)이라
알파가 먹는다.

### 3.4 닫을 때 (뷰어 → 그리드) — 현재 타일을 미리 준비한다

두 값의 역할을 섞지 않는다.

- **최초 진입 이름**: 공유 요소 코디네이터가 끝까지 사용하는 불변 프로토콜 키.
- **현재 경로**: 지금 어느 타일 View로 돌아갈지를 고르는 가변 값.

#### 3.4.1 현재 경로는 종료 결과로 계속 보관한다

`MediaViewerFragment`는 페이지가 확정될 때마다, 그리고 종료 훅에서
`activity.setResult(RESULT_OK, Intent().apply { extraPath = currentPath })`를 갱신한다.
⚠️ **위치(index)가 아니라 경로**를 돌려준다. 뷰어 안에서 파일을 삭제하면 목록이 밀려 위치가 어긋난다.
전환할 수 없는 상태면 **`setResult(RESULT_CANCELED)`** 로 되돌린다(§3.7 F4·F5).

#### 3.4.2 페이지 IDLE 때 아래 폴더를 선행 정렬한다 (9차)

종료 시점의 `onActivityReenter()`에서 처음 스크롤하면 swipe-down 중에는 사용자가 떠나온 옛
뷰포트가 보인다. MEDIA 모드 내부 실행에서는 뷰어가 불투명한 동안 미리 준비한다.

```
FileList open:  sessionId 생성 + listener 등록 + intent 전달
Viewer IDLE:    request(sessionId, currentPath) → coordinator가 sequence 발급
FileList:       이미 보임 → READY
                화면 밖 → scrollToPositionWithOffset → pre-draw 확인 → READY
                타일/화면 없음 → UNAVAILABLE
Viewer:         같은 sessionId + sequence + path의 READY만 수락
```

- 새 프로세스 내부 `MediaViewerViewportCoordinator`가 양쪽을 연결한다. `sessionId`는 내부 MEDIA
  실행마다 새로 만들고, 목록·바둑판 동영상 및 외부 `VIEW`에는 넣지 않는다. 전역 현재 액티비티나
  경로 하나만 두면 멀티 윈도우·복수 task가 섞이므로 금지한다.
- coordinator는 세션별 sequence number를 원자적으로 발급하고 최신 요청과 응답을 replay한다.
  번호를 뷰어 필드에서 증가시키면 재생성 후 0으로 되감겨 이전 요청보다 영원히 오래된 것으로 처리될 수 있으므로 금지한다.
  FileList listener는 `onViewCreated`부터 `onDestroyView`까지 등록해 위의 뷰어 때문에 PAUSED인 동안에도
  요청을 받으며, RESUMED에만 묶지 않는다. UI owner의 장기 강한 참조는 남기지 않는다. 새 뷰어 실행과
  정상 종료에서는 세션을 폐기하고, 구성 변경에서는 같은 token으로 재등록해 최신 요청을 즉시 replay한다.
  프로세스 복원 뒤 양쪽이 다시 연결되지 않으면 검정 폴백이다.
- `publishCurrentViewportIfIdle()` 하나를 두고 최초 pager pre-draw, **`onPageScrollStateChanged(IDLE)`**,
  paths 삭제·replace와 currentItem 보정 직후에 호출한다. 빠른 플링 중 지나가는 `onPageSelected`마다
  폴더를 스크롤하지 않고, 이미 IDLE인 채 같은 index의 경로만 바뀌는 경우도 놓치지 않는다.
- 새 `isMediaTileFullyVisible(path)`가 타일 전체 사각형이 RecyclerView의 padding을 제외한 뷰포트
  안에 든 것을 확인하면 스크롤 없이 즉시 READY다. holder가 붙어 있어도 일부가 잘렸으면 화면 밖으로
  보고, `smoothScroll` 대신 `scrollToPositionWithOffset(position, mediaReturnScrollOffset())` 한 번만 호출한다.
- viewport READY는 단순 스크롤 호출 완료가 아니다. pre-draw에서 위치를 다시 조회하고 holder가 현재
  경로에 해당하며 attach·layout 상태이고, 크기가 0보다 크며 전체 사각형이 뷰포트 안인지 확인한 결과다.
  **타일의 자리와 크기만**
  요구하고 Coil drawable 로딩은 기다리지 않는다. 제한된 재시도와 별도 준비 시간 제한 후 UNAVAILABLE이다.
- A→B→C처럼 빠르게 바뀌면 B의 늦은 pre-draw가 C를 덮어쓰면 안 된다. 요청·pre-draw·응답 양쪽에서
  `sessionId + sequence + path`가 모두 최신인지 다시 검사하고, 오래된 콜백은 no-op으로 버린다.
  `returnMappingState`는 종료 전환 전용으로 남기고 이 선행 상태와 공유하지 않는다.
- adapter data·정렬·filter·span/layout 변화와 view 파괴는 기존 READY를 준비 중 상태로 무효화한다.
  최신 요청을 새 위치에서 다시 처리하고, 목록이 아직 로딩 중이면 곧바로 UNAVAILABLE로 굳히지 않고
  제한 시간 안에서 데이터 갱신과 pre-draw를 기다린다. `AdapterDataObserver` 또는 목록 갱신 완료 훅에서
  현재 타일이 여전히 완전히 보이는지 재검증한다.

#### 3.4.3 READY 전에 빈칸을 현재 타일로 옮긴다 (10차)

프레임워크는 뷰어를 연 최초 타일 A를 원본 공유 요소로 숨겨 둔다. A에서 B로 넘긴 뒤 스크롤만
미리 맞추면 swipe-down 첫 프레임에는 A가 빈칸이고, 반환 전환이 시작될 때 A가 갑자기 채워진 뒤
B가 비는 교환이 그대로 보인다. 폴더 준비는 위치뿐 아니라 **어느 타일을 비워 둘지**까지 끝내야 한다.

- MEDIA 공유 요소 실행을 시작할 때 `mediaViewportHiddenPath = A`로 둔다.
- 최신 요청의 타일 B가 attach·layout된 것을 확인한 뒤, 기존 hidden path A의 썸네일을 다시 보이고
  B의 썸네일은 `visibility`가 아니라 `alpha = 0`으로 숨긴다. View의 자리와 반환 전환 캡처 가능 상태를
  유지한 채, 이 교환은 검정 window background가 불투명한 동안 끝낸다.
- 타일의 `View` 참조를 장기 보관하지 않고 경로만 보관한다. RecyclerView가 holder를 재활용하면 bind
  시 현재 hidden path인지 다시 적용하고, 다음 READY 검사에서도 실제 holder에 같은 상태를 적용한다.
- 아이콘·배지·날짜 타일까지 통째로 숨기지 않는다. 움직이는 미디어와 중복되는 썸네일 이미지만 숨겨
  B의 자리는 유지한다.
- `READY`는 스크롤과 이 빈칸 교환이 모두 끝난 뒤에만 보낸다. 따라서 swipe-down이 허용되는 순간에는
  A가 채워져 있고 B 자리만 비어 있다.
- 취소로 뷰어에 머물면 B를 계속 비워 둔다. C로 넘기면 B를 복원하고 C를 비운다. 정상·일반 종료,
  세션 폐기, 목록 교체, 화면 파괴에서는 남은 hidden path를 복원한다.
- 최종 return callback은 숨겨진 현재 타일도 대상 View로 찾을 수 있어야 한다. `isVisible`을 요구하는
  열기용 조회와, 숨겨진 View도 허용하는 반환용 조회를 분리한다.

#### 3.4.4 준비 상태가 아래 스와이프 노출을 결정한다

`ViewPager2`가 IDLE이고 현재 요청 상태가 READY일 때만 실제 폴더를 드러낸다.

| 상태 | 아래 스와이프 |
|---|---|
| `REVEAL_READY(currentPath)` | 정상적으로 확정하고 첫 사진 이동 프레임부터 준비된 폴더를 노출 |
| `WAIT` | 사진을 고정하고 터치를 아직 가로채지 않음. READY가 되면 다음 MOVE에서 누적량을 따라잡아 시작 |
| `BLACK_FALLBACK` | 사진 드래그·닫기는 허용하되 검정 배경 유지. 종료 시 §3.4.5의 최종 준비를 다시 시도 |
| 세션 없음(F1·F2) | 공유 요소와 폴더 선노출 없이 기존 검정 배경의 일반 종료 |

WAIT의 시간 제한은 제스처 인식 타이머가 아니다. 페이지 IDLE 직후 시작한 폴더 레이아웃 준비의
안전 종료일 뿐이며, 아래 제스처의 `touchSlop`·이동량 판정에는 시간을 섞지 않는다.

#### 3.4.5 `onActivityReenter()`는 최종 안전망이다

선행 준비가 UNAVAILABLE이었어도 반환 결과의 현재 경로를 다시 확인한다. `preparedPath`와 결과 경로가
같고 현재 타일이 실제로 붙어 있을 때만 바로 전환한다. 아니면 기존처럼 return transition을 postpone하고,
스크롤 후 pre-draw에서 타일을 재검증한 뒤 시작한다. `view == null` / `!isAdded` 가드와 시간 제한을
두며, 시작하지 못하면 names와 map을 비우고 일반 폴백으로 끝낸다. 최종 반환 요청이 선행 준비보다
항상 우선하고, 반환 준비가 시작되면 이전 sequence를 무효화한다.

#### 3.4.6 exit 콜백은 이름이 아니라 View만 바꾼다

`ActivityCompat.setExitSharedElementCallback()`은 뷰어를 열기 전에 등록한다. 방향과 실패를
`Opening / Returning(path) / ReturnBlocked` 삼상태로 표현한다. `null/non-null` 두 상태만 쓰면
복귀 대상이 없을 때를 opening과 구분하지 못해 최초 A 타일의 기본 매핑이 살아난다.

- 새 뷰어를 열 때 `Opening`.
- `onActivityReenter()`가 시작되면 먼저 `ReturnBlocked`. 결과와 현재 MEDIA 목록·뷰가 유효하고 최종
  타일 준비가 가능할 때만 `Returning(path)`.
- callback에서 `Opening`은 기존 open map을 그대로 두고, `ReturnBlocked`는 names와 map을 함께 비운다.
  `Returning(path)`만 아래의 View 재매핑을 한다. FileList view가 아직 없으면 blocked 상태를 보존했다가
  view 생성 뒤 최종 준비를 다시 시도한다.

반환 시에는 `Returning(path)`의 타일을 찾되, 성공 경로의 `names`는 **절대 수정하지 않는다.** 현재 코드는
`mediaTransitionName(path)`로 이름까지 교체해 A에서 열고 B로 넘겼을 때 양쪽 프로토콜 키가 달라지는
결함이 있다. 올바른 형태는 다음과 같다.

```kotlin
val originalName = names.firstOrNull() ?: return
val path = (returnMappingState as? Returning)?.path ?: return
val tile = mediaTileImageFor(path) ?: run {
    names.clear()
    sharedElements.clear()
    return
}
sharedElements.clear()
sharedElements[originalName] = tile
```

호출 측 exit 콜백은 열 때도 불리므로 `Opening`이면 아무것도 하지 않는다. 반환 상태는 다음 뷰어를
열 때 `Opening`으로 초기화하고, 반환 콜백이 여러 번 불릴 수 있으므로 사용 직후에는 비우지 않는다.

#### 3.4.7 최초 타일의 프레임워크 전환 상태도 복원한다 (11차)

10차 구현에서 A의 썸네일에 `alpha = 1`만 다시 주면 두 문제가 남았다. A→B 뒤 복귀가 끝나도 A가
계속 빈칸인 경우가 있었고, 전환 종료 시 A가 한 번 깜박였다. Android 공유 요소 전환은 일반
`View.alpha`와 별도로 합성되는 `transitionAlpha`를 사용하고, layout/focus 부작용 없이 visibility flag를
바꾸는 전환용 setter도 사용한다. 따라서 일반 alpha와 visibility만 되돌려서는 프레임워크가 조작한
표시 상태가 남을 수 있다. A View의 원래 이름을 잠시 떼는 것은 최초 map 결과를 바꾸기 위해서가 아니라,
callback 이후의 이름 재탐색·재호출·capture에서 A가 다시 선택되는 것을 막는 방어다.

- `mediaOpeningSharedElementPath`에 최초 경로 A를 세션 동안 별도로 보관한다. 현재 빈칸 경로
  `mediaViewportHiddenPath`와 합치지 않는다. 구성 변경을 위해 saved state에도 문자열 경로를 보존한다.
- B READY를 확정하는 pre-draw에서 A의 `visibility = VISIBLE`, 일반 `alpha = 1`,
  `transitionAlpha = 1`을 적용하고 전환용 visibility setter로 visibility flag도 정상화한 뒤 B의 일반
  `alpha = 0`을 적용한다. 이 교환이 끝난 다음에만 검정 배경을 낮춘다. API 29 이상은 공개 View API를
  쓰고, API 23~28은 프로젝트의 기존 hidden-API 호환 방식으로 같은 메서드를 호출한다.
- 손을 놓아 닫힘이 확정된 뒤 `Returning(B)` callback에서는 **B tile과 original name을 먼저 검증**한다.
  둘이 모두 유효하고 A와 B가 다를 때만 A의 `transitionName`을 `null`로 만들고 표시 상태를 다시
  정상화한 다음 `sharedElements[originalNameA] = tileB`로 B를 반환 대상에 넣는다. 폴백이면 A 이름을
  건드리지 않고 names와 map만 비운다. 프로토콜 키 A 자체는 바꾸지 않는다.
- 반환 overlay가 B로 들어가는 동안 A는 계속 표시하고 B의 실제 썸네일은 숨긴다. **Activity Result
  수신은 반환 애니메이션 완료 신호로 간주하지 않는다.** 성공 경로에서는 caller의 shared-element
  reenter transition `onTransitionEnd`/`onTransitionCancel`에서 한 번만 B를 표시하고
  `A.transitionName = mediaTransitionName(A)`와 A의 표시 상태를 복원한 뒤 세션을 정리한다.
  `ReturnBlocked`처럼 전환이 시작되지 않은 경로와 view 파괴는 즉시 정리하는 안전망을 둔다.
- 전환 상태는 경로가 아니라 ImageView 인스턴스에 남는다. A 경로와 이름 분리 상태는 transition 종료까지
  유지한다. adapter bind/recycle 및 child 재부착에서 모든 썸네일의 전환 상태를 기본값으로 정규화하되,
  세션 중 분리 대상 A에는 `transitionName = null`, 현재 빈칸 B에는 일반 `alpha = 0`을 다시 적용한다.
  A holder가 붙어 있지 않다고 경로를 먼저 버리지 않으며, 세션 종료 시 해당 adapter position을
  무효화해 cached holder도 다음 attach 전에 정상화한다.
- A로 다시 넘겨 A에 반환하는 경우에는 이름을 떼지 않는다.

사용자에게 보이는 순서는 **B READY 교환 → 폴더 첫 노출 → UP 닫힘 확정 → 반환 매핑 → reenter
transition 종료/cancel → 세션 정리**다. 현재 코드는 API 36의 A→B swipe-down 핵심 경로에서 빈칸과
깜박임을 없앴다. 위의 명시적 transition 종료 훅과 holder 재활용 정규화는 3인 검토에서 확인된
후속 구현 항목이다.

### 3.5 현재 페이지의 상태를 **한 곳에서** 묻는다

세 가지가 같은 것을 알아야 한다 — 닫을 때의 그림 출처, 진입 후 페이드아웃 시점(§3.3),
그리고 폴백 판정(§3.7 F5). 통로를 셋으로 나누면 어긋난다.

`MediaViewerFragment` 에 **`currentPageContent(): PageContent`** 하나를 둔다.
`videoHolderAt()` 이 이미 쓰는 방식으로 현재 페이지의 뷰를 꺼내
`READY(그림)` / `LOADING` / `ERROR` 중 하나를 답한다.

| 현재 페이지 | 준비 판정 | 닫을 때의 그림 |
|---|---|---|
| `PhotoView` 가 보임 | `image.drawable != null` | `image.drawable` — 원본이라 여백이 없다 |
| `SSIV` 가 보임 | `largeImage.isReady` | `largeImage.drawToBitmap()` 을 **`sourceToViewRect` 로 잘라서** ⚠️ |
| 동영상, 첫 프레임 이후 | 현재 경로의 첫 프레임 렌더링 이벤트를 받음 | `PlayerView`의 `SurfaceView`를 `PixelCopy`로 비동기 복사 — 이미 영상 영역 크기다 |
| 동영상, 재생 전 | `thumbnailImage.drawable != null` | `thumbnailImage.drawable` |
| 진행 표시·오류 | `LOADING` / `ERROR` | 없음 → 폴백 (§3.7 F5) |

⚠️ **`MediaViewerAdapter` 는 건드리지 않는다.** 로딩 완료 처리는 전부 어댑터 안에 있고
(`image.load { onSuccess }` 202행, `largeImage.onReady()` 220행) 프래그먼트로 나오는 통로가 없다.
콜백을 새로 뚫는 대신 **위 표처럼 뷰 상태를 직접 읽는다.** 묻는 시점이 §3.3에서 정한 두 번뿐이라
비용이 없다. (확대 판정 `root.canDismiss` 는 기본값이 `{ true }` 라 그대로 재사용된다.)

⚠️ **`SSIV` 한 갈래만의 함정 — 레터박스가 같이 뜬다.** 전체 화면을 그대로 뜨면 검은 여백이
포함되고, 복귀가 끝나는 지점에서 프레임워크가 타일의 `centerCrop` 을 적용하므로
**타일이 실제 보여 주는 썸네일과 어긋난다.** 가로 사진일수록 마지막에 눈에 띄게 튄다.
`PhotoView` 는 원본 `drawable` 이고 `SurfaceView`에서 복사한 프레임은 영상 영역 크기라
둘 다 이 문제가 없다.

⚠️ **`SurfaceView` 는 일반 View 캡처로 나오지 않는다.** 영상은 앱의 View 계층과 별도 Surface에
그려지므로 `drawToBitmap()`도 쓸 수 없고, `TextureView.bitmap`도 존재하지 않는다.
Android 7.0(API 24) 이상에서 `PixelCopy.request(surface, bitmap, ...)`로 복사해야 한다(§3.5.2).

**열 때는 프레임워크가 주는 스냅샷을 쓴다.**
`onSharedElementStart` 의 세 번째 인자 `sharedElementSnapshots` 는 떠나온 액티비티의
공유 요소를 프레임워크가 찍어 둔 뷰들이다. **Coil로 다시 불러올 필요가 없다.**

⚠️ **그 뷰가 `ImageView` 라는 보장이 없다.** `SharedElementCallback.onCreateSnapshotView()` 의
기본 구현은 두 갈래다 — 스냅샷이 `Bundle` 이면 `ImageView` 를 만들어 `drawable` 로 달고,
그냥 `Bitmap` 이면 **평범한 `View` 를 만들어 `BitmapDrawable` 을 `background` 로 단다.**

```kotlin
val snapshotDrawable = (snapshot as? ImageView)?.drawable ?: snapshot.background
```

`drawable` 만 읽으면 후자에서 언제나 `null` 이고, 증상은 **"열기 전환은 도는데 그림이 안 붙는다"** 로
나타난다 — §3.2의 두 결함과도 겹쳐 보인다. **둘 다 읽는다.**

어느 갈래로 오는지는 `captureSharedElementState` 가 정한다. 공유 요소가 `ImageView` 이고
`drawable` 이 있고 **불투명한 배경이 없으면** `Bundle` 갈래다. 미디어 타일의 `thumbnailImage` 는
배경이 없으므로([file_item_media.xml:41](../app/src/main/res/layout/file_item_media.xml#L41))
이 조건이 성립한다. ⚠️ **나중에 타일에 배경을 넣으면 갈래가 바뀐다.**

> ⚠️ **대비책.** 그래도 그림이 비어 오면 `transitionImage.load(path to attributes)` 로 직접 불러오고
> `supportPostponeEnterTransition()` / 로드 완료 시 해제로 바꾼다. 이때 **동영상은 프레임 추출이
> 느려 눈에 띄게 지연될 수 있으므로** 시간 제한을 둔다. 5단계에서 확인하고 결정한다.

#### 3.5.1 ⚠️ 하드웨어 비트맵이 앱을 죽인다 (5차)

프레임워크는 공유 요소를 **소프트웨어 `Canvas` 에 그려서** 스냅샷을 뜬다
(`SharedElementCallback.createDrawableBitmap`). 하드웨어 비트맵은 거기에 그릴 수 없다.

```
java.lang.IllegalArgumentException: Software rendering doesn't support hardware bitmaps
  at androidx.core.app.SharedElementCallback.createDrawableBitmap(SharedElementCallback.java:234)
  at android.app.ActivityTransitionCoordinator.captureSharedElementState(...)
```

**Coil 은 하드웨어 비트맵을 줄 때도 있고 아닐 때도 있다.** 그래서 증상이 **간헐적인 크래시**로
나타난다 — 같은 조작을 열 번 해도 안 죽다가 열한 번째에 죽는다. 열 때(그리드 타일 캡처)와
닫을 때(뷰어 `transitionImage` 캡처) 양쪽에서 난다.

**결정(D24) — 전환에 실리는 그림은 처음부터 소프트웨어로 받는다.** `allowHardware(false)` 를
**두 곳 모두**에 넣는다.

| 어디 | 무엇 |
|---|---|
| `FileListAdapter` | 미디어 타일의 `thumbnailImage` (미디어 모드일 때만 — 나머지 두 모드는 전환에 끼지 않는다) |
| `MediaViewerAdapter` | `image`(일반 사진)와 `thumbnailImage`(동영상) |

⚠️ **§6의 "`MediaViewerAdapter` 는 건드리지 않는다"는 여기서만 예외다.** 나중에 꺼내서 복사하는
길이 없기 때문이다 — Coil 은 페이드가 끝난 뒤에도 `CrossfadeDrawable` 을 **계속 달아 둔다.**
`drawable as? BitmapDrawable` 은 영원히 실패하고, `CrossfadeDrawable` 안의 비트맵을 파고드는
것은 Coil 내부 구현에 기대는 짓이다. **디코드 시점에 막는 것이 유일하게 튼튼하다.**

`SSIV`(우리가 `drawToBitmap` 한 것)와 동영상 현재 프레임(`PixelCopy` 대상 `ARGB_8888` 비트맵)은
이미 소프트웨어라 문제가 없다. `returnDrawable()` 에 하드웨어→소프트웨어 복사 가드를 하나 더 두지만,
그것은 위 두 곳이 뚫렸을 때를 위한 이중 안전장치일 뿐 **그것만으로는 못 막는다.**

#### 3.5.2 `SurfaceView`의 현재 프레임은 비동기로 복사한다 (6차)

12번의 HDR 밝기 개선으로 동영상 출력이 `TextureView`에서 `SurfaceView`로 바뀌었다.
공유 요소 전환은 동기적으로 그림을 준비하던 설계였으므로, 그대로 두면 재생 중인 동영상은
`currentPageContent()`에서 그림을 얻지 못해 `RESULT_CANCELED` 폴백으로만 닫힌다.

종료 요청이 들어오면 현재 페이지가 **실제로 첫 프레임까지 렌더링된 동영상**인지 확인한 뒤:

1. 플레이어를 일시 정지해 복사할 프레임을 고정한다.
2. `SurfaceView` 크기의 `ARGB_8888` 비트맵을 만든다.
3. `PixelCopy.request()`로 프레임을 복사한다.
4. 성공하면 그 비트맵을 `transitionImage`에 넣고, 실패하면 그림 없이 일반 종료로 폴백한다.

`PixelCopy`는 비동기이므로 종료를 기다리는 동안 중복 뒤로가기 요청을 막는다. **500ms 시간 제한**을
두고, API 24 미만·유효하지 않은 Surface·크기 0·경로 변경·뷰 생명주기 종료·복사 예외는 모두
조용히 폴백한다. 콜백이 늦게 돌아왔을 때 다른 페이지의 프레임을 싣지 않도록 요청 당시의
뷰와 경로를 다시 비교한다(D26).

### 3.6 종료 훅 — 닫는 경로 셋을 한 자리로 모은다

§3.2.1의 `isReturning`, §3.4.1의 최종 결과, §3.5의 그림, 아래 (1)·(2)가 모두 "종료 직전"에 일어나야 하는데,
**지금 코드에는 그런 자리가 없다.** `MediaViewerFragment` 가 `OnBackPressedCallback` 을 걸고
처리한다. 사진과 재생 전 동영상은 한 프레임에 끝나고, 재생 중 `SurfaceView` 동영상만
§3.5.2의 비동기 복사를 먼저 기다린다.

```kotlin
// MediaViewerFragment.onActivityCreated
activity.onBackPressedDispatcher.addCallback(viewLifecycleOwner) {
    if (isPreparingReturn || isReturning) return
    // 0. 렌더링된 SurfaceView 동영상이면 PixelCopy를 요청하고 성공/실패 콜백에서 아래를 계속한다 (§3.5.2)
    isReturning = true                       // §3.2.1 — 이제부터 가드가 산다
    // 1. currentPageContent() 또는 PixelCopy 결과를 transitionImage 에 넣는다 (§3.5)
    //    없으면 채우지 않는다 → §3.2.1 가드가 전환을 끊는다 (F4·F5)
    // 2. 아래로 끌던 변형을 옮겨 받는다 (아래 (2))
    // 3. viewPager · appBarLayout · playerControlView 를 감춘다 (아래 (1))
    // 4. setResult (§3.4.1) — 그림이 없으면 RESULT_CANCELED
    requireActivity().finishAfterTransition()
}
```

- **아래로 끌기**와 **뒤로가기**는 이미 디스패처로 오므로 그대로 이 콜백에 걸린다.
- **툴바 화살표**는 §0에서 본 대로 `finish()` 로 새 나간다. `MediaViewerActivity` 에
  `onSupportNavigateUp()` 을 재정의해 `onBackPressedDispatcher.onBackPressed()` 로 보낸다(D17).
- 콜백 안에서 `finishAfterTransition()` 을 **직접 부른다.** 콜백이 뒤로가기를 소비하므로
  기본 처리(= `Activity.onBackPressed()` 의 `finishAfterTransition()`)가 돌지 않는다.
- `SurfaceView` 동영상은 `finishAfterTransition()`을 `PixelCopy` 성공·실패 또는 500ms 시간 제한
  뒤에 부른다. 종료 상태는 `IDLE → PREPARING_RETURN → RETURNING`으로 관리한다.
  `PREPARING_RETURN`부터 ViewPager·아래 스와이프 입력을 잠그고, 성공·실패·시간 제한을 하나의
  `completeOnce` 경로로 모은다. 뷰가 파괴되면 timeout/callback을 무효화하고 살아 있는 액티비티는
  그림 없는 일반 종료로 끝낸다.
- ⚠️ **항상 켜진 콜백은 Android 13+ 예측형 뒤로가기 애니메이션을 끈다.** 지금도 쓰고 있지
  않으므로 회귀는 아니다(§1 비목표).

**(1) 페이지 쪽을 감추는 이유.**
`transitionImage` 를 채워 보이게만 하면 그 아래 페이지에 같은 사진이 그대로 있다.
뷰어 창은 `windowAllowReturnTransitionOverlap = true` 이므로
([themes.xml:22](../app/src/main/res/values/themes.xml#L22),
[:72](../app/src/main/res/values/themes.xml#L72)) 겹치는 동안 **같은 그림이 둘 보인다** —
하나는 타일로 날아가고 하나는 제자리에서 페이드아웃한다.

**(2) 아래로 끌던 변형을 이어받는다.**
10번 D12는 "닫을 때 페이지를 되돌리지 않고 그 자리에 둔다"였다. 이제 그 자리에서 타일로
이어져야 한다. `SwipeDownDismissLayout` 은 **그대로 두고**, 뷰어 쪽에서 옮겨 받는다.

```kotlin
transitionImage.translationY = page.translationY
transitionImage.scaleX = page.scaleX
transitionImage.scaleY = page.scaleY
```

`@android:transition/move` 에는 `ChangeTransform` 이 들어 있어서 시작 지점의 변형을 제대로
읽는다. 알파는 옮기지 않는다 — 공유 요소는 전환 동안 불투명해야 자연스럽다.

D12는 유지된다. 페이지를 되돌리지 않으므로 한 프레임 튀지 않는다.

### 3.6.1 아래로 끄는 동안의 폴더 선노출 (7차)

10번 5차에서 아래 스와이프는 두 단계가 된다.

| 시점 | 보이는 것 | 전환 상태 |
|---|---|---|
| 임계값 전 | 사진과 검정 배경 유지 | 경과 시간과 무관하게 공유 요소 복귀는 시작하지 않음 |
| 폴더 준비 `WAIT` | 사진과 검정 배경 유지 | 타일 READY 전에는 드래그를 확정하거나 옛 폴더를 노출하지 않음 |
| 드래그 확정 직후 | 검정 window background가 85% 알파가 되어 실제 폴더 화면이 비치기 시작 | 공유 요소 복귀는 아직 시작하지 않음 |
| 드래그 중 | 확정 순간 누적 이동량을 따라잡고, 폴더 화면 위에서 사진이 손가락과 1:1로 움직임 | 최초 타일은 채워지고 현재 타일 자리만 비어 있는 `FileListActivity`를 유지 |
| 취소 | 사진과 검정 배경이 같은 200ms 동안 원상 복귀 | 복귀 전환 없음 |
| 닫힘 확정 | 끌던 사진 위치·크기를 `transitionImage`가 이어받음 | 이때 처음 `finishAfterTransition()` 시작 |

**드래그 중 폴더 선노출은 공유 요소 전환이 아니다.** 기존 Immersive 뷰어 테마를 반투명 창으로
만들되 검정 `ColorDrawable` window background는 유지하고, 그 알파를 낮춰 바로 아래
`FileListActivity`를 보여 주는 것이다. 공유 요소
return coordinator를 MOVE 중에 시작하면 취소할 수 없고, 손가락을 놓기 전에 액티비티 수명이
끝날 수 있으므로 기존 종료 훅은 UP의 닫힘 확정 시점까지 호출하지 않는다(D27).

뷰어는 평소 window background 알파 255로 지금과 같은 검정 배경을 유지한다.
`FileListFragment.openMediaViewer()`의 **MEDIA 모드 공유 요소 실행**에서만 “아래에 폴더 화면이 있음”
extra와 §3.4의 viewport session을 주고 배경 알파를 낮춘다. 공용 `maybeAddMediaViewerExtras()`에는
넣지 않는다. 리스트·바둑판 모드 동영상은 아래 목록을 임의로 움직이지 않고 검정 배경의 기존 닫기를
유지한다(F1). 외부 `VIEW`도 검정을 유지해 다른 앱이나 런처를 드러내지 않는다.

내부 extra만으로는 부족하다. `ViewPager2`가 IDLE이고 coordinator가 현재 path를 READY로 응답한
경우에만 첫 이동과 배경 노출을 허용한다. READY 전에는 사진을 고정하고, READY가 되면 다음 MOVE에서
누적 이동량을 따라잡는다. 준비 실패는 사진 아래를 검정으로 둔 일반 폴백이며 옛 폴더 뷰포트를
대신 보여 주지 않는다.

확정 순간 배경 알파는 85%로 낮추고 실제 이동량 12.5%에서 0%가 되도록 선형으로 줄인다. 사진 이동과
배경 변경은 같은 MOVE/vsync에 반영한다. “폴더가 먼저”는 별도 이전 프레임이 아니라 **사진의 첫 이동
프레임에 이미 폴더가 보인다**는 뜻이다. 시스템 바는 뷰어가 계속 소유하고 드래그 중 바꾸지 않는다.

닫힘이 확정되면 §3.6 (2)처럼 **손가락과 1:1로 이동한 사진의 표시 변형**을 `transitionImage`로
옮긴다. 10번 §3.3에 따라 확정 MOVE에서 `touchSlop`을 빼지 않고 누적 이동량을 따라잡으므로,
공유 요소 전환도 사용자가 놓은 실제 위치에서 시작한다.

### 3.7 폴백 — 짝이 없으면 조용히 지금까지의 전환

다섯 가지다. 예외를 던지거나 사용자에게 알리지 않는다.
**그리드 쪽만으로는 부족하고, 뷰어 쪽에서도 끊어야 하는 것이 있다.**

| # | 상황 | 그리드 쪽 | 뷰어 쪽 |
|---|---|---|---|
| F1 | 뷰 모드가 미디어가 아님 (동영상은 모든 모드에서 뷰어를 연다) | 열 때 옵션을 안 붙인다. **닫을 때 `onActivityReenter` 를 그냥 빠져나온다** | — |
| F2 | 다른 앱이 `VIEW` 인텐트로 뷰어를 열었다 | 옵션이 애초에 없다. 자동 | — |
| F3 | 돌아갈 파일이 목록에 없다 (삭제·범위 밖) | `onMapSharedElements` 에서 **`names` 와 `sharedElements` 를 둘 다 비운다** | — |
| F4 | 사진이 확대된 채로 뒤로가기 | `setResult(RESULT_CANCELED)` | **그림을 채우지 않는다** → §3.2.1 가드 (`isReturning` 일 때만) |
| F5 | `currentPageContent()` 가 `LOADING`·`ERROR` | 같음 | 같음 |

⚠️ **F4·F5는 그리드 쪽만으로는 안 걸러진다.** `setResult(RESULT_CANCELED)` 가 막는 것은
**재매핑뿐**이고, 진입 때 `ActivityOptions` 에 실린 이름의 복귀 전환 자체는 그대로 돈다.
뷰어 쪽 가드가 실제 차단 장치다.

⚠️ **그런데 그 가드는 `isReturning` 이 있어야만 안전하다.** 방향 구분 없이 걸면
**열기 전환이 죽는다**(§3.2.1). 이 둘은 한 벌로 구현해야 한다.

⚠️ **F1·F2에서는 §3.6 (1)의 "페이지 감추기"를 하면 안 된다 (5차, D25).**
공유 요소 없이 들어온 뷰어는 **평범한 창 애니메이션으로** 나간다. 그런데도 종료 훅에서
`viewPager` 를 감추면 그 애니메이션이 도는 내내 **빈 화면이 날아간다.**
들어올 때 공유 요소가 실제로 매핑됐는지를 기억해 두고(`hasSharedElement`), 아니면
종료 훅에서 아무것도 건드리지 않고 그냥 끝낸다.

⚠️ **F4·F5는 "결과를 안 돌려준다"로도 안 걸러진다.** §3.4.1이 페이지 IDLE마다
`setResult(RESULT_OK, …)` 를 갱신하므로, 확대한 채 뒤로가기를 눌러도 **직전 페이지의 결과가 이미
설정되어 있다.** 반드시 `RESULT_CANCELED` 로 덮어써야 한다.

⚠️ **F3에서 `sharedElements` 만 비우면 부족하다.** `names` 도 함께 비운다.

**F4의 확대 판정은 새로 만들지 않는다.** 아래로 끌기 허용 판정이 이미 같은 검사를 하고 있다
([MediaViewerAdapter.kt:72-82](../app/src/main/java/me/zhanghai/android/files/viewer/media/MediaViewerAdapter.kt#L72)) —
`PhotoView.scale` / `largeImage.scale` 을 최소 배율과 비교한다. **그 판단을 재사용한다.**

⚠️ **`MediaViewerActivity` 에는 `VIEW image/*` 인텐트 필터가 있다**
([AndroidManifest.xml:343](../app/src/main/AndroidManifest.xml#L343)). F2는 실제 경로다.

⚠️ **F3은 목록 범위 때문에도 생긴다.** `maybeAddMediaViewerExtras` 는
`TransactionTooLargeException` 을 피하려고 경로를 잘라 보낸다(`MEDIA_VIEWER_PATH_LIST_SIZE_MAX`).

## 4. 단계별 구현 계획

각 단계는 **빌드가 통과하고 앱이 돌아가는 상태**로 끝난다.

```
ANDROID_HOME="C:\Users\hskang\AppData\Local\Android\Sdk" JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```

### 0단계 — 기준선 확인
- 현재 전환이 어떤 모양인지 화면 녹화로 남긴다.
- **확인**: 미디어 모드에서 사진·동영상을 열고 세 가지 방법으로 닫는다.

### 1단계 — 테마 확인 (아마 손댈 것이 없다)
- `android:windowActivityTransitions` 는 플랫폼 `Theme.Material` 이 이미 `true` 로 두고 있고,
  `Theme.MaterialComponents` → `Theme.AppCompat` → `Platform.V21.AppCompat`(parent
  `android:Theme.Material.NoActionBar`)로 상속된다. **이미 켜져 있을 가능성이 높다.**
- 그래도 두 테마에 명시해 두면 나중에 부모가 바뀌어도 안전하다.
  `Base.Theme.MaterialFiles.Translucent` 는 `Theme.MaterialFiles` 를 상속하므로
  ([themes.xml:42](../app/src/main/res/values/themes.xml#L42)) 같이 켜진다.
- **확인**: 아무것도 달라지지 않아야 한다.

### 2단계 — 타일에 이름 붙이기
- `viewer/media/MediaTransition.kt` (신규)에 `mediaTransitionName(path)` **하나만** 둔다.
- `FileListAdapter` 에서 `transitionName` 설정/해제, `filePositionMap` 읽기용 접근자 (§3.2.3·§3.3).
- **확인**: 세 모드를 오간 뒤 `dumpsys activity top` 또는 Layout Inspector로
  미디어 모드에서만 이름이 붙는지 본다.

### 3단계 — 미디어 모드에서 사진도 우리 뷰어로 (§3.0)
- `openFile()` 의 분기를 고친다. **전환과 무관하게 이것만으로 동작이 바뀌므로 따로 둔다.**
- **확인**: 미디어 모드에서 사진을 누르면 PhotoExplorer 뷰어가 열린다.
  리스트·바둑판 모드에서는 예전대로 기본 앱으로 나간다.
  ⚠️ 목록을 못 만드는 경우에도 예전 경로로 떨어지는지 본다.

### 4단계 — 닫는 경로 셋을 한 자리로 모으기 (§0·§3.6)
- `MediaViewerFragment` 에 `OnBackPressedCallback` (지금은 `finishAfterTransition()` 만 부른다).
- `MediaViewerActivity` 에 `fragment` 필드와 `onSupportNavigateUp()` 재정의 (D17).
- ⚠️ **전환 코드를 넣기 전에 이 단계를 끝낸다.** 훅이 없으면 6·9·10단계가 놓을 자리가 없다.
- **확인**: 셋 다 예전과 같이 닫힌다.
  ⚠️ **화살표는 `finish()` → `finishAfterTransition()` 으로 바뀌므로 이 단계만으로도
  종료 애니메이션이 달라진다.** 눈으로 확인한다.
  동영상 재생 중에 셋 다 눌러 보고 소리가 남지 않는지 본다.

### 5단계 — 뷰어 쪽 전환용 뷰 + 열 때 옵션 ← *여기까지로 "열기"가 완성된다*
- `media_viewer_fragment.xml` 에 `transitionImage`. **항상 `VISIBLE`, `drawable` 없음** (§3.1).
  `appBarLayout` 과의 앞뒤를 여기서 눈으로 정한다.
- **`MediaViewerActivity.onCreate` 에서** `ActivityCompat.setEnterSharedElementCallback` 등록 (§3.2.2).
  뷰는 콜백 안에서 늦게 찾는다. `onMapSharedElements`(가드는 `isReturning` 일 때만) ·
  `onSharedElementStart`(스냅샷 `drawable ?: background`) ·
  **`sharedElementEnterTransition` 의 `onTransitionEnd`**(pager 복원 + 페이드아웃, §3.3.2).
- `currentPageContent()` (§3.5)와 `FileListFragment.mediaTileImageFor(path)`, 옵션 붙여 시작 (§3.3).
- ⚠️ **뷰어 쪽만 먼저 만들지 않는다.** 옵션을 붙이기 전에는 `onSharedElementStart` 가 아예
  호출되지 않아 스냅샷 문제를 확인할 방법이 없다.
- **확인**: 미디어 모드에서 사진·동영상 타일이 커지며 열린다. 잘림 없이 이어진다.
  진입 직후 빈 화면이 번쩍이지 않는다.
  ⚠️ **세 가지 실패 모양을 구분해 기록한다** — ①전환이 아예 안 돈다(콜백 등록 시점, §3.2.2)
  ②전환은 도는데 공유 요소가 없다(가드 방향, §3.2.1) ③전환은 도는데 그림이 없다(스냅샷 갈래, §3.5).
  **화면으로는 거의 같아 보인다.** 로그로 갈라야 한다.

### 6단계 — 뷰어가 현재 경로를 결과로 돌려주기
- 페이지가 IDLE로 확정된 때와 종료 훅에서 `setResult(RESULT_OK, …)` (§3.4.1).
- `currentPageContent()` 가 `READY` 가 아니거나 확대 중이면 **`RESULT_CANCELED`**(F4·F5).
- **확인**: 겉보기 변화 없음. `logcat` 으로 결과 코드가 상황에 맞게 나가는지 본다.

### 7단계 — 복귀 매핑
- `FileListFragment` 뷰 생성 시점에 `setExitSharedElementCallback` 등록 (§3.4.6).
- `FileListActivity.onActivityReenter` → 프래그먼트의 상태를 먼저 `ReturnBlocked`로 설정 →
  **미디어 모드·결과·경로·타일 가드** 통과 시에만 `Returning(path)`. 프래그먼트/뷰가 아직 준비되지
  않았으면 blocked 상태와 결과를 보존해 생성 뒤 최종 준비를 재시도한다.
- 최초 `ActivityOptions` 이름은 성공 경로에서 절대 바꾸지 않는다. 그 original name에 현재 경로의
  타일 View만 연결한다(§3.2·§3.4.6). 타일이 없을 때만 names와 map을 함께 비운다.
- `returnMappingState`는 다음에 뷰어를 띄울 때 `Opening`으로 바꾼다. 반환 콜백 재호출 중에는 유지한다.
- **확인**: 화면에 보이는 타일 자리에서 창이 줄어든다. 좌우로 넘긴 뒤 닫아도 그 타일로 간다.
  로그에서 viewer와 grid의 names가 최초 경로 이름으로 끝까지 같은지 확인한다.
  ⚠️ **아직 `transitionImage` 가 비어 있어 "빈 사각형이 줄어드는" 그림이다.** 자리만 본다.
  ⚠️ 열 때가 망가지지 않았는지 반드시 같이 본다(exit 콜백은 열 때도 불린다).
  ⚠️ **리스트 모드에서 동영상을 열고 닫아 목록이 점프하지 않는지 본다**(F1).

### 8단계 — 페이지 IDLE 때 화면 밖 타일 미리 준비하기
- `MediaViewerViewportCoordinator`를 만들고 내부 MEDIA 실행의 `sessionId`를 intent와 폴더 쪽
  saved state에 보존한다. 세션별 최신 `sequence + path + state`를 replay하며 listener는 뷰
  생명주기에 맞춰 등록·해제한다(§3.4.2).
- 뷰어는 최초 페이지·IDLE·삭제 후 경로 변경에서 요청한다. 폴더는 이미 보이면 즉시 READY,
  아니면 `scrollToPositionWithOffset` 후 pre-draw에서 holder의 경로·attach·layout·가시성·크기를
  재검증해 READY 또는 UNAVAILABLE을 응답한다.
- 빠른 A→B→C의 모든 비동기 완료에서 sessionId/sequence/path 최신 여부를 재확인한다. 종료 준비가
  시작되면 선행 요청을 무효화하고 최종 결과 경로를 우선한다.
- `onActivityReenter`의 postpone/scroll/timeout은 제거하지 않고 선행 준비 실패의 안전망으로 둔다.
- 최초 경로 A를 현재 hidden path와 별도로 보존한다. A→B 반환 callback에서는 target과 original name을
  검증한 뒤 A의 `transitionName`을 잠시 제거하고 표시 상태를 정상화한다. reenter transition 종료/cancel
  뒤 이름과 두 타일을 복원한다. adapter 재활용 중에도 이 상태를 bind에서 재적용한다(§3.4.7).
- **확인**: 20장 이상 넘겨 IDLE이 된 뒤 아래로 끌기 전부터 그리드가 최종 타일 위치에 있고,
  첫 노출 프레임에 그 타일이 보인다. 이미 보이는 한 칸 이동은 목록을 움직이지 않는다.
  B 준비 응답을 늦춘 뒤 C로 넘겨도 B 위치로 돌아가지 않는다.

### 9단계 — 닫을 때 그림 채우기 ← *여기까지로 "닫기"가 완성된다*
- 종료 훅에서 `isReturning = true`, `currentPageContent()` 의 그림을 `transitionImage` 에 (§3.5).
- `SurfaceView` 동영상은 종료 훅에서 `PixelCopy`를 비동기로 요청하고, 성공·실패·500ms 시간 제한
  중 먼저 온 결과에서 종료를 계속한다(§3.5.2).
- **같은 프레임에 `viewPager`·`appBarLayout`·`playerControlView` 를 감춘다** (§3.6 (1)).
- **⚠️ 네 갈래를 전부 확인해야 한다.** 어느 뷰를 쓸지는 `shouldUseLargeImageView` 가 정하므로
  (픽셀×4 > 100MB, 또는 2048px 초과 + 비율 2:1 이상) 한쪽만 보면 절반을 못 본다.
- **확인**: 일반 사진 / **가로 대용량 사진** / 재생 중 동영상 / 재생 전 동영상 넷.
  ⚠️ **가로 대용량 사진에서 마지막에 튀지 않는지** 특히 본다(레터박스 잘라내기).
  **같은 그림이 둘 보이지 않는다.**

### 10단계 — 아래로 끌기와 접합
- 기존 Immersive Material 2·3 base에 `windowIsTranslucent=true`를 추가하고 검정
  `windowBackground`는 유지한다. 내부 폴더 진입 여부에 따라 그 drawable 알파만 제어한다(§3.6.1).
- 드래그 중에는 공유 요소 전환을 시작하지 않는다. 취소 시 사진·배경을 같은 200ms로 복원한다.
- `ViewPager2.IDLE + READY(currentPath)`를 폴더 선노출 시작 조건에 넣는다. 준비 중이면 `WAIT`으로
  사진을 고정하고, 준비 불가이면 `BLACK_FALLBACK`으로 사진 드래그는 허용하되 검정 배경을 유지한다.
  손을 놓은 뒤에는 최종 타일 준비를 다시 시도한다(§3.4.4·§3.4.5).
- 드래그 중 사진 자체의 alpha 감소는 제거한다. 이동·축소·배경 alpha만 사용한다.
- 종료 훅에서 손가락과 1:1로 이동한 페이지의 표시 변형을 `transitionImage` 로 옮긴다 (§3.6 (2)).
- **확인**: 40% 끌어 닫기, 12% 빠르게 튕겨 닫기 둘 다 끌던 자리에서 이어진다.
  10번 §6의 수용 기준 1~7이 유지되는지도 본다.

### 11단계 — 폴백 다섯 가지 훑기
- F1~F5를 하나씩 만들어 본다 (§3.7).
- ⚠️ **F4·F5에서 빈 사각형이 날아가지 않는지, 그러면서도 열기가 멀쩡한지**가 핵심이다.
  `isReturning` 이 제대로 갈라지는지 보는 단계다.
- ⚠️ F2는 갤러리에서 "다른 앱으로 열기" → PhotoExplorer 로 재현한다.
- ⚠️ **개발자 옵션 "액티비티 유지 안 함"을 켜고 한 번 더 훑는다**(§7).

### 12단계 (선택) — 값 다듬기
- 전환 시간, 인터폴레이터. 기본값(`move`, 300ms 남짓)으로 충분한지 본다.
- 필요하면 `windowSharedElementEnterTransition` / `...ReturnTransition` 을 테마에 명시한다.

## 5. 검증

현재 `app/src/test`가 있으므로 임계값·방향·거리 판정은 가능한 범위에서 단위 테스트한다.
공유 요소·창 합성·손맛은 **컴파일 통과 + 에뮬레이터·실기기 확인**으로 검증한다.

### 5.1 ⚠️ 실패 모양이 서로 구분되지 않는다

10번은 `adb shell input swipe` 로 판정 조건만은 확인할 수 있었다. 이번 것은 **애니메이션의
연속성**이 전부라 픽셀로 판정하기 어렵고, 게다가 **원인이 다른 실패가 화면으로는 같아 보인다.**

| 화면에 보이는 것 | 가능한 원인 |
|---|---|
| 열 때 그냥 슬라이드 | 콜백 등록이 늦었다(§3.2.2) / 옵션이 안 붙었다 / 타일을 못 찾았다 |
| 전환은 도는데 빈 사각형 | 가드가 진입에서 걸렸다(§3.2.1) / 스냅샷을 잘못 읽었다(§3.5) |
| 화면이 멈춤 | `postpone` 짝이 안 맞는다(§3.4) |

**그래서 로그가 눈보다 중요하다.** 최소한 이 넷은 찍는다 —
`onMapSharedElements` 호출(방향·`names`·결과 크기), `onSharedElementStart` 의 스냅샷 갈래,
`setResult` 코드, `postpone`/`start` 짝.

| 방법 | 무엇을 알 수 있나 |
|---|---|
| 화면 녹화 (`adb shell screenrecord`) 후 프레임 분해 | 미디어가 타일 자리에서 시작/끝나는지 |
| `dumpsys activity top` | `transitionName` 이 붙었는지 |
| `logcat` | 위 넷. **원인을 가르는 유일한 수단** |
| 눈 | 나머지 |

### 5.2 테스트 데이터

10번 §5.2에서 확인된 함정이 그대로 적용된다.

- 기존 `photo_*.png` · `emu*.png` 는 전부 1080×2400이라 **죄다 `SSIV` 로 간다.**
  `PhotoView` 경로를 보려면 1200×900 같은 것이 따로 필요하다.
- 9단계의 네 갈래를 덮으려면 **일반 사진 · 가로 대용량 사진 · 동영상** 세 종류가 한 폴더에 있어야 한다.
  **가로가 중요하다** — 레터박스 문제가 세로에서는 안 보인다.
- 8단계를 보려면 한 폴더에 **최소 40장** 이상 있어야 한다.

### 5.3 수용 기준

**열 때**

1. 미디어 모드에서 타일을 누르면 그 타일이 커지며 뷰어가 된다. 좌우 슬라이드가 보이지 않는다.
2. **사진과 동영상 모두** 그렇다. 사진이 다른 앱으로 나가지 않는다.
3. 타일의 `centerCrop` 이 뷰어의 `fitCenter` 로 **잘림 없이** 이어진다.
4. 진입 직후 **빈 화면이 번쩍이지 않는다.**

**닫을 때**

5. **뒤로가기·위 화살표·아래로 끌기 셋 다** 타일로 들어간다.
6. A 타일에서 열어 B·C로 좌우 이동한 뒤 닫아도 최초 공유 요소 이름 A는 양쪽 콜백에서 유지되고,
   사진은 **현재 파일 B·C의 타일 View**로 들어간다.
7. 현재 타일이 화면 밖이면 페이지 IDLE 때 아래 그리드가 미리 스크롤한다. swipe-down 첫 노출
   프레임에 현재 타일이 화면 안에 있고, 이미 보이는 타일이면 그리드 위치가 바뀌지 않는다.
   최초 타일은 이미 다시 채워져 있고 현재 타일의 썸네일 자리만 비어 있어야 한다.
8. 작은 이동량 임계값을 넘으면 사진이 누적 이동량을 따라잡아 손가락과 1:1로 움직이고, 사진이 움직이기 전에 실제 폴더 화면이 아래에 보인다. 놓으면 **끌던 자리에서 이어진다.** 튀지 않는다.
9. 전환 중에 **같은 그림이 둘 보이지 않는다.**
10. **가로 대용량 사진**이 마지막에 튀지 않는다.

**폴백**

11. 리스트·바둑판 모드, 외부 인텐트, 삭제된 파일, 확대 상태 — 넷 다 예전 전환으로 조용히 떨어진다.
    **빈 사각형이 날아가지 않는다.**
12. 그러면서도 **열기 전환은 멀쩡하다.** (11과 12는 한 벌로 본다 — §3.2.1)
13. 리스트 모드에서 동영상을 열고 닫아도 **목록이 점프하지 않는다.**
14. "액티비티 유지 안 함"을 켜도 멈추지 않는다.

**회귀 (10번·12번 수용 기준 유지)**

15. 아래로 끌기의 이동량 임계값·누적 이동 따라잡기·폴더 선노출·취소 복원·거리와 속도 판정이 10번 §6의 1~7과 같다.
16. 좌우 페이지 넘김, 탭 토글, 더블탭 확대, 확대 후 pan이 그대로다.
17. 동영상 재생 중에 **셋 중 어느 방법으로 닫아도** 소리가 남지 않고 플레이어가 해제된다.
18. 뷰어에서 파일을 삭제한 뒤에도 목록이 정상이다.
19. **리스트·바둑판 모드에서 사진을 열면 예전처럼 기본 앱으로 나간다.**
20. 빠른 다중 페이지 플링은 최종 IDLE 경로만 준비한다. 늦은 이전 sequence의 pre-draw/timeout은
    폴더 위치나 READY 상태를 덮어쓰지 않는다.
21. IDLE 직후 곧바로 swipe-down해도 이전 타일이 노출되지 않는다. 현재 타일 READY 전에는 사진이
    고정되고, 준비되면 누적 이동량을 따라잡는다.
22. A에서 B·C로 넘길 때 빈칸도 A→B→C로 이동한다. 검정 배경을 낮추기 전 교환이 끝나며, 되돌아간
    타일은 즉시 원래 썸네일을 보인다.
23. 삭제·정렬/목록 교체·holder detach·화면 재생성에서 준비 상태가 무효가 되면 잘못된 타일 대신
    검정 배경과 일반 복귀로 안전하게 폴백한다.
24. A에서 열어 B로 넘긴 상태에서 swipe-down 첫 노출 프레임부터 A는 계속 채워지고 B 자리만 비어
    있다. 드래그를 취소하면 A의 이름과 표시 상태를 건드리지 않으며, 다시 swipe-down하거나 뒤로가기로
    닫아도 B로 정상 반환한다. 반환 overlay가 B에 도착할 때까지 A는 숨거나 깜박이지 않고 B의 실제
    썸네일은 나타나지 않는다. reenter transition 종료 다음 프레임에는 A와 B가 모두 채워지고 A 이름이
    복구된다. 이후 B→A로 되돌려 닫기, A 다시 열기, 반환 중 holder rebind/recycle도 정상이어야 한다.

### 5.4 검증 결과 (5차)

두 번 확인했다. **넓게 본 것은 에뮬레이터**이고, **축소 빌드가 살아 있는지는 실기기**다.

| | 어디서 | 무엇으로 판정했나 |
|---|---|---|
| ① | 에뮬레이터 `Pixel_8` (1080×2400, 420dpi), **디버그** 빌드 | §5.1의 로그 넷 + 화면 녹화 |
| ② | **Galaxy Z Fold 7** (`SM-F971N`, Android 17, 커버 화면 1248×1972), **릴리스(R8) 빌드** | 화면 녹화 + 크래시 유무 **만** |

#### 5.4.1 에뮬레이터에서 확인한 것 (디버그 빌드)

| 기준 | 결과 |
|---|---|
| 1·3·4 | 타일이 자라며 열린다. 잘리지 않고, 빈 화면이 번쩍이지 않는다 |
| 2·19 | 미디어 모드 사진은 우리 뷰어(`MediaViewerActivity` 가 top). 리스트 모드 사진은 여전히 시스템 선택창 |
| 5 | 뒤로가기 · **툴바 화살표**(D17) · 아래로 끌기 셋 다 복귀 전환을 돈다 |
| 6 | `a_small` 에서 열어 `b_tall` 로 넘긴 뒤 닫으니 **`b_tall` 타일로** 재매핑됐다 |
| 7 | 화면 밖: `postpone` → **어댑터 위치 14** 로 스크롤 → 73 ms 뒤 `start`. ⚠️ 파일 인덱스는 11이다 — §3.3의 날짜 타일 경고가 실제로 걸렸다 |
| 8 | 끌던 자리·크기에서 이어진다 |
| 9 | 양방향 모두 그림이 하나만 보인다 (§3.3.1·§3.6 (1) 반영 후) |
| 10 | **가로 대용량 사진**이 레터박스 없이 날아가고 마지막에 튀지 않는다 |
| 11·12·13 | F1·F2·F4 조용히 폴백, 그러면서 열기는 멀쩡. 리스트 모드 동영상에서 목록이 점프하지 않는다 |
| 14 | "액티비티 유지 안 함" 켜고 3회 왕복 — 멈추지도 죽지도 않는다 |
| 18 | 뷰어에서 삭제한 뒤 닫으면 **그 자리를 차지한 파일**의 타일로 간다 (D7 경로 방식이 실제로 필요했다) |

부하: 네 갈래 × 3회, 페이징·스크롤 복귀 10회 — **크래시 0**.
(§3.5.1 이전에는 이 두 가지 모두에서 죽었다.)

#### 5.4.2 실기기에서 확인한 것 (릴리스 · R8 빌드)

**왜 따로 했나 — R8이 전환을 갈아 먹는지가 유일한 관심사였다.**
공유 요소 배선은 `SharedElementCallback` · `TransitionListener` 처럼 프레임워크가 이름으로
찾아 부르는 것들이라, 축소·난독화에서 조용히 사라지면 **디버그에서는 멀쩡한데 배포판만
전환이 안 도는** 모양이 된다.

- 타일이 자라며 열리고 **그림이 하나만** 보인다 (프레임으로 확인)
- 닫으면 원래 타일로 줄어든다
- 네 갈래 × 2회 + **닫는 경로 셋 모두** — **크래시 0**
- ⚠️ ProGuard 규칙을 따로 넣지 않아도 됐다. **넣어야 했다면 여기서 드러났을 것이다.**

⚠️ **폰에서는 로그로 가르지 못했다.** `logMediaTransition` 이 `BuildConfig.DEBUG` 게이트라
릴리스에서는 한 줄도 안 나온다. 그래서 폰 판정은 **화면과 크래시 유무뿐**이고,
§5.1이 "로그가 눈보다 중요하다"고 한 그 구분 — 전환이 아예 안 도는 것 / 공유 요소가 없는 것 /
그림이 없는 것 — 은 **폰에서는 못 가른다.** 배포판에서 의심스러우면 디버그 빌드로 재현해야 한다.

**확인하지 못한 것 — 남은 일**

- **0단계 기준선 녹화.** 변경 전 APK를 빌드하지 않았다. 그래서 4단계가 화살표의 종료
  애니메이션을 바꾼 것 말고, 리스트 모드 동영상 종료 때 보이는 **검은 몇 프레임**이
  회귀인지 원래 그런지 **가리지 못했다.** (코드상 두 경로 모두 `finishAfterTransition()` 로
  가므로 회귀는 아닐 것이다. 확인은 안 했다.)
- **F3의 "돌아갈 타일이 아예 없는" 갈래.** 복귀 전에 그리드가 그 행을 버리게 만들지 못했다.
  널 가드는 있지만 **한 번도 실행되지 않았다.**
- 수용 기준 15·16(10번·12번 회귀), 17의 **소리 잔존 여부**는 따로 재지 않았다.
- **실기기에서의 폴백 매트릭스(F1~F5)와 수용 기준 6·7·8·14.** 폰에서는 열고 닫는 것만 봤다.
- **접었다 펴는 중의 복귀**(§7의 Fold 7 위험). 폰이 붙어 있었지만 화면 전환은 시켜 보지 못했다.
- 12단계(전환 시간·인터폴레이터)와 §3.4의 `offset` 값 다듬기. 기본값으로 충분해 보였다.

**테스트 데이터** — §5.2가 요구한 세 종류가 `/sdcard/SwipeTest` 에 이미 있었다
(`a_small_photoview.png` = PhotoView, `b_tall_ssiv.png` = 세로 SSIV,
`bb_wide_ssiv.png` = **가로** SSIV, `c_video.mp4`). 8단계용으로 50개짜리
`/sdcard/ScrollTest` 를 새로 만들었다.

실기기에서는 **개인 사진 폴더를 쓰지 않았다.** 위 네 개를 `/sdcard/PhotoExplorerTransitionTest`
로 복사해 쓰고 지웠다.

### 5.5 파일 목록 UI 통합 검증 (6차)

검증일은 2026-09-11이다. `origin/feature/file-list-ui-refresh` (`f1d315fe`)를 기준으로
`codex/integrate-shared-transition`에서 `83019a68` → `037f76f4`,
`7d540783` → `d7cd2e97` 순서로 통합했다. `FileListAdapter`의 폴더 경로 집합·파일 위치 조회와
`FileListFragment`의 구분선·공유 요소 콜백을 모두 유지했다.

통합 직후 HDR용 `SurfaceView`와 5차의 `TextureView.bitmap` 설계가 충돌했다. 동영상 종료 로그는
`setResult(CANCELED), nothing to send`였고 타일 복귀가 생략됐다. §3.5.2의 `PixelCopy` 방식으로
고친 뒤 다음을 확인했다.

API 근거: [Android `PixelCopy`](https://developer.android.com/reference/android/view/PixelCopy).

환경: Pixel 8 AVD(`emulator-5554`), 디버그 APK, 기존 `MixTest` 미디어.

| 항목 | 결과 및 근거 |
|---|---|
| `assembleDebug` | 통합 직후와 호환성 수정 후 모두 성공 |
| `testDebugUnitTest` | `DirectoryItemCountLoaderTest` 7개, 실패·오류 0 |
| 목록 UI | 폴더 개수·파일 메타데이터·즐겨찾기 바·경로 바 표시 확인 |
| 사진 열기 및 페이지 이동 후 복귀 | `photo_1` → `photo_2` 후 뒤로가기와 아래로 끌기 모두 로그상 `photo_2` 타일을 선택했다. 당시에는 프레임 연속성과 양쪽 이름 일치를 확인하지 않아 9차 결함을 놓쳤다 |
| 동영상 세 종료 경로 | `clip_1`에서 뒤로가기·툴바 화살표·아래로 끌기 각각 `PixelCopy result=0`, `RESULT_OK`, 그리드 재매핑 |
| 손상 동영상 | `broken.mp4` 종료에서 공유 요소를 비우고 일반 종료, 목록 복귀 |
| 크래시 | 위 실행 구간의 `AndroidRuntime` 오류 없음 |
| 정적 확인 | `git diff --check` 통과 |

화면 캡처는 `app/build/integration-result.png`에 남겼다(빌드 산출물, Git 비추적).
기존 `Screenshots` 즐겨찾기 대상 폴더가 에뮬레이터에 없어 오류가 표시되어,
기존 `MixTest` 폴더를 직접 열어 테스트했다.

**검증 범위 밖** — 애니메이션 프레임별 품질, HDR 색·밝기, 실기기·R8 통합 빌드,
화면 밖 타일 복귀, 화면 회전·액티비티 재생성, `PixelCopy` 시간 제한과 구형 API 분기는 확인하지 않았다.
§5.4의 실기기 결과는 2026-09-04의 통합 전 빌드 결과이므로 이번 통합 빌드의 근거로 간주하지 않는다.

### 5.6 7차에서 새로 확인할 것

- 드래그 첫 이동 프레임보다 폴더 화면 노출이 늦지 않은지 화면 녹화로 프레임 단위 확인한다.
- 취소하면 뷰어가 불투명 검정으로 완전히 돌아오고 폴더 화면이 남아 비치지 않는지 확인한다.
- 임계값을 넘는 순간 사진이 누적 이동량을 따라잡는지, 닫힘 확정 순간 `transitionImage`가 그 위치·크기를 이어받아 한 프레임도 튀지 않는지 확인한다.
- 미디어·리스트·바둑판 모드의 내부 진입과 외부 `VIEW` 진입을 모두 확인한다.
- 반투명 뷰어 창에서 공유 요소 enter/return, `PixelCopy`, 화면 밖 타일 복귀가 그대로 동작하는지 확인한다.
- API 23의 PixelCopy 없는 폴백, API 24+, Samsung HDR 실기기에서 `SurfaceView`가 부모의 이동·축소를
  따라가는지, 검정 사각형·hole punch가 없는지 확인한다.
- 외부 `VIEW`의 최초 inflate 전·재생성 중에도 다른 앱이나 런처가 노출되지 않는지 확인한다.
- “액티비티 유지 안 함”, 회전, Fold 7 접기·펼치기에서 아래 `FileListActivity`가 없거나 재생성돼도
  검정 폴백으로 안전하게 끝나는지 확인한다.

### 5.7 7차 접합 구현 후 핵심 검증 (8차)

2026-09-11 Pixel 8 API 36 에뮬레이터에서 `assembleDebug`와 단위 테스트 7개가 통과했다. 타일의
공유 요소 진입, 임계값 미만 사진 고정, 임계값 이후 누적 이동량 추종과 실제 `MixTest` 폴더 선노출,
짧은 드래그 취소 후 완전 복원, 좌우 페이지 이동, 아래 스와이프 후 `FileListActivity` 복귀를
확인했다. `git diff --check`도 통과했다.

§5.6 중 첫 이동 프레임의 순서, 동영상 `SurfaceView`·`PixelCopy`, 외부 `VIEW`, 화면 밖 타일,
회전·재생성, 구형 API와 실기기 항목은 아직 검증하지 않았다.

### 5.8 좌우 이동 후 복귀 결함 재현과 9차 검증 계획

2026-09-11 Pixel 8 API 36 에뮬레이터의 `MixTest`에서 `photo_1` 타일로 열고 `photo_2`로 넘긴 뒤
swipe-down했다. 폴더로 돌아가지만 `photo_2` 타일로 줄어드는 구간이 끊겼다. 로그는 다음 불일치를
보였다.

```
viewer exit: setResult(OK) for .../photo_2.png
viewer map: returning=true names=[media:.../photo_1.png] -> mapped
grid map: returning to .../photo_2.png, names=[media:.../photo_1.png] -> remapped
```

현재 grid callback은 마지막 줄에서 이름을 `media:.../photo_2.png`로 바꾼다. viewer는 최초 이름
`photo_1`을 유지하므로 반환 코디네이터의 짝이 깨진다. 녹화는
`app/build/swipe_return_bug.mp4`에 남겼다(빌드 산출물, Git 비추적).

9차 구현 후에는 다음을 프레임 녹화와 로그로 확인한다.

- A로 열어 B/C 이동 후 뒤로가기·위 화살표·swipe-down: 양쪽 names는 끝까지 A이고 대상 View만 B/C.
- 같은 뷰포트 한 칸, 날짜 구간을 넘는 20장 이상, 역방향 A→B→A, 다중 플링 A→D.
- B READY를 늦춘 뒤 C로 이동: B 완료는 stale drop, 첫 swipe-down 프레임에는 C 타일.
- IDLE 직후 즉시 아래로 끌기, 준비 중 취소, 준비 후 다시 끌기.
- 현재 파일 삭제, 목록 교체·정렬, holder detach, 썸네일 로딩 중: 잘못된 타일 없이 폴백.
- FileList/viewer 회전, “액티비티 유지 안 함”, 프로세스 복원, 복수 task: 세션 격리·재연결 또는 검정 폴백.
- PhotoView·SSIV·동영상 썸네일·`SurfaceView`/`PixelCopy` × 화면 안/밖 × 종료 경로 셋.
- 로그에 sessionId/sequence/path의 request→pre-draw→READY/UNAVAILABLE, 오래된 응답 폐기,
  return originalName과 target path를 남겨 화면상 같은 실패를 구분한다.

### 5.9 최초 타일 빈칸·깜박임 수정 검증 (11차)

2026-09-12 Pixel 8 API 36 에뮬레이터의 `MixTest`에서 `photo_1`을 열고 `photo_2`로 넘긴 뒤
swipe-down했다. 일반 alpha만 복원했을 때는 반환 완료 후 `photo_1` 칸이 계속 비어 있었고, 최초 경로를
별도로 보관해 Activity Result 수신 시 `transitionAlpha`와 visibility flag까지 정상화하자 두 타일이 모두
채워졌다. 다만 이 수신 시점을 실제 reenter transition 종료로 볼 수 없다는 검토 결과는 아래 후속 항목이다.
그 뒤 반환 callback에서 최초 타일의 `transitionName`을 잠시 분리하도록 보완했고, 사용자의 육안
확인에서 한 번의 깜박임도 사라졌다. 프레임 녹화로 판정한 결과는 아니다. `assembleDebug`,
`testDebugUnitTest`, `git diff --check`가 통과했다.

3인 검토에서 현재 Activity Result callback을 reenter transition 완료로 간주할 수 없고, holder
rebind/recycle 때 전환 상태와 이름을 정규화하지 않는다는 두 후속 항목을 확인했다. 추가 검증은
A→B→A, A→B→C, 화면 밖 B, swipe-down 취소·재시도, 뒤로가기·위 화살표·swipe-down 세 종료 경로,
return callback 2회, transition cancel/null, 반환 중 notify/rebind/recycle과 회전을 포함한다. API 23과
API 28에서는 reflection 성공·크래시 없음·최종 alpha/visibility를 각각 확인한다.

## 6. 바뀌는 파일

| 파일 | 변경 | 단계 |
|---|---|---|
| `viewer/media/MediaTransition.kt` | **신규** — 이름 규칙만 | 2 |
| `viewer/media/MediaViewerViewportCoordinator.kt` | **신규** — 내부 MEDIA 세션별 sequence number·최신 path와 READY/UNAVAILABLE 전달·replay·정리 | 8 |
| `res/values/themes.xml`·`themes_material3.xml` | `windowActivityTransitions` 명시, 7차에서 기존 Immersive base에 `windowIsTranslucent=true` 추가. 검정 background 유지 | 1·10 |
| `compat/ViewCompat.kt` | 공유 요소 `transitionAlpha`와 전환용 setter를 통한 visibility flag 정상화 호환 함수. API 29 이상 직접 호출, API 23~28 reflection | 8·11 |
| `filelist/FileListAdapter.kt` | `transitionName` 설정/해제, `filePositionMap` 읽기용 접근자. 11차 후속으로 bind/recycle 때 transition 상태 기본값과 세션의 A 이름 분리·B 일반 alpha 상태 재적용 | 2·11 |
| `filelist/FileListFragment.kt` | 미디어 모드 사진 라우팅, session 등록·복원, adapter/layout 변화 시 READY 무효화·재검증, 현재 경로 선행 스크롤·pre-draw ACK, 삼상태 return mapping과 original name 유지, 현재 빈칸과 최초 경로의 분리, 최초 타일 전환 상태·이름 복원, 최종 복귀 가드·스크롤 | 3·5·7·8·10·11 |
| `viewer/media/MediaViewerActivity.kt` | `fragment` 필드, `onSupportNavigateUp()`, **enter 콜백 등록**, 내부 진입 extra와 검정 window background 알파 API | 4·5·10 |
| `viewer/media/MediaViewerFragment.kt` | 종료 훅·상태기·입력 잠금, IDLE path 요청과 READY gate, `isReturning`, `hasSharedElement`, `currentPageContent()`, `transitionImage`, 드래그 변형·배경 제어, `PixelCopy` | 4·5·6·8·9·10 |
| `viewer/media/MediaViewerAdapter.kt` | **`allowHardware(false)` 두 줄만** (§3.5.1, D24) | 5 |
| `viewer/media/SwipeDownDismissLayout.kt` | 이동량 임계값과 폴더 READY 시작 조건, 누적 이동량 따라잡기, UP 실제 이동량 판정, 멀티터치 거부, 배경용 진행·취소 콜백 | 10 |
| `res/layout/media_viewer_fragment.xml` | `transitionImage` 추가. 별도 스크림 View는 추가하지 않음 | 5 |
| `filelist/FileListActivity.kt` | `onActivityReenter` 전달 (+ `isInitialized` 가드) | 7 |

5차까지는 `SwipeDownDismissLayout.kt`를 건드리지 않았지만, 7차에서는 시작 임계값 뒤 누적 이동량을
따라잡고 창 배경 알파용 진행 상태를 전달해야 하므로 변경했다. 확대 판정은 기존처럼 뷰에서 직접 읽는다.

⚠️ **`MediaViewerAdapter.kt` 도 건드리지 않을 작정이었다** — 로딩 상태는 §3.5대로 뷰에서 직접
읽으므로 콜백을 뚫을 일은 없었다. 다만 **하드웨어 비트맵만은 디코드 시점에 막는 수밖에 없어서**
`allowHardware(false)` 두 줄이 들어갔다 (§3.5.1).

## 7. 위험

| 위험 | 크기 | 대비 |
|---|---|---|
| **세 가지 실패가 화면으로 구분되지 않는다** | 중 | §5.1의 로그 넷을 5단계부터 찍어 둔다. 이것 없이 손대면 헤맨다 |
| 진입 스냅샷이 비어 온다 | 중 | 5단계에서 `drawable`/`background` 둘 다 읽어 판정하고, 그래도 비면 §3.5 대비책 |
| `postpone`/`start` 짝이 어긋나 화면이 멈춘다 | 중 | 8단계에서 로그로 짝 확인 + 시간 제한 |
| `FileListActivity` 가 파괴됐다 재생성된 뒤 복귀 | 중 | `isInitialized` · `view == null` / `!isAdded` 가드와 시간 제한. 11단계에서 확인 |
| 복귀 대상 타일의 썸네일이 아직 로드 전 | 소 | `doOnPreDraw` 는 레이아웃만 기다리고 Coil 로딩은 안 기다린다. 20장 밖 타일은 메모리 캐시에 없을 수 있어 **끝나는 순간 한 프레임 빈 타일**이 보일 수 있다. 자리와 크기는 맞으므로 치명적이지 않다 |
| 4단계가 화살표의 기존 종료 애니메이션을 바꾼다 | 소 | 의도한 것이지만 4단계에서 눈으로 확인 |
| 미디어 모드 사진 라우팅이 사용자 기대와 다르다 | 소 | 의도한 동작(D14). 리스트·바둑판은 그대로라 되돌릴 길이 있다 |
| 대용량 사진에서 `drawToBitmap()` 이 무겁다 | 소 | 전체 화면 크기 비트맵 한 장. 종료 시 한 번뿐 |
| 접힌/펼친 화면 전환 중 복귀 (Fold 7) | 소 | 11단계에서 같이 본다. 폴백으로 떨어져도 무방 |
| 기본 `move` 전환의 속도가 미디어 모드 느낌과 안 맞는다 | 소 | 12단계 |
| `PixelCopy`가 늦거나 실패해 종료가 멈춘다 | 중 | 500ms 시간 제한 뒤 그림 없는 일반 종료. API·Surface·크기·생명주기·현재 경로 가드 (§3.5.2) |
| 반투명 창이 공유 요소 전환·외부 VIEW 생명주기·메모리 사용을 바꾼다 | 중 | Immersive는 현재 뷰어만 사용한다. 검정 배경을 유지하고 §5.6의 enter/return·외부 VIEW·회전·"액티비티 유지 안 함" 매트릭스 확인. 문제가 크면 내부/외부 activity 분리 |
| 외부 앱이나 런처가 사진 아래로 드러난다 | 중 | 검정 window background를 초기부터 유지하고, `openMediaViewer()`의 내부 extra가 있을 때만 알파를 낮춘다 |
| 사진 취소와 배경 복귀가 어긋난다 | 중 | 같은 200ms·interpolator, 현재 page 소유권 확인, 새 drag/page 변경/view 파괴 때 animator 취소·alpha 255 복원 |
| 빠른 A→B→C에서 B의 늦은 pre-draw가 폴더를 되돌린다 | 높음 | sessionId + sequence + path 모두 최신일 때만 scroll 완료와 READY를 반영하고 나머지 오래된 응답은 폐기 |
| READY 전에 아래로 끌어 옛 폴더 타일이 노출된다 | 높음 | ViewPager IDLE과 READY(currentPath)를 시작 조건에서 함께 검사. WAIT은 사진 고정, BLACK_FALLBACK은 검정 유지 |
| 아래 RecyclerView layout·썸네일 로딩이 뷰어 스와이프/재생을 끊는다 | 중 | 최종 IDLE만 publish, 이미 보이면 no-op, smooth scroll 금지, 최신 요청 1회만 layout. 20~40장 연속 플링과 동영상 시작 프레임 계측 |
| coordinator가 파괴된 Fragment를 잡거나 복수 task 상태를 섞는다 | 높음 | 실행별 session token, lifecycle unregister·약한 owner, saved state 재등록/replay, 종료 시 세션 제거. 전역 current activity 금지 |
| A→B 반환에서 최초 이름 A를 가진 원본 View가 다시 숨거나 깜박인다 | 높음 | 최초 경로를 별도 보존하고 유효한 반환 매핑 직전에 A 이름을 잠시 떼며 표시 상태를 정상화. Activity Result가 아니라 reenter transition 종료/cancel에서 이름 복구. A→B·A→B→A를 프레임 단위 확인 (§3.4.7) |
| 반환 중 A holder가 detach/recycle되어 이름 분리나 전환 상태 복원이 다른 타일로 이동한다 | 높음 | transition 종료까지 A 경로·분리 상태를 유지하고 adapter bind/recycle/attach에서 기본 상태와 세션 예외를 재적용. cached holder position 무효화와 반환 중 notify 회귀 확인 (§3.4.7) |

## 부록. 확정된 결정 기록

| # | 질문 | 결정 |
|---|---|---|
| D1 | 열 때도 바꿀 것인가, 닫을 때만 바꿀 것인가 | **둘 다.** 공유 요소 API는 enter/return이 한 쌍이라 이 조합이 구현도 더 단순하다 |
| D2 | 어느 뷰 모드에 적용할 것인가 | **미디어 모드만.** 리스트의 작은 아이콘이 전체 화면으로 퍼지는 것은 산만하다 |
| D3 | 공유 요소를 무엇으로 삼을 것인가 | **뷰어 쪽 전용 `ImageView` 한 장.** 실제 뷰를 쓰면 `SSIV` 가 `ImageView` 가 아니라 `ChangeImageTransform` 이 통하지 않는다 (§3.1) |
| D4 | 그 `ImageView` 를 페이지에 둘 것인가, 프래그먼트에 둘 것인가 | **프래그먼트.** 페이지에 두면 `offscreenPageLimit = 1` 때문에 한 화면에 셋이 생긴다 |
| D5 | 액티비티를 유지할 것인가, 뷰어를 프래그먼트로 바꿀 것인가 | **액티비티 유지.** `MediaViewerActivity` 는 외부 `VIEW image/*` 진입점이라 남겨야 한다 |
| D6 | 프레임워크 전환 API를 쓸 것인가, 직접 만들 것인가 | **프레임워크.** 직접 만들면 돌아갈 사각형을 두 액티비티가 주고받고 창 배경까지 손으로 다뤄야 한다 |
| D7 | 복귀 위치를 위치(index)로 줄 것인가 경로로 줄 것인가 | **경로.** 뷰어에서 파일을 삭제하면 위치가 밀린다 |
| D8 | 짝이 없을 때 어떻게 할 것인가 | **조용히 예전 전환.** 알리지 않는다 (§3.7) |
| D9 | 확대된 사진에서도 전환할 것인가 | **하지 않는다.** 폴백 |
| D10 | `SwipeDownDismissLayout` 을 고칠 것인가 (1차 → **7차에서 뒤집음**) | 6차까지는 건드리지 않았지만 **7차에서 고쳤다.** 이동량 임계값 뒤 누적 이동량을 따라잡고 창 배경 알파용 진행 상태를 전달한다. 끌던 변형을 `transitionImage`가 이어받는 원칙은 유지한다 (§3.6·§3.6.1) |
| D11 | 10번 D4(`onBackPressedDispatcher`)를 유지할 것인가 | **유지.** 다만 **세 경로가 자동으로 모인다고 본 것은 틀렸다** — D17을 볼 것 |
| D12 | 동영상은 썸네일로 닫을 것인가 현재 프레임으로 닫을 것인가 | **현재 프레임.** 처음에는 `TextureView.bitmap`, HDR용 `SurfaceView` 전환 뒤에는 `PixelCopy`로 얻는다 (§3.5.2) |
| D13 | 기획서와 계획서를 나눌 것인가 | **나누지 않는다.** 새 파일 1개 + 고친 파일 8개 규모다 |
| D14 | 사진을 어떻게 뷰어로 보낼 것인가 (2차) | **미디어 모드에서만 명시 라우팅.** 그대로 두면 동영상만 새 전환을 탄다 (§3.0) |
| D15 | 뷰어 쪽 뷰에 `transitionName` 을 붙일 것인가 (2차) | **붙이지 않는다.** 복귀는 진입 때 실린 이름으로 짝을 찾으므로 도중에 바꾸면 깨진다 (§3.2) |
| D16 | `transitionImage` 를 평소에 감출 것인가 (2차, 3차 근거 정정) | **감추지 않는다.** 이유는 `findNamedViews()` 가 아니라 **`ChangeImageTransform`·`ChangeBounds` 의 캡처가 `VISIBLE` 만 본다**는 것이다. `drawable` 을 비우는 것으로 대신한다 (§3.1) |
| D17 | 툴바 화살표를 어떻게 할 것인가 (3차) | **`MediaViewerActivity.onSupportNavigateUp()` 을 재정의해 디스패처로 보낸다.** 지금은 `AppActivity` 의 `finish()` 로 가서 복귀 전환을 돌지 않는다 (§0) |
| D18 | 폴백을 어디서 끊을 것인가 (3차) | **그리드와 뷰어 양쪽.** `RESULT_CANCELED` 는 재매핑만 막고 복귀 전환 자체는 돈다 (§3.7) |
| D19 | enter 콜백을 어디에 등록할 것인가 (3차 → **4차에서 뒤집음**) | **`MediaViewerActivity.onCreate`.** 코디네이터가 생성자에서 리스너를 붙잡으므로 프래그먼트 `onActivityCreated` 는 한 박자 늦다. 등록을 늦출 것이 아니라 **뷰 찾기를 늦춘다** (§3.2.2) |
| D20 | 폴백 가드를 언제 적용할 것인가 (4차) | **`isReturning` 일 때만.** 진입에서는 `onMapSharedElements` 가 `onSharedElementStart` 보다 앞서므로 `drawable` 이 당연히 비어 있다. 방향 구분 없이 걸면 **열기 전환이 죽는다** (§3.2.1) |
| D22 | 전환이 끝난 것을 어떻게 알 것인가 (5차) | **`window.sharedElementEnterTransition` 의 `TransitionListener.onTransitionEnd`.** 4차가 고른 `onSharedElementEnd` 는 애니메이션 **시작 전에** 불린다 (§3.3.2) |
| D23 | 진입 중 페이지를 감출 것인가 (5차) | **감춘다 — `alpha = 0f`.** 안 감추면 최종 크기의 사진 위로 타일이 자라 같은 그림이 둘 보인다. 알파여야 감춘 동안에도 로딩이 끝난다 (§3.3.1) |
| D24 | 하드웨어 비트맵을 어떻게 할 것인가 (5차) | **`allowHardware(false)` 로 처음부터 막는다**, 그리드와 뷰어 양쪽. 나중에 복사하는 길은 `CrossfadeDrawable` 때문에 없다 (§3.5.1) |
| D25 | 폴백에서도 페이지를 감출 것인가 (5차) | **감추지 않는다.** 평범한 창 애니메이션 내내 빈 화면이 날아간다 (§3.7) |
| D21 | 로딩 완료를 어떻게 알 것인가 (4차) | **뷰 상태를 직접 읽는 `currentPageContent()` 하나로.** `MediaViewerAdapter` 에 콜백을 뚫지 않는다. 닫을 때의 그림·페이드아웃 시점·폴백 판정 셋이 같은 통로를 쓴다 (§3.5) |
| D26 | `SurfaceView` 동영상의 현재 프레임을 어떻게 얻을 것인가 (6차) | **`PixelCopy`로 비동기 복사한다.** 500ms 안에 성공하지 않거나 대상이 바뀌면 그림 없는 일반 종료로 폴백한다 (§3.5.2) |
| D27 | 드래그 중 폴더 화면을 공유 요소 전환으로 보여 줄 것인가 (7차) | **아니다.** 반투명 뷰어 창의 검정 `windowBackground` 알파를 낮춰 아래 `FileListActivity`를 보여 주고, 공유 요소 복귀는 손을 놓아 닫힘이 확정된 뒤에만 시작한다 (§3.6.1) |
| D28 | 검정 배경을 레이아웃 스크림으로 옮길 것인가 (7차 검토) | **옮기지 않는다.** 외부 VIEW의 inflate 전 노출과 기존 enter coordinator 전제를 지키기 위해 검정 `ColorDrawable` window background를 유지하고 그 알파만 바꾼다 |
| D29 | 좌우로 넘긴 현재 타일이 화면 밖이면 언제 폴더를 스크롤할 것인가 (9차) | **페이지가 IDLE이 될 때 미리 스크롤한다.** 종료할 때 처음 움직이면 swipe-down 중 옛 뷰포트가 보인다. final `onActivityReenter` 스크롤은 안전망으로 유지한다 (§3.4) |
| D30 | 좌우 이동 후 공유 요소 이름을 현재 경로로 바꿀 것인가 (9차) | **바꾸지 않는다.** 최초 ActivityOptions 이름은 불변 프로토콜 키이고 현재 경로는 대상 View 선택에만 쓴다. 성공 callback은 `sharedElements[originalName] = currentTile`만 한다 (§3.2·§3.4.6) |
| D31 | 좌우 이동 뒤 어느 타일을 비울 것인가 (10차) | **현재 타일만 비운다.** 최신 타일이 READY 되기 전에 최초/직전 타일을 복원하고 현재 썸네일을 숨긴 뒤에만 폴더 선노출을 허용한다 (§3.4.3) |
| D32 | 현재 타일 B로 반환할 때 최초 타일 A의 공유 요소 상태를 어떻게 할 것인가 (11차) | **유효한 B 매핑을 만들 때만 A View를 잠시 분리하고 표시 상태를 정상화한다.** 프로토콜 키 A는 유지한다. A 이름과 B 빈칸은 Activity Result 수신이 아니라 reenter transition 종료/cancel 뒤 복구하며, holder 재활용에도 같은 세션 상태를 재적용한다 (§3.4.7) |
