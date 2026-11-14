# kotlinx.serialization (backup format)
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class kotlinx.serialization.json.** { kotlinx.serialization.KSerializer serializer(...); }
-keep,includedescriptorclasses class com.edubuddy.app.**$$serializer { *; }
-keepclassmembers class com.edubuddy.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.edubuddy.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# Room entities are referenced reflectively by generated code only; defaults are fine.
