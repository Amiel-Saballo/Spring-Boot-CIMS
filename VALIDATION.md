# Validation Notes

## What was changed

The served browser UI was rewritten so it no longer reads or writes inventory data from browser `localStorage`. It now uses the Spring Boot REST API for all persisted system data.

The old localStorage prototype has been removed from the runnable project. Both served entry points use the REST API client.

## Persistence safeguards

- `src/main/resources/static/js/app.js` contains no `localStorage` usage.
- Inventory, receiving, approvals, issuance, equipment, disposals, suppliers, users, roles, settings, reports, and transaction logs are loaded from `/api/...` endpoints.
- Mutations are sent to REST controllers and reloaded from the server after success.
- Browser focus and a 10-second interval refresh the visible page from the server.
- Opening the HTML directly with `file://` is intentionally rejected; the UI tells the user to open it through `http://localhost:8080/`.
- Only the Basic-auth token is kept in `sessionStorage`; no inventory/business records are stored there.
- The top bar checks `/api/system/health` and visibly reports whether the JDBC/database connection is reachable.

## Automated tests included

`ApiPersistenceIntegrationTest` uses Spring Boot, MockMvc, Spring Security, JPA, and an H2 database in MySQL compatibility mode to verify that:

1. A Nurse can read active items for Receiving/Issuance without Item Master permission.
2. A receiving transaction created through `POST /api/receiving` is persisted in the repository.
3. A separate subsequent HTTP request can read that same receiving transaction.
4. The served frontend JavaScript contains no `localStorage` or legacy `cimsMockupState` fallback.
5. The database health endpoint responds with `UP`.
6. Both `/` and `/mockup.html` load the same REST client.
7. The frontend source references every major persisted API area.

Run the full test suite in Eclipse/Maven with:

```bash
mvn test
```

## Validation performed during generation

- JavaScript syntax checked with `node --check`.
- Served static resources checked to confirm there is no `localStorage` inventory path.
- REST security was adjusted so Receiving/Issuance users can read Item lookup data without receiving Item Master write permission.
- Inline supplier creation is allowed by Receiving permission, while supplier update/delete/reactivate still requires Supplier permission.
- Dashboard transaction data is removed server-side when the authenticated user lacks Transaction Log permission.
- Existing role permissions are no longer silently re-seeded on application restart after permissions were intentionally revoked.
- Report history can be previewed/exported without creating duplicate report-history entries.

A dependency-resolved Maven build could not be executed in the artifact-generation container because Maven is not installed there. The project includes H2-backed tests specifically so Eclipse/Maven can validate the application without requiring your MySQL instance during `mvn test`.

## Startup hang prevention

- `index.html` and `mockup.html` load CSS/JS through relative application paths so opening a source HTML file no longer silently leaves the boot spinner.
- A frontend bundle-load diagnostic replaces the spinner if `app.js` fails to load.
- `app.js` wraps session storage access so blocked browser storage cannot prevent boot.
- REST requests have an explicit timeout and convert network/timeout failures into visible messages.
- Saved-session restoration times out and returns to the login screen rather than hanging indefinitely.
- `/api/system/health` is available before authentication so the login screen can distinguish a database/server problem from a credential problem.
- JavaScript syntax was checked with `node --check` after these changes.
