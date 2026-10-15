# Corta App ProGuard / R8 rules
#
# Strategy: lean on R8 (default rules + shrinking) and only add the
# keep rules that have been empirically needed. R8 strips everything
# else automatically, including unused Compose previews, internal
# SQLDelight helpers and Timber.

# ----- General ----------------------------------------------------------------
-keepattributes *Annotation*
-keepattributes Signature
-keepattributes InnerClasses
-keepattributes EnclosingMethod
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Keep generic signatures so Ktor / kotlinx.serialization can read
# them at runtime via reflection.
-keepattributes Signature,*Annotation*,InnerClasses,EnclosingMethod

# Keep module metadata for Kotlin reflection (used by Koin's viewModel DSL
# to resolve concrete viewmodel classes when binding by `KClass`).
-keep class kotlin.Metadata { *; }
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# ----- Kotlin coroutines ------------------------------------------------------
# Coroutines are not used reflectively, but the debug agent probes the
# stack via kotlin.coroutines.jvm.internal.DebugProbes. Keep its
# internals so the agent can attach without NoClassDefFoundError.
-keepclassmembernames class kotlinx.** {
    volatile <fields>;
}
-dontwarn kotlinx.coroutines.**
-dontwarn kotlinx.atomicfu.**

# ----- Koin -------------------------------------------------------------------
# Koin uses reflection for `viewModel { ... }` and `single { ... }`
# factories. It scans definitions in compiled modules via KSP-less
# reflection, so we need the generated module classes discoverable.
-keep class org.koin.** { *; }
-keep class com.corta.app.di.** { *; }
-keepclassmembers class * {
    @org.koin.core.annotation.* <methods>;
    @org.koin.core.annotation.* <fields>;
}
-dontwarn org.koin.**

# ----- Ktor / kotlinx.serialization -------------------------------------------
# Ktor is already ProGuard-friendly. Only the kotlinx.serialization
# companion serializers and generated $serializer need keeping.
-keepattributes RuntimeVisibleAnnotations
-keep,includedescriptorclasses class com.corta.app.**$$serializer { *; }
-keepclassmembers class com.corta.app.** {
    *** Companion;
}
-keepclasseswithmembers class com.corta.app.** {
    kotlinx.serialization.KSerializer serializer(...);
}
-dontwarn io.ktor.**
-dontwarn kotlinx.serialization.**

# ----- SQLDelight + SQLCipher -------------------------------------------------
# SQLDelight generates concrete CursorMapper / RowMapper classes. They
# are referenced by name from generated code, so R8 cannot always
# prove their reachability. Keep the generated package and the
# SQLCipher native bridge.
-keep class com.corta.db.** { *; }
-keep class com.squareup.sqldelight.** { *; }
-dontwarn com.squareup.sqldelight.**
-keep class net.zetetic.database.** { *; }
-keep class net.sqlcipher.** { *; }
-dontwarn net.sqlcipher.**
-keep class net.sqlcipher.database.** { *; }
-keepclasseswithmembernames class * {
    native <methods>;
}

# ----- Timber -----------------------------------------------------------------
# Timber is reflection-free; the tree installation happens via static
# field. No rules needed, but silence the warning about the unused
# log class stripping in test environments.
-dontwarn org.jetbrains.annotations.**
-dontwarn timber.log.**

# ----- WorkManager & Room -----------------------------------------------------
-keep class * extends androidx.work.Worker
-keep class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keepclassmembers class * extends androidx.work.ListenableWorker {
    public <init>(android.content.Context, androidx.work.WorkerParameters);
}
-keep class androidx.work.impl.** { *; }
-keep class * extends androidx.room.RoomDatabase { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public <init>();
}

# ----- CortaApplication + ContentProviders / Receivers / Services ------------
# Listed in the manifest but R8 sometimes drops them when they have
# no other references in code. Keep the entry points and the workers.
-keep class com.corta.app.CortaApplication { *; }
-keep class com.corta.app.workers.** { *; }
-keep class com.corta.app.services.** { *; }
-keep class com.corta.app.receivers.** { *; }
-keep class com.corta.app.MainActivity { *; }
-keep class com.corta.app.ui.InCallActivity { *; }

# ----- Compose ----------------------------------------------------------------
# Compose ships its own ProGuard rules in the AAR. We only need to
# keep the @Composable signature for debugging if Compose Hot Reload
# is ever turned on, otherwise nothing.
-dontwarn androidx.compose.**

# ----- Google Sign-In / Credentials -------------------------------------------
-keep class com.google.android.gms.** { *; }
-dontwarn com.google.android.gms.**

# ----- Misc -------------------------------------------------------------------
# Reflection-driven JUnit / Compose UI test classes should not be
# stripped in release, although they are not packaged.
-dontwarn org.junit.**
-dontwarn org.jetbrains.kotlin.**

# Suppress notes about missing optional java.lang.management.* APIs on
# older Android runtimes that AGP 9 still pulls in transitively.
-dontwarn java.lang.management.**
-dontwarn org.slf4j.**

