# Regole ProGuard/R8 per NinnaNanna
# (release attualmente senza minify: file mantenuto per configurazioni future)

# NewPipeExtractor usa reflection su alcuni modelli: teniamo tutto il package
-keep class org.schabi.newpipe.extractor.** { *; }
-dontwarn org.schabi.newpipe.extractor.**
-dontwarn com.fasterxml.jackson.**
-dontwarn org.mozilla.**
-dontwarn okhttp3.**
-dontwarn okio.**