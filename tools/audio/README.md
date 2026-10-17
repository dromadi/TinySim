# Original offline soundscapes

`GenerateSoundscapes.java` synthesizes three different 300-second stereo PCM16/48 kHz soundscapes: quarry wind/birds, workshop wind/birds with a quiet distant motor, and garden wind/birds/water. Seeded noise, slow gust envelopes, chirps, and harmonics are authored here; no recordings, third-party samples, network or microphone are used. Original output and generator are MIT licensed.

The Android `preBuild` task runs the Java 17 source launcher. No extra dependency is needed. Build-generated WAVs live under `app/build/generated/sound-assets/soundscapes/`, are bundled offline, and are ignored by Git. The three tracks total 172,800,132 bytes (15 minutes). PCM WAV is stored uncompressed so AssetManager.openFd/MediaPlayer can stream the asset directly. These are active soundscapes, not padding files. The APK exceeds 100 MB because it includes this lossless audio pack; file size itself is not a graphics quality metric.

GameAudio chooses quarry/workshop/garden by the viewed area, prepares asynchronously, fades in, crossfades on area changes, fades out when muted, and releases players on pause/detach. Playback pauses on audio focus loss; muted games do not request focus. Android system media volume and the sound switch control playback. Peaks are bounded; tracks fade smoothly at loop boundaries. Device listening/playback has not yet been tested.

```sh
java tools/audio/GenerateSoundscapes.java /tmp/soundscapes
python3 tools/audio/verify_pack.py /tmp/soundscapes
# Optional APK size, asset length and ZIP_STORED verification:
python3 tools/audio/verify_pack.py app/build/generated/sound-assets/soundscapes app/build/outputs/apk/debug/app-debug.apk
```

`index.json` records duration, byte count, peak/RMS and SHA-256 for every track. The verifier checks real WAV headers, data variation, hashes and packaging.
