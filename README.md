# WatchAlarm ⏰

Ein Wecker für **Wear OS** (Pixel Watch, Galaxy Watch 4/5/6, …) mit Begleit-App
für das Handy. Die Idee: Die **Uhr weckt per Vibration**, ausgeschaltet wird
aber **am Handy** — perfekt gegen das verschlafene Wegdrücken am Handgelenk.

## Verhalten (bewusst einfach gehalten)

- **Uhr:** vibriert, sonst nichts.
- **Handy:** kein Ton, keine Vibration — es erscheint im aus- **und**
  eingeschalteten Zustand ein Vollbild-Screen zum Ausschalten (mit Stopp
  und Schlummern).
- **Ausgeschaltet wird am Handy.** Stoppt man dort, hört die Uhr sofort auf
  zu vibrieren.
- 🛟 **Notfall-Stopp auf der Uhr:** Ist das Handy beim Alarm **nicht
  verbunden** (Bluetooth getrennt, außer Reichweite, aus), blendet die Uhr
  nach ~10 s einen Stopp-Button ein, damit sie nie unabschaltbar
  weitervibriert. Zusätzlich gilt ein 5-Minuten-Timeout (danach automatisch
  Schlummern bzw. Stopp).

## Features

- ⏰ Uhrzeit, Bezeichnung, Wochentags-Wiederholung (auf beiden Geräten
  einstellbar)
- 😴 Snooze konfigurierbar: Dauer (3–30 min) und maximale Anzahl (0–10×)
- 🔄 Ständige Synchronisation zwischen Uhr und Handy über die **Wearable
  Data Layer API** — Änderungen von **beiden** Seiten kommen an, geordnet
  über einen geräteunabhängigen Lamport-Versionszähler (kein Wanduhr-
  Zeitstempel, damit abweichende Emulator-Uhren nichts verwerfen)

## Projektstruktur

| Modul    | Inhalt |
|----------|--------|
| `core`   | Gemeinsame Logik: Datenmodell, Speicher, Alarm-Planung (`AlarmManager.setAlarmClock`), Klingel-Service, Data-Layer-Sync, Boot-Receiver |
| `mobile` | Handy-App (Jetpack Compose, Material 3): Alarmliste, Editor (Zeit, Bezeichnung, Wochentage, Snooze), Vollbild-Stopp-Ansicht |
| `wear`   | Wear-OS-App (Compose for Wear OS): Alarmliste mit Schaltern, einfacher Editor, Vibrations-/Stopp-Ansicht mit Verbindungs-Überwachung |

Beide Apps verwenden dieselbe `applicationId` (`com.watchalarm`) — Voraussetzung
dafür, dass die Data Layer API Handy- und Uhr-App als Paar erkennt.

## Wie die Synchronisation funktioniert

- Die komplette Alarmliste wird als **DataItem** (`/watchalarm/alarms`) mit
  **Lamport-Version** veröffentlicht. DataItems werden von den Play Services
  persistiert und **auch nach Verbindungsabbrüchen nachgeliefert**. Ein
  empfangener Stand wird übernommen, wenn seine Version höher ist als die
  eigene — geräteunabhängig, deshalb kommen Änderungen von Uhr **und** Handy
  zuverlässig an (der frühere Wanduhr-Zeitstempel verwarf Uhr→Handy-
  Updates, wenn die Emulator-Uhren auseinanderliefen).
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
