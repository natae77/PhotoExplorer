# 05. 테스트 데이터 이관 (폰 → PC → 에뮬레이터)

개인 폰을 매번 연결하지 않고 에뮬레이터로 개발하기 위해,
실제 파일을 옮겨 테스트 환경을 구성한 기록.

## 결과

| 위치 | 내용 |
|---|---|
| `D:\Work\SwingTestData` | 최근 50개 · 7.1 GiB (원본 백업) |
| 에뮬레이터 `/sdcard/스윙` | 최근 20개 · 4.76 GB |

원본 폴더 현황: 실제 미디어 **627개 / 61.9 GB**

## 핵심: 타임스탬프 보존

### `adb pull -a` 는 보존, `adb push` 는 보존 안 함

```bash
adb pull -a <원격> <로컬>    # -a = 타임스탬프/모드 보존
adb push  <로컬> <원격>       # 보존 옵션 없음 → mtime이 "지금"으로 찍힘
```

파일 관리자 앱을 테스트하는데 모든 파일의 수정 날짜가 "오늘"이면
정렬·필터·속성 표시 테스트가 무의미해진다.

### push 후 mtime 복원

PC 사본(pull -a로 원본 유지)에서 mtime을 읽어 `touch -d`로 복원한다:

```bash
# 1. PC 사본에서 mtime 추출 (KST 벽시계 문자열)
python -c "
import os, datetime, io
names = open('push20.txt').read().split()
lines = []
for n in names:
    ts = os.path.getmtime(os.path.join('D:/Work/SwingTestData', n))
    dt = datetime.datetime.fromtimestamp(ts)
    lines.append('%s|%s' % (dt.strftime('%Y-%m-%d %H:%M:%S'), n))
io.open('mtimes.txt','w',encoding='utf-8',newline='\n').write('\n'.join(lines)+'\n')
"

# 2. 에뮬레이터에 복원 (</dev/null 필수 — 07번 문서 참고)
while IFS='|' read -r ts name; do
    adb -s emulator-5554 shell "touch -d '$ts' '/sdcard/스윙/$name'" </dev/null
done < mtimes.txt
```

검증:

```bash
adb -s emulator-5554 shell "stat -c '%y  %n' '/sdcard/스윙/IMG_6992.mov'"
# 2026-08-17 08:47:09.000000000 +0900  /sdcard/스윙/IMG_6992.mov   ← 폰과 동일
```

### 영상 촬영 시각은 자동 보존

mp4/mov 내부 메타데이터(`mvhd`)라서 복사 방식과 무관하게 보존된다.
[06번 문서](06-video-date-metadata.md) 참고.

## 에뮬레이터 타임존

기본값이 **GMT**다. 그대로 두면 한국에서 찍은 파일이 9시간 어긋나 보인다.

```bash
adb -s emulator-5554 root                                    # Google APIs 이미지에서만 가능
adb -s emulator-5554 shell "setprop persist.sys.timezone Asia/Seoul"
adb -s emulator-5554 shell date
# Sun Aug 23 23:27:26 KST 2026
```

> `touch -d '2026-08-17 08:47:09'` 는 **기기 로컬 시간**으로 해석된다.
> 그러므로 타임존을 먼저 맞추고 mtime을 복원해야 순서가 맞다.

## 에뮬레이터 용량

```bash
adb -s emulator-5554 shell "df -h /sdcard"
# /dev/fuse  10G  1.1G  8.4G  12%  /storage/emulated
```

기본 10GB. 4.76GB를 넣고 4.0GB 남았다.
여유가 1GB 미만이 되면 앱 설치·캐시 증가 시 문제가 생길 수 있으므로
넉넉히 남기는 편이 좋다.

## 실제 사용한 절차

```bash
# 1) 폰에서 파일 목록 + 크기 + 날짜 확보
adb -s <시리얼> shell "ls -la '/sdcard/스윙'" > swing_ls.txt

# 2) 최근 N개 선별 (파이썬으로 파싱·정렬), 목록 파일 생성
#    - macOS 리소스 포크(._*) 제외
#    - .mov/.mp4 만

# 3) PC로 복사 (타임스탬프 보존)
adb -s <시리얼> pull -a "/sdcard/스윙/$f" "D:/Work/SwingTestData/$f"

# 4) 에뮬레이터로 밀어넣기
adb -s emulator-5554 shell "mkdir -p /sdcard/스윙"
adb -s emulator-5554 push "D:/Work/SwingTestData/$f" "/sdcard/스윙/$f"

# 5) 타임존 설정 + mtime 복원 (위 참고)

# 6) 앱 설치
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

전송 속도는 USB 3 기준 약 **40 MB/s** 였다 (7.6GB ≈ 3~4분).

## 발견한 부수적 사실

- **`.nomedia`** 파일이 `스윙` 폴더에 있었다. 이 때문에 안드로이드 미디어 스캐너가
  폴더를 건너뛰고, MediaStore 조회가 `No result found` 로 나온다.
  갤러리 앱에도 안 보인다. 에뮬레이터로는 일부러 복사하지 않았다
  (MediaStore 연동 동작도 테스트할 수 있도록).
- **macOS 리소스 포크** `._IMG_xxxx.MOV` 파일 18개가 섞여 있었다(각 4KB).
  맥에서 복사할 때 생기는 부산물이라 테스트 데이터에서 제외했다.

## 에뮬레이터 root 상태

타임존 변경 때문에 `adb root`를 실행해 두었다.
덕분에 Material Files의 **루트 권한 기능(libsu)도 에뮬레이터에서 테스트 가능**하다.
원래대로 되돌리려면:

```bash
adb -s emulator-5554 unroot
```
