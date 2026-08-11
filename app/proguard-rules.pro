# Keep line numbers for debugging
-keepattributes SourceFile,LineNumberTable

# kotlinx.serialization (导航键/NavKey 等 @Serializable 类)
-keepattributes *Annotation*, InnerClasses, Signature, ExceptionTable
-keep,includedescriptorclasses class com.ringlearn.app.**$$serializer { *; }
-keepclassmembers class com.ringlearn.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.ringlearn.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
