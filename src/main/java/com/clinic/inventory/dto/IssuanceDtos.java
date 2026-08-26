package com.clinic.inventory.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

import java.time.LocalDate;
import java.util.List;

public final class IssuanceDtos {
    private IssuanceDtos() {}

    public record ItemRequest(@NotNull Long itemId, @Min(1) int quantity) {}
    public record LineUpdateRequest(@NotNull Long batchId, @Min(1) int quantity) {}

    public record CreateRequest(
            @NotNull LocalDate dateIssued,
            @NotBlank @Size(max=80) String employeeNumber,
            @NotBlank @Size(max=160) String employeeName,
            @Size(max=120) String department,
            @Size(max=160) String supervisor,
            @NotBlank @Size(max=255) String chiefComplaint,
            @NotBlank @Size(max=120) String disposition,
            @Size(max=500) String remarks,
            @NotEmpty List<@Valid ItemRequest> items) {}

    public record UpdateRequest(
            @NotNull LocalDate dateIssued,
            @NotBlank @Size(max=80) String employeeNumber,
            @NotBlank @Size(max=160) String employeeName,
            @Size(max=120) String department,
            @Size(max=160) String supervisor,
            @NotBlank @Size(max=255) String chiefComplaint,
            @NotBlank @Size(max=120) String disposition,
            @Size(max=500) String remarks,
            @NotEmpty List<@Valid LineUpdateRequest> lines) {}

    public record LineResponse(Long id, Long itemId, String itemName, Long batchId,
                               String batchNumber, int quantity, String unitOfMeasure) {}

    public record Response(Long id, String referenceNumber, LocalDate dateIssued,
                           String employeeNumber, String employeeName, String department,
                           String supervisor, String chiefComplaint, String disposition,
                           String remarks, String recordedBy, List<LineResponse> lines) {}
}
