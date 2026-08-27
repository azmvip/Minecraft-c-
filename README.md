# PureStream Audio

تطبيق Android لتشغيل الصوت من روابط YouTube مباشرة باستخدام NewPipeExtractor و Media3/ExoPlayer.

## البناء على GitHub Actions

المشروع يحتوي على Gradle Wrapper كامل وWorkflow جاهز في:

`.github/workflows/android.yml`

الـWorkflow يستخدم:

- JDK 17
- Gradle 9.3.1
- Android SDK 36
- Build Tools 36.0.0
- `./gradlew assembleDebug`

بعد نجاح البناء ستجد ملف APK في Artifacts باسم:

`purestream-audio-debug`

## البناء محليًا

```bash
chmod +x gradlew
./gradlew assembleDebug
```

الناتج:

`app/build/outputs/apk/debug/app-debug.apk`


## GitHub Actions build

The project is pinned to a stable Android Gradle Plugin/Gradle pair for CI: AGP 8.7.3 with Gradle 8.9, using JDK 17 and Android SDK 36. The workflow builds `assembleDebug` and uploads the APK as an artifact.


## GitHub Actions toolchain

This project is intentionally pinned to a compatible Android build toolchain:
- Android Gradle Plugin: 8.7.3
- Gradle Wrapper: 8.9
- JDK: 17
- compileSdk/targetSdk: 35

Do not change only the Gradle wrapper to 9.x while keeping AGP 8.7.x. That combination is incompatible and can produce `DependencyHandler.module(Object)` / `NoSuchMethodError`.
