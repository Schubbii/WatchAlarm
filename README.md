# WatchAlarm ⏰

Ein Wecker für **Wear OS** (Pixel Watch, Galaxy Watch 4/5/6, …) mit Begleit-App
für das Handy. Die Idee: Die **Uhr weckt per Vibration**, ausgeschaltet wird
aber **am Handy** — perfekt gegen das verschlafene Wegdrücken am Handgelenk.

## Verhalten (bewusst einfach gehalten)

- **Uhr:** vibriert. Der Klingel-Screen hat einen **Stopp-Button**; das
  beendet den Alarm auch auf dem Handy.
- **Handy:** kein Ton, keine Vibration — es erscheint im aus- **und**
  eingeschalteten Zustand ein Vollbild-Screen zum Ausschalten (mit Stopp
  und Schlummern).
- Stopp auf einem Gerät beendet den Alarm auf beiden.
- 🛟 Zusätzliches Netz: Reagiert niemand, schlummert der Wecker nach einer
  einstellbaren Zeit von selbst (Standard **30 Minuten**, wählbar in
  5er-Schritten), damit die Uhr nie endlos weitervibriert.

## Features

- ⏰ Uhrzeit, Bezeichnung, Wochentags-Wiederholung (auf beiden Geräten
  einstellbar)
- 😴 Snooze konfigurierbar: Dauer (3–30 min), maximale Anzahl (0–10×) und
  Klingeldauer bis zum automatischen Schlummern (5–30 min)
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

### Version erhöhen

Version und Build-Nummer stehen zentral in `gradle.properties`
(`watchalarm.versionName` / `watchalarm.versionCode`); die Uhr bekommt
automatisch `versionCode + 1000`. Beide Apps zeigen die Version über
`BuildConfig.VERSION_NAME` an — nirgends sonst gepflegt.

### Veröffentlichen

Release-Signierung, Play-Console-Ablauf und die nötigen Berechtigungs-
Deklarationen stehen in **[RELEASING.md](RELEASING.md)**.
Datenschutzerklärung: **[PRIVACY.md](PRIVACY.md)**.

## Sprachen

Standardsprache ist **Englisch** (`values/strings.xml`), Deutsch liegt als
Übersetzung daneben (`values-de/strings.xml`). Wochentagskürzel und
Wochenanfang kommen über `java.time`/`WeekFields` aus der Gerätesprache, die
Uhrzeit über `DateFormat.getTimeFormat()` aus der 12-/24-Stunden-Einstellung
des Geräts.

## Berechtigungen

- `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` — exakte Weckzeiten
- `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT` — Vollbild-Klingelansicht
- `FOREGROUND_SERVICE(_SYSTEM_EXEMPTED)`, `WAKE_LOCK`, `VIBRATE` — Klingeln
- `RECEIVE_BOOT_COMPLETED` — Alarme nach Neustart wiederherstellen

> Ab Android 14 ist `USE_FULL_SCREEN_INTENT` eine widerrufbare Berechtigung.
> Fehlt sie, zeigt die Handy-App oben in der Liste einen Hinweis, der direkt
> in die passenden Systemeinstellungen führt.
