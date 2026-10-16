# Add project specific ProGuard rules here.
# You can control the set of applied configuration files using the
# proguardFiles setting in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ── kotlinx.serialization (Supabase + Hadith assets) ──
# R8 كان يجرد الـ serializers المولّدة في نسخة الإصدار فيفشل فك الترميز بصمت
# (سبب شاشة "0 كتاب / لا توجد فصول متاحة" في الصحيحين).
-keepattributes RuntimeVisibleAnnotations,AnnotationDefault,Signature,InnerClasses,EnclosingMethod
-keep @kotlinx.serialization.Serializable class * { *; }
-keepclassmembers @kotlinx.serialization.Serializable class * { *; }
-keepclasseswithmembers class kotlinx.serialization.json.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-keepclassmembers class com.qabas.app.FullSahihHadith { *** Companion; }
-keepclassmembers class com.qabas.app.FullSahihChapter { *** Companion; }
-keepclassmembers class com.qabas.app.FullSahihBook { *** Companion; }
-keepclassmembers class com.qabas.app.FullSahihBook$Metadata { *** Companion; }
