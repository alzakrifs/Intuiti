# OkHttp uses reflection internally; the published consumer rules cover most of it.

# kotlinx.serialization keeps reflection-free; @Serializable types only need this
# pattern if you also use generic-type-tokens. Keep classes annotated with @Serializable.
-keep,includedescriptorclasses class **$$serializer { *; }
-keepclassmembers class * {
    *** Companion;
}
-keepclasseswithmembers class * {
    kotlinx.serialization.KSerializer serializer(...);
}

# ML Kit text recognition models are loaded via reflection.
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.** { *; }
