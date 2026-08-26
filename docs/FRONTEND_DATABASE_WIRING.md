# Frontend ↔ Spring Boot ↔ MySQL Wiring

## Architecture

The served application now follows this path for every persisted operation:

```text
Browser UI
   ↓ fetch('/api/...')
Spring @RestController
   ↓
Service / business rules
   ↓
Spring Data JPA Repository
   ↓
MySQL
```

There is no browser-local inventory database and no fallback to localStorage.

## Browser storage

The only browser storage used by the served UI is `sessionStorage['cims.basic']`, which contains the current HTTP Basic credential token for the browser session. It is not business data.

The following data is always retrieved from the REST API:

- Items and reorder settings
- Units of measure and locations
- Suppliers
- Receiving requests and receiving records
- Approval queue
- Batches
- Equipment
- Issuance and issuance records
- Disposals
- Users
- Roles and permissions
- Transaction logs
- Report history and report contents

## Synchronization behavior

The UI reloads server data:

- after every successful create/update/delete/approve/return/resubmit/dispose operation;
- whenever the browser window receives focus;
- every 10 seconds while the page is visible and no modal is open;
- when the user presses Refresh.

This means two browsers signed into the same application read the same MySQL records rather than isolated browser state.

## Important backend fixes made for the browser client

- Item lookup GET access is available to users with Item Master, Receiving, or Issuance permission; Item writes still require Item Master permission.
- Receiving users can list/create suppliers inline; supplier update/delete/reactivate still requires Supplier permission.
- Role permissions are not silently restored on restart if an administrator intentionally revokes all permissions from an existing role.
- Dashboard transaction details are removed server-side when Transaction Log permission is absent.
- Report history records can be previewed and exported without creating duplicate history rows.

## Regression test

`ApiPersistenceIntegrationTest` verifies a receiving transaction created through REST is persisted and visible to a separate later HTTP request. It also verifies the served JavaScript has no localStorage inventory fallback.


## Database connection indicator

The top bar calls `GET /api/system/health`. The endpoint performs a JDBC `SELECT 1` and reports the actual database product returned by the active connection. This makes a lost database connection visible to the user and prevents confusion between a working UI and a disconnected persistence layer.
