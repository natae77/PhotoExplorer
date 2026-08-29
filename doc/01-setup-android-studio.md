# 01. Android Studio 설치와 SDK 구성

## 프로젝트가 요구하는 버전 (근거: `app/build.gradle`)

```groovy
buildToolsVersion = '37.0.0'
compileSdk = 36
ndkVersion '28.1.13356709'
minSdk 23
targetSdk 34
```

루트 `build.gradle` / `gradle-wrapper.properties`:

```
com.android.tools.build:gradle : 9.1.0
kotlin_version                 : 2.3.20
Gradle                         : 9.3.1
```

**AGP 9.1.0은 최신 Android Studio가 아니면 열리지 않는다.** 구버전 Studio를 쓰면
`Unsupported class file major version` 류 오류가 난다.

> `compileSdk`와 `buildToolsVersion`은 별개다. compileSdk가 36이어도
> Build-Tools는 **37.0.0**을 설치해야 한다 (프로젝트가 명시적으로 고정).

## JDK

**별도로 설치하지 말 것.** Android Studio 내장 JBR(JDK 21)을 쓴다.
CI(`.github/workflows/android.yml`)도 JDK 21을 쓴다.

커맨드라인에서 `gradlew`를 쓸 때만 경로를 알려주면 된다:

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```

또는 `gradle.properties`에 추가:

```
org.gradle.java.home=C:/Program Files/Android/Android Studio/jbr
```

## 설치 절차

### 1) Android Studio

```bash
winget install -e --id Google.AndroidStudio
```

설치는 **두 개의 마법사**가 연달아 나온다. 헷갈리기 쉬움:

| 단계 | 창 제목 | 하는 일 |
|---|---|---|
| 1차 | `Android Studio Setup` | 프로그램 파일 복사만. SDK는 안 받음 |
| 2차 | `Android Studio Setup Wizard` | SDK 기본 다운로드 |

2차 마법사는 버전에 따라 화면이 다르다. Import Settings 창이 생략되거나,
아예 마법사 없이 Welcome 화면으로 바로 가기도 한다 — 정상이다.
어느 쪽이든 목표는 Welcome 화면 도달.

### 2) SDK Manager

`More Actions ▾` → `SDK Manager` (또는 `Tools` → `SDK Manager`)

> ⚠️ **`Show Package Details`는 탭마다 따로 켜야 한다.**
> 한쪽 탭에서 켜도 다른 탭에는 적용되지 않는다. 이걸 안 켜면 세부 버전을
> 고를 수 없고, NDK가 최신 버전(29.x 등)으로 설치되어 빌드가 실패한다.

**SDK Platforms 탭** — `Android 16.0 ("Baklava")` 그룹 중 API Level이 **`36`** 인 것
(36.1 아님):

- `Android SDK Platform 36` — 필수
- `Sources for Android 36` — 선택(코드 읽기 편함)
- 시스템 이미지 — 아래 참고

**SDK Tools 탭**:

| 항목 | 버전 |
|---|---|
| Android SDK Build-Tools | `37.0.0` |
| NDK (Side by side) | `28.1.13356709` |
| CMake | 최신 하나 |
| Android SDK Platform-Tools | 최신 |
| Android Emulator | 에뮬레이터 사용 시 |

체크 불필요: Android Auto, Google Play *, Layout Inspector, Lightbuild, NDK (Obsolete)

## 시스템 이미지 선택

### Google APIs vs Google Play

| | Google APIs | Google Play |
|---|---|---|
| `adb root` | **가능** | 불가 |
| Play 스토어 | 없음 | 있음 |
| 기기 프로필 제약 | 없음 | Play Store 인증 기기만 |

**Google APIs를 권장.** Material Files는 루트 권한 기능(libsu)이 있어서
`adb root`가 되는 쪽이 테스트에 유리하다. Play 스토어는 이 앱 개발에 불필요.

> AVD 마법사에서 Google Play 이미지를 고르면 **Finish가 비활성화**되는 경우가 있다.
> Play 이미지는 Play Store 인증이 된 기기 정의에서만 쓸 수 있기 때문.
> Google APIs 이미지로 바꾸면 바로 풀린다.

### "16 KB Page Size" 이미지는?

Android 15+의 16KB 메모리 페이지 대응을 **검증**하기 위한 전용 이미지다.
일반 개발에는 불필요하고 용량만 차지한다.

이 프로젝트는 네이티브 코드(`app/CMakeLists.txt`, NDK 28)를 포함하므로
Play 스토어 배포 시에는 의미가 있지만, NDK 27+ 는 기본적으로 16KB 정렬로
빌드하므로 이미 대응돼 있을 가능성이 높다.

## AVD 생성

`Tools` → `Device Manager` → `+` → Pixel 계열 선택 → **Google APIs (API 36)** →
Finish → ▶ 부팅

### 에뮬레이터 기본 사양 주의

기본 내부 저장소는 **10GB**다. 대용량 테스트 데이터를 넣을 계획이면
생성 시 `Advanced Settings`에서 Internal Storage를 키워두는 편이 낫다
(나중에 늘리려면 AVD 재생성이 필요할 수 있음).

## 참고: 소스 받기

```bash
git clone https://github.com/zhanghai/MaterialFiles.git
```

`--depth 1`로 받았다면 나중에 전체 히스토리가 필요할 때:

```bash
git fetch --unshallow
```

서브모듈은 없다 (`.gitmodules` 부재 확인).
