# 파일 목록 UI와 공유 요소 전환 통합 검증

검증일: 2026-09-11

## 통합 이력

- 기준: `origin/feature/file-list-ui-refresh` (`f1d315fe`), fetch 후 확인.
- 통합 브랜치: `codex/integrate-shared-transition`.
- `83019a68` → `037f76f4`, `7d540783` → `d7cd2e97` 순서로 cherry-pick.
- FileListAdapter의 폴더 경로 집합과 파일 위치 조회, FileListFragment의 구분선과 공유 요소 콜백을 모두 유지했다. 문서 목차도 양쪽 항목을 보존했다.

## 실행 중 발견한 호환성 문제와 수정

HDR 밝기 수정은 SurfaceView를 사용하지만 공유 요소 전환은 TextureView.bitmap만 읽었다.
통합 직후 동영상 종료 로그는 `setResult(CANCELED), nothing to send`였으며 타일 복귀가 생략됐다.

SurfaceView 재생은 유지하고, 종료 시 PixelCopy로 현재 프레임을 비동기 복사한 뒤 복귀 전환에 전달한다.
중복 종료 요청을 막고, 500ms 제한·캡처 실패·지원하지 않는 API에서는 일반 종료로 돌아간다.
콜백에서 뷰 생명주기와 현재 파일을 확인한다. 진입 시 재생 준비 판정은 첫 프레임 이벤트를 사용한다.
API 근거: [Android PixelCopy](https://developer.android.com/reference/android/view/PixelCopy).

## 이번 실행 결과

환경: Pixel_8 AVD, emulator-5554, 디버그 APK. 기존 MixTest 미디어 사용.

| 항목 | 결과 및 근거 |
|---|---|
| assembleDebug | 통합 직후와 호환성 수정 후 모두 성공 |
| testDebugUnitTest | DirectoryItemCountLoaderTest 7개, 실패·오류 0 |
| 목록 UI | 폴더 개수·파일 메타데이터·즐겨찾기 바·경로 바 표시 확인 |
| 사진 열기 및 페이지 이동 후 복귀 | photo_1 → photo_2 후 뒤로가기, 수정 후 아래로 끌기 모두 photo_2로 remapped |
| 동영상 세 종료 경로 | clip_1에서 뒤로가기·툴바 화살표·아래로 끌기 각각 PixelCopy result=0, setResult(OK), grid remapped |
| 손상 동영상 | broken.mp4 종료에서 공유 요소를 비우고 일반 종료, 목록 복귀 |
| 크래시 | 위 실행 구간의 AndroidRuntime 오류 로그 없음 |
| 정적 확인 | git diff --check 통과 |

화면 캡처: `app/build/integration-result.png` (빌드 산출물, Git 비추적).
기존 Screenshots 즐겨찾기 대상 폴더가 에뮬레이터에 없어 오류가 표시됐으며, 기존 MixTest 폴더를 직접 열어 테스트했다.

## 검증 범위 밖

이번 확인은 UI 계층·최종 화면·전환 로그에 근거한다. 애니메이션 프레임별 품질, HDR 색/밝기,
실기기·R8, 화면 밖 타일 복귀, 화면 회전/액티비티 재생성, 캡처 제한시간 및 구형 API 분기는 이번에 검증하지 않았다.
과거 14번 문서의 실기기 검증은 이번 통합 빌드에 대한 결과가 아니다.
