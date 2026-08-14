# Module Guide

- **Version**: 1.0
- **Date**: 2026-03-14

---

## Table of Contents

- [How to Read This Guide](#how-to-read-this-guide)
- [shared-kmp](#shared-kmp)
- [core-ui](#core-ui)
- [core-data](#core-data)
- [core-network](#core-network)
- [feature-auth](#feature-auth)
- [feature-pos](#feature-pos)
- [feature-inventory](#feature-inventory)
- [feature-customers](#feature-customers)
- [feature-suppliers](#feature-suppliers)
- [feature-expenses](#feature-expenses)
- [feature-sales](#feature-sales)
- [feature-reports](#feature-reports)
- [feature-settings](#feature-settings)
- [feature-restaurant](#feature-restaurant)
- [android-app](#android-app)
- [ktor-server](#ktor-server)

---

## How to Read This Guide

Each module section answers four questions:

1. **Owns** — what this module is solely responsible for
2. **Does NOT own** — what this module must never touch directly
3. **Permissions checked** — which `Permission` constants gate UI or actions
4. **Room tables** — which tables this module reads from and writes to

If you are adding a feature and are unsure which module it belongs to, find
the section whose **Owns** list is the closest match. If a feature spans two
modules, it belongs in the module that initiates the action — the second
module exposes its data via `:core-data` repository interfaces.

---

## shared-kmp

### Owns
- All business logic that must run identically on Android, Ktor server,
  and future web targets
- `TaxResolver` — applies tax rates to line items (inclusive/exclusive)
- `InventoryEngine` — computes current stock from movement event log
- `SalesCalculator` — computes subtotals, discounts, tax, and totals
- `ConflictResolver` — replays event logs to resolve multi-device conflicts
- `HOTPGenerator` — generates and validates HOTP codes (RFC 4226)
- `AnalyticsAggregator` — aggregates raw transaction data into report summaries
- All shared data models, DTOs, and enums used across modules
- `SyncBackend` interface contract
- `NotificationBackend` interface contract

### Does NOT own
- Any Android-specific imports (`android.*`, `androidx.*`)
- Any UI code
- Any database access (Room is Android-only; Exposed is server-only)
- Any network calls

### Permissions checked
None — this module contains no UI and no access control logic.

### Room tables
None — this module has no database dependency.

---

## core-ui

### Owns
- Shared Compose component library (buttons, cards, dialogs, text fields,
  bottom sheets, snackbars, loading indicators)
- App theme — color scheme, typography, shape system (Material 3 Expressive)
- Common UI utilities — keyboard visibility, screen size classes, window insets
- The `ManagerApprovalOverlay` composable (used by multiple feature modules)

### Does NOT own (settled 2026-08-14, #233)
- The `LockScreen` composable. This file used to place its shell here with the
  logic in `:feature-auth`. Nothing outside `:feature-auth` composes the lock
  screen, so the split bought nothing and cost a round trip through two modules
  for every change. The boundary rule exists to stop `feature-*` modules
  depending on each other, and a screen only one feature uses does not test it.
  `ManagerApprovalOverlay` stays here precisely because POS, inventory and
  settings all raise it.

### Does NOT own
- Any business logic
- Any database access
- Any navigation logic
- Any permission checks

### Permissions checked
None — UI components are dumb. Permission checks happen in the calling
feature module before a composable is included in the composition.

### Room tables
None.

---

## core-data

### Owns
- Room database setup and configuration
- All Room entity definitions
- All Room DAO interfaces
- Repository interfaces (implemented by feature modules)
- `PrinterService` abstraction — `printReceipt()`, `printLabel()`
- Backup and restore file I/O logic (AES encryption, SAF integration)
- Media file path management — write, read, delete helpers

### Does NOT own
- Business logic (belongs in `:shared-kmp`)
- UI (belongs in `:core-ui` or feature modules)
- Sync scheduling (belongs in `:core-network`)
- Printer driver implementation (belongs in the concrete `PrinterService`
  implementation registered via DI in `:android-app`)

### Permissions checked
None directly — permission checks happen in feature modules before
repository calls are made.

### Room tables (owns all table definitions)
`transactions`, `transaction_line_items`, `stock_movements`, `items`,
`item_variants`, `item_attributes`, `categories`, `customers`,
`suppliers`, `purchase_orders`, `expenses`, `expense_categories`,
`sales_orders`, `delivery_notes`, `quotations`, `users`, `user_permissions`,
`sessions`, `hotp_secrets`, `override_logs`, `shift_records`,
`tables` (restaurant), `table_orders` (restaurant), `sync_queue`,
`backup_log`, `notification_log`

---

## core-network

### Owns
- Concrete `SyncBackend` implementations:
  `NoSyncBackend`, `KtorBackend`, `SupabaseBackend`, `AppwriteBackend`
- WorkManager job definitions and scheduling logic
- Sync queue management — batching, retry policy, exponential backoff
- Concrete `NotificationBackend` implementations:
  `LocalNotificationBackend`, `NtfyBackend`, `FcmBackend` (Play Store only)
- Notification event routing — maps app events to the correct backend

### Does NOT own
- Business logic (belongs in `:shared-kmp`)
- UI (belongs in `:core-ui` or feature modules)
- Database schema (belongs in `:core-data`)
- The `SyncBackend`, `NotificationBackend` and `UpdateChecker` interface
  definitions. Those belong in `:shared-kmp`, under `domain/sync`,
  `domain/notification` and `domain/update`. Note there is no
  `sync-contracts` source set: CONTEXT.md and several ADRs name one, but the
  interfaces live in the packages above.
- `NoSyncBackend`. It is pure no-op logic with a test that runs on every
  target, so it lives in `:shared-kmp` as ADR-004's built-in default and is
  provided by `NetworkModule`. The other backends are Android implementations
  and do belong here.
- The `github` flavor's `GitHubUpdateChecker`. ADR-014 assigns it here, but
  `:core-network` is compiled into every flavor, so `kmp-app-updater` would
  ship in the Play Store build. It lives in `android-app/src/github/` (#236).

### Permissions checked
None directly.

### Room tables
Reads: `sync_queue`, `transactions`, `stock_movements` (for batching)
Writes: `sync_queue` (status updates: PENDING → SYNCING → SYNCED / FAILED)

---

## feature-auth

### Owns
- Lock Screen UI — staff profile card grid
- PIN entry and biometric enrollment UI
- Session lifecycle — `SessionManager` singleton (in-memory only)
- User account management UI — create, edit, delete staff accounts
- Role assignment UI — assign Owner / Manager / Cashier / Crew
- Permission override UI — grant up or restrict down per user
- HOTP key generation and QR provisioning UI
- Override code generation UI (manager side)
- Override code entry and validation UI (staff side)
- Onboarding wizard — all 7 steps
- Auto-lock timeout logic

### Does NOT own
- Receipt printing (belongs in `:feature-pos`)
- Backend configuration (belongs in `:feature-settings`)
- Any report data (belongs in `:feature-reports`)

### Permissions checked
- `USERS_VIEW` — view staff list
- `USERS_ADD` — add new staff accounts
- `USERS_EDIT` — edit staff details and permissions
- `USERS_DELETE` — remove staff accounts
- `SETTINGS_FEATURE_FLAGS` — enable/disable optional modules

### Room tables
Reads: `users`, `user_permissions`, `hotp_secrets`
Writes: `users`, `user_permissions`, `hotp_secrets`, `override_logs`

---

## feature-pos

### Owns
- Sale register UI — cart, line items, quantity, discount entry
- Payment method selection UI — cash, card, split payment
- Change calculation display
- Shift open and close UI — cash-up, variance summary
- Receipt composition and print trigger — calls `PrinterService`
- Receipt email trigger
- Refund flow UI — including optional video/image capture
- Fulfillment status display for pre-orders

### Does NOT own
- Item lookup and search (reads from `:core-data` repositories owned by
  `:feature-inventory`)
- Customer lookup (reads from `:core-data` repositories owned by
  `:feature-customers`)
- Tax calculation logic (belongs in `:shared-kmp/domain/TaxResolver`)
- Discount validation logic (belongs in `:shared-kmp/domain/SalesCalculator`)
- Stock movement writes are triggered here but executed via
  `:core-data` repository — `:feature-pos` does not write to Room directly

### Permissions checked
- `POS_OPEN_SHIFT` — open a shift
- `POS_CLOSE_SHIFT` — close and reconcile a shift
- `POS_APPLY_DISCOUNT` — apply any discount
- `POS_DISCOUNT_ABOVE_X` — apply discount above manager-configured threshold
- `POS_VOID_TRANSACTION` — void a completed transaction
- `POS_ISSUE_REFUND` — issue a refund
- `POS_OVERRIDE_PRICE` — manually override an item price

### Room tables
Reads: `items`, `item_variants`, `customers`, `shift_records`
Writes: `transactions`, `transaction_line_items`, `stock_movements`,
        `shift_records`

---

## feature-inventory

### Owns
- Item list and detail UI
- Category management UI
- Item variant and attribute management UI
- Barcode scan UI (CameraX integration)
- Stock receiving UI — log incoming stock from supplier
- Stock adjustment UI — manual correction with reason
- Stock transfer UI — move stock between locations (Could Have)
- Reorder level configuration per item
- Item kit / bundle configuration (Should Have)
- Barcode label printing trigger — calls `PrinterService`

### Does NOT own
- Current stock level computation (belongs in
  `:shared-kmp/domain/InventoryEngine`)
- Purchase order management (belongs in `:feature-suppliers`)
- Stock valuation (Could Have — computation belongs in `:shared-kmp`)

### Permissions checked
- `INVENTORY_VIEW` — view item list and stock levels
- `INVENTORY_ADD_ITEM` — add new items
- `INVENTORY_EDIT_ITEM` — edit existing items
- `INVENTORY_DELETE_ITEM` — delete items
- `INVENTORY_RECEIVE_STOCK` — log incoming stock
- `INVENTORY_TRANSFER_STOCK` — transfer stock between locations

### Room tables
Reads: `items`, `item_variants`, `item_attributes`, `categories`,
       `stock_movements`, `suppliers`
Writes: `items`, `item_variants`, `item_attributes`, `categories`,
        `stock_movements`

---

## feature-customers

### Owns
- Customer list and detail UI
- Customer search UI
- Loyalty program configuration and points balance display (Should Have)
- Gift card issuance and redemption UI (Should Have)
- Customer purchase history view

### Does NOT own
- Transaction records (owned by `:feature-pos` / `:core-data`)
- Marketing integrations (Mailchimp — Could Have, reads customer list
  via `:core-data` repository)

### Permissions checked
- `CUSTOMERS_VIEW` — view customer list
- `CUSTOMERS_ADD` — add new customers
- `CUSTOMERS_EDIT` — edit customer records
- `CUSTOMERS_DELETE` — delete customers

### Room tables
Reads: `customers`, `transactions` (for purchase history)
Writes: `customers`

---

## feature-suppliers

### Owns
- Supplier list and detail UI
- Purchase order creation and management UI (Should Have)
- Supplier quotation UI (Could Have)
- Landed cost voucher UI (Could Have)

### Does NOT own
- Stock receiving (triggered from `:feature-inventory`, linked to
  purchase order via foreign key)
- Payment processing for purchase orders (post Year 1 — accounting module)

### Permissions checked
- `INVENTORY_RECEIVE_STOCK` — required to link a stock receiving to a PO
- No dedicated supplier permissions in v1 — access governed by role defaults

### Room tables
Reads: `suppliers`, `purchase_orders`, `items`
Writes: `suppliers`, `purchase_orders`

---

## feature-expenses

### Owns
- Expense entry UI — amount, category, date, notes, optional receipt photo
- Expense category management UI
- Expense list and filter UI

### Does NOT own
- Financial reporting on expenses (belongs in `:feature-reports`)
- Accounting entries for expenses (post Year 1)

### Permissions checked
- No dedicated expense permissions in v1 — access governed by role defaults
  (Manager and above by default)

### Room tables
Reads: `expenses`, `expense_categories`
Writes: `expenses`, `expense_categories`

---

## feature-sales

### Owns
- Quotation creation and management UI (Should Have)
- Sales order creation and status tracking UI (Should Have)
- Delivery note creation and fulfillment tracking UI (Should Have)
- Commission tracking per staff member UI (Could Have)

### Does NOT own
- Payment collection on a sales order (handed off to `:feature-pos`
  when the customer pays — the POS transaction references the sales order ID)
- Inventory reservation logic (belongs in `:shared-kmp/domain/InventoryEngine`)

### Permissions checked
- No dedicated sales order permissions in v1 — access governed by role
  defaults (Manager and above by default)

### Room tables
Reads: `sales_orders`, `delivery_notes`, `quotations`, `items`, `customers`
Writes: `sales_orders`, `delivery_notes`, `quotations`

---

## feature-reports

### Owns
- Sales report UI — by date range, by cashier, by item
- Inventory/stock report UI — current levels, movement history
- Payments summary report UI — breakdown by payment method per shift
- Expense report UI (Should Have)
- Gross margin / cost price report UI (Should Have)
- Advanced analytics dashboard with charts (Could Have)
- CSV export trigger for all report types
- PDF export trigger (Should Have)
- Report date filter and search UI

### Does NOT own
- Data aggregation logic (belongs in
  `:shared-kmp/domain/AnalyticsAggregator`)
- Raw transaction data (reads via `:core-data` repositories)
- Financial statements (post Year 1 — accounting module)

### Permissions checked
- `REPORTS_VIEW_SALES` — view sales reports
- `REPORTS_VIEW_INVENTORY` — view inventory/stock reports
- `REPORTS_VIEW_FINANCIAL` — view financial and margin reports
- `REPORTS_EXPORT` — export any report to CSV or PDF

### Room tables
Reads: `transactions`, `transaction_line_items`, `stock_movements`, `items`,
       `categories`, `customers`, `expenses`, `shift_records`, `users`
Writes: None — reports are read-only

---

## feature-settings

### Owns
- Backend tier configuration UI (Tier 0–3 selection, server URL, API keys)
- Processing mode selection UI (Standalone / Assisted / Server-First)
- Language selection UI
- Feature flag toggle UI (enable/disable optional modules)
- Video compression and duration settings UI
- Local file retention policy UI
- Housekeeping menu UI — synced file list with size summary, safe delete
- Manual "Sync Now" trigger
- Full database backup UI — destination picker, encryption password
- Restore from backup UI — file picker, validation summary
- CSV export trigger (delegates to `:feature-reports` for data, owns the
  export destination and file naming logic)
- Backup schedule configuration UI
- Analytics and crash reporting opt-in UI — shows explicit pros/cons warning
- Receipt template and footer editor (Could Have)

### Does NOT own
- User and permission management (belongs in `:feature-auth`)
- Tax rate configuration — tax is configured per item in `:feature-inventory`
  and per transaction in `:feature-pos`; there is no global tax settings screen
  because tax is user-defined and per-jurisdiction

### Permissions checked
- `SETTINGS_BACKEND` — change sync backend configuration
- `SETTINGS_FEATURE_FLAGS` — enable/disable optional modules
- `SETTINGS_HOUSEKEEPING` — run file cleanup
- `SETTINGS_TAX` — reserved for future per-store tax default configuration

### Room tables
Reads: `backup_log`, `sync_queue` (for housekeeping size estimates)
Writes: `backup_log`
DataStore: reads and writes all feature flags and app settings

---

## feature-restaurant

🔒 Only active when `FeatureFlags.restaurantEnabled = true`

### Owns
- Table grid UI — visual layout of all tables with status indicators
- Table creation, renaming, and merge UI
- Order assignment to table UI
- Table order status display — open, served, billed
- Split bill UI
- Order routing per table (which items go to which preparation area)

### Does NOT own
- The POS sale register (a table's bill is processed through `:feature-pos`
  when the customer pays — the transaction references the table order ID)
- Kitchen Display System (KDS — post Year 1)
- Item management (belongs in `:feature-inventory`)

### Permissions checked
- Crew role has access to table management and order entry by default
- `POS_VOID_TRANSACTION` required to cancel a table order after it is placed
- No additional restaurant-specific permissions in v1

### Room tables
Reads: `tables`, `table_orders`, `items`, `item_variants`
Writes: `tables`, `table_orders`

---

## android-app

### Owns
- Application class and Hilt component setup
- Root navigation graph — registers all feature nav graphs
- Feature nav graph conditional registration based on `FeatureFlags`
- Bottom navigation bar or navigation rail layout
- Deep link handling
- DI bindings — wires concrete implementations to interfaces
  (e.g., `KtorBackend` bound to `SyncBackend` based on Settings)

### Does NOT own
- Any business logic
- Any UI screens (all screens belong in feature modules)
- Any database access

### Permissions checked
None — permission checks happen inside feature modules.

### Room tables
None directly — Room database instance is provided via Hilt from `:core-data`.

---

## ktor-server

### Owns
- HTTP API route definitions
- JWT authentication and token issuance
- HTTPS configuration
- Rate limiting and input sanitization
- Request validation
- Sync endpoint — receives batched records from Android clients,
  runs `ConflictResolver` from `:shared-kmp`, persists to PostgreSQL
- File upload endpoint — receives media files, stores in configured bucket
- Pull endpoint — returns updates since a given timestamp to Android clients
- Docker Compose configuration for local deployment

### Does NOT own
- Business logic (imports and calls `:shared-kmp/domain` directly)
- Android-specific code
- Client-side sync scheduling (belongs in `:core-network` on Android)

### Permissions checked
All permissions are enforced on the Android client via `SessionManager`.
The Ktor server performs **JWT token validation only** — it trusts that the
Android client has already enforced RBAC locally. Server-side permission
enforcement is a post-v1 hardening task.

### Database tables (PostgreSQL, mirrors Room schema)
Same logical tables as Room — schema is defined once in `:shared-kmp/models`
and applied to both Room (via Room entity annotations) and PostgreSQL
(via Exposed table definitions in `:ktor-server`).
