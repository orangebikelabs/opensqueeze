# remove assertions on release builds
-assumenosideeffects class com.orangebikelabs.orangesqueeze.common.OSAssert {
    public static *** assert*();
}

-keep class com.beaglebuddy.mp3.** { *; }
-keep interface com.beaglebuddy.mp3.** { *; }

# java 8 lambda support (not required if/when all lambdas are moved to Kotlin)
-dontwarn java.lang.invoke.**

