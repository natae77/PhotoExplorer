# 06. 파일 속성의 "촬영 시각"은 어디서 오는가

## 질문

Material Files의 파일 속성에서 `20260819_062806_1.mp4` 의
**영상 촬영 시각**이 `2026.8.19 오전 6:29:48` 로 표시된다.
파일명은 `062806`(06:28:06)인데 값이 다르다. 어디서 온 값인가?

## 답: MP4 컨테이너 내부의 `mvhd` 아톰

파일명이 아니라 **동영상 파일 안에 기록된 메타데이터**를 읽는다.

### 코드 경로

`app/src/main/java/me/zhanghai/android/files/fileproperties/video/VideoInfoLiveData.kt`

```kotlin
val videoInfo = MediaMetadataRetriever().use { retriever ->
    retriever.setDataSource(path)
    ...
    val date = retriever.date        // ← 이것
    val location = retriever.location
    ...
}
```

`app/src/main/java/me/zhanghai/android/files/fileproperties/MediaMetadataRetrieverExtensions.kt`

```kotlin
private val dateFormat = SimpleDateFormat("yyyyMMdd'T'HHmmss", Locale.US)
    .apply { timeZone = TimeZone.getTimeZone("UTC") }   // ← UTC로 해석

// @see com.android.providers.media.scan.ModernMediaScanner.parseOptionalDate
val MediaMetadataRetriever.date: Instant?
    get() {
        val date = extractMetadataNotBlank(MediaMetadataRetriever.METADATA_KEY_DATE) ?: return null
        return dateFormat.parse(date, ParsePosition(0))?.time?.let { Instant.ofEpochMilli(it) }
    }
```

동작 흐름:

1. `MediaMetadataRetriever`(안드로이드 프레임워크 / Stagefright)가
   MP4의 `moov` → `mvhd` 아톰에서 `creation_time` 을 읽는다
2. `"20260818T212948.000Z"` 형태의 **UTC 문자열**로 돌려준다
3. 앱이 UTC로 파싱해 `Instant`로 만들고, 표시할 때 로컬 시간(KST)으로 변환

> `ParsePosition(0)` 을 쓰기 때문에 뒤에 붙은 `.000Z` 는 무시된다.

## 실제 파일로 검증

파일 구조가 `ftyp → mdat → moov` 순이라 `moov`가 **파일 끝**에 있었다.
`mdat`의 64비트 크기를 읽어 위치를 계산한 뒤 256바이트만 읽어 파싱:

```
mdat largesize: 46,073,066
moov offset   : 46,073,090   (파일 크기 46,086,894)

mvhd creation_time (raw): 3869933388     ← 1904-01-01 UTC 기준 경과 초
  → UTC : 2026-08-18 21:29:48
  → KST : 2026-08-19 06:29:48            ← 앱 표시값과 정확히 일치

mvhd modification_time   : 2026-08-19 06:29:48  (동일)
timescale 10000 / duration 355281  →  35.528초
```

MP4 표준은 시각을 **1904년 1월 1일 UTC 기준 경과 초**로 저장한다.

### 검증에 쓴 방법

```bash
# 1) 헤더 읽어 아톰 구조 파악
adb shell "dd if='<파일>' bs=512 count=8 2>/dev/null | base64" > head.b64

# 2) mdat largesize(바이트 32..40)로 moov 위치 계산
# 3) 그 지점에서 256바이트만 읽어 mvhd 파싱
adb shell "dd if='<파일>' bs=1 skip=<moov오프셋> count=256 2>/dev/null | base64" > moov.b64
```

288MB 파일 전체를 옮기지 않고 필요한 부분만 읽을 수 있다.

## 파일명과 다른 이유

| 출처 | 값 |
|---|---|
| 파일명 `20260819_**062806**_1.mp4` | 06:28:06 |
| mvhd creation_time | 06:29:48 |
| 차이 | 102초 |
| 영상 길이 | 35.5초 |

차이(102초)가 영상 길이(35.5초)보다 크므로 "촬영 시작 → 종료"의 차이는 **아니다**.

`creation_time` 과 `modification_time` 이 초 단위까지 완전히 동일한데,
이는 파일이 한 번에 쓰여지고 즉시 마무리됐다는 뜻이다
(녹화 중 점진적으로 기록된 파일은 보통 두 값이 다르다).

→ **해석**: 파일명의 06:28:06은 원본 촬영/세션 시각이고,
mvhd의 06:29:48은 그 원본에서 35.5초 구간을 잘라 새 파일로 내보내기(export)를
완료한 시각으로 보인다. (검증된 사실이 아닌 추론)

## 두 탭의 데이터 출처가 다르다

| 탭 | 표시값 | 출처 |
|---|---|---|
| **동영상** | 2026.8.19 06:29:48 | **파일 내부** mp4 메타데이터 (`MediaMetadataRetriever`) |
| **기본** | 2026-08-22 09:36:43 | **파일시스템** mtime |

기본 탭 구현: `fileproperties/basic/FilePropertiesBasicTabFragment.kt`

```kotlin
val lastModificationTime = file.attributes.lastModifiedTime().toInstant().formatLong()
```

이 값은 `adb shell stat` 의 `Modify` 와 정확히 일치한다.

**결과적으로:**
- **촬영 시각** — 파일을 복사·이동해도 변하지 않음 (파일 내용의 일부)
- **수정 날짜** — 복사할 때마다 갱신됨 (파일시스템 속성)

## 관련 함정들

### 1. mvhd를 로컬 시각으로 잘못 쓰는 기기가 있다

MP4 표준은 `mvhd`를 UTC로 저장하도록 정하고 있지만, 일부 카메라·앱은
로컬 시각을 그대로 써넣는다. 그런 파일은 Material Files에서
시간대만큼(한국이면 9시간) 어긋나 보인다.

### 2. mtime의 나노초가 `.000000000` 이면 복사된 파일

```
Modify: 2026-08-17 08:47:09.000000000 +0900   ← 초 단위로 잘림 = 타임스탬프 보존 복사
Modify: 2026-08-22 09:36:43.452389297 +0900   ← 나노초 있음 = 그 시점에 실제 쓰여짐
```

복사 도구가 원본 mtime을 보존하면 초 단위로 잘려 들어온다.
파일이 어떻게 들어왔는지 추정하는 단서가 된다.

### 3. `.nomedia` 가 있으면 MediaStore 조회가 실패한다

```bash
adb shell "content query --uri content://media/external/video/media \
  --projection _display_name:date_added:datetaken \
  --where \"_display_name='<파일명>'\""
# No result found.
```

폴더에 `.nomedia`가 있으면 미디어 스캐너가 건너뛰므로
`date_added`, `datetaken` 같은 MediaStore 값은 얻을 수 없다.
Material Files는 파일시스템을 직접 읽으므로 앱 동작 자체에는 영향이 없다.

### 4. 안드로이드 내부 저장소에는 생성 시각(birth time)이 없다

FUSE 기반 에뮬레이션 파일시스템이라 `stat` 에 Access/Modify/Change 셋만 나온다.
"파일 생성 날짜"는 파일시스템에서 얻을 수 없고, 위의 mp4 메타데이터처럼
**파일 내용 안에서** 찾아야 한다.
