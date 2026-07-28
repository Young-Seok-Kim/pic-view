# ===========================================================
#  정읍 시선 R8 규칙
#  릴리즈 빌드에서 코드 축소/난독화를 켤 때 깨지지 않게 하는 설정.
# ===========================================================

# -----------------------------------------------------------
# 크래시 로그를 읽을 수 있도록 줄 번호를 남긴다.
# (원본 파일명은 감춰서 난독화 효과는 유지)
# -----------------------------------------------------------
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# 제네릭/애노테이션 정보 — Gson, Retrofit 이 런타임에 사용한다.
-keepattributes Signature
-keepattributes *Annotation*
-keepattributes EnclosingMethod
-keepattributes InnerClasses
-keepattributes RuntimeVisibleAnnotations
-keepattributes RuntimeVisibleParameterAnnotations

# -----------------------------------------------------------
# Gson 으로 JSON 을 역직렬화하는 모델
#  필드 이름이 곧 JSON 키라서 난독화되면 전부 null 이 된다.
# -----------------------------------------------------------
-keep class com.youngs.picview.data.model.** { *; }

# SpotItem 은 Gson 역직렬화 대상은 아니지만
# Serializable 로 Bundle 에 담겨 화면 간 전달된다.
-keep class com.youngs.picview.ui.model.** { *; }

# Serializable 규약상 유지해야 하는 멤버
-keepclassmembers class * implements java.io.Serializable {
    static final long serialVersionUID;
    private static final java.io.ObjectStreamField[] serialPersistentFields;
    private void writeObject(java.io.ObjectOutputStream);
    private void readObject(java.io.ObjectInputStream);
    java.lang.Object writeReplace();
    java.lang.Object readResolve();
}

# -----------------------------------------------------------
# Gson
# -----------------------------------------------------------
-dontwarn sun.misc.**
-keep class com.google.gson.reflect.TypeToken { *; }
-keep class * extends com.google.gson.reflect.TypeToken
-keepclassmembers,allowobfuscation class * {
    @com.google.gson.annotations.SerializedName <fields>;
}

# -----------------------------------------------------------
# Retrofit
#  (라이브러리가 consumer 규칙을 포함하지만 방어적으로 명시)
# -----------------------------------------------------------
-keep,allowobfuscation,allowshrinking interface retrofit2.Call
-keep,allowobfuscation,allowshrinking class retrofit2.Response
-keep,allowobfuscation,allowshrinking class kotlin.coroutines.Continuation
-if interface * { @retrofit2.http.* public *** *(...); }
-keep,allowoptimization,allowshrinking,allowobfuscation class <1>

# -----------------------------------------------------------
# OkHttp / Okio
# -----------------------------------------------------------
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**

# -----------------------------------------------------------
# Glide
# -----------------------------------------------------------
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
    <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
    **[] $VALUES;
    public *;
}

# -----------------------------------------------------------
# 네이버 지도 SDK
# -----------------------------------------------------------
-keep class com.naver.maps.** { *; }
-dontwarn com.naver.maps.**

# -----------------------------------------------------------
# CameraX
# -----------------------------------------------------------
-dontwarn androidx.camera.**
