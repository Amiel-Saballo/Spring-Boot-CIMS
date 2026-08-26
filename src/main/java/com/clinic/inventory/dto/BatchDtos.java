package com.clinic.inventory.dto;

import com.clinic.inventory.enums.BatchStatus;
import java.time.LocalDate;

public final class BatchDtos {
    private BatchDtos() {}
    public record Response(Long id, Long itemId, String itemCode, String itemName, String batchNumber,
                           String brand, LocalDate expiryDate, int onHand, String unitOfMeasure,
                           String location, BatchStatus status, boolean editable) {}
}
