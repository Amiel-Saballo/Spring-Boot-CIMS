package com.clinic.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class ReferenceDtos {
    private ReferenceDtos() {}
    public record NameRequest(@NotBlank @Size(max=120) String name) {}
    public record ReferenceResponse(Long id, String name) {}
    public record NearExpiryRequest(@jakarta.validation.constraints.Min(0) @jakarta.validation.constraints.Max(3650) int days) {}
    public record NearExpiryResponse(int days) {}
}
