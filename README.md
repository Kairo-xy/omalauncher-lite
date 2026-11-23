# Omalauncher Lite

A lightweight Android launcher fork inspired by Omalauncher.

## Goals

- Android home screen, app drawer, and app search
- Widgets and the widget marketplace
- Theme and wallpaper customization
- Keep the launcher fast, simple, and low on background/resource usage
- No AI agents, agent integrations, Linux/proot environment, terminal, or desktop compositor

This fork is being simplified in stages. The current codebase still contains upstream Linux/agent components; removals are being done incrementally so the app stays buildable.

## Build

```bash
./gradlew :app:assembleFossDebug
```

## Automated builds

GitHub Actions runs tests and builds on pushes to `main` and pull requests. Push builds publish an APK artifact. Releases are published through the repository's release workflow.

## Upstream

This project is based on [SimonSchubert/omalauncher](https://github.com/SimonSchubert/omalauncher). See the upstream license and third-party notices in this repository.
