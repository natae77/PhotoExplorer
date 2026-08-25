# 10. 인계 문서 — 여기서부터 이어서 하면 된다

2026-08-25 작업 내용을 다음 사람(또는 다른 기계, 새 대화)이 이어받기 위한 문서.
**새로 시작한다면 이 문서를 먼저 읽으면 된다.**

- 최종 갱신: 2026-08-25 (브랜치를 `main`으로 바꾼 뒤)

## 1. 한 문장 요약

Material Files를 fork해 **PhotoExplorer**로 분리했고, 사진·동영상을 정사각형 격자로 보는
**미디어 보기 모드**를 추가하기로 했다. 기획과 구현 계획은 다 짰고, **코드는 아직 한 줄도 안 짰다.**

## 2. 지금 상태

### 프로젝트 정체성

| 항목 | 값 |
|---|---|
| 프로젝트 이름 | PhotoExplorer |
| git 원격 `origin` | `https://github.com/natae77/PhotoExplorer.git` |
| git 원격 `upstream` | `https://github.com/zhanghai/MaterialFiles.git` (원본 추적용) |
| 브랜치 | **`main`** (`origin/main` 추적) |
| 기준 커밋 | `fc12500` |
| 앱 식별자 | `com.natae.photoexplorer` |
| 코드 패키지(namespace) | `me.zhanghai.android.files` — **일부러 안 바꿨다** (아래 참고) |
| 앱 이름 | PhotoExplorer (32개 언어 파일 전부) |
| Firebase | 제거됨 |

**코드 패키지를 안 바꾼 이유**: 소스 폴더 구조와 클래스 이름이라 바꾸려면 파일 수백 개를 옮겨야
하고, 원본에서 변경을 가져올 때마다 충돌이 난다. 사용자에게 보이는 건 앱 식별자와 앱 이름이라
그 둘만 바꿨다. 필요하면 나중에 따로 하면 된다.

**Firebase를 뺀 이유**: 원저자가 Crashlytics는 패키지명·서명을 검사해서 남의 빌드에서는 안 켜지게
막아뒀지만(`"Please, don't spam."`), **Analytics에는 그런 검사가 없다.** 그대로 두면 우리 앱 사용
데이터가 원저자의 Firebase 프로젝트로 흘러간다. 그래서 통째로 제거했다.

### git 연결 — 정리 끝

원격 `origin`은 접속 확인됐고, 로컬 브랜치는 **`main`** 이 `origin/main`을 추적한다.

원래는 브랜치 이름이 `master`였고, 게다가 **`upstream/master`(원저자 저장소)를 추적하고 있었다.**
그대로 `git push` 하면 내 저장소가 아니라 원저자 저장소로 밀어 올리려는 셈이라 방향이 틀렸다.
2026-08-25에 `main`으로 이름을 바꾸고 추적 대상도 `origin/main`으로 고쳤다.

```bash
git branch -m master main
git branch -u origin/main main
```

**아직 남은 것 — 사람이 직접 해야 한다.**
GitHub 쪽 **기본 브랜치가 아직 `master`** 다. 원격에 `main`과 `master`가 같은 커밋으로 둘 다 있다.
웹에서 **Settings → General → Default branch** 를 `main`으로 바꾸고, 그 뒤에 `master` 브랜치를
지우면 된다(커밋이 같은 자리라 잃는 건 없다). 이 PC에는 `gh` CLI가 없어서 명령으로는 못 한다.

### 아직 커밋 안 됨

포크 분리 작업이 **아직 커밋되지 않은 채** 작업 폴더에 있다. 커밋은 사용자가 하기로 했다.

| 변경 | 내용 |
|---|---|
| `app/build.gradle` | dav4jvm 의존성 해시 고정, 앱 식별자 변경, Firebase 블록 3개 주석 처리 |
| `app/src/main/java/.../app/AppInitializers.kt` | Crashlytics 호출 주석 처리 |
| `app/src/main/java/.../nonfree/CrashlyticsInitializer.kt` | **삭제** |
| `app/src/main/res/values*/strings.xml` (32개) | 앱 이름 |
| `.gitignore` | `/gradle/gradle-daemon-jvm.properties` 한 줄 추가 (아래 참고) |
| `PersonalDev/` | 문서 전체 (git에 아직 안 올라감) |

빌드는 확인했다 — `BUILD SUCCESSFUL`, APK 식별자 `com.natae.photoexplorer`.

한 번 `c8d9c27`로 커밋했다가 되돌렸다(`git reset --soft`). `gradle-daemon-jvm.properties`가
딸려 들어가 있어서다. push 전이라 원격에는 흔적이 없다.

### `gradle/gradle-daemon-jvm.properties` 는 무시한다

`.gitignore`에 넣었다. 이유:

- **자동으로 생기는 파일이 아니다.** `./gradlew updateDaemonJvm`을 실행해야 만들어진다
  (Android Studio에서 Gradle 데몬 JVM 설정을 건드리면 대신 실행해준다). 평범한 빌드로는 안 생긴다.
- 이 파일이 있으면 **툴체인을 JDK 25로 고정**하고, 없으면 Gradle이 인터넷에서 받아온다.
  지금 빌드가 성공하는 환경은 Android Studio 내장 JBR이다. 파일을 빼두면 그 상태가 유지된다.

### 폴더 이름 — 바꿈

작업 폴더는 **`D:\Work\PhotoExplorer`** 다. `D:\Work\MaterialFiles`에서 이름을 바꿨다.
다른 기계에서 새로 받는다면 아무 폴더나 상관없다.

## 3. 무엇을 만들기로 했나

### 미디어 보기 모드

파일 목록의 보기 방식이 지금은 **목록 / 바둑판** 둘인데, 여기에 **미디어**를 하나 더 넣는다.

- 사진·동영상·폴더만 **정사각형 타일**로, **간격 없이** 빽빽하게 보여준다
- 사진 이름은 안 보여준다 (폴더 타일에만 이름 1줄)
- 화면 폭에 따라 최소 4열
- 타일 우상단에 `⋮` 메뉴 (기존 파일 메뉴 그대로)
- 동영상은 좌하단에 ▶ 표시

### 찍힌 날짜순 정렬

- 정렬 기준에 **"미디어 생성 시각"**(= 사진을 찍은 시각)을 추가한다
- 미디어 모드에서는 이 기준으로 고정하고, 오름/내림차순만 바꿀 수 있게 한다
- 기본은 오름차순이고, 폴더를 열면 **맨 아래(가장 최근 사진)** 에서 시작한다

### 폴더마다 보기 모드 기억

- 지금 있는 "이 폴더에만 적용" 체크박스를 **없앤다**
- 보기 모드를 바꾸면 자동으로 그 폴더에만 저장된다
- 설정 화면에 "폴더별 보기 설정 모두 초기화"를 넣는다

상세는 [08번 기획서](08-photo-view-mode-spec.md), 만드는 순서는 [09번 계획서](09-media-view-mode-plan.md).

## 4. 다음에 할 일

[09번 계획서](09-media-view-mode-plan.md)의 **1단계부터** 시작하면 된다.

> **1단계: 사진에서 찍힌 날짜 꺼내오기**
> 화면에는 아무 변화가 없다. MP4 박스를 따라가 촬영 시각을 읽는 코드와,
> 같은 파일을 반복해서 읽지 않게 하는 캐시를 만든다.

2단계까지 하면 평소 목록 화면에서 찍힌 날짜순 정렬이 된다. 3단계부터가 새 화면이다.

## 5. 조사해서 알아낸 것 — 다시 조사하지 말 것

이 결론들은 실제로 재보고 확인한 것이다. 근거는 [08번 §5](08-photo-view-mode-spec.md)에 있다.

### 안드로이드에는 쓸 만한 "파일 생성 날짜"가 없다

1. 앱 코드가 `creationTime`을 그냥 수정 시각으로 채워 넣는다
   (`provider/linux/LinuxFileAttributes.kt:47`)
2. 안드로이드가 값을 안 준다 — 에뮬레이터에서 `stat -c %W` → `birth=?`
3. 얻더라도 의미가 없다 — 사진을 복사하면 "복사한 날짜"가 된다

→ 찍힌 시각은 **파일 내용 안(EXIF / MP4 메타데이터)** 에서만 얻을 수 있다.

### MediaStore는 쓸 수 없다

미디어 스캐너가 이미 읽어둔 값을 쓰면 가장 싸지만, **사진 폴더에 `.nomedia`를 넣을 예정**이라
쓸 수 없다. 실제로 확인했다 — `.nomedia` 넣고 재스캔하니 20행 → 0행.

### 찍힌 날짜와 수정 날짜 중 어느 쪽도 항상 옳지 않다

테스트 파일 50개를 조사한 결과:

| 파일 종류 | 찍힌 날짜(메타데이터) | 수정 날짜 | 맞는 쪽 |
|---|---|---|---|
| `.mp4` 2개 | 정확 | 복사하면서 갱신됨 | 메타데이터 |
| `.mov` 48개 | 전송하면서 덮어써짐 (전부 08-18로 뭉침) | 원래 날짜 유지 | 수정 날짜 |

→ **둘 중 이른 쪽을 쓴다** (`min`). 찍은 시각이 파일을 마지막으로 쓴 시각보다 나중일 수는 없으니까.
이 규칙을 적용하면 50개 전부 올바른 값이 나온다.

### MP4에서 찍힌 날짜 읽는 비용은 거의 없다

박스 구조를 따라가면 **파일당 약 300바이트**만 읽으면 된다. 50개 전부 성공했다.

- 파일 뒤쪽 N바이트를 읽어서 찾는 방식은 **쓰지 말 것** — 끝에서 `moov`까지 거리가
  13KB~287KB로 들쭉날쭉해서 고정 크기로 자르면 일부가 빗나간다(50개 중 1개)

## 6. 반드시 지켜야 할 것

### enum에 값을 추가할 때는 맨 뒤에

`FileViewType`과 `FileSortOptions.By`는 설정에 **이름이 아니라 순서 번호(0,1,2...)** 로 저장된다.
중간에 끼워 넣으면 기존 사용자의 설정이 다른 값으로 뒤바뀐다.

- `FileViewType`: `LIST, GRID, MEDIA` — MEDIA를 맨 뒤에
- `FileSortOptions.By`: `NAME, TYPE, SIZE, LAST_MODIFIED, MEDIA_CREATED` — 맨 뒤에

### MP4 파서에 방어 코드를 꼭 넣을 것

테스트 파일 50개는 전부 정상이라 **아무 검사도 안 넣어도 통과한다.**
손상된 파일이 들어오면 무한 루프에 빠진다. 계획서 1.1의 검사 6가지를 반드시 넣을 것.

## 7. 개발 환경 상태

### 에뮬레이터

- AVD 이름: `Pixel_8` (Android 16)
- 앱이 설치돼 있다 (`me.zhanghai.android.files` — **식별자 변경 전에 설치한 것**.
  새로 빌드하면 `com.natae.photoexplorer`로 별도 설치되므로 옛것은 지워도 된다)

### 테스트 데이터

- PC 원본: `D:\Work\SwingTestData` (동영상 50개, 7.1GB)
- 에뮬레이터: `/sdcard/SwingTestData` (앞에서부터 20개, 1.74GB)
- **`.nomedia` 파일을 넣어뒀다** — 실제 사용 환경과 같게 하려고. MediaStore에서 빠져 있다.

이 데이터가 중요한 이유: 찍힌 날짜와 수정 날짜가 **서로 크게 어긋나 있어서**,
정렬이 제대로 되는지 확인하기 좋다. 찍힌 날짜순으로 정렬하면 **2026-04-11 ~ 08-19** 로
흩어져야 한다. 전부 08-18로 뭉치면 `min` 규칙이 안 걸린 것이다.

### 함정들

| 함정 | 대처 |
|---|---|
| **에뮬레이터를 Claude가 띄우면 죽는다** | 세션 정리 과정에서 종료 신호를 받는다. `Start-Process`로 분리해도 마찬가지. **사용자가 직접 띄울 것** |
| **강제 종료하면 다음 부팅이 멈춘다** | 반쪽짜리 스냅샷이 저장되어 `starting up`에서 정지한다. `-no-snapshot-load`로 띄우거나 `~/.android/avd/Pixel_8.avd/snapshots/default_boot` 폴더를 지운다. 지워도 `/sdcard` 파일과 설치된 앱은 안 없어진다 |
| **Git Bash에서 adb 경로가 깨진다** | [07번 문서](07-windows-gitbash-adb-pitfalls.md) 참고. `MSYS_NO_PATHCONV=1` 필요 |
| **`adb`가 PATH에 없다** | `C:/Users/hskang/AppData/Local/Android/Sdk/platform-tools/adb.exe` 전체 경로로 쓴다 |

### 빌드

```bash
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```

## 8. 문서 지도

| 문서 | 언제 보나 |
|---|---|
| [01](01-setup-android-studio.md) | 새 기계에 개발 환경을 깔 때 |
| [02](02-build-troubleshooting.md) | 빌드가 안 될 때 |
| [03](03-debug-variant-side-by-side.md) | **이제 필요 없다** — 앱 식별자를 바꿔서 그냥 나란히 깔린다. 히스토리로만 남김 |
| [04](04-device-connection-samsung.md) | 삼성 폰이 adb에 안 잡힐 때 |
| [05](05-test-data-pipeline.md) | 테스트 데이터를 옮길 때 |
| [06](06-video-date-metadata.md) | 촬영 시각이 어디서 오는지 — **1단계 구현 시 필독** |
| [07](07-windows-gitbash-adb-pitfalls.md) | adb 스크립트가 이상하게 실패할 때 |
| [08](08-photo-view-mode-spec.md) | **무엇을 만들 것인가** (기획) |
| [09](09-media-view-mode-plan.md) | **어떻게 만들 것인가** (계획, 1~6단계) |

## 9. 대화 방식에 대한 메모

사용자가 지적한 것: 문서 안에서 붙인 번호(D15, R1 같은 것)와 압축된 기술 표현을 대화에서
그대로 쓰면 알아들을 수 없다. 결정을 내리려고 읽는 것이지 암호를 풀려고 읽는 게 아니다.

- 문서 번호를 근거로 댈 때는 **내용을 한 문장으로 같이 풀어 쓸 것**
- "2차 비동기 단계로 분리" 같은 말 대신 **무슨 일이 벌어지는지**를 쓸 것
- 한 문단에 결정·근거·대안을 다 욱여넣지 말 것

이 문서와 08·09번도 그 지적을 반영해 용어를 고친 상태다.
