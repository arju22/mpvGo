# adblock-jni

JNI wrapper around [Brave's adblock-rust](https://github.com/brave/adblock-rust) (MPL-2.0), used by
the Go browser (`app.arju.mpvgo.webbrowser.adblock`).

The built `libadblock_jni.so` (arm64-v8a) is committed in `app/src/main/jniLibs/arm64-v8a`, so a
normal Gradle build doesn't need Rust. Rebuild it only after changing this crate or its
dependencies:

```powershell
powershell -File native\adblock-jni\build.ps1
```

Needs rustup with the `aarch64-linux-android` target, `cargo-ndk`, and the Android NDK
(`ANDROID_NDK_HOME`, or the newest NDK under `ANDROID_HOME`).

Filter lists aren't bundled. The app downloads them when the browser is first opened, compiles
them into one binary snapshot (`files/adblock/adblock_engine.dat`) and updates them weekly.
