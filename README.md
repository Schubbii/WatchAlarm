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
- ⏳ **Schlafdauer** in der Liste: Zeit von jetzt bis zum nächsten Klingeln
  (z. B. „😴 7 Std. 30 Min. Schlaf“), auf Handy und Uhr, minütlich
  aktualisiert und inklusive eines laufenden Snooze
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

Beide Apps verwenden dieselbe `applicationId` (`com.Rise.Alarm`) — Voraussetzung
dafür, dass die Data Layer API Handy- und Uhr-App als Paar erkennt.

> **Namensgebung:** Im Store und im Launcher heißt die App **RiseAlarm**.
> Repository, Gradle-Module und Kotlin-Pakete (`com.watchalarm.*`) tragen
> weiterhin den Arbeitstitel WatchAlarm — die sieht niemand von außen, und ein
> Umbenennen brächte nur Bewegung ohne Nutzen.

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

> **JDK 17–21, nicht neuer.** Gradle 8.14 (die Wrapper-Version hier) kann die
> Java-Version „25“ nicht parsen und bricht mit `IllegalArgumentException: 25`
> schon beim Übersetzen der `.gradle.kts`-Skripte ab. Das sieht im Editor
> harmlos aus, aber irreführend: Weil der Build gar nicht erst läuft, wird
> `BuildConfig` nie erzeugt, und die IDE meldet stattdessen ein „unresolved
> reference: BuildConfig“ mitten im Quelltext. Läuft das System auf einem
> neueren JDK, zeigt man Gradle ein passendes — benutzerweit in
> `~/.gradle/gradle.properties`, damit kein maschinenspezifischer Pfad ins
> Repo wandert:
>
> ```properties
> org.gradle.java.home=C:/Program Files/Java/jdk-21
> ```
>
> Die CI ist nicht betroffen, sie richtet sich JDK 17 selbst ein.

```bash
./gradlew :mobile:assembleDebug   # Handy-APK
./gradlew :wear:assembleDebug     # Wear-OS-APK
```

Installation zum Testen — **Release-Build nehmen, nicht Debug.** Debug-Builds
sind `debuggable`, werden von ART nicht optimiert und ruckeln auf der Uhr
deutlich. Ohne Keystore signiert der Release-Build mit dem Debug-Key und ist
damit direkt installierbar:

```bash
./gradlew :mobile:assembleRelease :wear:assembleRelease
adb -s <handy> install -r mobile/build/outputs/apk/release/mobile-release.apk
adb -s <uhr>   install -r wear/build/outputs/apk/release/wear-release.apk
```

Fertig gebaut liegen beide auch an jedem CI-Lauf als Artefakt
**`apks-zum-installieren`**. Die CI signiert mit einem pro Lauf neuen
Schlüssel: Handy- und Uhr-APK aus demselben Lauf nehmen, und beim Wechsel
von einem anderen Build vorher auf beiden Geräten deinstallieren
(`INSTALL_FAILED_UPDATE_INCOMPATIBLE`).

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
Testliste für Handy und Uhr samt Ergebnissen: **[TESTING.md](TESTING.md)**.

## Sprachen

Standardsprache ist **Englisch** (`values/strings.xml`), Deutsch liegt als
Übersetzung daneben (`values-de/strings.xml`). Wochentagskürzel und
Wochenanfang kommen über `java.time`/`WeekFields` aus der Gerätesprache, die
Uhrzeit über `DateFormat.getTimeFormat()` aus der 12-/24-Stunden-Einstellung
des Geräts.

## Berechtigungen

- `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` — exakte Weckzeiten
- `POST_NOTIFICATIONS`, `USE_FULL_SCREEN_INTENT` — Vollbild-Klingelansicht
- `FOREGROUND_SERVICE(_SPECIAL_USE)`, `WAKE_LOCK`, `VIBRATE` — Klingeln
- `RECEIVE_BOOT_COMPLETED` — Alarme nach Neustart wiederherstellen

> **Warum `specialUse` und nicht `systemExempted`:** Letzteres ist Apps
> vorbehalten, die ohnehin von den Hintergrund-Einschränkungen ausgenommen
> sind (Geräteverwaltung, VPN, Notfall-Apps). `USE_EXACT_ALARM` erlaubt uns
> den Start aus dem Hintergrund, macht die App aber nicht „system exempted“ —
> der Typ kann beim `startForeground()` also abgelehnt werden. `specialUse`
> ist der dokumentierte Auffangtyp; die Begründung für Play steht als
> `<property>` direkt im Manifest.

> Ab Android 14 ist `USE_FULL_SCREEN_INTENT` eine widerrufbare Berechtigung.
> Fehlt sie, zeigt die Handy-App oben in der Liste einen Hinweis, der direkt
> in die passenden Systemeinstellungen führt.
