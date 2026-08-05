# Datenschutzerklärung — RiseAlarm

Stand: siehe Datum des letzten Commits an dieser Datei.

## Kurzfassung

RiseAlarm sammelt keine personenbezogenen Daten, überträgt nichts an Server
des Entwicklers oder an Dritte und enthält weder Werbung noch Analyse- oder
Tracking-Bibliotheken.

## Welche Daten die App speichert

Ausschließlich das, was du selbst anlegst:

- Weckzeiten, Bezeichnungen und Wochentags-Wiederholungen
- Snooze-Einstellungen (Dauer, maximale Anzahl)
- den Laufzeitzustand eines gerade klingelnden Alarms

Diese Daten liegen lokal im privaten App-Speicher deines Geräts
(`SharedPreferences`) und sind für andere Apps nicht lesbar.

## Übertragung zwischen deinen Geräten

Damit Handy und Uhr denselben Weckerbestand haben, werden die oben genannten
Alarmdaten zwischen **deinen eigenen, miteinander gekoppelten Geräten**
ausgetauscht. Dafür nutzt die App die Wearable Data Layer API von Google Play
Services. Die Übertragung läuft über die bestehende Kopplung zwischen Handy und
Uhr; der Entwickler betreibt keinen Server und hat zu keinem Zeitpunkt Zugriff
auf diese Daten.

## Berechtigungen und wofür sie gebraucht werden

| Berechtigung | Zweck |
|---|---|
| `USE_EXACT_ALARM` / `SCHEDULE_EXACT_ALARM` | Wecker zur eingestellten Minute auslösen |
| `POST_NOTIFICATIONS` | Benachrichtigung des klingelnden Weckers anzeigen |
| `USE_FULL_SCREEN_INTENT` | Stopp-Bildschirm bei gesperrtem Display anzeigen |
| `FOREGROUND_SERVICE` (+ Typ) | Klingeln aufrechterhalten, solange der Wecker läuft |
| `WAKE_LOCK` | Verhindern, dass das Gerät während des Klingelns einschläft |
| `VIBRATE` | Vibration auf der Uhr |
| `RECEIVE_BOOT_COMPLETED` | Wecker nach einem Neustart wiederherstellen |

Keine dieser Berechtigungen wird für andere Zwecke als die genannten genutzt.

## Sicherung

Wenn du die Android-Datensicherung aktiviert hast, wird deine Alarmliste als
Teil des System-Backups in deinem Google-Konto gesichert — nach denselben
Regeln wie bei jeder anderen App und unter deiner Kontrolle. Der Laufzeit-
zustand ist von der Sicherung ausgenommen.

## Löschung

Beim Deinstallieren der App werden alle lokal gespeicherten Daten entfernt.

## Kontakt

Fragen zum Datenschutz: über ein Issue im GitHub-Repository dieser App.
