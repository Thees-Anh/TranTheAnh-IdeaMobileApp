# PawQuest release shrinking rules. Libraries (kotlinx.serialization, Ktor, Koin,
# supabase-kt, AndroidX) ship their own consumer rules; only app-specific needs live here.

# Readable crash stack traces without exposing source file names.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# kotlinx.serialization: keep generated serializers of app models (incl. private DTOs).
-keepattributes *Annotation*,InnerClasses,Signature,EnclosingMethod
-keepclassmembers @kotlinx.serialization.Serializable class com.pawquest.** {
    *** Companion;
    *** INSTANCE;
    kotlinx.serialization.KSerializer serializer(...);
}
-keep,includedescriptorclasses class com.pawquest.**$$serializer { *; }

