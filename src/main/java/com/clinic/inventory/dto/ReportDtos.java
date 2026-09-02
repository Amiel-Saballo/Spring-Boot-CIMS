package com.clinic.inventory.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import com.clinic.inventory.enums.ItemCategory;
import com.clinic.inventory.enums.ReportType;
import com.clinic.inventory.enums.TransactionType;

public final class ReportDtos {
    private ReportDtos() {
    }

    public record GenerateRequest(
            @jakarta.validation.constraints.NotNull ReportType reportType,
            LocalDate from, LocalDate to, TransactionType transactionType,
            ItemCategory itemCategory, Long locationId) {
    }

    public record Week(int week, int delivery, int pullOutReturn, int dispensed,
            int endingInventory, int actualInventory, int variance) {
    }

    public record StockBalanceRow(Long itemId, String itemCode, String itemName,
            ItemCategory category, String unitOfMeasure, int runningBalance,
            int totalMonthlyDispensed, int beginningInventory,
            List<Week> weeks) {
    }

    public record TransactionHistoryRow(OffsetDateTime date,
            TransactionType transactionType, String referenceNumber,
            String user, String itemName, ItemCategory itemCategory,
            String detail) {
    }

    public record MedicineIssuanceHistoryRow(LocalDate dateIssued,
            String nurseOnDuty, String employeeNumber, String employeeName,
            String department, String supervisor, String chiefComplaint,
            String disposition, String itemIssued, int quantity,
            String remarks) {
    }

    public record SupplyIssuanceHistoryRow(LocalDate dateIssued,
            String nurseOnDuty, String employeeNumber, String employeeName,
            String department, String supervisor, String chiefComplaint,
            String disposition, String itemIssued, int quantity,
            String remarks) {
    }

    public record EquipmentReportRow(String assetTag, String itemName,
            String serialNumber, String brand, String model, String location,
            List<Integer> monthlyPresence, String remarks) {
    }

    public record GeneratedReport(Long reportRecordId, ReportType reportType,
            String title, LocalDate from, LocalDate to,
            TransactionType transactionType, ItemCategory itemCategory,
            List<?> rows) {
    }

    public record RecordResponse(Long id, ReportType reportType,
            OffsetDateTime generatedAt, String generatedBy,
            String parametersJson) {
    }
}
