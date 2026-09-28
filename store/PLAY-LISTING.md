# Play Console — fertige Antworten zum Einsetzen

Alles, was die Console für RiseAlarm abfragt, in der Reihenfolge der Menüpunkte.
Texte sind zum Kopieren gedacht. Grafiken liegen daneben in diesem Ordner.

Standardsprache des Eintrags ist **Englisch** (so ist auch die App lokalisiert:
`values/` englisch, `values-de/` deutsch). Deutsch als zweite Sprache anlegen.

---

## 1 — Store-Eintrag

**App-Name** (30 Zeichen)

```
RiseAlarm
```

**Kurzbeschreibung EN** (max. 80)

```
The watch wakes you with vibration. You turn the alarm off on your phone.
```

**Kurzbeschreibung DE** (max. 80)

```
Die Uhr weckt per Vibration. Ausgeschaltet wird der Wecker am Handy.
```

**Vollständige Beschreibung EN** (max. 4000)

```
RiseAlarm is an alarm clock for Wear OS and your phone, built around one idea:
the watch wakes you, but you have to reach for your phone to switch the alarm
off. No more sleepily swiping it away on your wrist.

HOW IT WORKS

The watch vibrates with a continuous pattern. The phone stays silent and shows
a full-screen stop screen instead, even on a locked display. Stopping on either
device ends the alarm on both.

If nobody reacts, the alarm snoozes by itself after a time you choose, so the
watch never keeps vibrating forever.

FEATURES

- Set alarms on either device: time, label and weekday repetition
- Sleep duration in the list: how long until the next alarm rings
- Configurable snooze: duration, maximum count, and how long it rings before
  snoozing automatically
- Continuous sync between watch and phone
- Every device schedules its own alarms, so they ring reliably even when watch
  and phone are disconnected
- Alarms survive reboots, time changes and app updates

PRIVACY

RiseAlarm collects nothing. No accounts, no ads, no analytics, no tracking
libraries. Your alarms stay in your device's private storage and are exchanged
only between your own paired devices.
```

**Vollständige Beschreibung DE** (max. 4000)

```
RiseAlarm ist ein Wecker für Wear OS und dein Handy, gebaut um eine Idee: Die
Uhr weckt dich, ausschalten musst du aber am Handy. Damit ist Schluss mit dem
verschlafenen Wegwischen am Handgelenk.

SO FUNKTIONIERT ES

Die Uhr vibriert mit einem Dauermuster. Das Handy bleibt still und zeigt
stattdessen einen Vollbild-Stopp-Screen, auch bei gesperrtem Display. Ein Stopp
auf einem der beiden Geräte beendet den Alarm überall.

Reagiert niemand, schlummert der Wecker nach einer einstellbaren Zeit von
selbst, damit die Uhr nicht endlos weitervibriert.

FUNKTIONEN

- Wecker auf beiden Geräten anlegen: Uhrzeit, Bezeichnung, Wochentage
- Schlafdauer in der Liste: Zeit bis zum nächsten Klingeln
- Snooze einstellbar: Dauer, maximale Anzahl und Klingeldauer bis zum
  automatischen Schlummern
- Ständige Synchronisation zwischen Uhr und Handy
- Jedes Gerät plant seine Alarme selbst und weckt deshalb auch dann
  zuverlässig, wenn Uhr und Handy gerade getrennt sind
- Alarme überstehen Neustart, Zeitumstellung und App-Updates

DATENSCHUTZ

RiseAlarm sammelt nichts. Keine Konten, keine Werbung, keine Analyse, keine
Tracking-Bibliotheken. Deine Wecker liegen im privaten Speicher deines Geräts
und werden nur zwischen deinen eigenen gekoppelten Geräten ausgetauscht.
```

**Grafiken** (liegen in diesem Ordner)

| Was | Datei | Format |
|---|---|---|
| App-Symbol | `icon-512.png` | 512×512 PNG |
| Feature-Grafik | `feature-1024x500.png` | 1024×500 PNG |
| Screenshots Handy | noch aufzunehmen | mind. 2, 16:9 oder 9:16 |
| Screenshots Uhr | noch aufzunehmen | mind. 1, 1:1 (384×384 oder 454×454) |

> **Uhr-Screenshots sind nicht optional.** Ohne sie erscheint die App im Play
> Store auf dem Handgelenk gar nicht, egal wie vollständig der Rest ist.

**Datenschutz-URL**

```
https://schubbii.github.io/WatchAlarm/privacy/
```

---

## 2 — Die drei Deklarationen

Unter **App-Inhalte**. Ohne sie wird die Veröffentlichung abgelehnt.

### Vollbild-Benachrichtigung (`USE_FULL_SCREEN_INTENT`)

```
RiseAlarm is an alarm clock. When an alarm goes off, the full-screen intent is
the only way to present the stop and snooze controls on a locked device. The
user set the alarm themselves and expects the screen to come up at that exact
time. The permission is not used for any other purpose: no incoming calls, no
promotional content, no other notification in the app uses it.
```

### Exakte Alarme (`USE_EXACT_ALARM`)

```
The core function of the app is waking the user at a time they chose. An
inexact alarm may be deferred by minutes or longer, which would defeat the
purpose. The app schedules exact alarms only for user-created alarms and for
the snooze intervals those alarms produce.
```

### Vordergrund-Service-Typ `specialUse`

Begründung — wortgleich mit der `<property>` in `core/src/main/AndroidManifest.xml`:

```
Ringing an alarm the user scheduled. The service keeps vibration, the wake lock
and the full-screen stop screen alive until the user dismisses or snoozes the
alarm. None of the predefined foreground service types describe an alarm clock:
the app plays no media, records nothing, and tracks no location. The service
runs only while an alarm is actually ringing and stops as soon as the user
reacts, or automatically after the timeout the user configured.
```

> Play verlangt hier meist ein **kurzes Demo-Video** (Link, z. B. nicht
> gelistetes YouTube-Video): Wecker stellen, klingeln lassen, auf beiden
> Geräten stoppen. Eine Bildschirmaufnahme vom Handy reicht.

---

## 3 — Datensicherheit (Data Safety)

| Frage | Antwort |
|---|---|
| Erhebt oder teilt deine App Nutzerdaten? | **Nein** |
| Werden Daten bei der Übertragung verschlüsselt? | entfällt |
| Können Nutzer die Löschung ihrer Daten anfordern? | entfällt |

Begründung, falls nachgefragt wird:

```
The app has no backend. Alarm data stays in the app's private storage
(SharedPreferences). Synchronisation happens only between the user's own paired
devices through the Wearable Data Layer API; the developer operates no server
and has no access to the data at any point.
```

> **Ehrlich abwägen:** Die Data Layer API kann Daten zwischen Uhr und Handy
> auch über Googles Infrastruktur weiterreichen, wenn keine direkte Verbindung
> besteht. Das ist Plattformverhalten, nicht Erhebung durch die App — der
> Entwickler bekommt die Daten nie zu sehen. Übliche Praxis für Wear-Apps ist
> daher "keine Erhebung". Wer ganz sicher gehen will, erwähnt die Data Layer
> im Formular unter "Geräte-IDs/App-Aktivität" — das zieht dann aber Folge-
> fragen nach sich, die inhaltlich ins Leere laufen.

---

## 4 — Altersfreigabe (Fragebogen)

Kategorie: **Dienstprogramme / Produktivität / Kommunikation / Sonstiges**

Alle Fragen nach Gewalt, Sexualität, Schimpfwörtern, Drogen, Glücksspiel,
nutzergenerierten Inhalten, Standortfreigabe, Datenweitergabe und In-App-Käufen:
**Nein**. Ergebnis ist überall die niedrigste Stufe (USK 0, PEGI 3, ESRB E).

---

## 5 — Zielgruppe und Inhalte

| Feld | Antwort |
|---|---|
| Altersgruppen | **18 und älter** |
| Richtet sich die App an Kinder? | **Nein** |
| Werbung enthalten? | **Nein** |
| Regierungs-App? | Nein |
| Finanz-App? | Nein |
| Gesundheits-App? | Nein |

> Nur Erwachsene auszuwählen hält die App aus dem Programm "Familien" heraus,
> das zusätzliche Auflagen und Prüfungen mitbringt. Für einen Wecker gibt es
> keinen Grund, sich das anzutun.

---

## Was danach noch fehlt

- Screenshots von Handy und Uhr (Punkt 1) — am Emulator aufnehmbar
- Demo-Video für die `specialUse`-Deklaration (Punkt 2)
- Der Upload-Keystore, um überhaupt ein AAB hochladen zu können
