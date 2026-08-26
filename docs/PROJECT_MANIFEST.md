# Project Manifest

## REST controllers

- `ItemRestController`
- `SupplierRestController`
- `ReferenceDataRestController`
- `ReceivingRestController`
- `ApprovalRestController`
- `BatchRestController`
- `EquipmentRestController`
- `IssuanceRestController`
- `DisposalRestController`
- `UserRestController`
- `RoleRestController`
- `TransactionLogRestController`
- `DashboardRestController`
- `ReportRestController`
- `SessionRestController`

## Main MySQL entities

- Permission
- Role
- UserAccount
- UnitOfMeasure
- ClinicLocation
- SystemSetting
- Item
- Supplier
- ReceivingTransaction / ReceivingLine
- Batch
- EquipmentUnit
- IssuanceTransaction / IssuanceLine
- DisposalRecord
- TransactionLog
- ReportRecord

## Database setup

`src/main/resources/db/migration/V1__create_schema.sql` is the Flyway migration used by MySQL.

## Database-backed browser UI update

- `src/main/resources/static/index.html` — served application shell
- `src/main/resources/static/mockup.html` — same database-backed UI for backward-compatible URL
- `src/main/resources/static/css/app.css` — responsive mockup-derived styling
- `src/main/resources/static/js/app.js` — REST API client; no localStorage inventory state
- `src/test/java/com/clinic/inventory/ApiPersistenceIntegrationTest.java` — REST/database persistence regression test
- `docs/FRONTEND_DATABASE_WIRING.md` — architecture and persistence notes
- `docs/UPDATING_ECLIPSE_PROJECT.md` — update instructions
- `docs/reference/original-mockup-localstorage.html` — non-served visual/reference copy of the prior prototype

- `docs/FRONTEND_API_MATRIX.md` — screen-by-screen REST wiring map
