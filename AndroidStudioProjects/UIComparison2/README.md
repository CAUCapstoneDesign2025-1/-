# UIComparison2

Android Accessibility Service 기반 UI 작업 추적 및 단계별 가이드 애플리케이션

## 프로젝트 개요

UIComparison2는 Android Accessibility Service를 활용하여 사용자의 UI 상호작용을 실시간으로 추적하고, 미리 정의된 단계(Step)와 비교하여 가이드를 제공하는 애플리케이션입니다.

## 주요 기능

### 1. 실시간 UI 추적
- **Accessibility Service**: Android의 접근성 서비스를 통해 모든 앱의 UI 이벤트 감지
- **이벤트 종류**: 클릭, 텍스트 입력, 화면 전환, 콘텐츠 변경 등
- **UI Snapshot**: 현재 화면의 패키지명, Activity명, View ID, 텍스트 노드 수집

### 2. 유연한 Step 매칭 알고리즘
- **패키지 매칭**: 대소문자 무시, 부분 일치 지원
- **View ID 매칭**: 정확히 일치, 접미사 일치, 부분 포함 등 다중 전략
- **텍스트 매칭**: 양방향 부분 일치, 대소문자 무시

### 3. 플로팅 윈도우 UI
- **드래그 가능**: 사용자가 원하는 위치로 이동 가능
- **접기/펼치기**: 화면 공간 확보를 위한 최소화 기능
- **실시간 상태 표시**:
  - 👀 **WAITING**: 올바른 화면으로 이동하세요
  - 🔍 **CHECKING**: Step Matching...
  - ✅ **MATCHED**: 현재 단계 확인됨!
  - 🎉 **COMPLETED**: 완료!
- **수동 네비게이션**: ◀ ▶ 버튼으로 이전/다음 단계 이동

### 4. 오류 감지 시스템
- **WRONG_APP**: 잘못된 앱 실행 감지 (즉시 보고)
- **FROZEN_SCREEN**: 화면 정체 감지 (클릭 후 3초 동안 변화 없음)
- **WRONG_CLICK**: 잘못된 버튼 클릭 감지 (즉시 보고)
- **연속 오류 임계값**: 일시적 오류 무시, 지속적 문제만 보고 (5회 연속)

### 5. 서버 연동
- **실시간 로깅**: HTTP POST를 통한 Flask 서버로 실시간 로그 전송
- **커리큘럼 로드**: 서버에서 단계별 커리큘럼 다운로드
- **세션 관리**: 여러 커리큘럼 세션 선택 및 관리

## 기술 스택

- **Language**: Kotlin
- **Min SDK**: 21 (Android 5.0 Lollipop)
- **Target SDK**: 34 (Android 14)
- **주요 라이브러리**:
  - Android Accessibility Service
  - Gson (JSON 직렬화)
  - Android Material Components

## 아키텍처

```
┌─────────────────────────────────────────────────────────────┐
│                        MainActivity                          │
│  - 권한 관리 (Accessibility, Overlay)                        │
│  - 커리큘럼 선택 및 로드                                       │
│  - 추적 시작/종료                                             │
└──────────────────────┬──────────────────────────────────────┘
                       │ SharedPreferences
                       ↓
┌─────────────────────────────────────────────────────────────┐
│                  StepMonitoringService                       │
│  - Accessibility 이벤트 수신                                  │
│  - UI Snapshot 생성 (debounced)                              │
│  - Step 매칭 및 오류 감지                                     │
└──────────┬─────────────────────┬──────────────────┬─────────┘
           │                     │                  │
           ↓                     ↓                  ↓
┌──────────────────┐  ┌──────────────────┐  ┌─────────────┐
│   StepMatcher    │  │ FloatingWindow   │  │ServerLogger │
│  - 매칭 알고리즘  │  │    Manager       │  │ - HTTP POST │
│  - 오류 감지      │  │  - UI 표시/제어  │  │ - 실시간 로그│
└──────────────────┘  └──────────────────┘  └─────────────┘
```

## 설치 및 실행

### 사전 요구사항
- Android Studio Arctic Fox 이상
- Android SDK 34
- JDK 11 이상

### 빌드 방법
```bash
# 저장소 클론
git clone https://github.com/CAUCapstoneDesign2025-1/UIComparison2.git
cd UIComparison2

# 디버그 빌드
./gradlew assembleDebug

# APK 설치 (에뮬레이터/디바이스 연결 필요)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 서버 실행 (선택사항)
커리큘럼 로드 기능을 사용하려면 Flask 서버가 필요합니다:
```bash
# 서버 디렉토리로 이동 (별도 저장소)
cd mobilegpt-server

# Python 서버 실행
python -u server.py
```

## 사용 방법

### 1. 권한 설정
1. 앱 실행
2. **"Enable Accessibility Service"** 버튼 클릭
   - 설정 > 접근성 > UIComparison2 활성화
3. **"Allow Overlay Permission"** 버튼 클릭
   - 다른 앱 위에 표시 권한 허용

### 2. 커리큘럼 선택
1. **"Select Step File"** 버튼 클릭
2. 서버에서 제공하는 커리큘럼 세션 선택
3. 추적할 Step 선택 (다중 선택 가능)
4. **"Load Steps"** 클릭

### 3. 추적 시작
1. **"Start Tracking"** 버튼 클릭
2. 자동으로 홈 화면으로 이동
3. 플로팅 윈도우가 표시됨
4. 안내에 따라 앱 조작 수행

### 4. 플로팅 윈도우 조작
- **드래그**: 윈도우를 터치하고 이동
- **최소화**: "—" 버튼 클릭
- **펼치기**: "+" 버튼 클릭
- **이전 단계**: ◀ 버튼 클릭
- **다음 단계**: ▶ 버튼 클릭
- **닫기**: ✕ 버튼 클릭

## 핵심 컴포넌트

### StepMonitoringService
접근성 서비스의 핵심 구현체

**주요 메서드**:
- `onAccessibilityEvent()`: UI 이벤트 수신
- `createAndProcessSnapshot()`: UI 스냅샷 생성 및 분석
- `handleClickEvent()`: 클릭 이벤트 즉시 처리
- `handleTextChanged()`: 텍스트 입력 즉시 처리
- `moveToPreviousStep()`, `moveToNextStep()`: 수동 네비게이션

### StepMatcher
Step 매칭 및 오류 감지 알고리즘

**매칭 전략**:
```kotlin
// 패키지 매칭 (contains)
snapshot.packageName.contains(step.expectation.expectedPackage, ignoreCase = true)

// View ID 매칭 (다중 전략)
visibleViewId == keyView.viewId ||                        // 정확히 일치
visibleViewId.endsWith("/${keyView.viewId}") ||           // 접미사 일치
visibleViewId.endsWith(":id/${keyView.viewId}") ||        // Android ID 접미사
visibleViewId.contains(keyView.viewId, ignoreCase = true) // 부분 포함

// 텍스트 매칭 (양방향)
textNode.contains(keyView.text, ignoreCase = true) ||
keyView.text.contains(textNode, ignoreCase = true)
```

**오류 감지**:
```kotlin
// WRONG_APP (startsWith 체크 - 관대함)
!snapshot.packageName.startsWith(expected) && !expected.startsWith(snapshot)

// FROZEN_SCREEN (3초 기준)
클릭 후 3초 동안 UI 변화 없음

// WRONG_CLICK (즉시 보고)
클릭한 View가 expectedKeyViews에 없음
```

### FloatingWindowManager
플로팅 윈도우 UI 관리

**기능**:
- `show()`, `hide()`: 윈도우 표시/숨김
- `setCurrentStep()`: 현재 단계 정보 업데이트
- `setStatus()`: 상태 변경 (WAITING, CHECKING, MATCHED, COMPLETED)
- `animateSuccess()`: 성공 애니메이션
- `showError()`: 오류 배너 표시

### ServerLogger
실시간 서버 로깅

**로그 타입**:
- `logServiceStatus()`: 서비스 상태 변경
- `logSnapshot()`: UI 스냅샷 정보
- `logStepCheck()`: Step 확인 시도
- `logStepMatched()`: Step 매칭 성공
- `logError()`: 오류 발생
- `logAllCompleted()`: 모든 단계 완료

## 데이터 모델

### Step
```kotlin
data class Step(
    val stepId: Int,
    val stepName: String,
    val expectation: Expectation,
    var isCompleted: Boolean = false
)
```

### Expectation
```kotlin
data class Expectation(
    val expectedPackage: String,
    val expectedActivity: String?,
    val expectedKeyViews: List<KeyView>
)
```

### KeyView
```kotlin
data class KeyView(
    val viewId: String?,
    val text: String?,
    val contentDescription: String?
)
```

### UISnapshot
```kotlin
data class UISnapshot(
    val packageName: String,
    val activityName: String,
    val visibleViews: List<String>,
    val textNodes: List<String>
)
```

## 주요 설정

### Accessibility Service 설정
`app/src/main/res/xml/accessibility_service_config.xml`:
```xml
<accessibility-service xmlns:android="http://schemas.android.com/apk/res/android"
    android:accessibilityEventTypes="typeViewClicked|typeWindowStateChanged|typeViewTextChanged|typeWindowContentChanged"
    android:accessibilityFeedbackType="feedbackGeneric"
    android:accessibilityFlags="flagReportViewIds|flagRetrieveInteractiveWindows"
    android:canRetrieveWindowContent="true"
    android:notificationTimeout="100" />
```

### 디바운스 설정
- **클릭/텍스트 입력**: 0ms (즉시 처리)
- **화면 변경**: 150ms (과도한 이벤트 방지)

### 오류 임계값
- **연속 오류 카운트**: 5회
- **화면 정체 판정**: 3초

## 권한

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />
<uses-permission android:name="android.permission.INTERNET" />
```

## 트러블슈팅

### 접근성 서비스가 이벤트를 수신하지 못함
- `accessibility_service_config.xml`에서 `android:packageNames` 속성 제거
- 빈 문자열(`""`)일 경우 모든 이벤트 차단됨

### 플로팅 윈도우가 표시되지 않음
- Overlay 권한 확인: `Settings.canDrawOverlays()`
- 서비스 연결 전 권한 체크 필수

### 매칭이 너무 엄격함/느슨함
- `StepMatcher.kt`에서 매칭 로직 조정:
  - 패키지: `contains`, `startsWith`, `equals` 선택
  - View ID: 매칭 전략 추가/제거
  - 텍스트: 양방향 vs 단방향 선택

### 오류가 너무 자주/적게 보고됨
- `ERROR_THRESHOLD` 값 조정 (기본 5회)
- `FROZEN_SCREEN` 시간 조정 (기본 3초)
- 특정 오류 타입 비활성화

## 개발 노트

### 완료된 Step에서 로그 수집 방지
```kotlin
// StepMonitoringService.kt:187-192
if (currentStepIndex < currentSteps.size && currentSteps[currentStepIndex].isCompleted) {
    Log.d(TAG, "✅ 현재 Step이 이미 완료됨. 스냅샷 수집 건너뜀")
    return
}
```

### Fresh Start 기능
```kotlin
// MainActivity.kt:203-229
// tracking_active를 false → true로 전환하여 서비스 리셋
with(sharedPref.edit()) {
    putBoolean("tracking_active", false)
    apply()
}
Handler(mainLooper).postDelayed({
    with(sharedPref.edit()) {
        putBoolean("tracking_active", true)
        apply()
    }
}, 100)
```

## 라이선스

이 프로젝트는 중앙대학교 2025-1 캡스톤 디자인 과정의 일부입니다.

## 기여

버그 리포트, 기능 제안, Pull Request는 언제나 환영합니다!

## 연락처

- **Organization**: CAU Capstone Design 2025-1
- **Repository**: https://github.com/CAUCapstoneDesign2025-1/UIComparison2

---

**Last Updated**: 2025-12-10
