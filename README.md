# Kaup

> *From the Old Norse word for trade. Your business, handled.*

Kaup is a free, open-source, offline-first Point of Sale and business management
system for Android. Built for small and medium stores — retail, F&B, market
stalls — in any country, on any budget.

[![License: GPL v3](https://img.shields.io/badge/License-GPLv3-blue.svg)](LICENSE)
[![Build Status](https://img.shields.io/github/actions/workflow/status/ronimuliawan/kaup/ci.yml)](https://github.com/ronimuliawan/kaup/actions)
[![F-Droid](https://img.shields.io/f-droid/v/app.kaup.android)](https://f-droid.org/packages/app.kaup.android)
[![IzzyOnDroid](https://img.shields.io/badge/IzzyOnDroid-available-green)](https://apt.izzysoft.de/fdroid/index/apk/app.kaup.android)

---

## Why Kaup

Most POS apps require a subscription, lock your data in a proprietary cloud, or
stop working the moment your internet drops. Kaup does not.

- **Offline-first** — every sale, stock movement, and receipt works with no
  internet connection, always
- **Your data, your device** — all data is stored locally in a Room database on
  your device; you own it. At-rest encryption of that database is planned and
  not yet implemented, so do not run an alpha build in a real store
- **No subscription** — free forever under GPL v3; no paywalled features
- **No vendor lock-in** — sync is optional and pluggable; use your own server,
  Supabase, Appwrite, or nothing at all
- **F-Droid clean** — no proprietary SDKs, no Firebase in the core build, no
  trackers; the `fdroid` and `github` builds are fully free software

---

## Features

### Point of Sale
- Fast item search and barcode scan to cart
- Cash, card (capture-only), split payment, and custom payment methods
- Inclusive and exclusive tax, per-item rates, mixed-rate carts
- Per-item discounts and order-level discounts
- Bluetooth ESC/POS thermal receipt printing
- Shift open and close with cash-up and variance summary
- Void and refund with manager approval

### Inventory
- Item and category management with variants and attributes
- Stock receiving, manual adjustment, and movement history
- Reorder level alerts
- Barcode label printing
- FIFO stock valuation

### Staff and Security
- Role-based access control — Owner, Manager, Cashier, Waiter
- Per-user permission overrides
- PIN and biometric authentication
- HOTP-based offline manager approval — no internet needed to authorise a
  price override or void at the counter
- Full audit log of every authorisation event

### Business Management
- Customer profiles, purchase history, loyalty points, gift cards
- Supplier management and purchase orders
- Expense tracking with receipt capture
- Quotations, sales orders, and delivery notes
- Reporting — sales, inventory, payments, expenses, gross margin
- CSV and PDF export for all reports

### Sync (Optional)
- Tier 0 — fully local, no sync, no server
- Tier 1 — self-hosted Ktor server (Docker Compose, one command)
- Tier 2 — Supabase (free tier covers most small stores)
- Tier 3 — Appwrite self-hosted

---

## Download

| Channel | Audience | Auto-update |
|---|---|---|
| [GitHub Releases](https://github.com/your-username/kaup/releases) | Everyone | Via Obtainium (recommended) or in-app |
| [IzzyOnDroid](https://apt.izzysoft.de/fdroid/index/apk/app.kaup.android) | F-Droid users wanting faster releases | F-Droid client |
| [F-Droid](https://f-droid.org/packages/app.kaup.android) | F-Droid users wanting verified builds | F-Droid client |
| Google Play *(coming soon)* | General users | Play Store |

### Recommended: Install via Obtainium

[Obtainium](https://github.com/ImranR98/Obtainium) tracks GitHub Releases and
updates Kaup automatically — no app store needed.

1. Install Obtainium from [GitHub](https://github.com/ImranR98/Obtainium/releases)
   or F-Droid
2. Add source: `https://github.com/your-username/kaup`
3. Obtainium handles all future updates

---

## Build from Source

### Requirements
- Android Studio Meerkat or later
- JDK 17 or later
- Android SDK — API 26 minimum, latest stable target

```bash
# Clone
git clone https://github.com/your-username/kaup.git
cd kaup

# Build — choose your flavor
./gradlew :android-app:assembleGithubDebug     # GitHub / sideload build
./gradlew :android-app:assembleFdroidDebug     # F-Droid build
./gradlew :android-app:assemblePlaystoreDebug  # Play Store build

# Run unit tests (no emulator required)
./gradlew :shared-kmp:allTests
./gradlew test
```

See [CONTRIBUTING.md](CONTRIBUTING.md) for the full development setup guide.

---

## Documentation

| Document | Description |
|---|---|
| [ROADMAP.md](ROADMAP.md) | MoSCoW prioritisation, milestones, testing strategy |
| [CONTRIBUTING.md](CONTRIBUTING.md) | Development setup, module rules, PR requirements |
| [SECURITY.md](SECURITY.md) | Vulnerability reporting and security policy |
| [docs/architecture.md](docs/architecture.md) | Data flow, sync lifecycle, auth flow |
| [docs/modules.md](docs/modules.md) | Every module — what it owns and what it does not |
| [docs/adr/](docs/adr/) | Architecture Decision Records (ADR-001 to ADR-018) |
| [docs/personas.md](docs/personas.md) | User personas that drive product decisions |
| [docs/setup-tier1.md](docs/setup-tier1.md) | Self-hosted Ktor server setup guide |
| [docs/setup-supabase.md](docs/setup-supabase.md) | Supabase sync backend setup guide |
| [docs/setup-appwrite.md](docs/setup-appwrite.md) | Appwrite sync backend setup guide |
| [docs/design/design-system.md](docs/design/design-system.md) | Visual design language and component guidelines |

### Architecture Decision Records

| ADR | Decision |
|---|---|
| [ADR-001](docs/adr/ADR-001-offline-first-architecture.md) | Offline-first with Room + WorkManager sync queue |
| [ADR-002](docs/adr/ADR-002-event-sourced-inventory.md) | Event-sourced inventory via stock movement log |
| [ADR-003](docs/adr/ADR-003-kmp-shared-domain-module.md) | Kotlin Multiplatform shared domain module |
| [ADR-004](docs/adr/ADR-004-pluggable-sync-backend.md) | Pluggable sync backend interface |
| [ADR-005](docs/adr/ADR-005-hotp-offline-authorization.md) | HOTP-based offline manager authorization |
| [ADR-006](docs/adr/ADR-006-gpl-v3-license.md) | GPL v3 license |
| [ADR-007](docs/adr/ADR-007-feature-flag-module-system.md) | Feature flag module system |
| [ADR-008](docs/adr/ADR-008-multi-module-android-architecture.md) | Multi-module Android architecture |
| [ADR-009](docs/adr/ADR-009-rbac-permission-system.md) | Role-based access control permission system |
| [ADR-010](docs/adr/ADR-010-processing-modes.md) | Processing modes — Standalone, Assisted, Server-First |
| [ADR-011](docs/adr/ADR-011-notification-system.md) | Pluggable notification backend |
| [ADR-012](docs/adr/ADR-012-kanban-development-methodology.md) | Kanban development methodology |
| [ADR-013](docs/adr/ADR-013-fdroid-izzydroid-distribution.md) | F-Droid and IzzyOnDroid distribution |
| [ADR-014](docs/adr/ADR-014-in-app-update-mechanism.md) | In-app update mechanism for GitHub builds |
| [ADR-015](docs/adr/ADR-015-payment-gateway-architecture.md) | Payment gateway — capture-only default with pluggable adapters |
| [ADR-016](docs/adr/ADR-016-multi-location-schema.md) | Multi-location schema from day one |
| [ADR-017](docs/adr/ADR-017-receipt-email.md) | Receipt email — Android intent default with optional SMTP |
| [ADR-018](docs/adr/ADR-018-room-migration-strategy.md) | Room database migration strategy |

---

## Contributing

Contributions are very welcome — code, documentation, translations, and
country-specific payment gateway adapters.

Please read [CONTRIBUTING.md](CONTRIBUTING.md) before opening a PR.

For large changes, open a discussion issue first. For bugs and small fixes,
go straight to a PR.

**Areas actively looking for community contributions:**
- Payment gateway adapters (QRIS, SumUp, Square, GoPay, OVO, Dana, and others)
- Translations — see [Translation Contributions](CONTRIBUTING.md#translation-contributions)
- Country-specific tax presets
- Bug reports from real store use

---

## License

Kaup is free software: you can redistribute it and/or modify it under the terms
of the [GNU General Public License v3.0](LICENSE) as published by the Free
Software Foundation.

The full license text is in [LICENSE](LICENSE). The rationale for choosing GPL v3
is documented in [ADR-006](docs/adr/ADR-006-gpl-v3-license.md).

---

## Acknowledgements

Built with:
[Jetpack Compose](https://developer.android.com/compose) ·
[Kotlin Multiplatform](https://kotlinlang.org/docs/multiplatform.html) ·
[Room](https://developer.android.com/training/data-storage/room) ·
[Hilt](https://dagger.dev/hilt/) ·
[WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) ·
[Ktor](https://ktor.io) ·
[Material 3](https://m3.material.io)