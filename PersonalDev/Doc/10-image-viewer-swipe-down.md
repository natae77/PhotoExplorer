# 10. 미디어 뷰어 — 아래로 스와이프해서 닫기

미디어 뷰어에서 **아래로 빠르게 튕기면(flick) 뒤로가기와 똑같이 닫히게** 한다.
**사진 페이지와 동영상 페이지 모두**에 적용한다.

이 문서는 기획과 구현 방법을 함께 담는다. 08·09번이나 11·12번처럼 나누지 않은 이유는 §7에 있다.

- 작성일: 2026-08-28 / 최종 개정: **2026-08-28 (3차)**
- 프로젝트: **PhotoExplorer** (`natae77/PhotoExplorer`, `zhanghai/MaterialFiles` fork)
- 대상 버전: 1.7.4 (versionCode 39) 기준 소스 + 동영상 뷰어(11·12번) 적용 후
- 상태: **구현 완료 · 에뮬레이터 검증 통과(13건), 실기기 확인 남음** — §5.1

**개정 이력** — 이 문서는 개정할 때 새 문서를 만들지 않고 **본문을 직접 고친다.**

| 개정 | 날짜 | 무엇이 바뀌었나 |
|---|---|---|
| 3차 | 2026-08-28 | **리베이스 완료를 반영.** `feature/swipe-down` 을 `feature/video-viewer` 위로 옮기면서 사진·동영상 양쪽을 한 커밋으로 구현했다. §8을 "할 일"에서 "한 일과 거기서 나온 함정"으로 다시 썼다. §4.6에 공유 확장 함수 명시. **에뮬레이터 검증 결과와 거기서 걸린 함정을 §5.1에 기록** |
| 2차 | 2026-08-28 | **동영상 뷰어(11·12번) 적용 후 기준으로 다시 씀.** 대상이 `ImageViewer*` → `MediaViewer*`로 바뀌었고 **동영상 페이지가 추가**됐다. 11번 D12("합쳐진 뒤에 정한다")를 **적용하는 것으로 결정**(D8). 재생 컨트롤·재생 상태와의 관계를 §3·§4.4에 넣었다. 브랜치 정리 항목 §8 추가 |
| 1차 | 2026-08-28 | 최초 작성. 사진 전용 뷰어(`ImageViewerAdapter`/`ImageViewerFragment`) 기준 |

> **08번과의 관계**: 08번 기획서 §2는 "사진 뷰어/편집기 개선"을 **비목표**로 뒀다.
> 이 문서는 그 비목표를 뒤집는 것이 아니라 **별건**으로 다루는 것이다.
> 미디어 보기 모드(08·09)의 어떤 단계에도 의존하지 않는다.
>
> **11번과의 관계**: 11번 기획서는 이 제스처를 **비목표(D12)** 로 두면서
> "두 작업이 합쳐진 뒤에 동영상 페이지에도 적용할지 그때 정한다"고 남겼다.
> **이 문서가 그 결정이다 — 적용한다**(D8). 11번 §2 비목표와 D12는 이 결정에 맞춰
> 고치는 편이 낫지만, 그것은 11번 문서의 개정이므로 여기서 하지 않는다.
>
> **파일 이름을 그대로 둔 이유**: 뷰어가 `ImageViewer*` → `MediaViewer*` 로 바뀌었으니
> 이 문서도 `10-media-viewer-swipe-down.md` 가 맞다. 다만
> [11번 §2](11-video-viewer-spec.md)와 [12번 §0](12-video-viewer-plan.md)가
> 이 파일을 **현재 이름으로 링크**하고 있어서, 그 두 곳을 같이 고치기 전에는 이름을 바꾸지 않는다.

## 1. 배경

파일 목록에서 사진이나 동영상을 누르면 `MediaViewerActivity`가 전체 화면으로 뜬다
([MediaViewerFragment.kt](../../app/src/main/java/me/zhanghai/android/files/viewer/media/MediaViewerFragment.kt)).
이 화면은 몰입 모드(immersive)로 들어가면서 상태 표시줄과 앱 바가 사라지므로, 닫으려면
**화면을 한 번 탭해서 앱 바를 꺼낸 다음 ← 를 누르거나**, 시스템 뒤로가기 제스처를 써야 한다.
여러 장을 넘겨보다 빠져나올 때 이 두 단계가 번거롭다.

갤러리 앱들은 대부분 **아래로 스와이프 = 닫기**를 쓴다. 뷰어는 좌우로 페이지를 넘기므로
세로 제스처가 비어 있어, 기존 조작과 겹치지 않고 넣을 수 있다.

동영상 뷰어가 들어오면서 세로 방향에 **재생 컨트롤**이 생겼지만, 컨트롤은 화면 아래쪽에
따로 놓인 뷰라 페이지의 터치와 섞이지 않는다(§4.4).

## 2. 목표 / 비목표

**목표**

1. 미디어 뷰어에서 아래로 빠르게 튕기면 뒤로가기 버튼과 **완전히 동일하게** 닫힌다.
2. **사진 페이지와 동영상 페이지에서 똑같이** 동작한다. 페이지 종류에 따라 되고 안 되고가 갈리지 않는다.
3. 기존 조작을 **하나도 건드리지 않는다** — 좌우 페이지 넘김, 탭으로 앱 바·컨트롤 토글,
   더블탭 확대, 확대 후 끌기, 재생 슬라이더 드래그.

**비목표 (이번 범위 아님)**

- **손가락을 따라 이미지가 내려가고 배경이 흐려지는 드래그 추종 방식**(구글 포토식). D1 참고.
- 위로 스와이프에 기능 부여(예: 세부 정보 열기).
- 텍스트 뷰어 등 다른 뷰어 화면.
- 제스처 켜기/끄기 설정 항목.
- **재생 중에 세로로 끌어 밝기·음량 조절**(일부 플레이어 앱의 동작). 세로 제스처는 닫기에만 쓴다.

## 3. 동작 정의

**발동 조건** — 아래를 **모두** 만족할 때만 닫는다.

| # | 조건 | 사진 | 동영상 | 이유 |
|---|---|---|---|---|
| 1 | 플릭 방향이 아래쪽 (`velocityY > 0`) | O | O | 위로 튕기는 것은 아무 일도 하지 않는다 |
| 2 | 세로 속도 > 가로 속도 (`abs`) | O | O | 좌우 페이지 넘김과 섞이지 않게 |
| 3 | 세로 속도가 임계값 이상 | O | O | 천천히 끄는 것은 무시. 임계값은 §4.5 |
| 4 | 손가락이 **하나** | O | O | 핀치 줌 동작을 플릭으로 오인하지 않게 |
| 5 | **확대돼 있지 않다** | O | **해당 없음** | 확대 후 아래로 미는 것은 **이동(pan)** 이지 닫기가 아니다. 동영상 페이지에는 확대·이동이 없다 |

**발동하지 않는 경우**

| 상황 | 동작 |
|---|---|
| 사진이 확대된 상태 | 기존 pan 그대로. 닫히지 않는다 |
| 좌우 스와이프 | 기존 페이지 넘김 그대로 |
| 위로 스와이프 | 아무 일도 없음 |
| 두 손가락 | 기존 핀치 줌 그대로 |
| **재생 컨트롤 위에서 시작한 스와이프** | 컨트롤이 먹는다. 페이지까지 내려오지 않는다(§4.4). **슬라이더를 아래로 드래그해도 닫히지 않는다** |
| **"다른 앱으로 열기" 버튼 위** | 버튼이 먹는다 |
| 사진 로딩 실패(오류 문구 표시 중) | 발동하지 않음. 확대 여부를 판정할 이미지가 없다 |
| 재생 실패 화면(버튼 밖 영역) | **발동한다.** 닫는 것 말고 할 일이 없는 화면이라 막을 이유가 없다 |

**재생 중에 닫으면**

| 항목 | 동작 |
|---|---|
| 재생 | 뒤로가기와 동일하게 멈추고 플레이어가 해제된다. 별도 처리 없음 |
| 재생 위치 | **잊는다.** 뷰어를 닫으면 세션이 끝나므로 11번 §5.4 규칙 그대로다 |
| 배속 | 1×로 돌아간다. 11번 §6.3 그대로다 |

**닫는 방법**

`activity.onBackPressedDispatcher.onBackPressed()` 를 호출한다. `finish()` 가 아니다.
뒤로가기 버튼과 **같은 경로**를 타야 종료 애니메이션과 `OnBackPressedCallback` 처리가
그대로 유지된다(D4). 이 프로젝트는 이미 같은 디스패처를 쓴다
([FragmentExtensions.kt:38](../../app/src/main/java/me/zhanghai/android/files/util/FragmentExtensions.kt#L38)).

## 4. 구현

### 4.1 ⚠️ 페이지의 터치 모델이 **세 가지**다 — 전부 붙여야 한다

뷰어 페이지는 어댑터의 뷰 타입에 따라 두 레이아웃으로 갈리고, 사진 레이아웃 안에서 또 두 뷰로 갈린다
([MediaViewerAdapter.kt:55](../../app/src/main/java/me/zhanghai/android/files/viewer/media/MediaViewerAdapter.kt#L55)).

| 페이지 | 터치를 받는 뷰 | 언제 쓰나 | 출처 |
|---|---|---|---|
| 사진 | `PhotoView` | 보통 크기 이미지, GIF | `com.github.chrisbanes:PhotoView:2.3.0` ([app/build.gradle:116](../../app/build.gradle#L116)) |
| 사진 | `SubsamplingScaleImageView` | 대용량·극단적 비율 이미지 | `subsampling-scale-image-view-androidx:3.10.0` ([app/build.gradle:152](../../app/build.gradle#L152)) |
| 동영상 | **루트 `FrameLayout`** | `isPlayableVideo` 인 경로 | [media_viewer_video_item.xml](../../app/src/main/res/layout/media_viewer_video_item.xml) |

사진 쪽 판정은 `MediaViewerAdapter.shouldUseLargeImageView` 가 한다(100MB 초과 또는 2048px 초과 + 비율 2:1 이상).
**한 곳이라도 빠뜨리면 "어떤 것은 되고 어떤 것은 안 되는" 버그**가 된다. 이 작업에서 가장 놓치기 쉬운 지점이다.

세 뷰가 터치를 다루는 방식이 달라서 붙이는 방법도 다르다.

### 4.2 `PhotoView` — 라이브러리 API를 쓴다

`setOnSingleFlingListener()` 를 쓴다. 이 콜백은 라이브러리가 **조건 4·5를 미리 걸러준 뒤에만** 호출한다.
`PhotoView-2.3.0-runtime.jar` 를 역어셈블해 확인한 `PhotoViewAttacher` 의 실제 동작:

```java
public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
    if (mSingleFlingListener != null) {
        if (getScale() > DEFAULT_MIN_SCALE) return false;              // 확대 중이면 호출 안 함
        if (e1.getPointerCount() > SINGLE_TOUCH
                || e2.getPointerCount() > SINGLE_TOUCH) return false;  // 두 손가락이면 호출 안 함
        return mSingleFlingListener.onFling(e1, e2, velocityX, velocityY);
    }
    return false;
}
```

즉 이쪽은 **조건 1·2·3만 직접 검사**하면 된다. `DEFAULT_MIN_SCALE` 은 `1.0f` 상수이고,
PhotoView의 scale은 "화면에 맞춘 상태 = 1.0" 기준이라 `scale > 1.0`이 곧 확대 상태다.
[bindImage()](../../app/src/main/java/me/zhanghai/android/files/viewer/media/MediaViewerAdapter.kt#L88)의
기존 탭 리스너 옆에 붙인다.

### 4.3 `SubsamplingScaleImageView` — `GestureDetector`로 엿본다

이쪽엔 대응 API가 없다. `GestureDetector` 를 만들어 `setOnTouchListener` 로 붙이되,
**항상 `false` 를 반환해서 관찰만 하고 이벤트는 원래대로 흘려보낸다.**
`View.dispatchTouchEvent()` 는 `OnTouchListener` 를 먼저 부르고, 그것이 `false` 면
`onTouchEvent()` 를 이어서 부르기 때문에 뷰의 기존 제스처가 그대로 산다.

- SSIV는 내부에서 `setOnTouchListener` 를 쓰지 않는다(3.10.0 바이트코드 확인). 자리를 뺏지 않는다.
- 확대 판정은 `getScale() > getMinScale()` 로 한다. 둘 다 `public final` 이다.
  부동소수 비교이므로 **작은 여유값**을 둔다(예: `scale > minScale * 1.01f`).
- 이미지가 아직 준비 안 됐으면 `isReady()` 가 `false` 다. 이때는 발동하지 않는다.

### 4.4 동영상 페이지 — 루트 `FrameLayout`에 붙인다

4.3과 **같은 방식**(`GestureDetector` + 소비하지 않는 `setOnTouchListener`)을 쓰되,
붙이는 곳은 페이지의 **루트 `FrameLayout`** 이다. 확대 판정은 하지 않는다 — 동영상 페이지에는
확대·이동이 없다.

⚠️ **루트에 붙이는 것이 되는 이유**: `ViewGroup` 의 `OnTouchListener` 는 자식이 이벤트를
소비하지 않을 때만 불린다. 동영상 페이지는 이 조건을 이미 만족한다 — `PlayerView` 가
`app:use_controller="false"` 라 자체 탭 처리를 하지 않고(11번 §6.2), 썸네일은 그냥 `ImageView` 다.
그래서 지금도 **탭해서 앱 바를 토글하는 리스너가 루트에 걸려 있고 잘 동작한다**
([bindVideo() 95행](../../app/src/main/java/me/zhanghai/android/files/viewer/media/MediaViewerAdapter.kt#L95)).
탭이 도달하는 곳이면 플릭도 도달한다. 그래도 §6-5는 반드시 실기기로 확인한다.

**재생 컨트롤과는 자동으로 분리된다.** `PlayerControlView` 는 페이지가 아니라
**프래그먼트 레이아웃**에 있고 `ViewPager2` 위에 겹쳐 있다
([media_viewer_fragment.xml](../../app/src/main/res/layout/media_viewer_fragment.xml), 12번 §4.1).
컨트롤 영역(`minHeight 220dp`)에서 시작한 터치는 컨트롤이 가져가므로 페이지의 리스너까지
내려오지 않는다. **슬라이더를 아래로 드래그해도 뷰어가 닫히지 않는다.** 따로 막을 코드가 필요 없다.

⚠️ **`onFling` 의 첫 인자는 null이 될 수 있다.** `compileSdk 36` 이라 플랫폼 시그니처가
`onFling(e1: MotionEvent?, e2: MotionEvent, ...)` 로 잡힌다(API 34에서 nullable로 바뀌었다).
`e1` 을 참조한다면 반드시 null을 처리한다. 4.2의 PhotoView 인터페이스는 라이브러리 자체
인터페이스라 플랫폼 타입으로 들어온다 — 여기서도 방어적으로 다룬다.

### 4.5 임계값

다섯 조건 중 3번(속도)만 값을 정해야 한다.

- 기준: `ViewConfiguration.get(context).scaledMinimumFlingVelocity` **× 4**
- `scaledMinimumFlingVelocity` 는 밀도가 반영된 값이라 기기별로 알아서 맞는다.
  `GestureDetector` 가 `onFling` 을 부르는 최소 조건이 이 값 자체라 그대로 쓰면 너무 헐거워, 배수를 둔다.
- **이 값은 튜닝 대상이다.** 실기기에서 의도치 않게 닫히는 일이 잦으면 배수를 올리고,
  잘 안 닫히면 내린다. 상수 하나로 빼 두어 고치기 쉽게 한다.
- 세 경로가 **같은 상수**를 쓴다. 페이지 종류에 따라 손맛이 달라지면 안 된다.

### 4.6 변경할 파일

| 파일 | 변경 |
|---|---|
| [MediaViewerAdapter.kt:43](../../app/src/main/java/me/zhanghai/android/files/viewer/media/MediaViewerAdapter.kt#L43) | 생성자에 `onSwipeDown: () -> Unit` 추가. `bindImage()` 에서 `PhotoView` 에 fling 리스너, `onCreateViewHolder()` 의 두 갈래에서 각각 `SubsamplingScaleImageView` 와 동영상 루트에 `GestureDetector` 연결. 속도 판정 헬퍼와 상수를 한 곳에 둔다 |
| [MediaViewerFragment.kt:138](../../app/src/main/java/me/zhanghai/android/files/viewer/media/MediaViewerFragment.kt#L138) | 어댑터 생성 시 콜백 전달 → `activity.onBackPressedDispatcher.onBackPressed()` |

레이아웃·문자열·설정·의존성 변경 없음. 새 파일 없음.

`GestureDetector` 는 **`onCreateViewHolder` 에서 `ViewHolder` 당 하나만** 만든다.
`onBindViewHolder` 는 페이지를 넘길 때마다 불리므로 거기서 만들면 매번 새로 할당된다.

SSIV와 동영상 루트는 붙이는 방식이 같으므로 어댑터 안에 확장 함수
`View.setSwipeDownDetector(isSwipeAllowed: () -> Boolean)` 하나를 두고 둘 다 그것을 쓴다.
달라지는 것은 넘기는 람다뿐이다 — SSIV는 "준비됐고 확대 안 됨", 동영상은 `{ true }`.

**커스텀 루트 레이아웃을 만들지 않는 이유**(D5): `media_viewer_fragment.xml` 의 루트를
커스텀 뷰로 바꿔 `dispatchTouchEvent` 에서 한 번에 처리하는 방법도 있다. 그러면 제스처 검사는
한 곳에 모이지만, **확대 여부 판정은 결국 현재 페이지의 뷰를 찾아 종류별로 분기해야 한다.**
`ViewPager2` 안의 `RecyclerView` 에서 현재 `ViewHolder` 를 꺼내는 코드가 추가로 필요한데,
프래그먼트에 이미 그런 코드(`videoBindingAt()`)가 있고 12번 §3.2가 "화면 밖 페이지는 뷰가 아예
없을 수 있다"고 경고할 만큼 조심스러운 부분이다. 어댑터는 이미 뷰 종류별로 분기하고 있고
콜백을 받는 패턴도 이미 있다.

## 5. 검증

이 프로젝트에는 **테스트 소스셋이 없다.** `app/src` 아래에 `main` 뿐이고
`test`/`androidTest` 디렉터리도, JUnit·Espresso 의존성도 없다. 이 기능은 실제 터치 제스처가
대상이라 계측 테스트가 필요한데, 그 기반을 새로 까는 것은 이 작업 범위 밖이다
(12번 §0도 같은 판단을 했다). 따라서 **컴파일 통과 + 실기기 수동 확인**으로 검증한다.

```
ANDROID_HOME="C:\Users\hskang\AppData\Local\Android\Sdk" JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```

`local.properties` 가 없으면 `ANDROID_HOME` 을 줘야 한다.

실기기 확인 항목은 §6과 같다. **§6의 3·4·5번은 서로 다른 코드 경로를 타므로 반드시 셋 다** 해야 한다(§4.1).
큰 사진과 동영상은 `D:\Work\SwingTestData` 를 쓰면 된다.

### 5.1 에뮬레이터로 자동 검증할 수 있다

`adb shell input swipe` 가 플릭을 만들어 주므로 §6의 대부분은 손으로 하지 않아도 된다.
닫혔는지는 `dumpsys window | grep mCurrentFocus` 가 `MediaViewerActivity` 인지
`FileListActivity` 인지로 판정한다.

2026-08-28에 Pixel 8 / API 36 에뮬레이터에서 13건을 돌려 전부 통과했다 —
세 경로의 닫힘, 확대·느린 드래그·위쪽·대각선·컨트롤 위에서의 미발동,
확대를 푼 뒤 다시 닫히는 것, 재생 중 종료 후 오디오 미점유까지.

⚠️ **여기서 걸린 함정 세 가지.** 다시 할 때 그대로 겪는다.

**1. 기존 테스트 이미지로는 `PhotoView` 경로를 하나도 못 덮는다.**
`photo_*.png` · `emu*.png` 가 **전부 1080×2400**이다. 세로가 2048을 넘고 비율이 0.45라
§4.1의 조건에 걸려 **죄다 `SubsamplingScaleImageView` 로 간다.**
`PhotoView` 를 태우려면 가로·세로가 모두 2048 이하이거나 비율이 0.5~2 인 이미지를
따로 만들어야 한다(1200×900을 썼다).

**2. 1080×2400 이미지는 더블탭해도 확대되지 않는다.**
화면과 크기가 같아 `cropScale == minScale` 이라 확대할 여지가 없다. 제스처가 안 먹는 것이
아니라 **확대가 안 되는 것**이라, 모르고 보면 확대 가드가 통과한 것처럼 착각한다.
§3 조건 5를 진짜로 검사하려면 **비율이 극단적인 이미지**가 필요하다
(4000×1000을 썼다 — 최소 배율 0.27, 더블탭 배율 2.4).

**3. 더블탭은 두 탭 사이에 0.15초를 넣어야 만들어진다.**
`adb shell "input tap X Y; input tap X Y"` 처럼 붙여 쓰면 두 이벤트가 너무 가까워
한 번으로 합쳐지고, `adb shell input tap` 을 **두 번 나눠 호출**하면 이번엔 간격이
더블탭 인식 시간을 넘긴다. `adb shell "input tap X Y; sleep 0.15; input tap X Y"` 가 맞다.

## 6. 수용 기준 (Acceptance Criteria)

1. 뷰어에서 아래로 빠르게 튕기면 닫히고, **뒤로가기로 닫았을 때와 같은 화면·같은 애니메이션**으로 돌아간다.
2. 위로 튕기면 아무 일도 일어나지 않는다.
3. **보통 크기 사진**(`PhotoView` 경로)에서 닫힌다.
4. **대용량 사진**(`SubsamplingScaleImageView` 경로)에서도 똑같이 닫힌다.
5. **동영상 페이지**에서도 똑같이 닫힌다. 재생 중이든 일시정지든 상관없다.
6. 세 경로의 **손맛이 같다.** 한쪽만 유독 잘 닫히거나 안 닫히지 않는다.
7. **재생 컨트롤이 보이는 상태에서 슬라이더를 아래로 드래그해도 닫히지 않는다.**
   컨트롤 위에서 시작한 어떤 세로 스와이프도 뷰어를 닫지 않는다.
8. 좌우 스와이프로 페이지를 넘기는 동작이 그대로다. 비스듬히 넘겨도 닫히지 않는다.
9. 화면을 탭하면 앱 바와 컨트롤이 토글되는 동작이 그대로다(사진·동영상 모두).
10. 더블탭으로 확대한 뒤 **아래로 끌면 이미지가 이동만 하고 닫히지 않는다.** 3·4번 두 경로 모두.
11. 두 손가락으로 핀치 줌 하는 중에 닫히지 않는다.
12. 동영상 재생 중에 닫으면 **소리가 남지 않고** 플레이어가 해제된다.
13. 여러 장을 넘겨본 뒤 닫아도 파일 목록의 스크롤 위치가 흐트러지지 않는다.

## 7. 왜 기획서/계획서를 나누지 않았나

08·09번과 11·12번은 정렬·캐시·플레이어 수명까지 건드리는 큰 작업이라 "무엇을"과 "어떻게"를
분리할 값어치가 있었다. 이 기능은 **파일 2개, 새 파일 0개**다. 두 문서로 나누면 양쪽을
오가며 읽어야 하는 비용만 늘어난다. 나중에 드래그 추종 방식(D1)으로 확장하기로 하면
그때 이 문서를 개정하거나 계획서를 따로 뗀다.

## 8. 브랜치 정리 — 리베이스로 끝냈다 (기록)

1차 구현은 `feature/swipe-down` 에 `ImageViewer*` 를 고치는 커밋 하나로 들어가 있었고,
그 사이 동영상 뷰어 작업이 그 파일들을 `MediaViewer*` 로 바꿔 놓았다.
**`feature/swipe-down` 을 `feature/video-viewer` 위로 리베이스해서 정리했다.**

리베이스 결과는 커밋 하나이고, 사진 경로와 동영상 경로가 함께 들어 있다.
그 과정에서 나온 것들을 남겨 둔다 — 비슷한 이름 변경이 또 있을 때 쓸 수 있다.

| 파일 | 무슨 일이 있었나 |
|---|---|
| `ImageViewerAdapter.kt` → `MediaViewerAdapter.kt` | **git이 이름 변경을 스스로 찾아냈다.** 내용 충돌만 세 군데 났고, 위치는 전부 맞게 옮겨져 있었다 |
| `ImageViewerFragment.kt` → `MediaViewerFragment.kt` | **못 찾았다.** 동영상 작업으로 내용이 너무 많이 바뀌어(220줄 → 575줄) 유사도 기준에 못 미친 것으로 보인다. `modify/delete` 충돌로 떨어져서, 삭제를 택하고 어댑터 호출부를 손으로 옮겼다 |
| `PersonalDev/Doc/README.md` | 같은 표에 양쪽이 줄을 넣어 충돌. 동영상 쪽(11·12번이 있는 판)을 택했다 |
| `PersonalDev/Doc/08-media-view-mode-spec.md` | 충돌 없이 그대로 얹혔다 |

⚠️ **이름 변경을 따라간 자동 병합이 코드를 두 번 넣을 수 있다.**
`companion object` 의 상수 두 개가 **중복 선언**돼서 컴파일이 깨졌다
(`Conflicting declarations`, 그리고 그 상수를 쓰는 자리마다 `Overload resolution ambiguity`).
충돌 표시(`<<<<<<<`)가 없는 자리라 눈에 띄지 않는다.
**충돌 표시를 다 지운 뒤에도 반드시 컴파일해 봐야 한다.**

동영상 페이지분(D8)은 이 리베이스에서 **함께** 넣었다. 어차피 손으로 옮겨야 하는 코드였고,
사진만 되는 중간 상태를 커밋으로 남길 이유가 없었다.

## 부록. 확정된 결정 기록

| # | 질문 | 결정 |
|---|---|---|
| D1 | 플릭으로 즉시 닫을 것인가, 손가락을 따라오는 드래그 방식인가 | **플릭 즉시 닫기.** 드래그 추종은 세 터치 모델을 각각 가로채고 배경 알파·복귀 애니메이션까지 직접 다뤄야 해서, 얻는 것에 비해 기존 제스처를 깨뜨릴 위험이 크다. 써 보고 아쉬우면 그때 확장한다 |
| D2 | 위로 스와이프에도 기능을 줄 것인가 | **주지 않는다.** 아래로만. 쓸 곳이 정해지지 않은 제스처를 미리 점유하지 않는다 |
| D3 | 확대 상태에서도 발동시킬 것인가 | **발동하지 않는다.** 확대 후 아래로 미는 것은 이동(pan)이다. 여기서 닫히면 사진을 자세히 보는 동작 자체가 불가능해진다 |
| D4 | `finish()` 인가 `onBackPressedDispatcher.onBackPressed()` 인가 | **디스패처.** "뒤로가기 버튼과 같은 효과"가 요구사항이므로 같은 경로를 타야 한다. 나중에 뷰어에 `OnBackPressedCallback` 이 생겨도 저절로 존중된다 |
| D5 | 제스처 검사를 루트 레이아웃 한 곳에 모을 것인가 | **모으지 않는다.** 확대 판정이 뷰 종류별로 갈리므로 어차피 분기가 필요하다. 어댑터에 두는 편이 기존 구조와 맞는다 (§4.6) |
| D6 | 속도 임계값 | `scaledMinimumFlingVelocity × 4` 로 시작. **실기기 튜닝 대상**이며 상수로 뺀다 (§4.5) |
| D7 | 제스처 켜기/끄기 설정을 둘 것인가 | **두지 않는다.** 확대 중에는 발동하지 않으므로 끄고 싶어질 상황이 잘 그려지지 않는다. 불만이 나오면 그때 만든다 |
| D8 | **동영상 페이지에도 적용할 것인가** (11번 D12가 남긴 질문) | **적용한다.** 같은 뷰어 안에서 사진은 닫히고 동영상은 안 닫히면 그게 더 이상하다. 동영상 페이지에는 확대가 없어 조건 5가 빠지므로 오히려 단순하다 (§3·§4.4) |
| D9 | 재생 중에도 닫을 것인가 | **닫는다.** 뒤로가기와 같은 경로를 타므로 재생 정지·플레이어 해제는 기존 처리가 그대로 한다. 별도 확인 없이 바로 닫는다 |
| D10 | 재생 컨트롤 위의 세로 스와이프를 따로 막을 것인가 | **따로 막지 않는다.** 컨트롤이 프래그먼트 레이아웃에 있어 페이지보다 위에 있고, 터치를 먼저 가져간다. 코드로 막을 것이 없다 (§4.4) |
