package com.clinic.inventory.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class SupplierDtos {
    private SupplierDtos() {}
    public record UpsertRequest(@NotBlank @Size(max=180) String name,
                                @Size(max=160) String contactPerson,
                                @Size(max=80) String contactNo,
                                @Size(max=255) String address) {}
    public record Response(Long id, String name, String contactPerson, String contactNo, String address, boolean active) {}
}
