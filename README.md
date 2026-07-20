# WatchAlarm ⏰

Ein Wecker für **Wear OS** (Pixel Watch, Galaxy Watch 4/5/6, …) mit Begleit-App
für das Handy — und einer Besonderheit: Pro Alarm lässt sich einstellen, dass er
**nur am Handy ausgeschaltet** werden kann. Die Uhr klingelt dann zwar mit, der
Stopp-Button existiert aber nur auf dem Handy. Perfekt gegen das verschlafene
Wegdrücken am Handgelenk.

## Features

- ⏰ Vollständige Wecker-App: Uhrzeit, Bezeichnung, Wochentags-Wiederholung
- 🔔 Alarmton-Auswahl (Systemtöne des Geräts)
- 😴 Snooze konfigurierbar: Dauer (3–30 min) und maximale Anzahl (0–10×)
- ⌚ Klingel-Modus pro Gerät und Alarm:
  - **Uhr**: Ton + Vibration, nur Vibration oder nur Ton
  - **Handy**: Ton + Vibration, nur Vibration oder gar nicht
  - Steht das Handy auf „gar nicht" und ist der Alarm „nur am Handy
    ausschaltbar", zeigt das Handy eine **lautlose Stopp-Ansicht**,
    während die Uhr klingelt/vibriert
- 📱 **„Nur am Handy ausschaltbar"** — Schalter pro Alarm:
  - *Aus*: Alarm kann auf Uhr **und** Handy gestoppt werden (Stopp auf einem
    Gerät stoppt beide)
  - *An*: Die Uhr zeigt nur „Am Handy ausschalten"; gestoppt wird am Handy,
    die Uhr hört sofort mit auf
- 🔄 Ständige Synchronisation zwischen Uhr und Handy über die
  **Wearable Data Layer API** (Alarme können auf beiden Geräten angelegt,
  geändert und umgeschaltet werden)
- 🛟 **Sicherheitsnetz gegen Endlos-Klingeln auf der Uhr**:
  1. Ist das Handy beim Klingeln **nicht verbunden** (Bluetooth getrennt,
     außer Reichweite, aus), blendet die Uhr nach ~10 s Karenzzeit einen
     Notfall-Stopp ein.
  2. Zusätzlich gilt auf beiden Geräten ein Klingel-Timeout von 5 Minuten:
     danach wird automatisch gesnoozt bzw. beendet.

## Projektstruktur

| Modul    | Inhalt |
|----------|--------|
| `core`   | Gemeinsame Logik: Datenmodell, Speicher, Alarm-Planung (`AlarmManager.setAlarmClock`), Klingel-Service, Data-Layer-Sync, Boot-Receiver |
| `mobile` | Handy-App (Jetpack Compose, Material 3): Alarmliste, Editor (Zeit, Ton, Snooze, Wochentage, Nur-Handy-Schalter), Vollbild-Klingelansicht |
| `wear`   | Wear-OS-App (Compose for Wear OS): Alarmliste mit Schaltern, einfacher Editor, Klingelansicht mit Verbindungs-Überwachung |

Beide Apps verwenden dieselbe `applicationId` (`com.watchalarm`) — Voraussetzung
dafür, dass die Data Layer API Handy- und Uhr-App als Paar erkennt.

## Wie die Synchronisation funktioniert

- Die komplette Alarmliste wird als **DataItem** (`/watchalarm/alarms`) mit
  Zeitstempel veröffentlicht. DataItems werden von den Play Services
  persistiert und **auch nach Verbindungsabbrüchen nachgeliefert** — neuere
  Stände gewinnen (last-write-wins).
- **Jedes Gerät plant seine Alarme selbst** aus der synchronisierten Liste.
  Der Alarm klingelt also auch dann zuverlässig, wenn Uhr und Handy gerade
  getrennt sind.
- Stopp und Snooze werden zusätzlich als **Message** (`/watchalarm/dismiss`,
  `/watchalarm/snooze`) an alle verbundenen Geräte geschickt, damit das
  Klingeln überall sofort endet. Snooze wird mitsynchronisiert, sodass beide
  Geräte erneut klingeln.
- Nach Neustart / Zeitumstellung stellt ein Boot-Receiver alle Alarme wieder
  her und stößt einen Vollabgleich an.

## Build

Voraussetzungen: Android Studio (Ladybug oder neuer) bzw. Android SDK 35, JDK 17.

```bash
./gradlew :mobile:assembleDebug   # Handy-APK
./gradlew :wear:assembleDebug     # Wear-OS-APK
```

Installation zum Testen:

```bash
adb -s <handy> install mobile/build/outputs/apk/debug/mobile-debug.apk
adb -s <uhr>   install wear/build/outputs/apk/debug/wear-debug.apk
```

> **Wichtig:** Beide APKs müssen mit **demselben Schlüssel signiert** sein
> (beim Debug-Build automatisch der Fall), sonst verweigert die Data Layer
> API die Kommunikation.

## Berechtigungen

- `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` — exakte Weckzeiten
- `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT` — Vollbild-Klingelansicht
- `FOREGROUND_SERVICE(_MEDIA_PLAYBACK)`, `WAKE_LOCK`, `VIBRATE` — Klingeln
- `RECEIVE_BOOT_COMPLETED` — Alarme nach Neustart wiederherstellen
