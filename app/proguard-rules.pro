# R8 shrinks and obfuscates release builds. Libraries bring their own keep rules; these cover what
# R8 can't see. Write new rules with the constructor when code creates the class by reflection:
# -keep class A { <init>(); }  (gradle.properties keeps AGP 8's looser meaning for older libraries).

# Line numbers for crash reports. Class and method names are decoded with the build's
# mapping-N.txt (MAINTAINING.md, "Crash reports").
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# Glance creates the widget's button handler from its class name, with its no-argument
# constructor. Keeping the name also keeps widget buttons from an earlier version working.
-keep class * implements androidx.glance.appwidget.action.ActionCallback {
    <init>();
}
