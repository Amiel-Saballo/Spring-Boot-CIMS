#!/usr/bin/env bash
set -euo pipefail
BASE_URL="${BASE_URL:-http://localhost:8080}"
NURSE_AUTH="${NURSE_AUTH:-nurse@clinic.local:ChangeMe123!}"
SUPERVISOR_AUTH="${SUPERVISOR_AUTH:-supervisor@clinic.local:ChangeMe123!}"
ADMIN_AUTH="${ADMIN_AUTH:-admin@clinic.local:ChangeMe123!}"

echo "Session"
curl -fsS -u "$NURSE_AUTH" "$BASE_URL/api/session/me"; echo

echo "Items"
curl -fsS -u "$ADMIN_AUTH" "$BASE_URL/api/items?size=5"; echo

echo "Suppliers"
curl -fsS -u "$NURSE_AUTH" "$BASE_URL/api/suppliers?size=5"; echo

echo "Pending approvals"
curl -fsS -u "$SUPERVISOR_AUTH" "$BASE_URL/api/approvals?size=5"; echo

echo "Reports permission (Nurse default)"
curl -fsS -u "$NURSE_AUTH" "$BASE_URL/api/reports/records?size=5"; echo
