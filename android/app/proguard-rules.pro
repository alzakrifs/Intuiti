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

# Tesseract4Android — JNI-loaded native engine.
-keep class com.googlecode.tesseract.android.** { *; }
-keep class com.googlecode.leptonica.android.** { *; }

# Google AI Edge SDK (AICore) — kept as wildcard so a future bundled SDK survives
# minification without needing a release-build edit.
-dontwarn com.google.ai.edge.aicore.**
-keep class com.google.ai.edge.aicore.** { *; }
-keep class com.google.android.gms.** { *; }
