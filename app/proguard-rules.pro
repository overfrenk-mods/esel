# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in /home/adrian/Android/Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

# Uncomment this to preserve the line number information for
# debugging stack traces.
#-keepattributes SourceFile,LineNumberTable

# If you keep the line number information, uncomment this to
# hide the original source file name.
#-renamesourcefileattribute SourceFile

# ProGuard/R8 rules for Eversense-Reader

# Mantiene la classe SGV e tutti i suoi campi e metodi pubblici.
# Questo è fondamentale perché la classe viene passata tra componenti tramite Intent (Serializable)
# e i suoi campi vengono usati per creare oggetti JSON per l'invio.
# Senza questa regola, R8 potrebbe rinominare i campi "value", "raw", "direction",
# rompendo la comunicazione con AAPS.
-keep class esel.esel.esel.datareader.SGV { *; }

# Mantiene anche i nomi dei metodi pubblici e dei campi delle tue classi di utilità principali, per sicurezza.
-keep class esel.esel.esel.util.** { *; }
