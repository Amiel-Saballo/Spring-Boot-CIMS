# Frontend REST API Matrix

The served frontend (`src/main/resources/static/js/app.js`) uses the REST API for every persisted screen. Browser memory is used only for the currently unsaved Receiving/Issuance draft and `sessionStorage` is used only for the current Basic-auth credential token.

| Screen | Reads | Writes / Actions |
|---|---|---|
| Dashboard | `GET /api/dashboard`, `GET /api/system/health` | None |
| Item Master | `GET /api/items`, `GET /api/settings/units-of-measure` | `POST/PUT/DELETE /api/items`, `POST /api/items/{id}/reactivate`, `POST /api/settings/units-of-measure` |
| Receiving | `GET /api/items`, `GET /api/suppliers`, `GET /api/settings/locations` | `POST /api/receiving`, `POST /api/suppliers` |
| Receiving Records | `GET /api/receiving` | `PUT /api/receiving/{id}/returned`, `PUT /api/receiving/{id}/returned/lines/{lineId}`, `POST /api/receiving/{id}/resubmit`, `POST /api/receiving/{id}/cancel` |
| Approvals | `GET /api/approvals` | `POST /api/approvals/{id}/approve`, `POST /api/approvals/{id}/return` |
| Issuance | `GET /api/items` | `POST /api/issuances` |
| Issuance Records | `GET /api/issuances` | `PUT /api/issuances/{id}` |
| Batches | `GET /api/batches` | Disposal is performed through `/api/disposals/batch` |
| Equipment | `GET /api/equipment` | `PATCH /api/equipment/{id}/status`, disposal through `/api/disposals/equipment` |
| Disposals | `GET /api/disposals` | `POST /api/disposals/batch`, `POST /api/disposals/equipment` |
| Suppliers | `GET /api/suppliers` | `POST/PUT/DELETE /api/suppliers`, `POST /api/suppliers/{id}/reactivate` |
| Users | `GET /api/users`, `GET /api/roles` | `POST/PUT /api/users`, `PATCH /api/users/{id}/active` |
| Roles | `GET /api/roles`, `GET /api/roles/permissions` | `POST/PUT /api/roles`, `PATCH /api/roles/{id}/active` |
| System Settings | `GET /api/settings/...`, `GET /api/items` | `PUT /api/settings/near-expiry-days`, `POST /api/settings/units-of-measure`, `POST /api/settings/locations`, `PUT /api/items/{id}` |
| Transaction Log | `GET /api/transaction-logs` | None |
| Reports | `GET /api/reports/records`, `GET /api/settings/locations` | `POST /api/reports/generate`, report preview/export endpoints |

## Cross-browser persistence

Submitted records are never read from browser-local state. A record written in Browser A is read from MySQL by Browser B after sign-in/refresh. The UI refreshes on successful mutations, on window focus, and every 10 seconds while visible.
