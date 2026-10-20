-keepattributes SourceFile,LineNumberTable,Signature,InnerClasses,EnclosingMethod

# JNI surface of the ARMSX2 core. Class and method names are bound by the native library
# (Java_kr_co_iefriends_pcsx2_NativeApp_* symbols, FindClass/GetStaticMethodID callbacks),
# so they must survive R8 untouched.
-keep class kr.co.iefriends.pcsx2.** { *; }
-keep class com.armsx2.BiosInfo { *; }
-keep class org.libsdl.app.** { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}
