# ReelCraft

**A free, open-source short-video editor for Android phones.** Cut out the pauses, add captions and music, and export a vertical video for TikTok, Instagram Reels, YouTube Shorts and WhatsApp Status. No watermark, no account, no subscription, and everything runs on your phone, even offline.

ReelCraft is built for creators on everyday Android phones, starting in East Africa.

## Try it

1. On your Android phone, open the **[Releases](../../releases)** page of this project.
2. Under the newest release, tap the file ending in **.apk** to download it.
3. Open the downloaded file. If your phone asks, allow installing apps from your browser or file manager.
4. Open **ReelCraft** and tap **Add clips**.

Requires Android 8.0 or newer. Test builds update in place: install a newer one over the old one.

There is also a browser version of the first prototype in [`web/index.html`](web/index.html).

## What it does today (version 0.1)

| Feature | What you get |
|---|---|
| Import | Pick several videos from your gallery |
| Edit | Trim each piece, split at the playhead, reorder, delete |
| Zoom | Crop into a clip and move the frame, for example to remove black bars baked into reposted videos |
| Auto-cut | Finds the quiet gaps between sentences and removes them, with a sound graph showing what's kept |
| Captions | One caption line per piece, two styles, adjustable size and height, plus a "hook" line at the top of the first piece |
| Audio | Clip volume, looping background music with its own volume |
| Export | Vertical MP4 at 720p or 1080p, then share straight to TikTok or WhatsApp, or save to your gallery (Camera folder › ReelCraft) |

## Roadmap

1. **Automatic captions** that run on the phone: English and Swahili first, then Luganda and other local languages.
2. Templates: ready-made layouts for common video types.
3. Remove filler words ("umm", "eh") in addition to silences.
4. Export presets per platform, including WhatsApp Status length limits.
5. Optional paid cloud features (AI voiceover, background removal). The editor itself stays free.

## How it's built

- **Kotlin** with **Jetpack Compose** for the screens
- **Media3 ExoPlayer** for preview and **Media3 Transformer** for exporting, both using the phone's hardware video chips
- Auto-cut decodes the sound with Android's own `MediaCodec`, measures loudness every 20 ms, and keeps the loud stretches

Main files are in `app/src/main/java/org/reelcraft/app/`:

| File | Job |
|---|---|
| `EditorViewModel.kt` | The project: clips, pieces, playback, auto-cut, export |
| `AudioAnalyzer.kt` | Reads a clip's sound and finds where people are speaking |
| `Exporter.kt` | Turns the timeline into a finished video |
| `Captions.kt` | Caption wrapping and colors, shared by preview and export |
| `ui/` | The screens |

### Building it yourself

Every push to `main` is built automatically by GitHub Actions (see the **Actions** tab), and a test release with the APK is published. To build on your own computer, install Android Studio, open this folder, and press Run.

The signing key in `keystore/test.jks` is a **public test key** so testers can update in place. The Google Play version will be signed with a separate private key that is never committed.

## Contributing

Ideas, bug reports and code are welcome. Please open an issue first and include your phone model and Android version for bugs.

## License

MIT. See [LICENSE](LICENSE). Privacy: see [PRIVACY.md](PRIVACY.md).
