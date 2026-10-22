# OSINT Workbench

**OSINT Workbench** is an offline-first Android OSINT workbench for collecting publicly available information, preserving evidence, and building explainable technical relationships without a private backend or a bundled large language model.

> **Prototype status: v0.5.1-preview.** It is an actively developed prototype, not a complete replacement for SpiderFoot, Maltego, or a dedicated vulnerability scanner.

## Domain intelligence

- DNS over HTTPS for A, AAAA, NS, MX, TXT, and CAA records using Cloudflare and Google Public DNS fallback.
- Certificate Transparency lookup through crt.sh.
- Optional RDAP registration metadata.
- Internet Archive Wayback CDX snapshot discovery, retry of transient failures, a secondary Availability API fallback, and a visible no-snapshot / source-unavailable state.
- Separate Wayback CDX media lookup for archived images, with a direct replay-image URL and a source snapshot URL.
- Public indexed-page discovery for `site:domain` and domain-related PDF queries, in addition to DNS, RDAP, certificate, and HTML metadata.
- Best-effort extraction of social-preview / public image URLs from captured Wayback pages and the live site's public HTML.
- HTTPS status, redirects, selected response headers, HTML title and generator hints.
- Public discovery metadata from `robots.txt`, `sitemap.xml`, and `security.txt`, with bounded reads and source URLs.
- Approximate geolocation/ISP metadata for a small sample of resolved IPs using two public providers. This can locate a hosting provider or CDN, not a physical person or reliably the actual origin server.
- Optional active checks (off by default) for a fixed list of 23 TCP ports on at most two resolved IPs and seven common public exposure indicators. Enable them only for assets you own or are authorized to assess. They do not exploit services, authenticate, bypass controls, detect arbitrary custom backdoors, or prove a system is secure when no finding appears.
- Archive/public-image thumbnails where available; result cards retain source attribution and can open the underlying source.
- Per-source warnings and timeouts. A missing record is not proof of absence, and an archive capture does not mean the page is online today.

## Automatic public identifier search

- Search modes for names, usernames, phone numbers, and email addresses.
- Expands queries into multiple public-index searches (including Reddit-indexed pages and public caller-report directories for numbers).
- Attempts live public-index retrieval across DuckDuckGo HTML/Lite, Bing, Google, Brave, Mojeek, Qwant JSON, SearXNG JSON, Startpage, Ecosia and Yandex using provider-specific parsers and redirect normalization. One provider's block or changed markup is isolated from the rest.
- Tries image indexes independently: Bing Images metadata (`murl`/`turl`/`purl`), SearXNG image results, and Wikimedia Commons. The visible thumbnail links to the indexed source page; a thumbnail is not proof of identity or context.
- Keeps source-level warnings and bounded concurrency/timeouts. Public engines and SearXNG instances may disable HTML/JSON access, so the app reports the failed provider rather than implying that a zero-result search proves the information is absent.
- Image results are controlled by a single in-app toggle. When enabled, identifier search also calls Bing Images and SearXNG image search, alongside Wikimedia Commons; news feeds and page metadata may supply additional images. This remains a multi-source best-effort index, not a universal image index.
- Ranks matches deterministically: exact phone digits, exact email/username hits and title/token overlap rise above vague results. The relevance score is explicitly heuristic, not a probability.
- Combines available API results from GitHub public users search and Wikidata for suitable queries.
- Normalizes international-format phone numbers offline with Google's libphonenumber metadata and displays numbering-plan information only. It does **not** reveal a subscriber, confirm that a number is active, or identify its owner.
- Provider links remain available as fallbacks. Some result pages may be blocked, rate-limited, or changed; failure warnings remain visible.
- Does not authenticate to social networks, bypass access controls, or query private password/breach databases.

## Multi-source news

- Concurrent, bounded search through GDELT DOC API, Arabic/Saudi and English/US Google News RSS editions, Bing News RSS, and SearXNG News JSON. If those feeds all return no stories, the engine makes a smaller publisher-scoped Google News fallback for Reuters, BBC, Al Jazeera Arabic, and The Guardian.
- GDELT requests retry a small number of transient failures and report non-JSON/HTTP diagnostics rather than silently discarding the API error page.
- Each article retains a publisher label, source domain, click-through URL, publication date when provided, language/country metadata, and an image URL when the source provides one.
- Source failures are isolated; the screen can show partial results plus a warning naming failed or empty sources. Bing News RSS is an undocumented best-effort feed and may change without notice.
- A global image-results toggle hides or shows result/news thumbnails and disables image URL collection when off.
- No news provider guarantees comprehensive coverage, and image availability varies by feed.

## Evidence and local intelligence

- SQLite persists entities, evidence, scan payloads, relationships, and case workspaces locally.
- Relationship types distinguish DNS resolution, nameservers, mail exchanges, subdomains, archive captures, redirects, images, registration metadata, and observed web properties.
- SQLite schema v3 preserves the same relation when it is supported by multiple recorded sources instead of overwriting the prior source. The graph groups matching edges and displays the stored sources and observations.
- Repeated generic values (for example, an HTTP status or a page title) are scoped to the observed target to reduce accidental global “hub” relationships.
- An explainable, offline rule engine highlights shared-IP observations, stale/weak evidence, missing data, and suggested next verification steps. It does not invent identity links from name similarity.
- Confidence values are heuristic source-quality indicators, not calibrated statistical probabilities.

## Privacy and limitations

- No private backend, bundled large LLM, or huge local breach database is required.
- Live collection needs network access. Carrier, country, DNS, source, and rate-limit restrictions may still cause empty results.
- Automatic identifier lookup is based on public indexes and permitted public APIs, not on a universal, authoritative phone-owner database. A zero-result response does not prove that information does not exist.
- The app's SQLite database **is not encrypted at rest yet**; avoid storing highly sensitive data in the prototype.
- GitHub Actions builds the **v0.5.1 stable-signed preview channel** with application ID `com.cyberphantom.osintworkbench.preview051`. Future preview builds keep this same application ID and signing certificate, allowing Android to update the app in place without clearing its WebView session or local app data. The older `.preview050` prototype used a temporary CI key, so this first transition is intentionally installed side-by-side; keep that older app until the new one is verified and any local data has been exported/imported. Configure the two GitHub Actions secrets `PREVIEW_KEYSTORE_BASE64` and `PREVIEW_KEYSTORE_PASSWORD` as documented in [Stable preview signing](docs/STABLE_PREVIEW_SIGNING.md). CI does not publish an APK artifact until stable signing is configured. The downloadable artifact is a ZIP containing `app-debug.apk`; install the APK, not the ZIP itself.
- CI verifies compilation/tests and APK packaging; it does not validate each source's live response on every carrier or phone.

## Search-engine reference

`ALKASER_OSINT.py` in this repository was reviewed as an implementation reference for provider-specific adapters, bounded parallel requests, image metadata extraction, dork expansion, and Wayback CDX media lookups. The Android app does not execute the Python script or bundle its Python dependencies; equivalent Kotlin adapters are implemented separately and remain subject to each provider's access rules.

## Build

Requirements: JDK 17 and Android SDK API 36. The Gradle Wrapper downloads the pinned Gradle version on first use:

```bash
./gradlew testDebugUnitTest
./gradlew assembleDebug
```

APK output: `app/build/outputs/apk/debug/app-debug.apk`

GitHub Actions runs unit tests and builds the preview APK. It uploads `osint-workbench-v0.5.1-preview-apk` only when the stable signing secrets are configured; otherwise it deliberately publishes no installable artifact.

## Cumulative context

See [PROJECT_CONTEXT.md](PROJECT_CONTEXT.md) for the user's requirements, implementation decisions, CI results, limitations, and next steps.
