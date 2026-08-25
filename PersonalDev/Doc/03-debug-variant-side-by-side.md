# 03. 원본과 나란히 설치되는 debug 빌드

## 왜 필요한가

Play 스토어/F-Droid에서 받은 정품 Material Files가 이미 폰에 있으면,
직접 빌드한 APK는 설치되지 않는다. 패키지명(`me.zhanghai.android.files`)이 같은데
서명이 다르기 때문:

```
INSTALL_FAILED_UPDATE_INCOMPATIBLE
```

원본을 지우지 않고 **별개 앱으로 나란히 설치**되게 만든다.
비교 테스트도 가능해진다.

## 수정 내역 (3곳)

### 1. `app/build.gradle` — debug 빌드 타입 추가

```groovy
buildTypes {
    debug {
        // Use a distinct application ID so this build can be installed alongside
        // the official one.
        applicationIdSuffix '.debug'
        versionNameSuffix '-debug'
        // The provider authorities are derived from defaultConfig.applicationId, which
        // doesn't include the suffix, so they need to be overridden here as well.
        resValue 'string', 'app_provider_authority',
                'me.zhanghai.android.files.debug.app_provider'
        resValue 'string', 'file_provider_authority',
                'me.zhanghai.android.files.debug.file_provider'
    }
    release { ... }
}
```

#### `resValue` 두 줄이 왜 필요한가 (중요)

`defaultConfig`에 이런 코드가 있다:

```groovy
applicationId 'me.zhanghai.android.files'
resValue 'string', 'app_provider_authority', applicationId + '.app_provider'
resValue 'string', 'file_provider_authority', applicationId + '.file_provider'
```

여기서 쓰이는 `applicationId`는 **suffix가 붙기 전 값**이다.
그리고 `AndroidManifest.xml`이 이 문자열 리소스를 그대로 참조한다:

```xml
<provider android:authorities="@string/app_provider_authority" ... />
<provider android:authorities="@string/file_provider_authority" ... />
```

따라서 `applicationIdSuffix`만 붙이면 패키지명은 달라져도 **provider authority는
원본과 동일**해져서 설치가 거부된다:

```
INSTALL_FAILED_CONFLICTING_PROVIDER
```

> 참고로 `BuildConfig.FILE_PROVIDIER_AUTHORITY` 는
> `APPLICATION_ID + ".file_provider"` 로 정의돼 있어서 suffix가 자동 반영된다.
> **리소스 쪽만 수동으로 맞춰주면 둘이 일치한다.**

### 2. `app/src/debug/google-services.json` (신규)

Firebase 플러그인(`com.google.gms.google-services`)은 JSON에 등록된 패키지명과
빌드 패키지명이 정확히 일치해야 한다. 원본 `app/google-services.json`에는
`me.zhanghai.android.files` 하나뿐이라 그대로 두면:

```
No matching client found for package name 'me.zhanghai.android.files.debug'
```

**해결**: 원본을 복사해 `package_name`만 `.debug`로 바꾼 파일을
`app/src/debug/` 에 둔다. 플러그인이 빌드 타입별 소스셋을 우선 탐색한다.
원본 파일은 건드리지 않으므로 release 빌드는 영향 없다.

### 3. `app/src/debug/res/values/strings.xml` (신규)

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="app_name">Files (Debug)</string>
</resources>
```

앱 목록에서 원본과 구분하기 위한 이름.
debug 소스셋의 리소스가 main을 덮어쓴다.

## 검증

빌드 후 APK를 직접 뜯어서 확인했다:

```
package:            me.zhanghai.android.files.debug
versionName:        1.7.4-debug
application-label:  Files (Debug)
app_provider:       me.zhanghai.android.files.debug.app_provider
file_provider:      me.zhanghai.android.files.debug.file_provider
```

```bash
AAPT=$(ls -d /c/Users/hskang/AppData/Local/Android/Sdk/build-tools/*/aapt2.exe | head -1)
APK=app/build/outputs/apk/debug/app-debug.apk

"$AAPT" dump badging "$APK" | grep -E "^package|application-label"
"$AAPT" dump resources "$APK" | grep -A2 -E "string/(app|file)_provider_authority"
```

실기기 설치 후 확인:

```
$ adb shell pm list packages | grep zhanghai
package:me.zhanghai.android.files.debug   ← 내가 빌드한 것
package:me.zhanghai.android.files         ← 원본, 그대로 유지
```

## 주의

- 이 3개 변경은 **debug 빌드에만** 적용된다. release는 원본 그대로.
- upstream을 다시 fetch/merge 할 때 `app/build.gradle` 충돌 가능성이 있다.
- `app/src/debug/` 디렉터리는 upstream에 없는 신규 파일이라 충돌하지 않는다.
