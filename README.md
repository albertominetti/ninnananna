# NinnaNanna 🌙

App Android **nativa** per scaricare l'audio di una ninna nanna (o di qualsiasi video YouTube)
e riprodurlo **offline** a tutto schermo, con temi pensati per la camera dei bambini.

Tutto avviene **on-device**: nessun backend, nessun account, nessuna pubblicità.
I file audio vengono salvati nello **storage interno** dell'app
(`filesDir/lullabies`), al sicuro da altri utenti/app.

> ⚠️ **Responsabilità d'uso** — leggi le [note legali](#note-legali-youtube) in fondo.
> Scarica **solo** contenuti tuoi, con licenza Creative Commons/libera o per cui hai
> l'autorizzazione. Rispetta i Termini di Servizio di YouTube.

---

## Funzionalità

| Area | Cosa fa |
|---|---|
| **Schermata principale** | Campo per incollare un URL YouTube + bottone **"Scarica audio"** |
| **Intent** | `ACTION_SEND` (Condividi → NinnaNanna) e `ACTION_VIEW` (link `youtube.com/watch`, `youtu.be`, …): apre l'app, pre-compila il campo e avvia il download |
| **Lista audio** | Nome file, durata, dimensione e data; ordinate per nome |
| **Riproduzione** | Un **solo player alla volta** (singleton ExoPlayer): Play / Stop per ogni item |
| **Mini-player bar** | Barra in basso con titolo della traccia + pulsante **Stop** |
| **Gestione file** | **Rinomina** e **Elimina** ogni audio |
| **Impostazioni** | Tema **Chiaro / Scuro / Amoled** (nero puro), **Mantieni schermo attivo** (`FLAG_KEEP_SCREEN_ON`), **Reset di tutto** con dialog di conferma |
| **Per scaricare** | Risoluzione del **miglior stream audio progressivo** con **NewPipeExtractor**, download con **OkHttp** (`DownloadManager` del sistema non usato: download diretto HTTP) |

## Screenshot

> 📷 _Inserire qui gli screenshot reali dell'app (Schermata principale, Mini-player, Impostazioni)._

| Schermata principale | Impostazioni |
|---|---|
| _placeholder_ | _placeholder_ |

## Stack tecnologico

- **Kotlin** + **Jetpack Compose** (Material 3)
- **Gradle Kotlin DSL** — wrapper **Gradle 8.7**
- **AGP 8.2.2** · `minSdk 26` · `targetSdk 34` · package `com.alberto.ninnananna`
- **ExoPlayer** `androidx.media3:media3-exoplayer` (player locale)
- **NewPipeExtractor** `com.github.TeamNewPipe:NewPipeExtractor:v0.26.5` (risoluzione stream, on-device)
- **OkHttp** `com.squareup.okhttp3:okhttp:4.12.0` (Downloader dell'estrattore + download file)
- **DataStore Preferences** (impostazioni tema / keep-screen-on)
- **Navigation Compose** (schermata principale ↔ impostazioni)
- **Nessuna** dipendenza da `yt-dlp`, nessun backend, nessuna pubblicità

### Struttura del progetto

```
ninnananna/
├── settings.gradle.kts
├── build.gradle.kts
├── gradle.properties
├── gradle/wrapper/…                    # wrapper Gradle 8.7
├── app/
│   ├── build.gradle.kts
│   └── src/main/
│       ├── AndroidManifest.xml         # INTERNET + SEND/VIEW + FOREGROUND_SERVICE_MEDIA_PLAYBACK + POST_NOTIFICATIONS
│       ├── res/…                       # strings, themes, icona launcher adattiva
│       └── java/com/alberto/ninnananna/
│           ├── MainActivity.kt         # activity + intent SEND/VIEW + FLAG_KEEP_SCREEN_ON
│           ├── NinnanannaApp.kt        # tema + navigazione + DataStore (SettingsStore)
│           ├── Theme.kt                # color scheme Chiaro / Scuro / Amoled
│           ├── DownloadRepository.kt   # NewPipeExtractor + OkHttp + filesDir/lullabies
│           ├── PlayerManager.kt        # ExoPlayer singleton (un solo player alla volta)
│           ├── LullabyList.kt          # schermata principale + mini-player bar
│           └── SettingsScreen.kt       # impostazioni + reset
├── scripts/build-apk.sh                # build automatica (vedi sotto)
├── .github/workflows/release.yml       # release APK/AAB su tag v*
└── README.md
```

## Come compilare

### Prerequisiti

- **JDK 17 o 21** (consigliato). Impostare `JAVA_HOME` se non è già nel PATH.
- **Android SDK** (opzionale): se `ANDROID_SDK_ROOT`/`ANDROID_HOME` non sono configurati,
  lo script lo installa automaticamente in `~/android-sdk`.

### Con lo script automatico (consigliato)

Lo script `scripts/build-apk.sh`:

1. controlla `JAVA_HOME`;
2. installa **cmdline-tools** se manca `ANDROID_SDK_ROOT`;
3. accetta le **licenze** SDK;
4. installa `platforms;android-34` e `build-tools;34.0.0`;
5. lancia `./gradlew assembleDebug` (default) o `assembleRelease`;
6. copia l'APK finale in `./dist/`.

```bash
cd ninnananna

# APK di debug
./scripts/build-apk.sh

# APK di release
# (senza keystore: firmato con la chiave debug, installabile ma non per Google Play)
./scripts/build-apk.sh release

# Release con firma vera (variabili d'ambiente opzionali)
export KEYSTORE_FILE=/percorso/keystore.jks
export KEYSTORE_PASSWORD=...
export KEY_ALIAS=...
export KEY_PASSWORD=...
./scripts/build-apk.sh release

# Oppure keystore in base64 (comodo per CI):
export KEYSTORE_BASE64="$(base64 -w0 keystore.jks)"
./scripts/build-apk.sh release
```

Lo script è idempotente: al secondo giro salta il setup dell'SDK già presente.

### A mano (debug)

```bash
cd ninnananna
export ANDROID_SDK_ROOT=$HOME/android-sdk   # se non già impostato
./gradlew assembleDebug
# APK: app/build/outputs/apk/debug/app-debug.apk
```

## Come installare l'APK

Collegare un dispositivo Android con **debug USB** abilitato oppure usare un emulatore:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

In alternativa: copiare l'APK sul telefono e toccarlo per installarlo
(consentire "Installa da fonti sconosciute" se richiesto).
L'app richiede **solo** il permesso `INTERNET` per il download; non chiede altri permessi.

## Note legali YouTube

- Questa app usa **NewPipeExtractor**, una libreria open source (GPLv3) che **non** utilizza
  API YouTube ufficiali. La disponibilità degli stream può cambiare ed è fuori dal controllo dell'app.
- YouTube e i singoli video sono soggetti ai **Termini di Servizio di Google/YouTube**.
- **Scarica esclusivamente**:
  - video di tua proprietà;
  - video con **licenza Creative Commons / libera** (visibile nella descrizione del video);
  - contenuti per cui hai il permesso esplicito dell'autore.
- **Non** scaricare musica o video protetti da copyright senza autorizzazione:
  in molti Paesi è illegale e comunque contrario ai ToS di YouTube.
- L'autore dell'app non è responsabile dell'uso improprio.

> Un uso corretto e legale tipico: scaricare **le proprie** ninnenanne
> (ad esempio registrazioni personali caricate su YouTube) per riprodurle offline di notte.

## Release automatica (GitHub Actions)

Su un tag `v*` (es. `git tag v1.0.0 && git push origin v1.0.0`) il workflow
`.github/workflows/release.yml`:

1. compila `assembleRelease` + `bundleRelease` con `setup-java` (Temurin 17) e `setup-android`;
2. carica APK e AAB come **artifact**;
3. crea una **GitHub Release** con i file allegati (`softprops/action-gh-release`).

Per firmare con la chiave di release su GitHub, aggiungere i **repository secrets**:
`KEYSTORE_BASE64` (keystore codificato in base64), `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
Senza secrets la build è firmata con la chiave debug (installabile, non pubblicabile su Google Play).

## Autore

Alberto Minetti — app pubblicata a scopo dimostrativo/test con licenza GPLv3.