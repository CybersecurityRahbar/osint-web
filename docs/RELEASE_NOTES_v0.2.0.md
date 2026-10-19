# OSINT Workbench v0.2.0 — Prototype release notes

This release incorporates the first user-test feedback and expands the initial domain-only prototype.

## Changes
- Cloudflare and Google DNS-over-HTTPS fallback for A, AAAA, NS, MX, TXT, and CAA.
- Optional public certificate-transparency, RDAP registration metadata, Internet Archive Wayback snapshots, and passive HTTPS page/header metadata.
- Public research screen for names, usernames, phone numbers, and email identifiers.
- In-app GitHub public user search and Wikidata entity search. Other search engines/social sources are opened through user-selected public search links rather than auto-scraped.
- News search through GDELT with Google News RSS fallback.
- Local scan-history payloads for independent HTML/JSON reports.
- Professional Arabic RTL HTML reports for one operation or a full workspace.
- JSON workspace backup export and additive restore.
- Relationship view that labels the reference entity, relation type, source, and confidence.

## Known limitations
- Prototype only; no active Nmap-like port scanning or SpiderFoot-equivalent breadth of modules.
- Data sources require network access and may be blocked or unavailable in some regions; a VPN may improve connectivity but is not guaranteed to fix every source.
- Phone/email/social research is not guaranteed to identify the owner of an identifier.
- SQLite is still not encrypted at rest. Avoid storing high-sensitivity secrets.
- CI verification proves compilation and the unit tests only. The owner must test network availability and HTML/JSON flows on the target Android phone.
- GitHub-hosted debug APKs can have different signing certificates between builds. The v0.2.0 debug certificate differs from the prior v0.1.0 artifact; Android may refuse to update it in place. If old local results matter, do not uninstall v0.1.0 until a migration/backup path is secured.

## Version
- Application ID: `com.cyberphantom.osintworkbench`
- versionCode: `2`
- versionName: `0.2.0`
- Android minimum SDK: 26
