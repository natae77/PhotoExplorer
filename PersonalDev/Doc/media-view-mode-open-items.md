# 미디어 보기 모드 — 미결 항목

[09번 계획서](09-media-view-mode-plan.md)를 [08번 기획서](08-media-view-mode-spec.md) 및
실제 코드와 대조해서 나온 것들 중, **계획서 본문에 아직 반영하지 않은 것들**을 모아 둔다.

- 작성일: 2026-08-27 / 갱신: 2026-08-27 (구현 완료 후)
- 대조 기준: 커밋 `2f1cddb` 시점의 `app/src/main/`
- 계획서에 이미 반영한 것(파서 시크 방식, 캐시 null, `pendingState`, 전역 기본 정렬)은
  여기 없다. 09번 §1.1 / §1.3 / 5단계 / §6.3을 보면 된다.

> **구현이 끝났다(2026-08-27).** B·C 항목은 전부 처리했고 각 절 앞에 결과를 적어 뒀다.
> **본문의 원래 설명은 지우지 않았다** — 왜 그렇게 정했는지가 나중에 필요하다.
> 구현하면서 **새로 나온 함정 다섯 개**는 아래 [§E](#e-구현하면서-새로-나온-것)에 있다.
> 계획서에도, 이 문서의 원래 B·C·D에도 없던 것들이다.

**이 문서를 어떻게 쓰나** — 아래 항목들은 셋 중 하나다.

| 표기 | 뜻 | 언제 처리 |
|---|---|---|
| **누락** | 기획서에 있는데 계획서에 만드는 방법이 없다 | 해당 단계 착수 **전에** 계획서에 넣는다 |
| **결정 필요** | 어느 쪽으로 할지 안 정해졌다 | 해당 단계 착수 **전에** 정한다 |
| **확인 완료** | 계획서의 "확인 필요"에 대한 답을 코드에서 찾았다 | 그대로 반영하면 된다 |

체크 칸을 채우면서 진행한다. 처리한 항목은 **지우지 말고** 상태만 바꾼다 —
왜 그렇게 정했는지가 나중에 필요해진다.

---

## B. 기획서에 있는데 계획서에 없는 것 (누락)

| # | 기획서 근거 | 내용 | 들어갈 단계 | 상태 |
|---|---|---|---|---|
| B1 | §10, 수용기준 12 | 빈 상태 안내 | 3단계 | ✅ 구현 |
| B2 | §4.2, 수용기준 6 | 선택 상태 표시 | 3단계 | ✅ 구현 |
| B3 | D6 | Pick 모드 | 3단계 | 🟡 그대로 동작 · 미검증 |
| B4 | D5 | 검색 결과에 미디어 모드 | 3단계 | ✅ 필터 그대로 적용 |
| B5 | §4.1 | 썸네일 실패 시 표시 | 3단계 | ✅ 구현 |

**처리 결과**

- **B1** — `updateEmptyView()`를 새로 만들어 **필터 이후 어댑터 개수**로 판정한다.
  문자열 `file_list_empty_media`("표시할 미디어가 없습니다") 추가. 문서만 든 폴더를
  미디어 모드로 열면 안내가 뜬다(확인함). 부제는 "3 files" 그대로 — D2대로다.
- **B2** — 타일 위에 `selectionOverlayView`(체크 시 어두운 스크림)를 깔고, 선택되면
  `⋮`를 숨기고 같은 자리에 체크 마크를 띄운다. `duplicateParentState`로 상태를 받는다.
  ⚠️ 처음에 `<animated-selector>`로 만들었더니 **스크림이 안 그려졌다.** 평범한
  `<selector>` + `<shape><solid>`로 바꿔서 해결.
- **B3** — Pick 모드에서 미디어 타일도 **탭 = 열기, 롱프레스 = 선택**이다(§4.2 그대로).
  "탭 = 선택 토글"로 바꾸는 건 하지 않았다. 기획서를 바꿔야 하는 사안이라 **미결로 남긴다.**
  다른 앱이 사진 첨부를 요청하는 시나리오는 아직 테스트하지 않았다.
- **B4** — §3.3 필터가 `updateAdapterFileList()`에 있어 검색 중에도 그대로 걸린다.
  검색 중 날짜 타일이 사라지고 검색어를 지우면 돌아오는 것까지 확인(수용기준 16).
- **B5** — 기존 `thumbnailIconImage`(가운데 종류 아이콘)를 미디어 레이아웃에도 두고
  기존 바인딩 흐름을 그대로 탔다. 썸네일이 아직 없는 동영상은 녹색 필름 아이콘이 뜬다.

전부 3단계다. 09번 §9의 작업량 표에서 3단계를 "중상"으로 잡았는데, B1~B5를 더하면
**3단계가 이 프로젝트에서 제일 큰 단계**가 된다. 착수 전에 계획서 §3에 절을 더 만드는 게 맞다.

### B1. 빈 상태 — 이건 누락이 아니라 **버그가 된다**

기획서 §10: "폴더에 사진·동영상도 하위 폴더도 없으면 '표시할 미디어가 없습니다' 안내를 띄운다."
수용 기준 12번도 같은 요구다. 계획서에는 없다.

그냥 빠진 게 아니라, **그대로 두면 백지 화면이 나온다.**

[FileListFragment.kt:609](../../app/src/main/java/me/zhanghai/android/files/filelist/FileListFragment.kt#L609):

```kotlin
val hasFiles = !files.isNullOrEmpty()          // files = stateful.value — 필터 이전 목록
...
binding.emptyView.fadeToVisibilityUnsafe(stateful is Success && !hasFiles)
```

`hasFiles`는 계획서 §3.3이 넣기로 한 **미디어 필터를 거치기 전** 목록으로 계산된다.
그래서 문서만 들어 있는 폴더를 미디어 모드로 열면:

```
stateful.value = [문서 20개]     → hasFiles = true  → emptyView 안 뜸
어댑터 목록     = []              → 화면에 아무것도 없음
                                 → 백지
```

재미있는 건, 계획서 §3.3이 **바로 이 성질을 근거로** "툴바 부제는 필터 전 목록을 받으므로
전체 개수가 그대로 유지된다(D2 요구와 일치 — 별도 작업 불필요)"라고 옳게 판단했다는 점이다.
같은 성질이 부제에서는 이득이고 빈 상태에서는 손해다. 한쪽만 보고 넘어간 자리다.

**해야 할 일**

- 필터를 거친 뒤의 개수로 빈 상태를 판정한다. 필터 로직이 `updateAdapterFileList()` 안에
  있으므로(§3.3), 빈 상태 판정도 거기서 같이 하거나 필터 결과를 밖으로 내보내야 한다.
- 문자열이 하나 더 필요하다. 현재 `file_list_empty`는 "No files"뿐이다
  ([strings.xml:320](../../app/src/main/res/values/strings.xml#L320)).
  미디어 모드 전용 문구를 추가한다 — ko "표시할 미디어가 없습니다" / en "No media".
- 부제(개수)는 **지금대로 전체 개수**를 유지한다. 기획서 D2가 그걸 요구한다.
  "화면은 비었는데 부제는 20개"가 되지만, 그게 D2의 의도다
  (사용자가 "파일이 사라졌다"고 오해하지 않도록).

### B2. 선택 상태 표시

기획서 §4.2:

| 동작 | 결과 |
|---|---|
| 롱프레스 | 선택 모드 진입 + 해당 항목 선택 |
| 선택됨 표시 | 타일 위 반투명 오버레이 + 체크 마크. **선택 모드에서는 `⋮`를 체크 마크로 교체** |

계획서 §3.2 표에는 `선택 아이콘 영역 | 없음 — 롱프레스로 선택` 한 줄뿐이다.
**보이는 방법**이 없다. 09번 §9 작업량 표에는 3단계 난이도 근거로 "레이아웃·오버레이·**선택 표시**"라고
적혀 있으니 인지는 하고 있다 — 본문에 안 내려온 것이다.

**참고할 기존 구조**

- 선택 상태는 `holder.itemLayout.isChecked = checked`로 들어간다
  ([FileListAdapter.kt:222](../../app/src/main/java/me/zhanghai/android/files/filelist/FileListAdapter.kt#L222)).
  루트가 `CheckableForegroundLinearLayout`이라 `state_checked`가 자식들에게 전파된다.
- 배경은 `CheckableItemBackground.create(radius, radius, context)`로 만든다
  (미디어 타일은 라운드 없음이므로 `0f, 0f` — 목록 모드와 같다).

**정할 것**: `⋮` → 체크 마크 교체를 어디서 하는가. `menuButton`의 이미지를 갈아 끼우는지,
같은 자리에 겹쳐 둔 별도 뷰를 토글하는지. 기획서 §4.1이 "선택 모드에서 체크 마크로 바뀔 때도
**같은 위치**를 쓴다"고 못박았으므로, 비대칭 패딩(위·오른쪽 4dp) 구조를 둘 다 공유해야 한다.

### B3. Pick 모드 — 계획서에 한 줄도 없다

기획서 D6: "파일 선택기(Pick) 모드에서도 미디어 모드를 허용한다. 다른 앱이 사진 첨부를
요청했을 때 특히 유용하다."

Pick 모드에서 어댑터는 이렇게 다르게 동작한다:

- `isFileSelectable(file)` — `pickOptions.mimeTypes`에 안 맞는 파일은 선택 불가
  ([FileListAdapter.kt:135](../../app/src/main/java/me/zhanghai/android/files/filelist/FileListAdapter.kt#L135))
- 선택 불가 항목은 `itemLayout.isEnabled = false`로 흐려진다
- `allowMultiple`이 꺼져 있으면 새로 고를 때 기존 선택을 지운다

**문제가 되는 지점**: 목록·바둑판은 "아이콘 영역 탭 = 선택"이라 Pick 모드에서 항목을 고르기 쉽다.
미디어 타일에는 아이콘 영역이 **없다**(기획서 §4.2가 롱프레스만 제공하기로 했다).
Pick 모드에서 사진 여러 장을 고르려면 **매번 롱프레스**해야 한다.

**정할 것**: Pick 모드일 때는 미디어 타일도 **탭 = 선택 토글**로 둘 것인가.
(Pick 모드는 어차피 "열기"가 목적이 아니므로 자연스럽다. 다만 기획서 §4.2 표와 어긋나므로
기획서에 D 항목으로 추가해야 한다.)

### B4. 검색 결과에 미디어 모드

기획서 D5. 계획서 §3.3의 필터는 `updateAdapterFileList()`에 들어가고 이 함수는
검색 중에도 불리므로 **자동으로 걸린다.** 즉 구현은 이미 되는데, **의도인지 확인이 안 됐다.**

확인할 것: 검색 결과를 미디어 모드로 보면 미디어가 아닌 검색 결과가 안 보인다.
검색은 사용자가 이름으로 특정 파일을 찾는 행위인데, 찾은 게 안 보이면 혼란스럽다.
(날짜 타일은 검색 중 안 넣기로 이미 정해져 있다 — D19, 계획서 §4.2.)

**정할 것**: 검색 중에는 미디어 필터를 끌 것인가, 아니면 그대로 걸 것인가.
어느 쪽이든 계획서 §3.3에 한 줄 명시한다.

### B5. 썸네일 로딩 실패 / 미지원

기획서 §4.1: "썸네일 로딩 실패/미지원 → 파일 종류 아이콘을 타일 중앙에 놓고 배경은 단색으로 채운다."
계획서 §3.4에 없다.

기존 그리드 레이아웃에 `thumbnailIconImage`(중앙 아이콘)가 이미 있고 `ViewHolder`가
nullable로 들고 있다. 미디어 레이아웃에도 같은 뷰를 두고 기존 바인딩 흐름을 따라가면 된다.
새로 만들 게 아니라 **빠뜨리지만 않으면 되는** 항목이다.

---

## C. 결정이 필요한 것

| # | 항목 | 걸린 단계 | 상태 |
|---|---|---|---|
| C1 | 미디어 모드의 정렬 고정 — 표시만인가, 저장하는가 | 3·6단계 | ✅ **표시·적용만** 으로 결정 |
| C2 | `ListPreference`의 저장 형식 | 6단계 | ✅ 확인 완료 · 반영 |
| C3 | 초기화 후 즉시 반영 | 6단계 | ✅ 확인 완료(안 됨) · 타협안 채택 |
| C4 | 원격 제외 조건에서 FTP | 1단계 | ✅ 기존 판정과 맞춤 |
| C5 | `loadFileItem()` 호출부가 목록만이 아니다 | 1·2단계 | 🟡 인지함 · 미측정 |

**결정 결과**

- **C1 — 저장하지 않는다.** `FileListAdapter.effectiveSortOptions`가 미디어 모드일 때만
  `by`를 `MEDIA_CREATED`로 바꿔 **비교자와 메뉴 표시에만** 쓴다. 저장소는 건드리지 않는다.
  검증함: 목록 모드에서 정렬을 "이름"으로 두고 미디어 모드로 전환해도 저장값은 `NAME`
  그대로이고, 미디어 화면은 촬영 시각순으로 나온다. 미디어 → 목록으로 돌아가면 이름순이
  그대로 복원된다.
- **C2** — entryValues를 `0`/`1`/`2` (ordinal 문자열)로 넣었다.
  ⚠️ 추가로 발견: 정렬 기준·방향 두 항목은 **가짜 key**를 쓰기 때문에
  `android:defaultValue`가 **반드시** 있어야 한다. 없으면 `dispatchSetInitialValue()`가
  `onSetInitialValue()`를 아예 건너뛰어 요약이 "Not set"으로 뜬다(실제로 그렇게 나왔다).
- **C3** — 타협안 채택. 다이얼로그 문구에 "지금 열려 있는 폴더는 다시 열 때 반영됩니다"를
  넣었다.
- **C4** — `path.isFtpPath`를 제외 조건에 넣어 `supportsThumbnail`과 동일하게 맞췄다.
- **C5** — `FileJobs`(복사 충돌)·`FileLiveData`(속성)·`SearchFileListLiveData`도
  `loadFileItem()`을 부르므로 파싱이 딸려간다. 캐시가 있어 대부분 히트지만
  **복사 대상 폴더 쪽은 캐시에 없다.** 7.2 실기기 측정에 같이 넣을 것.

### C1. "정렬 기준을 `MEDIA_CREATED`로 고정" — 표시만인가, 실제로 쓰는가

계획서 §3.6: "MEDIA일 때 이름·종류·크기·수정시각 항목을 `isEnabled = false`로 흐리게,
**정렬 기준을 `MEDIA_CREATED`로 고정**." 어느 쪽인지 안 적혀 있다. 둘 다 문제가 있다.

**(가) 실제로 `putBy(MEDIA_CREATED)`를 호출한다**

6단계 이후 정렬은 **경로별로 저장**된다(§6.1). 그러면:

```
사진 폴더를 미디어 모드로 봄  → 그 폴더에 sort=MEDIA_CREATED 가 저장됨
다시 바둑판으로 되돌림        → 보기 모드만 바뀌고 정렬은 MEDIA_CREATED에 눌러앉음
```

사용자가 지정한 적 없는 정렬이 그 폴더에 남는다.

**(나) 화면 표시만 고정하고 저장은 안 한다**

그러면 기획서 §6의 "**기본 정렬**: 미디어 생성 시각 + 오름차순"을 어디서 적용할지가 빈다.
비교자를 만들 때 보기 모드를 보고 덮어쓰는 식이 되는데, `FileSortOptions.createComparator()`는
보기 모드를 모른다. 어댑터의 `buildItems()`에서 덮어쓸 수는 있다(§4.2가 이미 그 자리다).

**참고**: 미디어 모드로 **전환할 때만** 저장하고, 되돌릴 때 이전 값을 복원하는 절충도 있다.
다만 "이전 값"을 어디에 둘지가 또 늘어난다.

→ **정하고 나서 3단계에 착수한다.** 3단계와 6단계 양쪽에 걸쳐 있어 나중에 바꾸기 번거롭다.

### C2. `ListPreference`의 entryValues는 **ordinal 문자열**이어야 한다 ✅

계획서 §6.3이 기본 보기 모드를 `ListPreference`로 노출한다. 저장 형식을 확인했다.

[SettingLiveDatas.kt:239](../../app/src/main/java/me/zhanghai/android/files/settings/SettingLiveDatas.kt#L239):

```kotlin
override fun putValue(sharedPreferences: SharedPreferences, key: String, value: E) {
    sharedPreferences.edit { putString(key, value?.ordinal?.toString()) }
}

override fun getValue(...): E {
    val valueOrdinal = sharedPreferences.getString(key, null)?.toInt() ?: return defaultValue
    return if (valueOrdinal in enumValues.indices) enumValues[valueOrdinal] else defaultValue
}
```

**ordinal을 문자열로** 넣는다 — `"0"` / `"1"` / `"2"`.

`ListPreference`도 String을 저장하므로 **타입은 맞는다.** 다만 entryValues를
`LIST` / `GRID` / `MEDIA` 로 두는 게 자연스러운 직관인데, 그러면 `"LIST".toInt()`에서
`NumberFormatException`이 난다. 반드시:

```xml
<string-array name="pref_entry_values_file_list_view_type">
    <item>0</item>   <!-- LIST -->
    <item>1</item>   <!-- GRID -->
    <item>2</item>   <!-- MEDIA -->
</string-array>
```

정렬 기준(A-4로 추가한 항목)도 `FileSortOptions`가 `@Parcelize`라 사정이 다르다 —
`ListPreference`를 그대로 못 쓴다. `by`와 `order`를 따로 노출하고 저장 시
`FILE_LIST_SORT_OPTIONS`를 `copy()`해서 넣는 어댑터 코드가 필요하다.

### C3. 초기화 직후 화면 반영 — **안 된다** ✅

계획서 §6.3의 주의 문구:

> `SettingLiveData`는 `OnSharedPreferenceChangeListener`로 동작하므로 `clear()`에도 콜백이 온다
> (키가 `null`로 오는 경우가 있어 **확인 필요** — 안 오면 화면 재진입 시 반영으로 타협).

확인했다. **콜백은 오지만 무시된다.**

[SettingLiveData.kt:65](../../app/src/main/java/me/zhanghai/android/files/settings/SettingLiveData.kt#L65):

```kotlin
override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences, key: String?) {
    if (key == this.key) {
        loadValue()
    }
}
```

`clear()`는 `key == null`로 콜백한다. `this.key`는 절대 null이 아니므로 조건이 항상 거짓이다.
→ **`loadValue()`가 안 불린다.**

**선택지**

1. 타협안 그대로 — 초기화 후 화면 재진입 시 반영. 다이얼로그 문구에 그 사실을 적어 둔다.
2. 초기화 직후 현재 화면을 강제로 다시 만든다(프래그먼트 재생성 등). 거칠지만 확실하다.
3. `onSharedPreferenceChanged`에 `key == null` 분기를 추가한다. **upstream 파일을 건드리므로**
   병합 충돌 면에서 불리하다(§8-6번).

→ 1번을 기본으로 하되, 다이얼로그 문구를 그에 맞게 쓴다.

### C4. 원격 제외 조건이 기존 판정과 다르다

계획서 §1.2 제외 조건:

> 원격 경로(`path.isRemotePath`)이고 `READ_REMOTE_FILES_FOR_THUMBNAIL`이 꺼져 있으면 → null

같은 절이 참고하라고 가리킨 `supportsThumbnail`
([FileItemExtensions.kt:63](../../app/src/main/java/me/zhanghai/android/files/filelist/FileItemExtensions.kt#L63))은
조건이 하나 더 있다:

```kotlin
if (path.isRemotePath) {
    val shouldReadRemotePath = !path.isFtpPath
        && Settings.READ_REMOTE_FILES_FOR_THUMBNAIL.valueCompat
    if (!shouldReadRemotePath) {
        return false
    }
}
```

**`!path.isFtpPath`** — FTP는 설정과 무관하게 항상 제외다. 계획서 조건대로 짜면
설정이 켜진 FTP 경로에서 미디어 생성 시각을 읽으러 간다.

→ 의도인지 정한다. 특별한 이유가 없으면 기존 판정과 맞추는 게 안전하다
(FTP를 제외해 둔 데는 이유가 있었을 것이다).

### C5. `loadFileItem()`은 파일 목록에서만 불리지 않는다

계획서 §2.1이 "`loadFileItem()`에서 채운다"고 했고, §8-5번이 "생성 지점을 전수 확인"하라고
적어 뒀다. 생성 지점(`FileItem(...)`)은 확인했다 — 두 곳이다:

- `Path.loadFileItem()` ([FileItem.kt:48](../../app/src/main/java/me/zhanghai/android/files/file/FileItem.kt#L48))
- `createDummyArchiveRoot()` ([FileItemExtensions.kt:103](../../app/src/main/java/me/zhanghai/android/files/filelist/FileItemExtensions.kt#L103)) — 가짜 항목이므로 null

그런데 **`loadFileItem()`의 호출부**는 전수 확인이 안 돼 있다. 목록 로딩 말고도 세 군데다:

| 호출부 | 언제 도는가 | 영향 |
|---|---|---|
| [FileListLiveData.kt:46](../../app/src/main/java/me/zhanghai/android/files/filelist/FileListLiveData.kt#L46) | 폴더 목록 로딩 | 의도한 자리 |
| [SearchFileListLiveData.kt:42](../../app/src/main/java/me/zhanghai/android/files/filelist/SearchFileListLiveData.kt#L42) | 검색 결과 | 검색 결과가 많으면 비용이 붙는다 |
| [FileJobs.kt:1305-1306](../../app/src/main/java/me/zhanghai/android/files/filejob/FileJobs.kt#L1305) | **복사/이동 충돌 다이얼로그** | 충돌 파일마다 source·target 둘 다 파싱 |
| [FileLiveData.kt:39](../../app/src/main/java/me/zhanghai/android/files/fileproperties/FileLiveData.kt#L39) | 파일 속성 화면 | 1개뿐이라 무해 |

`FileJobs` 쪽이 눈에 띈다. 사진 수백 장을 덮어쓰기로 복사하면 충돌마다 mvhd 파싱이 붙는데,
**그 파일들은 방금 목록을 읽으며 캐시에 들어갔을 가능성이 높다**(같은 프로세스, 같은 캐시 키).
따라서 실제 비용은 캐시 히트일 것이다 — 다만 **target 쪽은 다른 폴더라 캐시에 없다.**

→ 09번 §8 걱정거리 표에 한 줄 추가하고, 7단계 실측에 "사진 대량 복사 시 충돌 다이얼로그가
뜨는 속도"를 넣는다. 심각하진 않지만 측정 없이 지나가면 나중에 원인을 못 찾는다.

---

## D. 사소한 것 · 문서 정합성

| # | 내용 | 어디 | 상태 |
|---|---|---|---|
| D1 | 08번 §2 비목표와 §10이 서로 모순 | **08번 기획서** | ⬜ 미처리 |
| D2 | `inferDateTimeOriginal()`은 인자를 받는다 | 09번 §1.2 | ✅ 구현에 반영 |
| D3 | 행 번호 6줄 오차 | 08·09번 | ⬜ 미처리 |

D1·D3은 문서만 고치면 되는 것이라 손대지 않았다. D1은 08번을 6차 개정으로 처리해야 한다.

### D1. 기획서 자체의 모순 — 빠른 스크롤 팝업의 날짜

08번 §2 **비목표**:

> - 빠른 스크롤 팝업에 날짜를 띄우는 것.

08번 §10 **그 밖**:

> - **빠른 스크롤(fast scroll)** 유지. 미디어 생성 시각 정렬일 때 팝업에는 날짜를 보여준다.
>   이름이 안 보이는 모드라 위치 파악의 유일한 단서다.

정반대다. 09번 §10은 비목표 쪽으로 정리했다("4단계에서는 크래시를 막을 값만 반환한다").

→ **08번 §10에서 그 줄을 빼거나**, 비목표에서 빼고 09번을 고친다.
계획서가 이미 비목표로 진행 중이므로 08번 §10을 고치는 쪽이 맞다.
08번은 "본문을 직접 고치고 개정 이력에 남긴다"가 규칙이므로 6차 개정으로 처리한다.

참고로 구현 부담은 크지 않다 — `getPopupText()`는 `sortOptions.by`에 대한 exhaustive `when`이라
`MEDIA_CREATED` 분기를 **어차피 만들어야** 하고, 거기서 날짜를 포맷하면 그게 곧 §10의 요구다.
비목표로 두더라도 반환할 값은 날짜가 자연스럽다.

### D2. `inferDateTimeOriginal()`의 시그니처

09번 §1.2 의사코드는 무인자로 적혀 있다:

```
mimeType.isImage → ExifInterface.inferDateTimeOriginal()
```

실제로는 인자를 받는다
([ExifInterfaceExtensions.kt:31](../../app/src/main/java/me/zhanghai/android/files/fileproperties/image/ExifInterfaceExtensions.kt#L31)):

```kotlin
fun ExifInterface.inferDateTimeOriginal(lastModifiedTime: Instant): Instant?
```

`lastModifiedTime`은 EXIF에 시간대 정보가 없을 때 시간대를 **역추정**하는 데 쓴다
(`withTimezoneInferredFrom`). 즉 mtime이 이미 이 함수 안에서 한 번 쓰인다.

→ §5.4의 `min(메타데이터, mtime)`을 적용할 때, 이 함수가 반환한 값은 **이미 mtime을 참고해
보정된 값**이라는 점을 알고 있어야 한다. 이미지에서 `min()` 규칙이 동영상과 다르게 동작할 수 있다.
08번 §11-6번("이미지 EXIF 비용 미측정")과 함께 7.3 커버리지 확인에서 같이 본다.

### D3. 행 번호

`maybeAddImageViewerActivityExtras`는 **1288행**이다. 08·09번 모두 1294로 적혀 있다.
나머지 행 번호는 대조한 범위에서 전부 정확했다
(`FileListAdapter` 51·60·124·164·172·206·395·409, `FileListFragment` 589·656·734·1705,
`SettingLiveDatas` 239, `FileItemExtensions` 59·103).

---

## E. 구현하면서 새로 나온 것

계획서에도, 위의 B·C·D에도 없던 것들이다. **전부 실제로 앱이 죽거나 기능이 안 먹어서**
찾은 것이고, 코드에는 이미 고쳐 넣었다. 여기 적는 이유는 같은 구조를 다시 만질 때
또 밟기 때문이다.

| # | 무엇 | 증상 | 어디 |
|---|---|---|---|
| E1 | LiveData 초기화 순서 | 앱 시작 즉시 크래시 | `FileListAdapter`, `FileListFragment` |
| E2 | `valueCompat`가 값 없을 때 던진다 | 앱 시작 즉시 크래시 | `FileListFragment` |
| E3 | 스크롤 대상 목록이 **이전 폴더 것** | 최신으로 스크롤이 안 먹음 | 5단계 |
| E4 | `DateFormat.format`의 `y`는 2자리 | 날짜 타일에 "26" | 4단계 |
| E5 | `⋮` 스크림이 회색 사각형으로 보임 | 밝은 썸네일에서 지저분함 | 3단계 |

### E1. 보기 모드와 정렬은 **서로 다른 LiveData**라 순서를 보장할 수 없다

계획서 §4.1이 `viewType` 세터와 `sortOptions` 세터 **둘 다** `rebuildItems()`를 부르라고 했다.
그대로 하면 앱이 시작하자마자 죽는다.

```
kotlin.UninitializedPropertyAccessException: lateinit property _sortOptions has not been initialized
  at FileListAdapter.getSortOptions(FileListAdapter.kt:75)
  at FileListAdapter.getEffectiveSortOptions
  at FileListAdapter.buildItems
  at FileListAdapter.rebuildItems
  at FileListAdapter.setViewType          ← viewType 세터가 먼저 왔다
```

원래 코드에서는 `viewType` 세터가 `super.replace(list, true)`만 해서 정렬을 안 건드렸다.
`rebuildItems()`로 합치면 **먼저 도착한 쪽이 아직 안 온 쪽을 읽는다.**

**고친 방법**: `rebuildItems()` 맨 앞에서 둘 다 초기화됐는지 확인하고 아니면 그냥 돌아간다.
그리고 `onSortOptionsChanged()`에서도 `updateAdapterFileList()`를 부르게 했다 —
먼저 온 쪽의 rebuild가 no-op이었으므로 나중에 온 쪽이 목록을 만들어야 한다.

### E2. `viewModel.sortOptions` / `viewType` / `fileListStateful`은 값이 오기 전에 읽으면 던진다

셋 다 `valueCompat`를 쓴다(`LiveData.value!!`에 해당). 5단계의 스크롤 판단이
보기 모드 옵저버에서 불리는데, 그 시점에는 정렬이나 목록이 아직 없을 수 있다.

```
java.lang.NullPointerException: <get-valueCompat>(...) must not be null
  at FileListViewModel.getSortOptions(FileListViewModel.kt:114)
  at FileListFragment.maybeScrollToLatest
```

**고친 방법**: 그 판단 안에서는 `viewModel.sortOptionsLiveData.value?.order` 처럼
**LiveData의 `.value`를 직접** 읽는다. `updateAdapterFileList()`·`updateEmptyView()`도
`viewModel.fileListLiveData.value?.value ?: return`으로 바꿨다.

### E3. 폴더를 바꾼 직후의 "목록"은 아직 **이전 폴더 것**이다 — 5단계의 진짜 함정

계획서 5단계(와 이 문서를 만들 때의 A-1 수정)는 `pendingState`가 소비형이라는 것과
"폴더 진입당 1회" 플래그까지는 맞게 짚었다. 그런데 **그 플래그를 누가 먼저 세우느냐**가 빠졌다.

실제 로그:

```
maybeScroll flag=false stateful=Success view=MEDIA order=ASCENDING count=15
scrollToPosition(14)            ← 15는 이전 폴더(루트)의 개수다
updateAdapterFileList           ← 이제야 새 폴더 목록으로 교체
maybeScroll flag=true ... count=39   ← 플래그에 막혀 진짜 스크롤이 안 된다
```

`fileListLiveData`는 현재 경로에 대한 **switch map**이라, 경로가 바뀌어도 새 폴더가 로딩될
때까지 **이전 폴더의 `Success`를 그대로 들고 있다.** 그래서 "`Success`인가?"만 봐서는
그 목록이 지금 폴더 것인지 알 수 없다.

**고친 방법**: 화면의 목록이 **어느 폴더 것인지**를 따로 기억한다.

```kotlin
private var loadedPath: Path? = null       // onCurrentPathChanged에서 null로 초기화

// onFileListChanged에서 Success일 때
loadedPath = viewModel.currentPath

// 스크롤 판단
if (loadedPath != viewModel.currentPath) return
```

그리고 **실제로 스크롤했을 때만** 플래그를 세운다. 못 했으면 그대로 둬야 진짜 기회가 왔을 때
다시 시도한다.

### E4. `android.text.format.DateFormat.format()`의 `y`는 **두 자리** 연도다

`getBestDateTimePattern(locale, "yMMMd")`가 en_US에서 `MMM d, y`를 준다. 이걸 그대로
`DateFormat.format(pattern, date)`에 넣으면 **"Jun 1, 26"** 이 나온다.

기획서 §4.3은 연도를 "2026"으로 항상 표시하라고 했다.

**고친 방법**: 패턴은 `getBestDateTimePattern`으로 얻되(로케일 존중), 렌더링은
`java.text.SimpleDateFormat(pattern, locale)`로 한다. 이쪽은 `y`를 4자리로 낸다.

### E5. 모서리에만 깐 그라데이션은 밝은 썸네일에서 **회색 사각형**으로 보인다

기획서 §4.1이 "`⋮`가 어두운 사진 위에서도 보이도록 반투명 스크림 또는 그라데이션을 옅게 깐다"고
했다. 처음에 64dp 정사각형에 대각선(225°) 그라데이션을 깔았더니, 흰 배경 타일에서
**회색 네모가 툭 튀어나온 것처럼** 보였다. 대각선 그라데이션은 상자의 왼쪽 아래 모서리에서
갑자기 투명해져 경계가 드러난다.

**고친 방법**: **타일 폭 전체 × 48dp**짜리 위→아래 그라데이션으로 바꿨다.
갤러리 앱들이 쓰는 형태고, 경계가 안 보인다.

---

## 부록. 대조하면서 확인된 것 (수정 불필요)

계획서가 맞게 짚은 것들. **다시 의심하지 않아도 된다.**

| 항목 | 확인 결과 |
|---|---|
| 보기 모드·정렬 enum을 맨 뒤에 추가 | ordinal을 문자열로 저장하는 게 맞다. 순서 바뀌면 실제로 깨진다 |
| `FileViewType` / `By` 추가 시 컴파일러가 누락을 잡아 준다 | `onCreateViewHolder`·`updateSpanCount`·`getPopupText`·`createComparator` 네 곳이 exhaustive `when`이다 |
| 툴바 부제가 필터 전 목록을 쓴다 | 맞다. `getSubtitle(stateful.value)` — 숨김 파일 필터보다도 앞이다 |
| `maybeAddImageViewerActivityExtras`가 어댑터를 훑는다 | 맞다. 날짜 항목을 안 건너뛰면 사진 열 때 크래시 |
| `AnimatedListAdapter`를 안 고쳐도 된다 | `bindViewHolderAnimation(holder)`는 `holder.itemView`만 쓴다 |
| `FileItem`에 필드를 더해도 선택이 안 깨진다 | `FileItemSet`은 `path`를 키로 하는 `LinkedMapSet`이라 `equals` 변경과 무관 |
| `scrollToPosition(itemCount - 1)`의 타이밍 | `ListDiffer`가 **동기**라 `replace` 직후 `itemCount`가 유효하다 |
| `touch_target_size` | `dimens.xml:41`에 48dp로 존재. §3.2의 XML 스니펫 그대로 쓸 수 있다 |
| `FileViewTypeLiveData.putValue` 단순화 | 맞다. `loadValue()`는 이미 `경로별 ?: 전역` 구조라 손댈 필요 없다 |
| 경로별 설정이 별도 prefs 파일에 있다 | 맞다. `NAME_SUFFIX = "path"` → `<기본이름>_path`. 통째로 `clear()` 가능 |
