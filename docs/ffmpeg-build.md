# FFmpeg-Decoder-Extension bauen (`:libs:media3-ffmpeg`)

Anleitung zum Bau der optionalen Media3-FFmpeg-Audiodecoder-Extension
(Plan Phase 3, ADR-0006). Google liefert `media3-decoder-ffmpeg` **nicht**
als Maven-Artefakt; die Extension wird aus den `androidx/media`-Quellen mit
einem eigenen, rein auf Audio reduzierten FFmpeg-Build kompiliert und als
lokales Gradle-Modul eingebunden.

> Der Bau ist der Risikotreiber der Phase und benoetigt NDK + Host-Toolchain.
> Ohne das Artefakt bleibt die App voll funktionsfaehig und faellt auf die
> Plattformdecoder zurueck (Gradle-Flag `dropsync.enableFfmpeg=false`, Default).

## Ziel-Formate

Nur Audio-Decoder, keine Encoder, kein Muxing, kein GPL:

```
alac, aiff (pcm_s16be/pcm_s24be), wmav1, wmav2, wmapro,
ape, tak, tta, dsd_lsbf, dsd_msbf, dsd_lsbf_planar, dsd_msbf_planar, wavpack
```

DSD (DSF/DFF) wird zu PCM dekodiert; natives DoP ist Folgeausbau (ADR-0009).

## Voraussetzungen

- Android NDK r27 (oder die im Projekt gepinnte Version)
- FFmpeg 6.1+ Quellen (`git clone https://git.ffmpeg.org/ffmpeg.git`)
- `androidx/media` Quellen passend zur genutzten Media3-Version (1.10.1)
- Host: Linux/macOS mit `make`, `clang`, `bash` (Windows via WSL)

## Schritte

1. **Media3-Quellen holen** und in das Extension-Verzeichnis wechseln:
   ```bash
   git clone https://github.com/androidx/media.git
   cd media/libraries/decoder_ffmpeg/src/main
   ```
2. **FFmpeg-Symlink** setzen und Build-Skript aufrufen (baut nur die oben
   gelisteten Decoder, dynamisch, ohne GPL):
   ```bash
   FFMPEG_MODULE_PATH="$(pwd)/jni"
   ln -s "<pfad-zu-ffmpeg>" "$FFMPEG_MODULE_PATH/ffmpeg"
   ENABLED_DECODERS=(alac aiff wmav1 wmav2 wmapro ape tak tta \
     dsd_lsbf dsd_msbf dsd_lsbf_planar dsd_msbf_planar wavpack)
   ./build_ffmpeg.sh "$FFMPEG_MODULE_PATH" "<ndk-pfad>" "android-24" \
     "${ENABLED_DECODERS[@]}"
   ```
   Wichtig: **`--enable-gpl` und `--enable-nonfree` NICHT** setzen
   (LGPL-Konformitaet, siehe THIRD_PARTY_NOTICES).
3. **AAR bauen**:
   ```bash
   ./gradlew :lib-decoder-ffmpeg:assembleRelease
   ```
4. **Artefakt einbinden**: Die entstandene `.aar` (bzw. die `jni/`-`.so`-
   Dateien) nach `libs/media3-ffmpeg/` im DropSync-Repo kopieren. Das Modul
   `:libs:media3-ffmpeg` (Android-Library) exportiert sie und wird in
   `settings.gradle.kts` registriert, sobald das Artefakt vorliegt.
5. **Aktivieren**: In `gradle.properties` `dropsync.enableFfmpeg=true`
   setzen. Dann nimmt `:data:audio` die Extension als
   `implementation(project(":libs:media3-ffmpeg"))` auf; die
   `DspRenderersFactory` bevorzugt sie bereits via
   `EXTENSION_RENDERER_MODE_PREFER`.

## Lizenz

FFmpeg ist LGPL-2.1-or-later und wird **dynamisch** gelinkt. Der Quellcode,
die exakte Version und die Build-Flags werden bereitgestellt; die `.so`-
Dateien sind durch eine eigene kompatible FFmpeg-Version ersetzbar. Details
in `THIRD_PARTY_NOTICES.md`.
