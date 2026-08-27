# PhotoExplorer 개인 개발 문서

`zhanghai/MaterialFiles`를 fork한 **PhotoExplorer**(`natae77/PhotoExplorer`)의 개발 기록.
Windows 11에서 실제로 겪고 검증한 내용만 정리했다.

> **처음 보는 사람은 [09번 구현 계획](09-media-view-mode-plan.md)과
> [미결 항목](media-view-mode-open-items.md)부터 읽으면 된다.**

## 환경 요약

| 항목 | 값 |
|---|---|
| 소스 | `D:\Work\PhotoExplorer` (upstream 커밋 `fc12500`) |
| 앱 버전 | 1.7.4 (versionCode 39) |
| Android SDK | `C:\Users\hskang\AppData\Local\Android\Sdk` |
| 실기기 | SM-F971N (Galaxy Z Fold 7), Android 17 |
| 에뮬레이터 | Pixel, API 36, Google APIs, KST |
| 테스트 데이터 원본 | `D:\Work\SwingTestData` (50개, 7.1 GiB) |

## 문서 목록

| 문서 | 내용 |
|---|---|
| [01-setup-android-studio.md](01-setup-android-studio.md) | Android Studio 설치, SDK/NDK 정확한 버전, AVD 생성 |
| [02-build-troubleshooting.md](02-build-troubleshooting.md) | dav4jvm 의존성 오류와 해결, 커맨드라인 빌드 |
| [03-debug-variant-side-by-side.md](03-debug-variant-side-by-side.md) | 원본 앱과 나란히 설치되는 debug 빌드 만들기 |
| [04-device-connection-samsung.md](04-device-connection-samsung.md) | 삼성 폰 ADB 연결 문제(자동 차단), 보안 범위 |
| [05-test-data-pipeline.md](05-test-data-pipeline.md) | 폰 → PC → 에뮬레이터 테스트 데이터 이관 |
| [06-video-date-metadata.md](06-video-date-metadata.md) | 파일 속성의 "촬영 시각"이 어디서 오는가 (코드 분석) |
| [07-windows-gitbash-adb-pitfalls.md](07-windows-gitbash-adb-pitfalls.md) | Windows/Git Bash에서 adb 스크립팅 함정 |
| [08-media-view-mode-spec.md](08-media-view-mode-spec.md) | 미디어 보기 모드 추가 기획서 (목록/바둑판/미디어, 날짜 타일) |
| [09-media-view-mode-plan.md](09-media-view-mode-plan.md) | 미디어 보기 모드 구현 계획 (0~7단계) |
| [media-view-mode-open-items.md](media-view-mode-open-items.md) | 09번 계획서의 미결 항목 — 누락·미결정·문서 모순 (착수 전 확인) |

## 핵심 요약 (급할 때 여기만)

- **빌드**: Android Studio 최신 버전 필수 (AGP 9.1.0 / Gradle 9.3.1). SDK Manager에서 **탭마다** `Show Package Details`를 켜야 정확한 버전을 고를 수 있다.
- **필수 버전**: SDK Platform **36**, Build-Tools **37.0.0**, NDK **28.1.13356709**, CMake 최신.
- **첫 빌드 실패**: `dav4jvm:02fe1a95e6` 를 못 찾는다 → 전체 커밋 해시로 교체 ([02](02-build-troubleshooting.md)).
- **폰이 안 잡힘**: 삼성 **자동 차단(Auto Blocker)** 이 USB 명령을 막는다 ([04](04-device-connection-samsung.md)).
- **커맨드라인 빌드**:
  ```
  JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
  ```