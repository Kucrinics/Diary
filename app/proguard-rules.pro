# MyTracker и AppMetrica везут собственные consumer-rules через AAR.
# Здесь — только то, что нужно сохранить явно.

-keep class ru.diary.app.data.model.** { *; }
-keepclassmembers class ru.diary.app.data.model.** { *; }

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod

-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
