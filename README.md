# PocketLab
**Aditya Bhosale** · +91 9324218321 · adityabhosale212223@gmail.com
GitHub: https://github.com/Adiseclabs/PocketScan

*A small contribution to the open-source community: free forever, no paid servers, no telemetry.*

PocketLab is a pocket-sized network, tracker and app security toolkit for Android. Everything runs on your phone. No root, no VPN, no firewall, no packet capture, no accounts, no paid APIs.

## Features

### 1. LAN Scanner
- Sweeps your Wi-Fi subnet (/24) with up to 50 parallel probes: ping first, then TCP fallback on ports 80, 443, 22 and 8080.
- Checks common risky ports (21, 23, 445, 3389, 6379, 27017) on every live host.
- Reads service banners (HTTP `Server`, SSH, FTP greeting, 2 s timeout), resolves hostnames, guesses the vendor from the MAC (best effort).
- Dashboard with summary stats, risk badges, pull-to-refresh, progress bar and a Scan button.
- Tap any IP for details: ports, banner, vendor, first/last seen, open in browser, copy IP.

### 2. New-device alerts
- Optional foreground monitor re-scans every 5 minutes (15 in battery saver).
- A device not seen before triggers a high-priority notification with vibration: `New device: <IP> <hostname>`.
- The first scan only builds the baseline, so you are not flooded with alerts.

### 3. BLE tracker detection
- Background service scans Bluetooth Low Energy for 5 seconds every 15 minutes (low-power mode).
- Recognises advertisement patterns of Apple Find My (AirTag), Samsung SmartTag, Tile, Chipolo and Pebblebee.
- Known tracker seen: silent log only. Same tracker near you for about 15 minutes, or seen at 3+ different places: CRITICAL alert with long vibration, sound and a full-screen notification.
- Optional "new device very close" notification: a never-seen Bluetooth device within roughly 1-3 m (RSSI -55 dBm or stronger), at most one per 10 minutes; the first scan is only a baseline. Can be switched off in Settings.
- BLE tab lists nearby advertising devices with name, signal strength (dBm) and a category badge.

### 4. Fox Hunt (find a hidden tracker)
- Pick a device on the BLE tab and tap **Hunt**. An info popup explains how to hunt (can be switched off).
- Runs a fast, filtered scan of that one device and shows a radar: the circle grows and a bar fills as the signal gets stronger.
- Phone vibrates in pulses that get longer as you get closer (50 ms far, 250 ms close).
- Stops automatically after 10 minutes, or when the signal is stronger than -45 dBm ("found it").
- Never runs together with a LAN scan, to save battery and keep readings clean.

### 5. Offline CVE hints and risk score
- Bundled `cve-db.json` (3 example entries; extend it yourself) is loaded into the local database on first launch.
- Banner service and version are matched against it. Matches are expandable cards with CVSS, description, reference link and a fix hint.
- Each device gets a 0-100 score from risky open ports plus CVSS values: Critical / High / Medium / Low / Clean. The Security tab shows a network score and the top 5 vulnerable devices.
- Banner versions ignore distro backports, so these are hints, not proof.

### 6. Advisory blocklists
- Downloads StevenBlack, Phishing Army, URLhaus and OpenPhish lists (ETag-cached, about 10 MB once).
- Used only to flag hostnames seen on your LAN. Nothing is ever blocked or intercepted.

### 7. Wi-Fi Security Check (More tab)
- Rates the Wi-Fi you are connected to from 0 to 100: encryption type (open, WEP, WPA/WPA2, WPA3), Wi-Fi standard, router exposure (Telnet, FTP, plain-HTTP admin), router software CVE hints, unencrypted DNS and risky devices found by your LAN scan.
- Each finding has a severity and a fix. It reads only what Android allows without location permission (the network name may be hidden; encryption type needs Android 12+).

### 8. App Risk Review (More tab)
- Reviews installed user apps and rates each one: sideloaded (not from a trusted store), hidden (no launcher icon), over-permissioned, powerful permissions (SMS, overlay, install packages, accessibility, background location), debuggable builds and apps targeting very old Android.
- Filter by Sideloaded / Hidden / Over-permissioned; tap an app for details and a shortcut to its system settings. Hints only, not proof of malware.

### 9. Export
- After a scan is stopped you can export LAN devices, BLE devices and Fox Hunt signal history as CSV through the system file picker (no storage permission). Device-supplied text is sanitised against spreadsheet formula injection.

### 10. App basics
- New radar app icon and dark theme, animated radar splash, 3-step onboarding, "About Me" screen with a full project description.
- Settings: data retention (1-30 days), start tracker watch on boot, battery saver, nearby-device alert, refresh blocklists, reset data.

## Battery behaviour
- Default: BLE background watch only (short, low-power scans). Power use has not been measured yet.
- LAN scans are on demand, or every few minutes only if you switch the monitor on.
- Fox Hunt is short-lived by design.

## Permissions
| Permission | Why |
|---|---|
| INTERNET | Download blocklists only |
| ACCESS_NETWORK_STATE, ACCESS_WIFI_STATE, NEARBY_WIFI_DEVICES | Find your local subnet and read Wi-Fi security (never for location) |
| BLUETOOTH_SCAN (neverForLocation), BLUETOOTH_CONNECT | BLE tracker detection |
| POST_NOTIFICATIONS, VIBRATE, USE_FULL_SCREEN_INTENT | Alerts |
| FOREGROUND_SERVICE (+ data sync / connected device) | Background monitor and tracker watch |
| RECEIVE_BOOT_COMPLETED | Optional start on boot |
| QUERY_ALL_PACKAGES | App Risk Review (list installed apps) |

No `BIND_VPN_SERVICE` and no `VpnService`.

## Limitations (honest list)
- Android 10+ hides other devices' MAC addresses from apps, so vendor names are often "Unknown".
- BLE detects only devices that are actively advertising. Bluetooth Classic and non-discoverable devices are invisible.
- Trackers rotate their Bluetooth address, so long-term detection is best effort. The "3 places" rule uses the Wi-Fi gateway as a rough location stand-in.
- The "very close" alert only sees devices during the short background scan window.
- Tracker signatures are community-known patterns and can change with firmware.
- App flags such as "hidden" or "sideloaded" can have legitimate reasons. A tracker alert is a warning to investigate, not proof of stalking.

## Build
```
./gradlew assembleDebug
```
APK: `app/build/outputs/apk/debug/`. The GitHub Actions workflow `.github/workflows/build-apk.yml` builds it on every push and uploads it as the **PocketLab-debug-apk** artifact.

## License
Apache-2.0. See `LICENSE` and `NOTICE`.
