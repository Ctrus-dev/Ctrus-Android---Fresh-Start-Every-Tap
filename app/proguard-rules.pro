# Keeps stack traces in crash reports readable (file + line number) without keeping real class
# names — obfuscation still applies to everything else.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- kotlinx.serialization ---
# Standard rules from https://github.com/Kotlin/kotlinx.serialization#android, scoped to this
# app's own serializable models (RecoveryCodeClient's request/response, PhysicalUnblockItem) —
# R8 can't see that these are constructed reflectively by the serialization runtime, so without
# this it strips the generated $serializer classes and the Companion.serializer() accessors.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.AnnotationsKt

-keep,includedescriptorclasses class mo.dev.ctrus.**$$serializer { *; }
-keepclassmembers class mo.dev.ctrus.** {
    *** Companion;
}
-keepclasseswithmembers class mo.dev.ctrus.** {
    kotlinx.serialization.KSerializer serializer(...);
}
