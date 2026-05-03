# Keep stack traces useful in Play Console crash reports.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# ---- Room ----
# Generated DAO impls reference our entities by name; without this, R8 can
# strip fields and Room runtime fails with "missing column".
-keep class com.elias.autosms.data.** { *; }

# ---- Firebase / Google Play services ----
# Firebase SDKs use reflection for serialization and component discovery.
# Bundled rules from each AAR cover most cases; defensive keep below.
-keep class com.google.firebase.** { *; }
-dontwarn com.google.firebase.**
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ---- Google Play Billing ----
-keep class com.android.billingclient.api.** { *; }
-keep class com.elias.autosms.billing.** { *; }

# ---- Our Parcelable data classes (passed via Intent extras) ----
-keep class com.elias.autosms.data.AutoReplyRule { *; }
-keep class com.elias.autosms.data.ContextDocument { *; }
-keep class com.elias.autosms.data.SmsSchedule { *; }
