# AutoFlip RuneLite Plugin

AutoFlip is a RuneLite Grand Exchange assistant for AutoFlip.gg. It reads Grand Exchange state in the client, renders setup and recommendation overlays, and can send telemetry to AutoFlip.gg so the web assistant can keep account-specific flip state in sync.

## Third-party service notice

This plugin communicates with `https://autoflip.gg`. It may send plugin telemetry, Grand Exchange offer state, item setup state, local workflow events, and non-sensitive account/session identifiers used by AutoFlip.gg to connect the RuneLite client with the web assistant.

## Development

Requirements:

- Java 11
- Gradle wrapper included in this repository

Useful commands:

```powershell
.\gradlew.bat test
.\gradlew.bat runDev
```

Runtime files are written under the RuneLite plugin data directory:

```text
%USERPROFILE%\.runelite\plugin-data\autoflip
```

## Plugin Hub Submission

This repository is intended to be pushed as the public plugin source repository. The RuneLite `plugin-hub` pull request should add one marker file under `plugins/` pointing at this repository URL and the exact commit hash to review.
