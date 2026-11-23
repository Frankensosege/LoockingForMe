# Looking for Me — 프로젝트 개발 가이드라인

---

## 1. 프로젝트 개요
### 1-1. 개요
본 프로젝트는 Android 9(API 28) 이상 기기 내 로컬 저장소(On-device)에서 작동하는 고성능 인물 사진 검색 및 분류 애플리케이션입니다. 
외부 서버로 사용자의 미디어 데이터를 전송하지 않고, 기기 자체 AI 모델을 활용해 프라이버시를 보장하면서 원하는 인물이 포함된 사진을 신속하게 찾아내어 메인 화면에서 선택한 검색 대상이 포함된 사진들을 분류하여 목록화 하는 것을 목표로 합니다.
| 항목 | 내용 |
|------|------|
| 앱 이름 | 나를 찾아다오(LookingForMe) |
| 패키지 | `com.frankensosege.loockingforme` |
| 플랫폼 | Android (Kotlin + Jetpack Compose) |
| 최소 SDK | Android 9(API 28) 이상 |
| 아키텍처 | MVVM + Foreground Service + Room DB + ML Kit + TFLite(FaceNet)|
| 다국어 | Room DB 기반 동적 문자열 관리 (AppStrings) |
| 코드 품질 | Slack Compose Lint 규칙 전면 준수 |

### 1-2. 목적 및 기능
- **분류 목록:** 메인 화면에서 사진을 분류할 복수의 목록을 만들어 Room DB에 저장합니다. 목록의 추가는 '+' 버튼을 눌러 추가합니다.
- **직관적인 인물 지정:** 목록의 오른쪽 사진 아이콘을 클릭하여 설정된 특정 사진 폴더(디렉토리)에서 목록마다 검색대상 사진을 선택하고, 해당 사진 속 인물의 얼굴 영역을 터치하여 탐색 대상을 지정합니다.
- **로컬 폴더 탐색:** 설정된 특정 사진 폴더(디렉토리)를 스캔하여 조건에 맞는 사진을 필터링하여 목록별로 파일명을 분류하여 Room DB로 저장 합니다.
- **분류된 목록 확인:** 목록을 보여주고 선택한 목록으로 분류된 사진들을 보여 줍니다.
- **개인정보 보호(On-device AI):** 모든 얼굴 감지(Face Detection) 및 임베딩 비교(Face Recognition) 연산은 오프라인 상태에서 기기 자체 자원만을 활용하여 수행됩니다.

---

## 2. 기술 스택 및 아키텍처 제약 사항
안드로이드 시니어 개발자로서 클로드(Claude)는 코드를 생성하거나 검토할 때 반드시 다음 지침을 엄격히 준수해야 합니다.

1. **UI 프레임워크:** XML을 완전히 배제하고 **100% Jetpack Compose**만을 사용해 UI를 구성합니다. 모든 컴포저블 함수는 프리뷰(`@Preview`) 코드를 포함해야 합니다.
2. **코드 틴팅 규칙:** 대규모 프로젝트의 일관성과 성능을 위해 **Slack Compose Lint 규칙**을 반드시 준수합니다.
   - 예: 컴포저블의 가시성 규칙, 무분별한 `remember` 내 객체 할당 금지, `Modifier` 파라미터는 첫 번째 선택적 파라미터로 전달 및 기본값 제공(`modifier: Modifier = Modifier`), 람다 인자 최적화 등.
3. **아키텍처 패턴:** 데이터 흐름의 단방향 제어(UDF)를 위해 **MVVM(Model-View-ViewModel)** 패턴을 적용합니다. UI 상태 관리는 ViewModel 내에서 `MutableStateFlow`와 `StateFlow`를 조합하여 처리합니다.
4. **비동기 및 동시성:** 대량의 사진을 스캔하는 AI 연산은 메인 스레드를 차단하지 않도록 코틀린 코루틴(**Kotlin Coroutines**)의 `Dispatchers.Default` 또는 `Dispatchers.IO` 환경에서 수행합니다.
5. 앱내 모든 Text는 하드코딩 하지 말고 언어별로 Room DB에 저장해서 SettingScreen에서 선택한 언어에 따라 동적으로 표시하도록 합니다.

---

## 3. 개발 가이드라인

### 3-1. 화면 흐름 (Wireframe Flow)

```
최초 실행()
    └→ SettingsScreen (설정 화면)
            └→ [저장하고 시작] 버튼 클릭
                    └→ FindMyFaceScreen (메인 실행 화면)
                            ├→ [⚙️] → SettingsScreen
                            ├→ [폴더생성] → 화면 실행시 만들어지 목록이 이 있으면 목록을 보여 주고 없으면 목록명을 입력할 빈 필드를 보여준다.
                            ├→ [🖼️] → [찾을얼굴] → Directory 선택 Popup
                            │       └→ 선택한 사진파일 로드 후 찾을 얼굴 선택[최대 4장 선택가능]
                            ├→ [+] → 목록을 생성할 필드 추가
                            ├→ [찾을사진 dir] Directory 선택 버튼(default : SettingsScreen에서 지정한 Directory) 
                            ├→ [나를 찾아줘] -> 선택한 얼굴이 있는 사진 찾아 목록별로 분류한다.
                            ├→ [찾은 파일 명 목록]
                            │       ├→ 목록으로 분류된 사진 view
                            │       └→ [이전화면으로 클릭] → FindMyFaceScreen으로 이동
                            │
                            └→ [앱 종료] → 앱 완전 종료

재실행 시 (setup_done = true)
    └→ FindMyFaceScreen 바로 진입
```

- 설정 완료 여부: SharedPreferences `setup_done` Boolean으로 관리
### 3-2. 메인 화면 (FindMyFaceScreen)
- **기능:**
  - Room DB에 저장된 목록이 있으면 목록을 보여줍니다. 저장된 목록이 없는 경우 목록명을 입력할 빈 필드보여 줍니다.
  - 저장된 목록에 분류되지 않은 사진을 찾아 미분류 목록을 생성 합니다.(최초 실행시 폴더내 전 파일이 미분류 목록으로 지정 됩니다.)
  - 탐색할 얼굴 선택 (최대 4번)
    - 목록명을 입력하고 목록명 압력필드의 오른쪽에 붙어있는 사진 아이콘(🖼️: 현재 '+')을 클릭하여 사진을 선택할 팝업을 띄워 설정 화면 (SettingScreen)에서 선택한 '탐색대상폴더'에 있는 사진(들)을 보여 줍니다.
    - 사용자가 사진을 선택하면 선택한 사진을 팝업에 보여주고 얼굴부분을 표시해 줍니다.
    - 사용자가 표시된 인물의 얼굴을 클릭하면 터치 좌표를 계산하여 해당 인물의 얼굴 이미지를 크롭(Crop)하고 **facenet.tflite**모델을 사용하여 고유 특징점 벡터(Face Embedding)를 추출합니다.
    - 사용자가 선택한 얼굴을 바로 세워서 사진 아이콘(🖼️: 현재 '+') 대신 출력 됩니다.
  - 다른 탐색 대상이 있으면 '목록 이름 입력' 필드에 목록명을 입력하고, 오른쪽 옆 '+' 버튼을 클릭하여 목록을 추가할 수 있도록 합니다.
  - 선택된 얼굴 사진 스와이프시 목록에서 제거
  - 목록 삭제 시 해당 목록으로 분류되어 있는 사진들 '미분류'로 이동(다른 목록에 있는 사진 제외).
  - **좌측 상단**: ⚙️ 아이콘 버튼 → SettingsScreen 이동
  - **나를 찾아줘**: AdMob 전면 광고를 노출한 다음, Text 버튼 → 위 목록에서 선택한 얼굴들을 정규화하고, 선택한 얼굴이 있는 사진 찾아 목록별로 분류하여 폴더를 포함한 파일명을 저장합니다. 설정 화면 (SettingScreen)에서 입력한 유사도 기준치를 넘는 '동일 인물'의 사진들만 필터링하여 목록화 합니다.
  - 사진을 분류하는 동안 화면 아래 '사진 검색 중...' Text와 함께 프로그레스가 노출 됩니다. 사진분류 작업은 백그라운드로 실행 되어 화면이 종료되거나, 사용자가 다른 작업을 하는 동안에도 진행 하도록 합니다.
  - **앱 종료**: Text 버튼 → 종료 확인 팝업(배너 포함). 
    - 취소 : 팝업을 닫고 화면 복귀
    - 확인 : 앱을 종료합니다.
  - AdMob 광고 배너를 화면 하단에 배치 합니다.

### 3-3. 설정 화면 (SettingScreen)
- **기능:**
  - 언어 선택 목록: **영어 / 한국어 / 일본어 / 중국어 4개만** 표시 합니다.
  - `SupportedLanguage` enum에서 ENGLISH, KOREAN, JAPANESE, CHINESE만 정의 합니다.
  - 언어 변경 시 즉시 앱내 전체 UI에 선택 언어 반영 
  - 탐색 대상을 선택할 소스사진이 있는 경로를 텍스트로 보여줍니다. 오른쪽 옆에 '🗂️' 버튼을 위치 합니다.
  - '🗂️' 버튼 클릭 시 안드로이드 시스템의 **Storage Access Framework (SAF)** 팝업을 호출하여 유저가 직접 대상 디렉토리를 선택하도록 유도합니다.
  - 현재 탐색 대상으로 지정되어 있는 로컬 폴더 경로를 텍스트로 보여줍니다. 오른쪽 옆에 '🗂️' 버튼을 위치 합니다.
  - 소스 사진 폴더(source_dir_uri)는 얼굴 선택 팝업의 사진 그리드 출처로 사용됩니다.
  - '🗂️' 버튼 클릭 시 안드로이드 시스템의 **Storage Access Framework (SAF)** 팝업을 호출하여 유저가 직접 대상 디렉토리를 선택하도록 유도합니다.
  - 사진을 AI로 분류 시 매칭 percentage를 입력 받아 분류시 설정된 매칭 percentage이상이 되는 사진을 골라 분류 하도록 합니다.
  - 선택된 폴더의 URI 권한을 지속적으로 유지(Persistable Uri Permission)하도록 처리합니다.
  - 저장 시 SharedPreferences(`lfmf_settings`)에 저장합니다.
  - AdMob 광고 배너를 화면 하단에 배치 합니다.

### 3-4. 검색 결과 화면 (ResultScreen)
- **기능:**
  - 메인 화면에서 선택된 목록의 사진들을 타일 형태로 보여줍니다
  - 사진 타일 오른쪽위에 위치한 체크박스를 클릭하여 사진을 선택핳 수 있도록 합니다.
  - 체크박스에 체크되어 있는 사진을 재분류, 삭제(분류DB에서)하거나 블루투스 등으로 이동하는 작업을 합니다.
  - 재분류 시 현재 분류된 목록을 제외한 모든 목록 리스트를 보여주고, 저장시 AdMob 전면 광고를 노출한 다음, 선택한 모든 목록으로 재분류 합니다.
  - 체크박스에 체크gks 사진을 삭제할 때, 다른 목록에 분류돼 있지 않은 사진은 미분류로 이동한다.
  - **돌아가기**: Text 버튼 → 메인화면으로 돌아 갑니다.
  - AdMob 광고 배너를 화면 하단에 배치 합니다.

### 3-5. 다국어(i18n) 처리 원칙

- **모든 UI 텍스트**는 `AppStrings` data class를 통해 선택된 언어로 표시
- 문자열은 `StringsDatabase` (Room)에 저장, `StringsRepository`로 접근
- 언어 변경 시 즉시 전체 UI 갱신 (StateFlow)
- 앱 미시작 시 기본값(`defaultStrings`)은 한국어 기준

## 3-6. 인앱 업데이트기능
- 앱의 업데이트가 있으면 앱 실행시 업데트여부를 확인한 후 업대이트하고 업데이트가 끝나면 재실행 하도록 유도한다.

---

## 4. 프로젝트 파일(디렉토리) 구조
본 프로젝트는 MVVM 패턴과 관심사 분리(SoC)를 명확히 하기 위해 기능 및 계층별로 디렉토리를 구조화합니다.
광고 단위 ID는 build.gradle의 buildType별 buildConfigField(AD_*)로만 관리하며, 코드에 ID 문자열을 직접 쓰지 않는다.

```text
com.frankensosege.lookingfome/          # 패키지명 변경 반영
│
├── data/
│   ├── db/
│   │   └── AppDatabase.kt              
│   ├── model/
│   │   ├── FaceFeature.kt              # 얼굴 특징 벡터 데이터 모델
│   │   ├── PhotoItem.kt                # 사진 메타데이터 모델
│   │   └── SearchFolderList.kt         # 탐색 목록 및 분류 매핑 DB 엔티티
│   │
│   ├── repository/
│   │   ├── PhotoRepository.kt          # MediaStore 스캔 및 이미지 처리
│   │   └── PhotoRepositoryImpl.kt      # 레포지토리 구현체
│   │
│   └── strings/
│       ├── AppStrings.kt               # UI 동적 문자열 데이터 클래스
│       ├── SupportedLanguage.kt        # EN, KO, JA, ZH Enum 클래스
│       ├── StringsDao.kt               # 다국어 쿼리 DAO
│       ├── StringsDatabase.kt          # Room DB (v2)
│       └── StringsRepository.kt        # 다국어 데이터 제어 및 캐싱
│
├── service/
│       └── FaceScanService.kt        
│
├── ui/
│   ├── main/
│   │   ├── FindMyFaceScreen.kt         # 메인 실행 화면 컴포저블
│   │   ├── MainViewModel.kt            # 메인 UDF 상태 관리
│   │   └── FindMyFaceUiState.kt        # 메인 UI 상태 모델
│   ├── navigation/
│   │   └── LoockingForMeNavHost.kt
│   │
│   ├── setting/
│   │   ├── SettingScreen.kt            # 설정 화면 컴포저블
│   │   ├── SettingViewModel.kt         # 설정 UDF 상태 관리
│   │   └── SettingUiState.kt           # 설정 UI 상태 모델
│   │
│   ├── result/
│   │   ├── ResultScreen.kt             # 결과 분류 화면 컴포저블
│   │   ├── ResultViewModel.kt          # 결과 화면 상태 관리
│   │   └── ResultUiState.kt            # 결과 UI 상태 모델
│   │
│   └── theme/
│       ├── Color.kt                    # 디자인 시스템 정의
│       ├── Theme.kt                    # Compose 테마 래퍼
│       └── Type.kt                     # 타이포그래피 시스템
│
└── utils/
    ├── AdBanner.kt
    ├── AdManager.kt
    ├── InAppUpdateManager.kt
    ├── FaceAnalyzer.kt                 # MediaPipe Face Mesh 기반 벡터 추출
    └── StorageHelper.kt                # SAF 권한 및 URI 지속성 유틸리티
```
---

## 5. 다음 개발 시 주의사항 및 확장 포인트

### 반드시 지켜야 할 원칙

1. **새 UI 텍스트 추가 시**: `AppStrings` data class 필드 추가 → `StringsDatabase`의 4개 언어 모두 값 추가 → `StringsRepository.toAppStrings()` 매핑 추가 → `MainViewModel.defaultStrings` 추가 (영어 기준) — 이 4곳을 반드시 동시에 수정
2. **새 언어 추가 금지**: 현재 4개 언어(EN/KO/JA/ZH)가 확정 목록. 추가 필요 시 가이드라인 개정 필요
3. **하드코딩 문자열 금지**: 모든 사용자 노출 텍스트는 `strings.*` 참조
4. **DB 스키마 변경 시**: `AppDatabase` 또는 `StringsDatabase` 버전 번호 올릴 것. 현재 AppDatabase v7, StringsDatabase v7
5. 문자열 시드는 LoockingForMeApp.onCreate()에서 수행한다
6. **Compose Lint 준수**: 모든 신규 Composable 작성 및 기존 코드 수정 시 섹션 6의 체크리스트를 반드시 확인. 특히 아래 항목은 자주 누락되므로 주의:
  - 모든 UI Composable에 `modifier: Modifier = Modifier` 선언 및 루트 전달
  - `remember` 없이 `mutableStateOf` 단독 사용 금지
  - `Scaffold` padding 반드시 내부 콘텐츠에 적용
  - `InfiniteTransition` / `animateFloat` 등 애니메이션 API에 `label` 필수 지정
  - `LinearProgressIndicator`는 람다 형식 progress 사용

### 기능상 상충 발생 시

> 새 기능 개발 중 기존 가이드라인과 상충되는 경우, 반드시 먼저 질문하고 답변을 이 문서에 추가사항 안내 후 개발 진행.

---

## 6. Slack Compose Lint 준수 원칙

> **모든 Composable 코드는 Slack Compose Lint 규칙을 전면 준수한다.**
> 신규 코드 작성 및 기존 코드 수정 시 아래 규칙을 체크리스트로 활용한다.

### 6-1. Composable 함수 설계

#### ✅ Modifier 파라미터 필수 선언
모든 UI를 구성하는 `@Composable` 함수는 반드시 `modifier: Modifier = Modifier` 파라미터를 첫 번째 또는 명확한 위치에 선언하고, 루트 레이아웃에 전달해야 한다.

```kotlin
// ✅ 올바른 예
@Composable
fun MicIndicator(
  modifier: Modifier = Modifier,   // 항상 선언
  isListening: Boolean,
  ...
) {
  Column(modifier = modifier, ...) { ... }   // 루트에 전달
}

// ❌ 잘못된 예 — modifier 파라미터 없음
@Composable
fun MicIndicator(isListening: Boolean) {
  Column { ... }
}
```

#### ✅ Composable 함수 명명 규칙
- 값을 반환하는 Composable: 소문자 카멜케이스 (`remember`, `produceState` 등 관용어 제외)
- UI를 방출(emit)하는 Composable: **PascalCase** (대문자 시작)
- `@Composable` 람다 파라미터 이름: `content`를 기본으로 사용

```kotlin
// ✅ UI 방출 → PascalCase
@Composable fun RunningScreen(...) { }
@Composable fun StatCard(...) { }

// ✅ 값 반환 → camelCase
@Composable fun rememberScrollState(): ScrollState { }
```

#### ✅ Unit을 반환하는 Composable에 @Composable 람다 사용 시 후행 람다 형식 권장
```kotlin
// ✅ 올바른 예
SettingSection(title = "언어 선택") {
  DropdownSelector(...)
}

// ❌ 잘못된 예
SettingSection(title = "언어 선택", content = { DropdownSelector(...) })
```

---

### 6-2. 상태(State) 관리

#### ✅ remember 없이 mutableStateOf 사용 금지
Composable 내부에서 상태를 생성할 때는 반드시 `remember`로 감싸야 한다. 리컴포지션마다 초기화되는 버그를 방지한다.

```kotlin
// ✅ 올바른 예
var expanded by remember { mutableStateOf(false) }
var dragAccum by remember { mutableFloatStateOf(0f) }

// ❌ 잘못된 예 — 리컴포지션마다 초기화됨
var expanded by mutableStateOf(false)
```

#### ✅ 타입별 특화 State API 사용
| 타입 | 사용할 API |
|------|-----------|
| `Boolean` | `remember { mutableStateOf(false) }` |
| `Int` | `remember { mutableIntStateOf(0) }` |
| `Float` | `remember { mutableFloatStateOf(0f) }` |
| `Long` | `remember { mutableLongStateOf(0L) }` |
| 객체/String | `remember { mutableStateOf(...) }` |

#### ✅ ViewModel State는 StateFlow로, UI에서 collectAsStateWithLifecycle() 사용
```kotlin
// ViewModel
val state = MutableStateFlow(RunningState())

// Composable
val state by viewModel.state.collectAsStateWithLifecycle()

// ❌ 금지 — collectAsState()는 라이프사이클 미인식
val state by viewModel.state.collectAsState()
```

---

### 6-3. 람다와 이벤트 핸들러

#### ✅ 이벤트 콜백 파라미터 명명: `on` + 동사
```kotlin
// ✅ 올바른 예
onStartClick: () -> Unit
onNavigateToSettings: () -> Unit
onLanguageChange: (SupportedLanguage) -> Unit

// ❌ 잘못된 예
startCallback: () -> Unit
settingsNavigator: () -> Unit
```

#### ✅ 람다를 매번 새로 생성하지 않기 — 안정적인 참조 사용
Composable에 전달하는 람다가 매번 새 인스턴스로 생성되면 불필요한 리컴포지션이 발생한다. NavController 콜백 등은 상위에서 안정적인 참조로 전달한다.

```kotlin
// ✅ 올바른 예 — 상위 Composable에서 람다를 한 번만 정의하여 전달
RunningScreen(
  onNavigateToSettings = { navController.navigate(Routes.SETTINGS) },
  ...
)

// ❌ 잘못된 예 — 내부에서 매번 생성
Button(onClick = { viewModel.doSomething() })  // ViewModel 참조가 stable하면 허용
```

---

### 6-4. 성능 — 리컴포지션 최소화

#### ✅ 무거운 계산은 remember로 캐싱
```kotlin
// ✅ 올바른 예
val intervalLabels = remember(strings) {
  listOf(
    strings.intervalRandom to NotifyInterval.RANDOM,
    strings.interval5min   to NotifyInterval.MIN_5,
    ...
  )
}

// ❌ 잘못된 예 — 리컴포지션마다 리스트 재생성
val intervalLabels = listOf(
  strings.intervalRandom to NotifyInterval.RANDOM,
  ...
)
```

#### ✅ LaunchedEffect / SideEffect 올바른 사용
- 부수 효과(side effect)는 반드시 `LaunchedEffect`, `SideEffect`, `DisposableEffect` 안에서 처리
- Composable 본문에서 직접 코루틴 실행, 서비스 시작 등 금지

```kotlin
// ✅ 올바른 예
LaunchedEffect(language) {
  viewModel.changeLanguage(language)
}

// ❌ 잘못된 예 — Composable 본문에서 직접 부수 효과 실행
viewModel.changeLanguage(language)  // 리컴포지션마다 호출됨
```

#### ✅ key 파라미터 명시 — LaunchedEffect, remember
```kotlin
// ✅ key를 명확히 지정
LaunchedEffect(Unit) { /* 최초 1회만 실행 */ }
LaunchedEffect(state.isRunning) { /* isRunning 변경 시 실행 */ }

// ❌ key 없이 사용 — Lint 경고 발생
LaunchedEffect { }
```

---

### 6-5. Compose UI 구성 규칙

#### ✅ LinearProgressIndicator — progress 람다 형식 사용 (Compose 1.6+)
```kotlin
// ✅ 올바른 예 (Compose 1.6+ API)
LinearProgressIndicator(
  progress = { state.downloadProgress / 100f },
  modifier = Modifier.fillMaxWidth()
)

// ❌ 구버전 API — Lint 경고 발생
LinearProgressIndicator(
  progress = state.downloadProgress / 100f,
  ...
)
```

#### ✅ Scaffold padding 반드시 사용
`Scaffold`가 제공하는 `paddingValues`는 반드시 내부 콘텐츠에 적용해야 한다.

```kotlin
// ✅ 올바른 예
Scaffold { paddingValues ->
  Column(modifier = Modifier.padding(paddingValues)) { ... }
}

// ❌ 잘못된 예 — padding 무시
Scaffold { _ ->
  Column { ... }
}
```

#### ✅ Box/Column/Row — fillMaxSize/fillMaxWidth 등 크기 지정 시 modifier 순서
Modifier 체이닝은 선언 순서가 적용 순서이므로, 크기 → 패딩 → 클릭 순서를 권장한다.

```kotlin
// ✅ 권장 순서: 크기 → 패딩 → 동작
Modifier
  .fillMaxWidth()
  .padding(16.dp)
  .clickable { }

// ❌ 잘못된 순서 (패딩이 클릭 영역에 포함됨)
Modifier
  .clickable { }
  .padding(16.dp)
  .fillMaxWidth()
```

#### ✅ InfiniteTransition — label 파라미터 필수 지정
```kotlin
// ✅ 올바른 예
val infiniteTransition = rememberInfiniteTransition(label = "mic_pulse")
val scale by infiniteTransition.animateFloat(
  ...,
label = "scale"
)

// ❌ label 없음 — Lint 경고
val infiniteTransition = rememberInfiniteTransition()
val scale by infiniteTransition.animateFloat(...)
```

---

### 6-6. 접근성(Accessibility)

#### ✅ 아이콘 버튼에 contentDescription 지정
```kotlin
// ✅ 올바른 예
IconButton(onClick = onNavigateToSettings) {
  Icon(
    imageVector = Icons.Default.Settings,
    contentDescription = strings.settings   // 다국어 문자열 사용
  )
}

// 현재 코드처럼 Text("⚙️")를 사용할 경우 Box에 semantics 추가
Box(
  modifier = Modifier.semantics { contentDescription = strings.settings }
) {
  Text("⚙️", fontSize = 24.sp)
}
```

#### ✅ RadioButton — onClick = null + 부모 selectable 조합
```kotlin
// ✅ 올바른 예 — 접근성 트리 중복 방지
Row(
  Modifier.selectable(
    selected = selected,
    onClick  = onClick,
    role     = Role.RadioButton
  )
) {
  RadioButton(selected = selected, onClick = null)  // null로 위임
  Text(label)
}
```

---
