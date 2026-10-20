# UniFFI bindings are called reflectively by JNA; keep them and JNA intact.
-keep class com.sun.jna.** { *; }
-keep class * implements com.sun.jna.** { *; }
-keep class uniffi.cybermeta_ffi.** { *; }
-dontwarn java.awt.**
-dontwarn com.sun.jna.**
