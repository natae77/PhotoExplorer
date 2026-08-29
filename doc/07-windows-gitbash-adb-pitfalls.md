# 07. Windows / Git Bash 에서 adb 스크립팅 함정

이번 세션에서 스크립트가 3번 통째로 실패했다. 전부 아래 함정 때문이었다.
증상이 "전부 실패(0/50)" 형태로 나오면 이 문서를 먼저 볼 것.

## 1. Git Bash 경로 자동 변환

Git Bash(MSYS)는 `/`로 시작하는 인자를 **Windows 경로로 자동 변환**한다.
`adb.exe`는 Windows 바이너리이므로 이 변환이 양방향으로 문제를 일으킨다.

### 증상 A — 안드로이드 경로가 망가짐

```bash
adb shell ls /sdcard
# ls: C:/Program: No such file or directory
# ls: Files/Git/sdcard: No such file or directory
```

`/sdcard`가 `C:/Program Files/Git/sdcard`로 변환됐다.

**해결**: 변환을 끈다.

```bash
export MSYS_NO_PATHCONV=1
```

### 증상 B — 변환을 끄면 이번엔 Windows 목적지가 망가짐

`MSYS_NO_PATHCONV=1` 상태에서:

```bash
adb pull -a "/sdcard/파일.mp4" "/d/Work/SwingTestData/파일.mp4"   # 실패
```

`/d/Work/...`가 변환되지 않아 `adb.exe`가 인식하지 못한다.

**해결**: 원격은 POSIX 경로, **로컬은 Windows 형식으로 직접 쓴다.**

```bash
export MSYS_NO_PATHCONV=1
adb pull -a "/sdcard/파일.mp4" "D:/Work/SwingTestData/파일.mp4"   # 성공
```

> 규칙: `MSYS_NO_PATHCONV=1` 을 켜면 **양쪽 경로를 모두 직접 책임져야 한다.**
> 안드로이드 쪽은 `/sdcard/...`, Windows 쪽은 `D:/...`.

## 2. `adb shell` 이 표준입력을 삼킨다

루프 안에서 `adb shell`을 호출하면 **루프의 입력 스트림을 통째로 소비**해서
첫 번째 반복만 실행되고 끝난다.

```bash
# 잘못된 코드 — 1번만 실행됨
while IFS='|' read -r ts name; do
    adb shell "touch -d '$ts' '/sdcard/x/$name'"
done < mtimes.txt
```

**해결**: `adb`의 stdin을 막는다.

```bash
while IFS='|' read -r ts name; do
    adb shell "touch -d '$ts' '/sdcard/x/$name'" </dev/null
done < mtimes.txt
```

`ssh`와 같은 고전적인 함정이다.

## 3. Python이 만든 목록 파일의 CRLF

Windows에서 `io.open(path, 'w')` 는 `\n` 을 `\r\n` 으로 변환한다.
그 파일을 bash 루프로 읽으면 변수 끝에 `\r`이 붙어 경로가 전부 깨진다.

```bash
$ head -1 pull_list.txt | od -c
0000000  I  M  G  _  6  9  9  2  .  m  o  v  \r  \n
```

**해결 (둘 중 하나)**

```python
# 쓸 때
io.open(path, 'w', encoding='utf-8', newline='\n').write(...)
```

```bash
# 읽기 전에 정리
tr -d '\r' < list.txt > list_lf.txt
```

## 4. 콘솔 출력의 한글 깨짐

`python -c` 등의 출력에서 한글이 `���` 로 보이는 경우가 있다.
**출력만 깨지는 것이고 파일 내용은 정상**이다. 파일을 쓸 때
`encoding='utf-8'` 만 지정돼 있으면 문제없다.

확인:

```bash
od -c 파일.md | head -2      # UTF-8 바이트인지 확인
cat 파일.md                  # 실제 내용 확인
```

## 5. `find` 가 기기에서 동작하지 않을 수 있다

Galaxy Z Fold 7(Android 17)에서 `adb shell find` 가 존재하는 파일에 대해서도
아무 결과를 내지 않았다:

```bash
adb shell "find /sdcard -iname '존재하는파일.mp4' 2>/dev/null"
# (결과 없음)
```

**해결**: `ls` + `grep` 으로 대체한다.

```bash
adb shell "ls '/sdcard/스윙'" | grep -i "6992"
```

> 새 도구를 쓸 때는 **결과가 있는 게 확실한 입력으로 먼저 검증**할 것.
> 이번엔 "찾는 파일이 없다"고 잘못 결론 낼 뻔했다.

## 6. 안전한 루프 템플릿

위 함정들을 모두 피한 형태:

```bash
export MSYS_NO_PATHCONV=1
ADB="/c/Users/hskang/AppData/Local/Android/Sdk/platform-tools/adb.exe"
SERIAL="R5KL709ZVYP"

tr -d '\r' < list.txt > list_lf.txt          # 3번 대비

ok=0; fail=0; n=0
while IFS= read -r f; do
    [ -z "$f" ] && continue
    n=$((n+1))
    if "$ADB" -s "$SERIAL" pull -a "/sdcard/스윙/$f" "D:/Work/Dest/$f" \
        </dev/null >/dev/null 2>&1; then      # 2번 대비
        ok=$((ok+1)); echo "[$n] ok   $f"
    else
        fail=$((fail+1)); echo "[$n] FAIL $f"
    fi
done < list_lf.txt
echo "완료: ok=$ok fail=$fail"
```

## 7. 대량 작업은 한 건으로 먼저 검증

3번의 실패 모두 **50개를 한 번에 돌려서** 발견이 늦어졌다.
단일 파일로 먼저 성공을 확인하고 루프를 돌리면 훨씬 빠르다.

```bash
# 먼저 이것부터
adb -s "$SERIAL" pull -a "/sdcard/스윙/한개.mp4" "D:/Work/Dest/한개.mp4"
# → "1 file pulled, 0 skipped. 40.2 MB/s" 확인 후 루프 실행
```
