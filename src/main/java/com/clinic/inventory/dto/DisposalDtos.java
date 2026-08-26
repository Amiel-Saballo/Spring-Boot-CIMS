package com.clinic.inventory.dto;

import jakarta.validation.constraints.*;
import java.time.LocalDate;

public final class DisposalDtos {
    private DisposalDtos() {}
    public record BatchRequest(@NotNull Long batchId, @Min(1) int quantity,
                               @NotBlank @Size(max=180) String reason,
                               @Size(max=500) String remarks) {}
    public record EquipmentRequest(@NotNull Long equipmentUnitId,
                                   @NotBlank @Size(max=180) String reason,
                                   @Size(max=500) String remarks) {}
    public record Response(Long id, String referenceNumber, LocalDate disposalDate,
                           Long itemId, String itemName, Long batchId, Long equipmentUnitId,
                           int quantity, String reason, String remarks, String recordedBy) {}
}
