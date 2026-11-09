# kotlinx.serialization: keep generated serializers for content models
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keepclasseswithmembers class com.algoarch.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.algoarch.app.**$$serializer { *; }
-keepclassmembers class com.algoarch.app.** {
    *** Companion;
}
