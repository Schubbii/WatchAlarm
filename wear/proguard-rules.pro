# R8-Regeln für die Wear-App. Siehe mobile/proguard-rules.pro.

-keep class com.watchalarm.core.** { *; }
-keep class com.watchalarm.wear.WatchRingActivity { *; }

-dontwarn org.json.**

-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
