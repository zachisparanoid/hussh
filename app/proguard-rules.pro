# --- Hussh R8/ProGuard rules ---

# Apache MINA SSHD relies on reflection + ServiceLoader + optional deps not present on Android.
-keep class org.apache.sshd.** { *; }
-dontwarn org.apache.sshd.**
-dontwarn javax.management.**
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**
-dontwarn org.ietf.jgss.**
-keep class org.apache.sshd.common.util.security.** { *; }

# BouncyCastle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# SQLCipher
-keep class net.zetetic.** { *; }
-keep class net.sqlcipher.** { *; }

# Room generated
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# kotlinx.serialization
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class **$$serializer { *; }
-keepclasseswithmembers class * { @kotlinx.serialization.Serializable <methods>; }

# Keep our serializable model classes (key bar definitions, etc.)
-keep,includedescriptorclasses class com.hussh.**$$serializer { *; }
-keepclassmembers class com.hussh.** { *** Companion; }
