# CIMS Business Rules Implemented

1. Transaction types are limited to `ADJUSTMENT`, `DISPOSAL`, `ISSUANCE`, and `RECEIVING`.
2. Reorder level is 0–100. Reorder quantity is 0–500.
3. Near-expiry days is one global value used for all medicines.
4. Receiving remarks and Supervisor return reason are limited to 150 characters.
5. Batch number is optional in receiving.
6. Location is mandatory for every receiving line. Default locations are Alabang, Cebu, and Makati.
7. Equipment receiving requires manual asset tag + serial number, quantity 1 per line.
8. Approved receiving transactions are immutable.
9. Returned receiving transactions are editable and each returned line has a dedicated update endpoint.
10. Approving receiving creates Batch or Equipment Unit records and logs stock movement.
11. Batches created from approved receiving transactions are read-only; disposal remains a separate operation.
12. Equipment edit changes status only. `DISPOSED` cannot be selected from status edit; dedicated disposal must be used.
13. Disposed equipment cannot be edited.
14. Issuance uses FEFO and requires employee number + employee name.
15. Issuance records are editable; stock is reconciled when issued quantities change.
16. Item deletion is soft-delete and is blocked by active batches/equipment. Items may be reactivated.
17. Supplier deletion is soft-delete and is blocked by pending/returned receiving, approved receiving within 3 years, or an active item that was received from that supplier.
18. Role permissions are database-driven. A role assigned to active users cannot be deactivated.
19. Administrator does not have Transaction Log access by default but can be granted it.
20. Nurse has Reports permission by default.
21. Stock Balance report always sets Pull Out / Return to zero.
22. Transaction History can filter by transaction type and item category.
23. Equipment report includes only equipment units stored in the system.
24. Past report records are created only when a report is generated.
25. Master/reference endpoints default to A–Z sorting; time-based endpoints default newest-first.
