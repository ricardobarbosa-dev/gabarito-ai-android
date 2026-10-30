# Add project specific ProGuard rules here.
-keep class com.gabaritoai.mobile.model.** { *; }
-keepclassmembers class * {
    @com.google.gson.annotations.SerializedName <fields>;
}
