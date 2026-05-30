-keepattributes SourceFile,LineNumberTable,Signature,EnclosingMethod,InnerClasses
-keep class com.analogvault.data.model.** { *; }
-keep class com.analogvault.data.backup.** { *; }
-keep class com.analogvault.data.network.** { *; }
-keepclassmembers class * { @androidx.room.* <methods>; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**

# Gson specifics
-keep class com.google.gson.reflect.TypeToken
-keep class * extends com.google.gson.reflect.TypeToken
-keep public class * implements java.lang.reflect.Type
