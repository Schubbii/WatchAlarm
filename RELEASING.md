# Veröffentlichen

RiseAlarm besteht aus **zwei Artefakten in einer einzigen Play-Eintragung**:
Handy-App und Uhr-App teilen sich die `applicationId` `com.Rise.Alarm`. Google
Play liefert anhand des `<uses-feature android:name="android.hardware.type.watch">`
im Wear-Manifest automatisch das passende an das jeweilige Gerät aus — und
schiebt die Uhr-App selbstständig auf die gekoppelte Uhr, sobald die Handy-App
installiert wird.

## Einmalig: Schlüssel erzeugen

```bash
keytool -genkeypair -v \
  -keystore upload.jks \
  -storetype PKCS12 \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -alias upload
```

Dann `keystore.properties.example` nach `keystore.properties` kopieren und
ausfüllen. Beide Dateien sind über `.gitignore` ausgeschlossen.

> **Beide Module müssen mit demselben Schlüssel signiert werden.** Sonst
> verweigert die Wearable Data Layer API die Kommunikation zwischen Handy und
> Uhr, und die Synchronisation ist tot. Die Gradle-Konfiguration liest deshalb
> in `mobile` und `wear` denselben Keystore.

Keystore und Passwörter sichern. Ohne sie sind keine Updates mehr möglich.

## Version erhöhen

Nur an einer Stelle — `gradle.properties`:

```properties
watchalarm.versionName=1.8
watchalarm.versionCode=5
```

Die Uhr bekommt automatisch `versionCode + 1000` (also `1005`), weil Play für
zwei Artefakte derselben Eintragung unterschiedliche Codes verlangt. Die
Versionsanzeige in beiden Apps kommt über `BuildConfig.VERSION_NAME` aus
demselben Wert.

## Bauen

```bash
./gradlew :mobile:bundleRelease :wear:bundleRelease
```

Ergebnis:

- `mobile/build/outputs/bundle/release/mobile-release.aab`
- `wear/build/outputs/bundle/release/wear-release.aab`

Beide AABs gehören in **denselben Release** derselben Play-App.

> Ohne hinterlegten Keystore signiert der Release-Build mit dem Debug-Key und
> gibt eine Warnung aus. Play lehnt solche Uploads ab.

## Play Console: was ausgefüllt werden muss

Diese App nutzt drei Berechtigungen, die eine ausdrückliche Erklärung
brauchen. Ohne sie wird die Veröffentlichung abgelehnt:

| Thema | Wo | Begründung |
|---|---|---|
| **Vollbild-Benachrichtigung** (`USE_FULL_SCREEN_INTENT`) | App-Inhalte → Deklaration | Nur Wecker- und Anruf-Apps erhalten sie. RiseAlarm ist ein Wecker: Der Vollbild-Screen ist der einzige Weg, den Alarm bei gesperrtem Bildschirm zu beenden. |
| **Exakte Alarme** (`USE_EXACT_ALARM`) | App-Inhalte → Deklaration | Ein Wecker muss auf die Minute genau auslösen; ungenaue Alarme wären zweckwidrig. |
| **Foreground-Service-Typ** (`systemExempted`) | App-Inhalte → Deklaration | Meist mit kurzem Demo-Video. **Riskantester Punkt:** Google legt `systemExempted` eng aus. Falls abgelehnt, ist der Ausweg `mediaPlayback` oder `shortService` — dann muss aber `AlarmService` mitziehen. |

Dazu:

- **Datenschutzerklärung** — siehe `PRIVACY.md`, muss unter einer öffentlichen
  URL erreichbar sein (z. B. GitHub Pages).
- **Data Safety** — die App sammelt und überträgt nichts an Dritte; die
  Synchronisation läuft ausschließlich Gerät↔Gerät über die Data Layer API.
- **Wear-OS-Store-Eintrag** — eigene Screenshots von der Uhr, sonst erscheint
  die App nicht im Play Store auf dem Handgelenk.

## Testen ohne Kabel

Wear OS kann APKs **nur** über ADB oder den Play Store installieren — es gibt
keinen Sideload-Weg über Browser oder Dateimanager. Deshalb führt am Play
Store kein Weg vorbei, wenn man das Wireless Debugging loswerden will.

- **Internal App Sharing** — Upload, Link, fertig. Kein Review, kein
  Hochzählen von `versionCode`. Schnellster Weg zum Ausprobieren.
- **Interner Test-Track** — meist in Minuten live, echtes Play-Update, die
  Uhr zieht automatisch nach. Bis zu 100 Tester.
- **Geschlossener Test** — nötig, bevor neue Privat-Entwicklerkonten auf
  Produktion freigeschaltet werden (fortlaufender Test mit einer
  Mindestanzahl Tester über mehrere Tage; genaue Anforderungen stehen in der
  Console).

## CI

`.github/workflows/build.yml` baut bei jedem Push Debug-APKs und lässt Lint
laufen. Für signierte AABs (manuell per *Run workflow* oder über ein `v*`-Tag)
müssen diese Repository-Secrets gesetzt sein:

| Secret | Inhalt |
|---|---|
| `WATCHALARM_KEYSTORE_BASE64` | `base64 -w0 upload.jks` |
| `WATCHALARM_STORE_PASSWORD` | Keystore-Passwort |
| `WATCHALARM_KEY_ALIAS` | Alias, z. B. `upload` |
| `WATCHALARM_KEY_PASSWORD` | Schlüssel-Passwort |

Fehlen die Secrets, überspringt der Release-Job sich selbst, statt
fehlzuschlagen.
