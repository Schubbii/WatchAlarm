# Testen

Zwei Ebenen: Die **Unit-Tests** prüfen die Logik und laufen bei jedem Push in
der CI (`./gradlew :core:testDebugUnitTest`). Ob ein Wecker wirklich klingelt,
hängt aber am System — Doze, Bluetooth, Hersteller-ROM — und zeigt sich nur
auf echten Geräten. Dafür ist die Liste unten.

Vorher: **Release-Build** auf beiden Geräten (Debug ruckelt auf der Uhr, siehe
README). Wecker 2–3 Minuten in die Zukunft stellen.

## Gerätetests

### Grundfunktion

1. Einmal-Wecker klingelt: Uhr vibriert, Handy zeigt Stopp-Screen und bleibt stumm.
2. Stopp auf der **Uhr** → beide hören auf, Einmal-Wecker danach auf beiden aus.
3. Stopp am **Handy** → beide hören auf.
4. Schlummern am Handy → beide hören auf und klingeln nach der eingestellten Zeit erneut.
5. Maximale Schlummer-Anzahl erreicht → nur noch Stopp möglich.
6. Niemand reagiert → nach der Klingeldauer schlummert er selbst; ist das Limit erreicht, stoppt er.
7. Wiederholender Wecker → bleibt nach Stopp an, nächster Termin stimmt.
8. Zwei Wecker auf dieselbe Minute → ein Stopp beendet beide, keiner klingelt am nächsten Tag erneut.

### Bildschirmzustände

9. Handy gesperrt, Display aus → Stopp-Screen erscheint trotzdem.
10. Handy entsperrt in einer anderen App → Stopp-Screen kommt nach vorne.
11. Uhr im Display-aus, Schlafmodus und „Nicht stören" → vibriert trotzdem.

### Synchronisation

12. Wecker auf der Uhr anlegen, ändern, löschen → kommt am Handy an, und umgekehrt.
13. Bluetooth am Handy aus, Wecker abwarten → **beide** klingeln selbstständig.
14. Getrennt auf der Uhr ändern, dann wieder verbinden → Änderung kommt nach.
15. Auf beiden Geräten fast gleichzeitig ändern → beide landen auf demselben Stand.

### System-Ereignisse

16. Beide Geräte neu starten, Wecker geplant → klingelt trotzdem.
17. Neustart während eines laufenden Schlummerns → klingelt zur Schlummer-Zeit.
18. Uhrzeit oder Zeitzone manuell ändern → klingelt zur richtigen neuen Zeit.
19. Neue App-Version drüberinstallieren → Wecker sind weiter geplant.
20. Doze erzwingen (`adb -s <gerät> shell dumpsys deviceidle force-idle`, danach
    `… unforce`) → klingelt pünktlich.
21. „Beenden erzwingen" → Android löscht dabei die geplanten Wecker (System-
    verhalten, kein App-Fehler); nach erneutem Öffnen der App sind sie wieder da.

### Berechtigungen (Handy, Android 14+)

22. Benachrichtigungen bzw. Vollbild-Berechtigung entziehen → Hinweis erscheint
    und führt in die passenden Einstellungen.

### Anzeige und Bedienung

23. Schlafdauer in der Liste stimmt und läuft minütlich mit, auch beim Schlummern.
24. Krone/Lünette: Liste scrollen, Uhrzeit im Editor stellen, nach Zurück aus
    dem Editor weiter scrollen — flüssig.
25. Deutsch und Englisch, 12- und 24-Stunden-Format → Zeit und Wochentage korrekt.

### Ernstfall

26. Eine echte Nacht: Handy am Ladekabel, Uhr am Handgelenk.

## Ergebnisse

| Datum | Version | Build | Tests | Ergebnis |
|---|---|---|---|---|
| 2026-10-07 | 2.1 | Release | 1–8 Grundfunktion | ✅ fehlerfrei |
| 2026-10-07 | 2.1 | Release | 9–11 Bildschirmzustände | ✅ fehlerfrei |
| 2026-10-07 | 2.1 | Release | 12–15 Synchronisation (mit und ohne Bluetooth bzw. WLAN) | ✅ fehlerfrei |
| — | — | — | 16–26 | offen |

Bei einem Fehler festhalten: Testnummer, Gerät, was passiert ist — und am
besten einen Logcat-Mitschnitt, gefiltert auf `com.Rise.Alarm`.
