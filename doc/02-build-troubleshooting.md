# 02. 빌드 오류와 해결

## 발생한 오류: dav4jvm 을 찾을 수 없음

첫 빌드에서 실패:

```
> Task :app:compileDebugAidl FAILED

Execution failed for task ':app:compileDebugAidl'.
> Could not resolve all files for configuration ':app:debugCompileClasspath'.
   > Could not find com.github.bitfireAT:dav4jvm:02fe1a95e6.
     Searched in the following locations:
       - https://dl.google.com/dl/android/maven2/...
       - https://repo.maven.apache.org/maven2/...
       - https://jitpack.io/com/github/bitfireAT/dav4jvm/02fe1a95e6/dav4jvm-02fe1a95e6.pom
```

WebDAV 기능에 쓰이는 라이브러리다. **프로젝트 소스 자체의 문제**이지 설치 실수가 아니다.

### 진단 과정

JitPack API로 빌드 상태를 확인하면 정상이라고 나온다:

```bash
curl -s "https://jitpack.io/api/builds/com.github.bitfireAT/dav4jvm/02fe1a95e6"
# {"version":"02fe1a95e6","status":"ok","modules":[],...}
```

그런데 아티팩트는 404. JitPack 자체는 살아 있다 — 같은 프로젝트의 다른
JitPack 의존성은 정상이었다:

```
/com/github/chrisbanes/PhotoView/2.3.0/PhotoView-2.3.0.pom    -> 200
/com/github/topjohnwu/libsu/service/5.2.2/service-5.2.2.pom   -> 200
```

여러 버전 표기를 찔러본 결과 **원인 발견**:

| 요청 경로 | 응답 |
|---|---|
| `dav4jvm/02fe1a95e6/...` (짧은 해시) | **404** |
| `dav4jvm/02fe1a95e6b86e323bec3784d7d2fe2d4081dde6/...` (전체 해시) | **200** |

같은 커밋인데 **짧은 해시 형태만 서빙되지 않는 상태**였다.
(Maven Central에는 이 라이브러리가 없어서 JitPack이 유일한 경로다.)

### 해결

`app/build.gradle` 한 줄 수정:

```groovy
// 변경 전
implementation('com.github.bitfireAT:dav4jvm:02fe1a95e6') {

// 변경 후
implementation('com.github.bitfireAT:dav4jvm:02fe1a95e6b86e323bec3784d7d2fe2d4081dde6') {
```

수정 후 `BUILD SUCCESSFUL`.

### 같은 증상이 다시 나면

JitPack 의존성이 `Could not find` 로 실패할 때 확인 순서:

```bash
# 1. 해당 아티팩트가 실제로 서빙되는지
curl -s -o /dev/null -w "%{http_code}\n" "https://jitpack.io/<group경로>/<ver>/<artifact>-<ver>.pom"

# 2. JitPack 빌드 상태
curl -s "https://jitpack.io/api/builds/<group>/<artifact>/<ver>"

# 3. 다른 버전 표기(전체 해시/태그)로 되는지
curl -s "https://jitpack.io/api/builds/<group>/<artifact>"   # 전체 버전 목록
```

## 커맨드라인 빌드

```bash
cd /d/Work/MaterialFiles
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew assembleDebug
```

출력물:

```
app/build/outputs/apk/debug/app-debug.apk
```

`Build` → `Build APK(s)` 메뉴는 APK 파일이 필요할 때만 쓴다.
**▶ Run 버튼은 빌드 + 설치 + 실행을 한 번에 하므로**, 코드 수정 후 따로 빌드할 필요 없다.

## 무시해도 되는 경고

빌드 중 대량으로 나오지만 문제 없는 것들:

- `w: ... is deprecated. Deprecated in Java.`
  → 원본 프로젝트가 구형 Fragment API(`setHasOptionsMenu`, `onActivityCreated` 등)를 씀
- `Java compiler version 25 has deprecated support for compiling with source/target version 8`
  → 프로젝트가 `JavaVersion.VERSION_1_8` 로 컴파일
- `Deprecated Gradle features were used ... incompatible with Gradle 10`
  → 미래 버전 대비 경고
- `WARNING: A restricted method in java.lang.System has been called`
  → Gradle native-platform 관련, 무해

**실제 문제는 `FAILURE`, `error:`, `FAILED` 뿐이다.**

## 기타 자주 쓰는 명령

```bash
# 완전히 새로 빌드
JAVA_HOME="/c/Program Files/Android/Android Studio/jbr" ./gradlew clean assembleDebug

# 캐시 문제 의심 시 (Studio)
File → Invalidate Caches... → Invalidate and Restart
```

## APK 검증 (aapt2)

빌드 결과가 의도대로인지 확인할 때:

```bash
AAPT=$(ls -d /c/Users/hskang/AppData/Local/Android/Sdk/build-tools/*/aapt2.exe | head -1)

# 패키지명 / 버전 / SDK
"$AAPT" dump badging app/build/outputs/apk/debug/app-debug.apk | head -4

# 리소스 값 확인
"$AAPT" dump resources app/build/outputs/apk/debug/app-debug.apk | grep -A2 "string/app_provider_authority"

# 매니페스트 확인
"$AAPT" dump xmltree --file AndroidManifest.xml app/build/outputs/apk/debug/app-debug.apk | grep -i authorities
```
