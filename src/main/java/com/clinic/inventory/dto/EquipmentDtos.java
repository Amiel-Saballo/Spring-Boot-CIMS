package com.clinic.inventory.dto;

import com.clinic.inventory.enums.EquipmentStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public final class EquipmentDtos {
    private EquipmentDtos() {}
    public record StatusUpdateRequest(@NotNull EquipmentStatus status,
                                      @NotBlank @Size(max=500) String reason) {}
    public record Response(Long id, Long itemId, String itemCode, String equipmentName,
                           String assetTag, String serialNumber, String brand, String model,
                           Long locationId, String location, LocalDate acquiredDate,
                           EquipmentStatus status) {}
}
