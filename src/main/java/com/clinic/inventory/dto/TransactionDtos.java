package com.clinic.inventory.dto;

import com.clinic.inventory.enums.ItemCategory;
import com.clinic.inventory.enums.TransactionType;
import java.time.OffsetDateTime;

public final class TransactionDtos {
    private TransactionDtos() {}
    public record Response(Long id, OffsetDateTime transactionDate, TransactionType transactionType,
                           String referenceNumber, String user, Long itemId, String itemName,
                           ItemCategory itemCategory, Integer quantityBefore, Integer quantityAfter,
                           String detail) {}
}
