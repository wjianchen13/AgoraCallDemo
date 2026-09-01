# Keep the public Agora SDK surface when the host application enables shrinking.
-keep class io.agora.** { *; }
-dontwarn io.agora.**

# NetEase Yunxin IM and V2 signalling reflection entry points.
-dontwarn com.netease.nim.**
-keep class com.netease.nim.** { *; }
-dontwarn com.netease.nimlib.**
-keep class com.netease.nimlib.** { *; }
