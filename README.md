# Omalauncher Lite

A lightweight Android home launcher focused on the essentials.

## Included
- Android home screen and app drawer
- App search
- Android AppWidgetHost support
- On-device widget catalogue for widgets supplied by installed apps
- Dark and light appearance settings

## Removed from the Lite build
- AI coding agents and agent integrations
- Arch Linux / proot runtime and package manager
- Terminal and Linux desktop windows
- Wayland compositor and native Linux bridges
- Voice-agent runtime and its speech model dependency

The Lite build uses a dedicated source set (app/src/lite/kotlin) and does not compile or package the original Linux/agent implementation.

## Build
- Debug APK: ./gradlew :app:assembleFossDebug
- Release APK: ./gradlew :app:assembleFossRelease

GitHub Actions builds the debug APK on pushes to main and publishes an APK plus checksum for version tags.

## Widget catalogue
The catalogue lists Android widgets exposed by apps already installed on the device. It uses Android's AppWidgetHost API and the provider app's own widget implementation; it does not download third-party widget packages.
