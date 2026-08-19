# Project Beta — 볼더링 동작 분석 (Android)

휴대폰 카메라만으로 촬영한 볼더링 등반 영상을 분석해 **속도 곡선 · 안정성 곡선 · 크럭스(crux) 구간**을 산출하는 온디바이스 안드로이드 앱입니다.

> **코드네임 안내** — "Project Beta"는 내부 코드네임입니다. climbing 은어인 "beta"(루트 공략법 / 동작 정보)에서 따왔으며, 정식 제품명은 시장 조사 이후 결정합니다.

---

## 1. 개요

| 항목 | 내용 |
| --- | --- |
| 플랫폼 | Android 네이티브 (Kotlin) |
| 최소 SDK | 26 (Android 8.0) / target · compile SDK 34 |
| 패키지 | `com.projectbeta` |
| 처리 방식 | **전 과정 온디바이스** — 서버·백엔드·네트워크 호출 없음 |
| 촬영 조건 | **고정 카메라** (삼각대 또는 거치) — 등반 중 카메라 이동 없음 |
| 분석 시점 | 등반 종료 후 **배치 분석** (실시간 오버레이 아님) |
| 분석 범위 | **순수 동작 분석** — 홀드/루트 인식은 범위 밖 |

### 사용 흐름

1. **캘리브레이션** — 등반 전 화면에서 기준 길이(예: 본인 키)를 탭해 픽셀↔실제 거리 배율을 정합니다. 생략하면 상대 단위(body-heights/sec)로 대체됩니다.
2. **촬영** — CameraX로 한 번의 등반 시도를 녹화합니다.
3. **분석** — MediaPipe 포즈 추정 → 궤적 생성 → 지표 계산이 순차적으로 실행됩니다.
4. **리포트** — 요약 통계, 속도/안정성 차트, 스켈레톤 오버레이 영상 리플레이를 확인합니다.
5. **히스토리** — 모든 분석 결과가 로컬에 저장되어 지난 등반을 다시 열어볼 수 있습니다.

---

## 2. 빠른 시작

### 사전 준비

- JDK 17
- Android SDK 34 (Android Studio 또는 command-line tools)
- **MediaPipe 포즈 모델 파일** — 저장소에 포함되어 있지 않으므로 직접 내려받아야 합니다.

```
app/src/main/assets/pose_landmarker_full.task
```

이 파일이 없으면 `PoseLandmarker.createFromOptions` 단계에서 실패합니다. 자세한 내용은 [`app/src/main/assets/README.md`](app/src/main/assets/README.md)를 참고하세요.

### 빌드 · 테스트

```bash
./gradlew :app:assembleDebug        # 디버그 APK 빌드
./gradlew test                      # JVM 단위 테스트 (JUnit 5)
```

`engine`, `pipeline`, `pose`(매핑 로직), `data`(매퍼), `report`(좌표 변환) 계층은 안드로이드 의존성이 없어 기기 없이 JVM에서 바로 테스트됩니다.

---

## 3. 아키텍처

```
[Capture]  CameraX 녹화
    ↓
[Calibration]  픽셀 → 실제 거리 배율 (선택, 미수행 시 상대 단위)
    ↓
[Pose]  MediaPipe PoseLandmarker — 프레임별 관절 좌표 + 신뢰도
    ↓
[Depth fusion]  (선택 seam — Android v1에서는 항상 비활성, 향후 iOS+LiDAR용)
    ↓
[TrajectoryBuilder]  COM·사지 위치 시계열 + 스무딩
    ↓
[MetricsEngine]  속도 곡선 · 안정성 곡선 · 크럭스 구간
    ↓
[Report / History]  차트 + 스켈레톤 오버레이 리플레이, Room 영속화
```

**설계 원칙:** `engine` 계층은 순수 Kotlin으로 유지되어 CameraX·MediaPipe·Android API를 전혀 알지 못합니다. 하드웨어 의존성은 `PoseEstimator` 인터페이스 뒤에 격리되어 있으며, 이 덕분에 향후 iOS + LiDAR 깊이 융합이나 Path B(멀티 카메라, 홀드 인식) 확장이 **재작성이 아닌 추가**로 가능합니다.

---

## 4. 핵심 지표

### 속도 (Speed)
스무딩된 궤적에서 `|d(COM)/dt|`로 계산합니다. 캘리브레이션이 있으면 실제 단위, 없으면 body-heights/sec로 보고하며, 속도-시간 곡선과 요약값(평균 페이스, 최고 속도)을 함께 제공합니다.

### 안정성 (Stability)
두 신호를 결합한 불안정성 점수입니다.
- **Sway** — 등반 진행 방향에 수직인 축의 COM 분산 (좌우 흔들림)
- **Jerk** — 가속도의 변화율(위치의 3차 미분). 생체역학의 표준 부드러움 지표로, 값이 클수록 동작이 덜 통제된 상태입니다.

### 크럭스 (Crux)
"낮은 속도 / 긴 체류 시간" + "높은 불안정성" + "다음 동작 전 정지 시간"을 가중 결합한 난이도 점수의 국소 최댓값을 **단일 프레임이 아닌 시간 구간**으로 보고합니다.

> ⚠️ **검증 필요:** 난이도 점수의 가중치는 검증된 공식이 아니라 **가설**입니다. 실제 등반 데이터(가능하면 코치가 크럭스 위치에 동의한 영상)에 대한 튜닝이 선행되어야 출력을 신뢰할 수 있습니다. 지표 튜닝은 파이프라인이 end-to-end로 동작한 이후의 별도 반복 과제로 다룹니다.

---

## 5. 디렉터리 구조

```
project-beta/
├── index.md                      # 이 문서
├── settings.gradle.kts           # 루트 Gradle 설정 (:app 단일 모듈)
├── gradle.properties
├── gradlew / gradlew.bat
├── app/
│   ├── build.gradle.kts          # 의존성: CameraX, MediaPipe, Room, Media3, MPAndroidChart
│   └── src/
│       ├── main/
│       │   ├── AndroidManifest.xml
│       │   ├── assets/           # pose_landmarker_full.task (직접 배치)
│       │   ├── res/values/
│       │   └── java/com/projectbeta/
│       │       ├── engine/       # 순수 Kotlin 분석 코어
│       │       ├── pose/         # 포즈 추정 경계 (인터페이스 + MediaPipe 구현)
│       │       ├── pipeline/     # 분석 오케스트레이터
│       │       ├── capture/      # CameraX 촬영 + 캘리브레이션 UI
│       │       ├── data/         # Room 영속화
│       │       ├── report/       # 리포트 화면
│       │       └── history/      # 히스토리 화면
│       └── test/java/com/projectbeta/   # JUnit 5 단위 테스트
└── docs/superpowers/
    ├── specs/                    # 설계 문서
    └── plans/                    # 구현 계획
```

### 패키지별 상세

| 패키지 | 역할 | 주요 파일 |
| --- | --- | --- |
| [`engine`](app/src/main/java/com/projectbeta/engine) | 하드웨어를 모르는 순수 Kotlin 분석 코어 | [`Point3D`](app/src/main/java/com/projectbeta/engine/Point3D.kt) · [`PoseFrame`](app/src/main/java/com/projectbeta/engine/PoseFrame.kt) · [`Trajectory`](app/src/main/java/com/projectbeta/engine/Trajectory.kt) · [`TrajectoryBuilder`](app/src/main/java/com/projectbeta/engine/TrajectoryBuilder.kt) · [`MetricsEngine`](app/src/main/java/com/projectbeta/engine/MetricsEngine.kt) · [`CalibrationCalculator`](app/src/main/java/com/projectbeta/engine/CalibrationCalculator.kt) · [`AnalysisReport`](app/src/main/java/com/projectbeta/engine/AnalysisReport.kt) |
| [`pose`](app/src/main/java/com/projectbeta/pose) | 포즈 추정 경계 — 유일한 MediaPipe 의존 지점 | [`PoseEstimator`](app/src/main/java/com/projectbeta/pose/PoseEstimator.kt) · [`MediaPipePoseEstimator`](app/src/main/java/com/projectbeta/pose/MediaPipePoseEstimator.kt) |
| [`pipeline`](app/src/main/java/com/projectbeta/pipeline) | 영상 → 리포트 오케스트레이션 | [`AnalysisPipeline`](app/src/main/java/com/projectbeta/pipeline/AnalysisPipeline.kt) |
| [`capture`](app/src/main/java/com/projectbeta/capture) | CameraX 촬영 화면 (런처) + 캘리브레이션 오버레이 | [`CaptureActivity`](app/src/main/java/com/projectbeta/capture/CaptureActivity.kt) · [`CalibrationOverlayView`](app/src/main/java/com/projectbeta/capture/CalibrationOverlayView.kt) |
| [`data`](app/src/main/java/com/projectbeta/data) | Room DB — 등반 기록 영속화 (`reportJson` 블롭 + 조회용 컬럼) | [`ClimbRecord`](app/src/main/java/com/projectbeta/data/ClimbRecord.kt) · [`ClimbDao`](app/src/main/java/com/projectbeta/data/ClimbDao.kt) · [`ClimbDatabase`](app/src/main/java/com/projectbeta/data/ClimbDatabase.kt) · [`ClimbRepository`](app/src/main/java/com/projectbeta/data/ClimbRepository.kt) · [`ClimbMapper`](app/src/main/java/com/projectbeta/data/ClimbMapper.kt) · [`ReportPayload`](app/src/main/java/com/projectbeta/data/ReportPayload.kt) |
| [`report`](app/src/main/java/com/projectbeta/report) | 리포트 화면 — 차트 + 스켈레톤 오버레이 플레이어 | [`ReportActivity`](app/src/main/java/com/projectbeta/report/ReportActivity.kt) · [`SkeletonOverlayView`](app/src/main/java/com/projectbeta/report/SkeletonOverlayView.kt) · [`PoseOverlayMath`](app/src/main/java/com/projectbeta/report/PoseOverlayMath.kt) |
| [`history`](app/src/main/java/com/projectbeta/history) | 지난 등반 카드 리스트 (크럭스 구간 루프 재생 + 스파크라인) | [`HistoryActivity`](app/src/main/java/com/projectbeta/history/HistoryActivity.kt) · [`HistoryAdapter`](app/src/main/java/com/projectbeta/history/HistoryAdapter.kt) · [`SparklineView`](app/src/main/java/com/projectbeta/history/SparklineView.kt) · [`ClimbCardData`](app/src/main/java/com/projectbeta/history/ClimbCardData.kt) |

### 화면 이동

```
CaptureActivity ──(녹화 + 분석)──> ReportActivity
CaptureActivity ──(History 버튼)──> HistoryActivity ──(카드 탭)──> ReportActivity
```

---

## 6. 기술 스택

| 영역 | 사용 기술 |
| --- | --- |
| 언어 · 빌드 | Kotlin 1.9.24, Gradle (Kotlin DSL), AGP 8.5.0, KSP |
| 촬영 | AndroidX CameraX 1.3.4 (core / camera2 / lifecycle / video / view) |
| 포즈 추정 | MediaPipe Tasks Vision 0.10.14 (`PoseLandmarker`) |
| 영속화 | Room 2.6.1 + kotlinx.serialization 1.6.3 |
| 재생 | AndroidX Media3 (ExoPlayer) 1.3.1 |
| 차트 | MPAndroidChart v3.1.0 (히스토리 스파크라인은 자체 Canvas 뷰) |
| 비동기 | kotlinx.coroutines 1.8.1 |
| 테스트 | JUnit 5 (Jupiter) 5.10.2 |

---

## 7. 예외 처리 원칙

- **가려짐(Occlusion)** — 짧은 공백(≤5 프레임)은 보간하고, 그보다 긴 구간은 "tracking lost"로 **표시**합니다. 위치를 지어내지 않습니다.
- **낮은 신뢰도 키포인트** — 스무딩으로 덮지 않고 **버립니다**.
- **다중 인물** — 가장 크고 중앙에 가까운 사람을 등반자로 간주하며, 비슷한 크기의 인물이 계속 잡히면 모호성을 표시합니다.
- **캘리브레이션 미수행** — 상대 단위로 대체합니다. 보정되지 않은 픽셀 거리를 실제 단위로 취급하지 않습니다.
- **분석 실패** — 포즈 모델 누락 등은 조용히 넘어가지 않고 명시적으로 실패하며, `CaptureActivity`가 오류 다이얼로그를 띄우고 기록을 생성하지 않습니다.
- **영상 파일 누락** — 리포트 통계·차트는 `reportJson`에 독립 저장되므로, 영상이 없으면 "video unavailable" 상태로만 표시하고 크래시하지 않습니다.

---

## 8. 문서 색인

| 문서 | 설명 |
| --- | --- |
| [v1 (Path A) 설계](docs/superpowers/specs/2026-07-03-project-beta-bouldering-analysis-design.md) | 제품 방향(Path A/B), 아키텍처, 지표 정의, 예외 처리 |
| [Phase 2 리포트 UI 설계](docs/superpowers/specs/2026-07-04-project-beta-phase2-report-ui-design.md) | 리포트 화면, 히스토리, Room 영속화 설계 |
| [Phase 1 구현 계획](docs/superpowers/plans/2026-07-03-project-beta-phase1-core-pipeline.md) | 코어 파이프라인 태스크 분해 |
| [에셋 준비 안내](app/src/main/assets/README.md) | MediaPipe 모델 파일 배치 방법 |

---

## 9. 범위 밖 · 향후 계획

**현재 범위 밖 (의도적 제외)**
- 홀드/루트 인식 (SAM 기반 세그멘테이션, 색·테이프 기반 스타트 인식, "잡은 홀드" 추적)
- 멀티 카메라 구성
- 서버 기반 코스/세션 관리
- 녹화 중 실시간 화면 피드백
- 리포트 공유·내보내기, 클라우드 동기화, 저장 공간 자동 정리

**계획된 확장**
- **iOS 네이티브 포팅 (Swift)** — ARKit LiDAR 깊이 융합 포함. `PoseFrame`이 관절별 nullable depth를 이미 수용하므로 추가 작업으로 처리됩니다.
- **Path B (짐·코치 대상 솔루션)** — 고급 하드웨어, 홀드 단위 인식, 서버 사이드 코스 관리.
- **지표 튜닝** — 실제 등반 데이터 기반 크럭스 난이도 가중치 검증.
