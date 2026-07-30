# R8-Regeln für die Handy-App.
#
# Das meiste erledigen die mitgelieferten Regeln von AGP (Manifest-Komponenten
# wie Activities, Services und Receiver bleiben automatisch erhalten) sowie die
# consumer-Regeln von AndroidX und den Play Services. Hier steht nur, was R8
# nicht selbst sehen kann.

# Klingel-Activity und -Service werden über Class-Referenzen aus dem core-Modul
# gestartet (AppRegistry.ringActivityClass). Sicherheitshalber vollständig
# behalten — ein verschluckter Wecker ist teurer als ein paar Kilobyte.
-keep class com.watchalarm.core.** { *; }
-keep class com.watchalarm.mobile.AlarmActivity { *; }

# org.json wird für die Alarm-Serialisierung genutzt; die Klassen kommen aus
# dem Framework, aber die Aufrufe sollen nicht wegoptimiert werden.
-dontwarn org.json.**

# Zeilennummern in Play-Console-Crashes lesbar halten.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
