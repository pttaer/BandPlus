# BandPlus Architecture & Codebase Reconnaissance

## 1. Database Access & DAOs

- **DB Session Accessor**:
  - `GBApplication.acquireDB(): DBHandler` (read/write, implements `AutoCloseable`)
  - `GBApplication.acquireDbReadOnly(): DBHandler` (read-only)
  - Inside `DBHandler`: `dbHandler.getDaoSession()` provides GreenDAO session.

- **Xiaomi Sample DAOs**:
  - **Activity**: `XiaomiActivitySampleDao` (Properties: `timestamp`, `deviceId`, `userId`, `rawIntensity`, `steps`, `rawKind`, `heartRate`, `stress`, `spo2`, `distanceCm`, `activeCalories`, `energy`).
  - **Sleep Stage**: `XiaomiSleepStageSampleDao` (`stage`, `timestamp`) and `XiaomiSleepTimeSampleDao` (`wakeupTime`, `intoBedTime`, `totalDuration`, `deepSleepDuration`, `lightSleepDuration`, `remSleepDuration`, `awakeDuration`).
  - **Heart Rate**: `XiaomiActivitySampleDao` (continuous 1-min samples in `heartRate` property); resting HR in `XiaomiDailySummarySampleDao` (`hrResting`, `hrAvg`, `hrMin`, `hrMax`). Chest strap RR intervals in `HeartRrIntervalSampleDao`.
  - **SpO2**: `XiaomiActivitySampleDao` (`spo2` property) + `XiaomiDailySummarySampleDao`.
  - **Stress**: `XiaomiActivitySampleDao` (`stress` property, range 1-100) + `XiaomiDailySummarySampleDao` (`stressAvg`, `stressMin`, `stressMax`).
  - **Workout Summary**: `BaseActivitySummaryDao` (`baseActivitySummaryDao`), raw tracks indexed by `XiaomiActivityFileDao` (DETAIL types: `SUMMARY`, `DETAILS`, `GPS_TRACK`).

## 2. Band 9 Coordinator & Service

- **Coordinator Class**: `nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.watches.MiBand9Coordinator`
- **Parent Coordinator**: `nodomain.freeyourgadget.gadgetbridge.devices.xiaomi.XiaomiCoordinator`
- **Device Support Class**: `nodomain.freeyourgadget.gadgetbridge.service.devices.xiaomi.XiaomiSupport`
- **Service Handlers**:
  - Health & Realtime: `XiaomiHealthService`
  - Activity Fetching & Parsing: `XiaomiActivityFileFetcher`, `XiaomiActivityTrackProvider`, `WorkoutSummaryParser`, `WorkoutGpsParser`
  - Settings & Preferences: `XiaomiPreferences`, `XiaomiSettingsCustomizer`

## 3. Scope Questions (Plan Sizing)

| Question | Answer | Implication for Tasks |
|---|---|---|
| **Compose in build?** | **No** | Task 1 Step 4 adds Jetpack Compose + Material 3 dependencies in `:bandplus` module. |
| **Health Connect sync exists upstream?** | **Yes** | Fully implemented in `nodomain.freeyourgadget.gadgetbridge.util.healthconnect` (`HealthConnectSyncWorker`, `HealthConnectWorkoutSync`, `HealthConnectUtils`). Task 9 Step 3 only needs UI toggle wrap. |
| **Notification word filter / quiet hours exist?** | **Yes** | Fully implemented in `nodomain.freeyourgadget.gadgetbridge.externalevents.notifications.apps.NotificationAppRules` (`passesFilter`, regex, word lists, quiet hours). Task 8 is Compose UI wrapper only. |
| **Phone-GPS workout tracking exists for Xiaomi?** | **Yes** | Fully implemented. `XiaomiCoordinator.supportsGpsSharing()` is `true`. `XiaomiHealthService` handles `CMD_WORKOUT_LOCATION` using `GBLocationService` and streams GPS to band; band records `GPS_TRACK` files parsed by `WorkoutGpsParser`. Task 7 proceeds with Step 2 (maps + GPX). |
| **Metrics stored by Band 9 parser** | Steps, distance, active calories, HR, SpO2, stress, sleep stages & durations, sleep respiratory rate, body energy, PAI/vitality score, workout summaries + GPS coordinates. | Matches feature spec. |
| **HR sample interval** | 1-minute continuous resolution stored in `XiaomiActivitySample`. | Provides fine-grained timeline for day/night graphs. |
| **Band 9 HRV / RR intervals?** | **No** | Protobuf (`wear_fitness.proto`) has no HRV or RR interval fields. Band 9 firmware does not stream RRI. Task 6 computes **SDANN (24h)** proxy; real RMSSD reserved for Task 11 (chest strap). |
| **SpO2 sampling during sleep?** | **Yes** | Supports `SPO2_MODE_SLEEP_ONLY` (1) and `SPO2_MODE_ALL_DAY` (2). |
| **Respiratory rate field?** | **Yes** | `sleepRespiratoryRate` in `wear_fitness.proto`, `supportsSleepRespiratoryRate()` is `true`. |
| **Skin temperature field?** | **No** | Band 9 hardware lacks a dedicated skin temperature sensor. Weather temperature only. |

