# Chaquopy ships its own keep rules. Keep our classes' names readable in crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
# Python calls nothing in our code, but keep the app's own classes intact to be safe with reflection
# from AndroidX (WorkManager workers, receivers, services are referenced by name).
-keep class com.example.trackerfinder.** { *; }
