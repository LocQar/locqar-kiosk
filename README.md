# locqar-kiosk

Android kiosk application that bridges the LocQar cloud API with Winnsen RS485 lock controller hardware.

## Architecture

This app replaces the Winnsen kiosk software. It sits between two systems:

- **Cloud API** (`dashboard-api`) — order management, authentication, payments via `POST /api/winnsen/events`
- **Lock hardware** — Winnsen RS485 control boards accessed via USB-to-RS485 adapter

```
┌─────────────────────────────────────────────────────────┐
│                    locqar-kiosk (Android)                │
│                                                         │
│  ┌──────────────┐    ┌──────────────┐    ┌───────────┐ │
│  │  UI Screens   │───▶│  ViewModel   │───▶│ Dashboard │ │
│  │  (Compose)    │    │  (Bridge)    │    │ ApiClient │──── HTTPS ──▶ api.locqar.com
│  └──────────────┘    │              │    └───────────┘ │
│                      │              │    ┌───────────┐ │
│                      │              │───▶│  Locker   │ │
│                      └──────────────┘    │  Daemon   │──── RS485 ──▶ Lock Board
│                                          └───────────┘ │
└─────────────────────────────────────────────────────────┘
```

## RS485 Protocol

From the Winnsen Lock Control Board Serial Command Document (V202104):

- **Serial**: 9600 baud, 8N1
- **Adapter**: CH340/CH341/PL2303 USB-to-RS485
- **Open lock**: `90 06 05 <station> <lock> 03` → response includes success/fail status
- **Poll state**: `90 07 02 <station> <lowMask> <highMask> 03` → 16-bit door state bitmask

Each control board supports up to 16 locks. Station number set by DIP switch.

## Project Structure

```
app/src/main/java/com/locqar/kiosk/
├── hardware/
│   ├── codec/          WinnsenCodec — RS485 frame builder/parser
│   ├── serial/         SerialManager — USB serial connection
│   ├── controller/     LockerController interface + real/demo impls
│   ├── demo/           DemoLockerController for testing
│   └── service/        LockerDaemonService (foreground service)
├── network/
│   ├── api/            DashboardApiClient — Ktor HTTP client
│   └── model/          Request/response models (kotlinx.serialization)
├── ui/screens/
│   ├── home/           Kiosk idle screen (agent/member/guest entry)
│   ├── agent/          Agent login + order list screens
│   ├── member/         Member login + menu screens
│   ├── guest/          Guest order entry screen
│   └── common/         Door open + completion screens
├── viewmodel/          KioskViewModel — coordinates API ↔ hardware
├── MainActivity.kt     Entry point, service binding, screen routing
└── LocQarKioskApp.kt   Application class
```

## Key Dependencies

| Library | Purpose |
|---------|---------|
| `usb-serial-for-android` | RS485 via USB adapter |
| `ktor-client-android` | HTTP client for dashboard-api |
| `kotlinx-serialization` | JSON parsing |
| `jetpack-compose` | UI framework |
| `room` | Local database |
| `zxing` | QR code generation (payment URLs) |

## Configuration

Set in `app/build.gradle.kts`:

- `API_BASE_URL` — dashboard-api base URL (dev: `https://api.dev.locqar.com`)
- `API_KEY` — x-api-key header value

Set at runtime in `MainActivity.kt`:

- `lockerSN` — this kiosk's locker serial number (registered in LocQar system)
- `stationNumber` — RS485 board station (DIP switch setting)
- `demoMode` — true for testing without hardware

## Kiosk Flows

### Agent Drop-off (Flow 1)
1. Agent enters phone + password → `agent-login-by-phone`
2. Agent selects order → `agent-validate-order` + `agent-reuse-door`
3. Door opens (RS485 open command)
4. Agent places package, closes door
5. Kiosk reports → `order-dropoff`

### Guest Pickup (Flow 2)
1. Guest enters order number → `order-payment` check
2. If payment required → `generate-payment-page` → QR code
3. Door opens
4. Guest takes package, closes door
5. Kiosk reports → `order-collected`

### Member Storage (Flow 3)
1. Member enters phone + password → `member-login-by-phone`
2. Member selects storage → `member-create-storage-order`
3. Payment (if not subscriber)
4. Door opens, member stores items
5. Later: member returns → `member-package` → door opens → `order-collected`

## Building

Open in Android Studio. Target SDK 34, min SDK 26. Requires:
- Kotlin 2.0.21
- Gradle 8.7.3
- Android Gradle Plugin 8.7.3
