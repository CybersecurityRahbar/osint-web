# OSINT Workbench

**Offline-first Android OSINT workbench** — a native Kotlin + Jetpack Compose application for collecting public domain intelligence, storing evidence locally, and exploring relationships between discovered entities.

> Current status: early prototype (0.1.0). The repository is being built in small, testable increments. The first implementation is passive-only; it does not perform port scanning, exploitation, credential attacks, or social-media scraping.

## Current implementation

- Arabic-first, dark intelligence-dashboard UI with five bottom-navigation sections.
- Local SQLite store for entities, relationships, findings, scan history, and case workspaces.
- Passive DNS lookups over HTTPS through Google Public DNS for A, AAAA, NS, MX, TXT, and CAA records.
- Optional certificate-transparency name discovery through the public crt.sh endpoint.
- Evidence cards showing source, confidence estimate, and source URL.
- Relationship graph based only on relationships stored from collected results.
- Local case/workspace creation with a scope and notes field.
- Network calls are asynchronous and DNS record queries are launched concurrently.
- GitHub Actions workflow tests the project and uploads a debug APK artifact.

## Architecture

- **UI:** Kotlin, Jetpack Compose, Material 3.
- **Local data:** SQLite via `SQLiteOpenHelper`; no backend service is required.
- **Collection:** small isolated passive-source engine with request timeouts and error reporting.
- **Analysis:** deterministic local normalization and evidence linking; no downloaded LLM or large bundled data set.
- **Testing:** unit tests for domain normalization and validation.

## Build

Requirements: JDK 17 and Android SDK API 36. The Gradle Wrapper downloads Gradle 9.8.1 automatically on first use.

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

The generated debug APK is:

`app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions attaches `osint-workbench-debug-apk` to a successful workflow run. This is a debug build, not a release-signed production APK.

## Use responsibly

Only investigate domains and assets you own or are explicitly authorized to assess. Passive results may be incomplete, delayed, or inaccurate. A technology fingerprint or certificate name is not proof that an asset is vulnerable or controlled by a particular party.

The app currently stores data in local SQLite but **does not yet encrypt the database at rest**. Avoid placing highly sensitive secrets in this prototype. Encrypted export, database protection, schema migration tests, and broader source connectors remain planned work.

## Project context

See [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for the cumulative project requirements, design decisions, and next steps.
