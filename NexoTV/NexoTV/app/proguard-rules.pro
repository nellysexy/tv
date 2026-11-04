# Modelos serializados con kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-keep,includedescriptorclasses class com.nexotv.app.data.**$$serializer { *; }
-keepclassmembers class com.nexotv.app.data.** {
    *** Companion;
}
-keepclasseswithmembers class com.nexotv.app.data.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
