# BandPlus Implementation Plan (Updated)

**Goal:** Android app for Xiaomi Smart Band 9 providing deep health analytics, long-term history, OLED design, and 100% local-first privacy (no Xiaomi cloud account).

**Architecture:** Fork of upstream Gadgetbridge (Codeberg canonical, AGPLv3) preserving its Bluetooth/protocol/database engine. All user-facing UI lives in a separate Jetpack Compose module (`:bandplus`, package `vn.bandplus`) that queries Gadgetbridge's GreenDAO database via a single `HealthRepository`. Upstream `app/` modifications are strictly bounded to rebrand and launcher hooks to keep monthly upstream merges trivial.

**Tech Stack:** Kotlin 2.x, Jetpack Compose + Material 3, Vico (charts), WorkManager, Health Connect, Glance (widgets), GreenDAO (Gadgetbridge DB).

---

## Progress Ledger & Current Baseline

* **Base Repository**: Codeberg canonical (`https://codeberg.org/Freeyourgadget/Gadgetbridge.git`), local path `C:\Users\thanh\BandPlus` on branch `bandplus`.
* **Toolchain**: Eclipse Temurin JDK 21, Android SDK (platforms;android-37.0, build-tools 35.0.0), Gradle 9.9.1, AGP 9.5.1.
* **[x] Task 1 Completed (`5702011255`)**:
  - `applicationId`: `vn.bandplus`
  - `app_name`: `BandPlus`
  - `:bandplus` Compose module initialized and compiled into `:app`.
  - Full codebase reconnaissance completed (`docs/recon.md`).
* **[x] Fork Engines Cherry-Picked (`dfdceb2b9e`)**:
  - `HeartRatePercentiles.java`: R-7 linear interpolation for P5, P25, P50, P75, P95 (6/6 unit tests passing).
  - `HealthCsvExporter.java`: UTF-8 raw sample stream & daily/range percentile CSV exporter (3/3 unit tests passing).
  - `HealthDataExport.kt` + `health_analytics.xml`: Android SAF document picker contract and cyan percentile palette.
  - Verified with `./gradlew testMainlineDebugUnitTest` (all 9 unit tests passing).

---

## Visual Design Specification (Adopted from Fork)

* **Background**: Pure OLED black (`#000000`).
* **Surfaces & Cards**: Dark surface (`#121212` / `#161616`) with 1dp border outline (`#262626`).
* **Primary Accent**: Cyan (`#8BDCFB`).
* **HR Percentile Palette**:
  - P5: `#4BA6C9` (Deep rest)
  - P25: `#64C2E5` (Sedentary baseline)
  - P50: `#8BDCFB` (Median / typical)
  - P75: `#C6ECFB` (Active baseline)
  - P95: `#FFFFFF` (Sustained peak)
* **Components**: 8dp rounded outlined buttons and cards without heavy filled backgrounds. Linear bar gauges replace circular rings.

---

## Updated Implementation Tasks

### [x] Task 1: Fork, build, rebrand, install (COMPLETED)
- [x] Clone Codeberg Gadgetbridge, create `bandplus` branch.
- [x] Configure JDK 21, `local.properties`, android-37.0.
- [x] Rebrand `applicationId` (`vn.bandplus`) and `app_name` (`BandPlus`).
- [x] Create `:bandplus` Compose module and placeholder `BandPlusActivity`.
- [x] Populate `docs/recon.md`.
- [x] Cherry-pick `HeartRatePercentiles`, `HealthCsvExporter`, and unit tests.
- [x] Verify `./gradlew assembleMainlineDebug` and `./gradlew testMainlineDebugUnitTest`.

---

### [ ] Task 2: Background Reliability & Auto-Sync
*Files:*
- `bandplus/src/main/java/vn/bandplus/setup/ReliabilityScreen.kt`
- Modify app start flow to show setup dialog once after initial pairing.

*Interfaces:*
- Produces: `isIgnoringBatteryOptimizations(ctx: Context): Boolean`, route `"setup/reliability"`.

- [ ] *Step 1:* Compose screen with 3 system permission checks + 1-tap fix intents:
  1. Battery Optimization whitelist (`ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS`).
  2. Notification Listener access (`ACTION_NOTIFICATION_LISTENER_SETTINGS`).
  3. OEM Autostart (deep-link to `com.vivo.permissionmanager`, fallback to App Details).
- [ ] *Step 2:* Set Gadgetbridge's periodic auto-sync to 30 minutes by default.
- [ ] *Step 3:* Commit `Keep band connected overnight on vivo`.
- [ ] *Device check:* Overnight test on vivo PD2445M: morning sleep data fetched without manually launching app.

---

### [ ] Task 3: Health Data Repository
*Files:*
- `bandplus/src/main/java/vn/bandplus/data/HealthRepository.kt`
- `bandplus/src/main/java/vn/bandplus/data/Models.kt`

*Interfaces:*
- Consumes: GreenDAO `XiaomiActivitySampleDao`, `XiaomiDailySummarySampleDao`, `XiaomiSleepStageSampleDao`, `XiaomiSleepTimeSampleDao`.
- Produces:
  - `data class DaySummary(date: LocalDate, steps: Int?, kcal: Int?, activeMin: Int?, restingHr: Int?, sleep: SleepNight?, spo2Avg: Int?, spo2Min: Int?, stressAvg: Int?)` (null for missing days, never fake 0).
  - `data class SleepNight(start: Instant, end: Instant, deepMin: Int, lightMin: Int, remMin: Int, awakeMin: Int, respiratoryRate: Int?)`
  - `data class HrPoint(t: Instant, bpm: Int)`
  - `HealthRepository`: `day(date): Flow<DaySummary>`, `range(from, to): Flow<List<DaySummary>>`, `hr(date): Flow<List<HrPoint>>`, `lastSyncedAt(): Flow<Instant?>`.

- [ ] *Step 1:* Implement queries on `Dispatchers.IO`, zone handling via `ZoneId.systemDefault()`.
- [ ] *Step 2:* Sleep day assignment rule: a night belongs to the date of its wake-up time.
- [ ] *Step 3:* Integrate `HeartRatePercentiles` to calculate daily P5, P25, P50, P75, P95 on demand.
- [ ] *Step 4:* Commit `Add health repository over Gadgetbridge DB`.
- [ ] *Device check:* Output `day(today)` matches raw database values; walking out of range mid-sync does not produce duplicate records.

---

### [ ] Task 4: OLED Dashboard
*Files:*
- `bandplus/src/main/java/vn/bandplus/ui/dashboard/DashboardScreen.kt`
- `bandplus/src/main/java/vn/bandplus/ui/dashboard/DashboardViewModel.kt`
- `bandplus/src/main/java/vn/bandplus/ui/components/MetricCard.kt`
- `bandplus/src/main/java/vn/bandplus/ui/components/LinearGauge.kt`

*Interfaces:*
- Consumes: `HealthRepository.day`, `range`, `lastSyncedAt`.
- Produces: `MetricCard(title, value, deltaYesterday, delta7DayAvg, unit, onClick)`, `LinearGauge(progress, target, color)`.

- [ ] *Step 1:* Metric cards: Steps, Active Kcal, Active Minutes, Resting HR, Sleep Duration, SpO2, Stress, Battery.
- [ ] *Step 2:* Delta calculation: vs yesterday (+/-) and vs trailing 7-day average. Missing days excluded from 7-day average.
- [ ] *Step 3:* Linear gauge visualizer replacing radial rings.
- [ ] *Step 4:* Pull-to-refresh triggering Gadgetbridge sync service; header displays "Đồng bộ lúc HH:mm".
- [ ] *Step 5:* Reorderable cards with layout preference stored in AndroidX DataStore.
- [ ] *Step 6:* Commit `Add OLED dashboard with deltas and linear gauges`.
- [ ] *Device check:* Day with band not worn displays "—" without skewing averages.

---

### [ ] Task 5: Deep Sleep Analysis & Explanatory Score
*Files:*
- `bandplus/src/main/java/vn/bandplus/ui/sleep/SleepScreen.kt`
- `bandplus/src/main/java/vn/bandplus/analytics/SleepScore.kt`

*Interfaces:*
- Consumes: `XiaomiSleepStageSampleDao`, `XiaomiSleepTimeSampleDao`, `XiaomiActivitySampleDao`.
- Produces: `data class SleepScoreBreakdown(totalScore: Int, durationScore: Int, deepScore: Int, remScore: Int, continuityScore: Int, consistencyScore: Int, explanations: List<String>)`.

- [ ] *Step 0:* Clear non-medical disclaimer banner in UI footer.
- [ ] *Step 1:* Multi-factor heuristic score (0–100):
  - Duration: 35 pts (optimal 7–9h)
  - Deep sleep: 20 pts (target ≥ 15%)
  - REM sleep: 20 pts (target ≥ 20%)
  - Continuity: 15 pts (awake time < 5%)
  - Bedtime consistency: 10 pts (bedtime standard deviation over last 14 nights < 30 min)
- [ ] *Step 2:* Vico hypnogram chart (Deep, Light, REM, Awake stages) with synchronized HR and SpO2 curves.
- [ ] *Step 3:* Advanced sleep biomarkers:
  - Sleep latency, number and duration of awakenings, WASO, sleep efficiency.
  - Lowest sleeping HR & time of occurrence (early = good recovery; late = strain/alcohol/late meal).
  - Nocturnal HR dip percentage vs daytime mean.
  - Sleep respiratory rate (using Band 9 `sleepRespiratoryRate` field).
  - Social jetlag (weekday vs weekend midpoint difference).
  - 30-day factor correlation (bedtime, evening steps, daytime stress vs sleep score).
- [ ] *Step 4:* Commit `Add deep sleep analysis with explained score`.
- [ ] *Device check:* Night spanning past midnight attributes to wake-up date; timezone change does not shift day attribution.

---

### [ ] Task 6: Heart Rate & Vitals Deep Analytics
*Files:*
- `bandplus/src/main/java/vn/bandplus/ui/heart/HeartScreen.kt`
- `bandplus/src/main/java/vn/bandplus/ui/vitals/VitalsScreen.kt`
- `bandplus/src/main/java/vn/bandplus/alerts/HrAlertWorker.kt`

*Interfaces:*
- Consumes: `HeartRatePercentiles`, `XiaomiActivitySampleDao`.
- Produces: Karvonen zones, SDANN (24h) circadian HRV proxy, morning readiness score (0–100).

- [ ] *Step 1:* Heart Rate Screen with Vico chart:
  - 24h continuous HR timeline.
  - Interactive P5, P25, P50, P75, P95 percentile bands overlay using cyan palette.
  - 30-day and 90-day resting HR trend.
- [ ] *Step 2:* SpO2 & Stress Screen:
  - Overnight SpO2 min/avg and time below 95% / 90%.
  - Hourly drops ≥ 3% estimation (ODI proxy).
  - 30-day stress vs sleep score scatter plot.
- [ ] *Step 3:* Circadian & Recovery Biomarkers:
  - Resting HR baseline (30-day rolling median); deviation alert (+5 bpm for 2 consecutive mornings flags illness/overtraining).
  - **SDANN (24h)** circadian HRV proxy calculated from standard deviation of 5-minute mean intervals over 24h (clearly labeled as 24h circadian HRV, never mislabeled as beat-to-beat RMSSD).
  - Morning Readiness Score (0–100) combining RHR deviation, sleep score, overnight SpO2, and yesterday's stress.
- [ ] *Step 4:* Background HR Alert Worker: post notification if resting HR exceeds user high threshold (default > 100 bpm) or drops below low threshold (default < 40 bpm) for ≥ 10 minutes.
- [ ] *Step 5:* Commit `Add heart and vitals detail with percentiles and alerts`.
- [ ] *Device check:* Trigger alert threshold test; alert fires once without repeating every minute.

---

### [ ] Task 7: Workouts & Phone-GPS Tracking
*Files:*
- `bandplus/src/main/java/vn/bandplus/ui/workout/WorkoutListScreen.kt`
- `bandplus/src/main/java/vn/bandplus/ui/workout/WorkoutDetailScreen.kt`
- `bandplus/src/main/java/vn/bandplus/export/GpxExporter.kt`

*Interfaces:*
- Consumes: `BaseActivitySummaryDao`, `XiaomiActivityTrackProvider`, `WorkoutGpsParser`.

- [ ] *Step 1:* Workout List & Detail:
  - Type, duration, active calories, avg/max HR, Karvonen HR zones (reusing `hrZones`).
  - Workout recovery HR (HR drop at 1 min and 2 min post-workout).
- [ ] *Step 2 (Recon Confirmed):* Route mapping & GPX:
  - Display route map (Mapsforge / OpenStreetMap) parsed from `GPS_TRACK` files streamed during workout.
  - Per-km split pace and elevation graph.
  - Direct GPX/TCX export via Android share sheet.
- [ ] *Step 3:* Commit `Add workout detail with phone GPS routes and splits`.
- [ ] *Device check:* Record 10-minute walk with phone in pocket; verify route, splits, and GPX export in Strava/Google Earth.

---

### [ ] Task 8: Settings & Smart Notification Filter
*Files:*
- `bandplus/src/main/java/vn/bandplus/ui/band/BandSettingsScreen.kt`
- `bandplus/src/main/java/vn/bandplus/ui/band/NotificationFilterScreen.kt`

*Interfaces:*
- Consumes: `NotificationAppRules`, `NotificationAppConfig`, `CompiledNotificationAppConfig`.

- [ ] *Step 1:* Compose screens wrapping band controls:
  - Alarms, reminders, weather sync toggle, watch face `.bin` installer, find band vibration.
- [ ] *Step 2:* Notification filtering UI:
  - Per-app toggle list.
  - Keyword blocking/allowing rules (powered by `NotificationAppRules.passesFilter`).
  - Quiet hours scheduling.
- [ ] *Step 3:* Commit `Add band settings and notification filters`.
- [ ] *Device check:* Blacklisted keyword message does not vibrate band; normal messages pass through.

---

### [ ] Task 9: Reports, CSV Export, Health Connect & Glance Widget
*Files:*
- `bandplus/src/main/java/vn/bandplus/reports/WeeklyReportWorker.kt`
- `bandplus/src/main/java/vn/bandplus/ui/reports/ReportScreen.kt`
- `bandplus/src/main/java/vn/bandplus/export/ExportScreen.kt`
- `bandplus/src/main/java/vn/bandplus/widget/TodayWidget.kt`

*Interfaces:*
- Consumes: `HealthCsvExporter`, `HealthDataExport`, `HealthConnectSyncWorker`.

- [ ] *Step 1:* Weekly report notification (Monday 08:00) with week-over-week deltas; monthly summary view.
- [ ] *Step 2:* CSV Export Screen using cherry-picked `HealthCsvExporter` and `HealthDataExport` (raw samples and daily percentiles).
- [ ] *Step 3:* Health Connect toggle switch wrapping upstream `HealthConnectSyncWorker` (syncs steps, sleep sessions, HR).
- [ ] *Step 4:* Android Glance home-screen widget (Steps progress, sleep score, band battery).
- [ ] *Step 5:* Commit `Add reports, CSV export, Health Connect toggle and Glance widget`.
- [ ] *Device check:* CSV export opens in spreadsheet; Health Connect shows today's data; app update retains database history.

---

### [ ] Task 10: Localization, Theming & Release Build
- [ ] *Step 1:* Complete Vietnamese strings (`values-vi/strings.xml`) and English fallbacks.
- [ ] *Step 2:* Final pure-black OLED contrast audit and empty state designs.
- [ ] *Step 3:* Configure release signing in `app/build.gradle`.
- [ ] *Step 4:* `./gradlew assembleMainlineRelease` build verification.
- [ ] *Step 5:* Commit `Prepare release build`.
- [ ] *Device check:* Run BandPlus exclusively for 3 days on vivo PD2445M.

---

### [ ] Task 11: Chest Strap HRV (Polar / Generic BLE HR Strap)
*Files:*
- `bandplus/src/main/java/vn/bandplus/analytics/HrvStrap.kt`
- `bandplus/src/main/java/vn/bandplus/ui/heart/HrvMorningScreen.kt`

*Interfaces:*
- Consumes: `HeartRrIntervalSampleDao`.
- Produces: `rmssd(rrMs: List<Int>): Double`, `sdnn(rrMs: List<Int>): Double`, `cleanRr(rrMs: List<Int>): List<Int>`.

- [ ] *Step 1:* Pair BLE chest strap via Gadgetbridge BLE HR profile; verify beat-to-beat RR intervals stored in `HeartRrIntervalSampleDao`.
- [ ] *Step 2:* 2-minute morning guided test: artifact rejection (drop intervals < 300ms or > 2000ms), compute RMSSD, SDNN, ln(RMSSD).
- [ ] *Step 3:* Trend against 7-day and 60-day baseline; feed RMSSD into morning Readiness score.
- [ ] *Step 4:* Commit `Add chest strap HRV readings`.
- [ ] *Device check:* Consecutive tests within 15% variation; movement artifacts prompt retry.
