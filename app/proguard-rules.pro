# R8 shrinks and obfuscates release builds. Libraries bring their own keep rules; these cover what
# R8 can't see. Since AGP 9, keeping a class no longer keeps its no-argument constructor unless the
# rule lists it.

# Line numbers for crash reports. Class and method names are decoded with the build's
# mapping-N.txt (MAINTAINING.md, "Crash reports").
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Glance creates the widget's button handler from its class name, with its no-argument
# constructor. Keeping the name also keeps widget buttons from an earlier version working.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
}
