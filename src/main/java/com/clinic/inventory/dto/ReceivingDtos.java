package com.clinic.inventory.dto;

import com.clinic.inventory.enums.ItemCategory;
import com.clinic.inventory.enums.ReceivingStatus;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public final class ReceivingDtos {
    private ReceivingDtos() {}

    public record LineRequest(
            @NotNull Long itemId,
            @Min(1) int quantity,
            @Size(max=120) String brand,
            @Size(max=120) String batchNumber,
            LocalDate expiryDate,
            @Size(max=120) String model,
            @Size(max=160) String serialNumber,
            @Size(max=160) String assetTag,
            @NotNull Long locationId) {}

    public record CreateRequest(
            @NotNull Long supplierId,
            @NotBlank @Size(max=100) String referenceNumber,
            @NotNull LocalDate dateReceived,
            @Size(max=150) String remarks,
            @NotEmpty List<@Valid LineRequest> lines) {}

    public record UpdateReturnedRequest(
            @NotNull Long supplierId,
            @NotBlank @Size(max=100) String referenceNumber,
            @NotNull LocalDate dateReceived,
            @Size(max=150) String remarks,
            @NotEmpty List<@Valid LineRequest> lines) {}

    public record ReasonRequest(@NotBlank @Size(max=150) String reason) {}

    public record LineResponse(Long id, Long itemId, String itemCode, String itemName, ItemCategory category,
                               int quantity, String unitOfMeasure, String brand, String batchNumber,
                               LocalDate expiryDate, String model, String serialNumber, String assetTag,
                               Long locationId, String location) {}

    public record Response(Long id, Long supplierId, String supplierName, String referenceNumber,
                           LocalDate dateReceived, String remarks, String returnReason,
                           ReceivingStatus status, String receivedBy, String approvedBy,
                           List<LineResponse> lines) {}
}
