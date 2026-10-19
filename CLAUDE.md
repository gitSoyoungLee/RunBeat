# CLAUDE.md — RunBeat 작업 안내

이 파일은 Claude Code가 이 저장소에서 작업할 때 따를 규칙과, 진행 중인 v0.7 작업의 인수인계 내용이다.
(이전 작업은 클라우드 작업 공간에서 진행됐고, 실제 빌드를 위해 Claude Code로 옮겨왔다.)

## 1. 프로젝트 한눈에

- 초보 러너용 안드로이드 러닝 앱. 인터벌 코스 + 케이던스 메트로놈 + GPS 기록. 서버 없음(기록은 기기 내부 JSON).
- **Java** (Kotlin 아님), 안드로이드 기본 프레임워크 `Activity` 사용 (AppCompat/Compose 없음), XML 레이아웃 + 일부는 코드로 뷰 생성(`Ui` 도우미).
- 외부 라이브러리는 `osmdroid`(OpenStreetMap 지도) 하나뿐.
- minSdk 26 / targetSdk 35 / compileSdk 35, AGP 8.7.3, Gradle wrapper 8.11.1.
- 클래스 구조와 기능 목록, 지금까지의 개발 기록은 `README.md` 참고.

## 2. 사용자에 대해

- 자바를 공부하는 신입 개발자. **설명은 한국어로, 신입 눈높이로(10년차 강사처럼)** 해 주기.
- 갤럭시 폰에서 직접 APK를 설치해 쓴다. 이 앱을 포트폴리오로 쓸 예정이라 **커밋 기록과 README 개발 기록이 중요**하다.
- push는 사용자가 직접 한다 (Android Studio `Git > Push` 또는 `git push --all origin`). 묻지 않고 push하지 말 것.

## 3. 빌드와 테스트

```bash
./gradlew assembleDebug        # 디버그 APK → app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest    # 단위 테스트 (아래 2.의 테스트를 추가한 뒤)
```
- Windows에서 `JAVA_HOME`을 못 찾으면 Android Studio에 들어 있는 JDK를 쓴다:
  `C:\Program Files\Android\Android Studio\jbr`
- 폰이 USB 디버깅으로 연결돼 있으면 `adb install -r app/build/outputs/apk/debug/app-debug.apk` (`-r` = 덮어쓰기, 기록 유지).

**테스트 규칙**
1. 커밋마다 최소 `assembleDebug`가 통과해야 한다. 실패하면 커밋하지 않는다.
2. 아직 저장소에 단위 테스트가 없다. 이전 작업에서는 아래 순수 자바 클래스를 컨테이너에서 따로 떼어 테스트했다
   (26개 시나리오 통과). **v0.7 작업 중 이것들을 정식 JUnit 테스트로 옮기는 것을 권장**:
   - `IntervalEngine`: 구간 진행, 일시정지 중 타이머 정지, 건너뛰기, 10초 전/절반/완료 이벤트 횟수
     (주의: 입문 RUN은 워밍업 5:00 → 달리기 1/6 5:00~6:00 → 걷기 1/6 6:00~8:00)
   - `AutoPauseDetector`: 출발 30m 전 무시, 걷기 속도는 진행, 6초 정지 → 일시정지, 빠른 위치 2연속 또는 걸음 감지 → 재개
   - `Course`: 프리셋 총 시간(입문 28분/초보 30분/심화 40분/30분 지속 40분), flatten 구간 수, 라벨 "달리기 2/6", JSON 왕복
   - JUnit에서 `org.json`은 안드로이드 스텁이라 동작하지 않으므로 `testImplementation("org.json:json:20240303")` 추가 필요.
   - 의존성: `testImplementation("junit:junit:4.13.2")`
3. 화면이 바뀌는 작업은 커밋 메시지나 작업 보고에 **"실기기에서 확인할 것"** 목록을 남긴다.

## 4. 작업 규칙 (중요)

- **브랜치 전략**: `main`에서 기능별 브랜치 생성 → 작은 단위로 여러 번 커밋 → 끝나면 `main`에 `git merge --no-ff`로 병합 → 다음 브랜치는 병합된 `main`에서 시작.
- **커밋 메시지**: 한국어. 첫 줄 `feat:` / `fix:` / `refactor:` / `docs:` / `chore:` + 요약, 본문에 **무엇을, 왜**(문제 → 원인 → 해결). 한 커밋에는 한 가지 일만.
- 코드 주석도 한국어, 신입이 읽고 이해할 수 있게 "왜 이렇게 하는지"를 적는다.
- 각 작업이 끝나면 README의 **개발 기록**에 버전별로 문제 → 원인 → 해결을 남긴다 (v0.7은 release 브랜치에서 한 번에).

## 5. 꼭 지켜야 할 것

- `applicationId`(`com.runbeat.app`)와 서명 키(디버그 키 `C:\Users\soo71\.android\debug.keystore`)를 바꾸지 말 것.
  바뀌면 기존 앱 위에 덮어 설치가 안 돼서 사용자의 러닝 기록이 사라진다. 업데이트 때는 `versionCode`만 올린다.
- 기록 JSON(`RunRecord`) 형식을 바꿀 때는 예전 파일도 읽히게(`opt...` 기본값) 할 것.
- 모든 화면은 `BaseActivity`를 상속한다. Android 15 edge-to-edge 대응(시스템 바 영역만큼 여백)과 공통 헤더가 여기 있다.
- **색은 하드코딩 금지**. 새 색은 `values/colors.xml`(라이트)과 `values-night/colors.xml`(다크)에 **같은 이름으로 둘 다** 추가.
  (예외: 알림 아이콘 `ic_run`, 앱 아이콘, GPS 점 `dot`은 의도적으로 흰색)
- OpenStreetMap 지도에는 출처 표시(`CopyrightOverlay`, `RouteMap`에 있음)를 유지.
- 메트로놈은 오디오 포커스를 요청하지 않는다(음악과 섞이게). 음성 안내만 말할 때 DUCK 포커스를 잠깐 빌린다.

## 6. v0.7 진행 현황

| # | 브랜치 | 상태 |
|---|---|---|
| 1 | `fix/detail-map-scroll` | ✅ main에 병합됨 — 상세 지도 고정(스크롤 캡처 문제), 전체 화면 지도 `MapActivity`, OSM 출처 표시 |
| 2 | `feat/save-image` | ✅ main에 병합됨 — `ViewCapture`, `ImageSaver`(Pictures/RunBeat), 상세 화면 "이미지로 저장" |
| 3 | `feat/theme` | 🔄 **①②③ 완료, main 병합 대기** (지금 체크아웃된 브랜치) |
| 4 | `feat/settings` | ⏳ |
| 5 | `feat/sound-settings` | ⏳ |
| 6 | `feat/countdown` | ⏳ |
| 7 | `feat/backup` | ⏳ |
| 8 | `chore/release-0.7` | ⏳ |

### 3. feat/theme (진행 중)
- ✅ ① `refactor:` 하드코딩 색 → 색 리소스 (on_brand, on_warn, field, block_bg, chart_avg, ripple, ripple_strong, ripple_danger)
- ✅ ② `feat:` 라이트 팔레트(`values/`) + 다크(`values-night/`), `Base.AppTheme`(라이트: Theme.Material.Light + 시스템 바 아이콘 어둡게), WCAG 대비 점검 완료
- ✅ ③ `feat:` 테마 적용 로직 — `ThemeMode`(+ `ThemeModeTest` 9개, 저장소 첫 JUnit 테스트) / `BaseActivity.attachBaseContext`·`onResume` / `fix:` API 29 이하 상태바 플래그
  - 테마를 고르는 UI는 아직 없다(④ feat/settings). 그래서 지금은 항상 기본값 다크로 보인다.
  - 설정값: SharedPreferences `"runbeat"`의 `"theme"` = `"system"` / `"light"` / `"dark"`. **기본값은 `"dark"`** (기존 사용자 화면이 갑자기 바뀌지 않게).
  - `BaseActivity.attachBaseContext()`에서 `Configuration.uiMode`의 NIGHT 비트를 설정값으로 덮어쓴 Context로 교체 (`createConfigurationContext`). `"system"`이면 그대로.
  - 모드 → uiMode 계산은 **순수 자바 클래스(예: `ThemeMode`)로 분리해서 단위 테스트**.
  - `BaseActivity.onResume()`에서 적용된 모드와 현재 설정이 다르면 `recreate()` (설정 화면에서 바꾸고 돌아왔을 때 반영).
  - 버그 주의: `BaseActivity.onCreate()`의 API 29 이하 분기에서 `setSystemUiVisibility(...)`가 테마의 `windowLightStatusBar` 플래그를 지워버린다 → 기존 값에 `|`로 더해야 라이트 테마에서 상태바 아이콘이 보인다.
  - 라이트 테마에서 확인할 곳: 홈, 코스 고르기/편집, 기록 목록/상세, 전체 화면 지도, 다이얼로그(AlertDialog), NumberPicker, Switch.

### 4. feat/settings
- ① 설정값을 한 곳에서 읽고 쓰는 `Settings` 클래스 (기존 키 유지: `"weight"`, `"autoPause"`, `"bpm"`, `"mode"`, `"courseId"`, `"theme"` / prefs 이름 `"runbeat"` = `MainActivity.PREFS`)
- ② `SettingsActivity` + 홈 오른쪽 위 ⚙️ 버튼
- ③ 체중·자동 일시정지·배터리 최적화 해제를 홈 → 설정으로 이동. 자동 일시정지가 켜져 있으면 홈에 작은 표시를 남긴다(대회 전에 끄는 걸 잊지 않게).
- ④ 테마 선택: 시스템 따라가기 / 라이트 / 다크
- ⑤ 앱 정보: 버전(`versionName`), 지도 출처 "© OpenStreetMap contributors"

### 5. feat/sound-settings
- ① 메트로놈 볼륨 0~100% (`SoundPool.play`의 볼륨 인자, 러닝 중에도 즉시 반영)
- ② 음성 안내 볼륨 (`TextToSpeech.Engine.KEY_PARAM_VOLUME` 번들)
- ③ 안내 항목 켜고 끄기: 1km 안내 / 구간 전환 / 10초 전 / 절반 지점 (필터 로직은 순수 자바로 분리해 테스트)
- ④ 구간 전환 진동 (켜고 끄기, `VIBRATE` 권한 필요)
- 설정 화면에 "소리 미리 듣기" 버튼이 있으면 볼륨 조절이 편하다.

### 6. feat/countdown
- 시작을 누르면 "3, 2, 1, 출발!" 음성과 큰 숫자 표시 후 기록 시작. 설정에서 켜고 끄기(기본 켜짐). 카운트다운 중 취소 가능.

### 7. feat/backup
- ① 모든 기록(`RunStorage`) + 커스텀 코스(`CourseStore`)를 JSON 하나로 만드는 변환 로직 + **병합 규칙: 같은 id가 이미 있으면 건너뛴다** (단위 테스트)
- ② 내보내기: 파일 저장 창(`ACTION_CREATE_DOCUMENT`)으로 `RunBeat_backup_yyyyMMdd.json` 저장 → 사용자가 드라이브/카톡으로 보관
- ③ 불러오기: `ACTION_OPEN_DOCUMENT` → 병합 → "기록 N개, 코스 M개를 불러왔어요 (중복 K개 건너뜀)"
- 사용자가 업데이트 때 기록이 사라질까 걱정했기 때문에 넣는 기능. 기록 유지의 진짜 안전장치.

### 8. chore/release-0.7
- `versionCode = 2`, `versionName = "0.7"`
- README: 기능 목록 갱신 + 개발 기록 v0.7 (각 브랜치의 문제 → 원인 → 해결, 특히 "갤럭시 스크롤 캡처가 지도를 움직이던 문제")
- 실기기 확인 목록 정리해서 사용자에게 전달

## 7. 참고: 이미 정해진 설계 결정
- 코스 달리기에서 메트로놈은 **달리기 구간에서만** 울린다.
- 자동 일시정지 중에는 인터벌 타이머도 함께 멈춘다(운동 시간 기준). 걷기 구간에서도 자동 일시정지는 켜 둔다.
- 코스를 다 마쳐도 기록은 자동 종료하지 않는다(종료 버튼으로 저장).
- 기본 코스는 수정 불가, [복사]만 가능. 커스텀 코스 이름은 `커스텀 코스`, `커스텀 코스(1)`… 자동 생성(중복 허용).
- 1km 음성 안내는 자유 달리기에서도 이미 동작한다(사용자 확인 완료).
- 알려진 개선 후보(이번 범위 아님): 자동 일시정지 확정 시 6초를 운동 시간에서 빼기(`slowSince`로 되돌리기), 재개 시 멈춘 지점부터 거리 이어 붙이기, 현재 페이스에 GPS 도플러 속도 사용.
- 앱 이름 "RunBeat"는 App Store에 같은 이름(러닝 메트로놈 포함)이 있어서, 정식 출시 전에는 이름 변경을 검토해야 한다.
