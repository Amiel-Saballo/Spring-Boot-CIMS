# REST API Summary

All `/api/**` endpoints require HTTP Basic authentication. Authorization uses role permissions stored in MySQL.

## Items
- `GET /api/items?q=&category=&status=&unitOfMeasureId=`
- `POST /api/items`
- `PUT /api/items/{id}`
- `DELETE /api/items/{id}` soft-delete
- `POST /api/items/{id}/reactivate`

## Settings
- `GET /api/settings/units-of-measure`
- `POST /api/settings/units-of-measure`
- `GET /api/settings/locations`
- `POST /api/settings/locations`
- `GET /api/settings/near-expiry-days`
- `PUT /api/settings/near-expiry-days`

## Suppliers
- `GET /api/suppliers`
- `POST /api/suppliers`
- `PUT /api/suppliers/{id}`
- `DELETE /api/suppliers/{id}` soft-delete with business-rule validation
- `POST /api/suppliers/{id}/reactivate`

## Receiving
- `GET /api/receiving`
- `POST /api/receiving`
- `GET /api/receiving/{id}`
- `PUT /api/receiving/{id}/returned`
- `PUT /api/receiving/{id}/returned/lines/{lineId}`
- `POST /api/receiving/{id}/resubmit`
- `POST /api/receiving/{id}/cancel`

## Supervisor approvals
- `GET /api/approvals`
- `GET /api/approvals/{id}` review; response includes reference number, remarks and item details
- `POST /api/approvals/{id}/approve`
- `POST /api/approvals/{id}/return`

## Inventory
- `GET /api/batches`
- `GET /api/equipment`
- `PATCH /api/equipment/{id}/status`
- `POST /api/disposals/batch`
- `POST /api/disposals/equipment`
- `GET /api/disposals`

## Issuance
- `GET /api/issuances`
- `GET /api/issuances/{id}`
- `POST /api/issuances`
- `PUT /api/issuances/{id}`

## Administration
- `GET/POST/PUT /api/users`
- `GET/POST/PUT /api/roles`
- `GET /api/roles/permissions`
- `PATCH /api/roles/{id}/active?active=false`

## Transaction Log
- `GET /api/transaction-logs?transactionType=&itemCategory=&from=&to=`

Allowed transaction types: `ADJUSTMENT`, `DISPOSAL`, `ISSUANCE`, `RECEIVING`.

## Reports
- `GET /api/reports/records`
- `POST /api/reports/generate`
- `POST /api/reports/export/csv`
- `POST /api/reports/export/xlsx`
- `POST /api/reports/export/pdf`
