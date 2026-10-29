# Cat Puzzle R8 rules (full mode, AGP default). Hilt, Room, Compose, DataStore, Coroutines, Billing, Firebase, UMP, GMA Next-Gen,
# Unity Ads and Meta Audience Network ship their own consumer rules: do NOT re-keep their packages here.
# No blanket keeps of com.threetech.** : the app has no reflection on its own classes (audited: no Class.forName / getDeclared* /
# getIdentifier / Gson / Moshi / kotlinx.serialization). Persisted ids use StableIds (explicit ints / string keys), not enum
# name() or ordinal, so obfuscation cannot change stored data.

# Crashlytics: readable stack traces (the Crashlytics Gradle plugin uploads mapping.txt on release builds when applied).
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Optimization / obfuscation levers (full mode).
-allowaccessmodification
-repackageclasses ''

# Release: strip verbose/debug/info logging. Log.w/e kept (non-sensitive: billing/ads failure notes). Log.d calls are already
# guarded by BuildConfig.DEBUG; this removes the string building too. Requires proguard-android-optimize.txt (set in build.gradle.kts).
-assumenosideeffects class android.util.Log {
    public static int v(...);
    public static int d(...);
    public static int i(...);
}

# Mediation adapters are instantiated by class name by the GMA Next-Gen SDK. The SDK's own rule already keeps
# MediationAdapter / Adapter implementations; the adapter AARs ship NO consumer rules, so pin the two entry classes
# (class-level, no members) as a cheap guard against a rule-shape change in a future SDK bump.
-keep class com.google.ads.mediation.facebook.FacebookMediationAdapter
-keep class com.google.ads.mediation.unity.UnityMediationAdapter
