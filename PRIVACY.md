# Datenschutzerklärung — RiseAlarm

Stand: 7. Oktober 2026

## Kurzfassung

RiseAlarm sammelt keine personenbezogenen Daten, überträgt nichts an Server
des Entwicklers oder an Dritte und enthält weder Werbung noch Analyse- oder
Tracking-Bibliotheken.

## Welche Daten die App speichert

Ausschließlich das, was du selbst anlegst:

- Weckzeiten, Bezeichnungen und Wochentags-Wiederholungen
- Snooze-Einstellungen (Dauer, maximale Anzahl)
- den Laufzeitzustand eines gerade klingelnden Alarms
- die Einstellungen des Schlafplaners (Zykluslänge, Einschlafzeit,
  Schlafziel) und eine eventuell gestellte Schlafenszeit-Erinnerung

Diese Daten liegen lokal im privaten App-Speicher deines Geräts
(`SharedPreferences`) und sind für andere Apps nicht lesbar.

## Schlafdaten aus Health Connect (optional)

Der Schlafplaner kann – nur wenn du es in der Handy-App ausdrücklich
erlaubst – deine **Schlafsitzungen der letzten 7 Nächte** aus Health Connect
lesen. Daraus berechnet die App pro Nacht nur die **Schlafdauer** und zeigt
dir Durchschnitt, Schlafdefizit und eine Empfehlung, wie viele Schlafzyklen du
einplanen solltest.

- Gelesen werden ausschließlich Schlafsitzungen, keine anderen
  Gesundheitsdaten. Die App schreibt nichts in Health Connect.
- Gespeichert wird je Nacht nur Datum und Schlafdauer in Minuten, im privaten
  App-Speicher. Diese Werte sind von der Datensicherung ausgenommen.
- Sie werden an deine gekoppelte Uhr übertragen (siehe unten), damit der
  Planer dort dieselbe Empfehlung zeigt – sonst nirgendwohin.
- Die Daten werden nicht für Werbung verwendet, nicht verkauft und nicht an
  Dritte weitergegeben.
- Widerrufst du den Zugriff in Health Connect, löscht die App die
  gespeicherten Werte beim nächsten Öffnen, auch auf der Uhr.

Die Nutzung von Informationen aus Health Connect richtet sich nach der
[Health Connect Permissions Policy](https://support.google.com/googleplay/android-developer/answer/12991134)
von Google, einschließlich der Anforderungen zur eingeschränkten Nutzung.

## Übertragung zwischen deinen Geräten

Damit Handy und Uhr denselben Weckerbestand haben, werden die oben genannten
Alarmdaten – und, falls freigegeben, die Schlafdauer der letzten Nächte –
zwischen **deinen eigenen, miteinander gekoppelten Geräten**
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
| `RECEIVE_BOOT_COMPLETED` | Wecker und Schlafenszeit-Erinnerung nach einem Neustart wiederherstellen |
| `health.READ_SLEEP` (nur Handy, optional) | Schlafdauer der letzten 7 Nächte für den Schlafplaner |

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
