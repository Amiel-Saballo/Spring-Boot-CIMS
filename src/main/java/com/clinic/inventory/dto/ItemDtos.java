package com.clinic.inventory.dto;

import com.clinic.inventory.enums.ItemCategory;
import com.clinic.inventory.enums.ItemStatus;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class ItemDtos {
    private ItemDtos() {
    }

    public record CreateRequest(@NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 180) String name,
            @NotNull ItemCategory category, @NotNull Long unitOfMeasureId,
            @Min(0) @Max(100) int reorderLevel,
            @Min(1) @Max(500) int reorderQuantity) {
    }

    public record UpdateRequest(@NotBlank @Size(max = 80) String code,
            @NotBlank @Size(max = 180) String name,
            @NotNull ItemCategory category, @NotNull Long unitOfMeasureId,
            @Min(0) @Max(100) int reorderLevel,
            @Min(1) @Max(500) int reorderQuantity) {
    }

    public record Response(Long id, String code, String name,
            ItemCategory category, Long unitOfMeasureId, String unitOfMeasure,
            int reorderLevel, int reorderQuantity, ItemStatus status) {
    }
}
