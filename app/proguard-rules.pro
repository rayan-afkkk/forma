# kotlinx.serialization: keep generated serializers for stored and exported data.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt
-keepclassmembers class app.forma.** {
    *** Companion;
}
-keepclasseswithmembers class app.forma.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class app.forma.**$$serializer { *; }

# Navigation routes are serializable objects/classes.
-keep class app.forma.android.nav.** { *; }
