# OSINT Workbench

**OSINT Workbench** is an offline-first Android application for collecting public information, organizing evidence, exploring technical relationships, and exporting readable investigation reports.

> Prototype status: v0.2 development branch. It is not a replacement for Nmap's active network scanning or SpiderFoot's wide plugin ecosystem. This app currently focuses on passive/public sources and selected public APIs.

## What is being added in this iteration

### Domain intelligence
- DNS over HTTPS for A, AAAA, NS, MX, TXT, and CAA records with Cloudflare and Google resolver fallback.
- Optional certificate-transparency search through crt.sh.
- Optional public RDAP registration status/dates/registrar metadata.
- Optional Internet Archive Wayback snapshots.
- HTTPS status, redirect, HTML title/generator hints, selected response headers, and security-header observations.
- Source warnings, timeouts, URL provenance, and confidence estimates with explicit limits.

### Public identifier research
- Search modes for a name, username, phone number, and email.
- In-app API results from GitHub public users search and Wikidata.
- Provider-specific public search links for other engines and indexed social pages. Links open only when the user taps them; the app does not claim that a search link confirms an account or its owner.
- Phone/email modes do not perform subscriber lookup, private-data extraction, or breach-password searches.

### News
- Search through GDELT DOC API with an Arabic Google News RSS fallback.
- Store result titles, source domains, URLs, timestamps/language/country metadata where available.
- Network/region restrictions may prevent one or both sources from returning data.

### Local evidence, relations, and reports
- SQLite stores entities, evidence, relationships, scans, and case workspaces.
- Relationship view groups observed entities under a reference entity and displays relation meaning, source, and confidence; it is not a claim of personal association.
- Each search stores its own JSON payload, allowing a standalone HTML or JSON report for that specific search.
- Full workspace HTML and JSON exports.
- JSON backup import is additive and deduplicates matching records; it does not delete existing records.
- Android's document picker lets the user choose where an export is saved; a report can also be shared using the Android share sheet.
- The Settings page shows the private database path. It is intentionally stored in the app's internal database directory, not in a public Downloads folder.

## Build

Requirements: JDK 17 and Android SDK API 36. Use the included Gradle Wrapper:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

Debug APK path:

`app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions runs tests, assembles a debug APK, and uploads the `osint-workbench-debug-apk` artifact.

## Architecture
- Kotlin + Jetpack Compose / Material 3.
- Local SQLite with an additive v1→v2 schema migration that preserves older local data.
- Coroutines for network collection and background storage/export work.
- Deterministic normalization and evidence linking; no downloaded large LLM or large breach database is required.
- CI build/test workflow.

## Important limitations
- **The database is not encrypted at rest yet.** Avoid storing highly sensitive material in this prototype.
- Internet access is required for live-source queries. In some countries/carrier networks, DNS-over-HTTPS, RDAP, Wayback, GDELT, or Google News RSS may be blocked or unreliable. VPN can help reach sources, but it does not guarantee results.
- DNS/CT/archives can be incomplete or stale. A name in a certificate or archive does not prove the host is active now.
- A missing security header from one response is only an observation, not proof of a vulnerability.
- GitHub/Wikidata results are API results. Searches for phones, emails, and many social sites are explicit public web-search links rather than automatic scraping of every platform.
- Passive recon is not the same as Nmap port scanning or SpiderFoot's many modules; active scanning and broad integrations are not yet included.
- The new features still require CI validation and a test on a real Android device before calling the iteration fully verified.

## Cumulative context
See [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for decisions, implementation notes, tests, issues, and next steps.
