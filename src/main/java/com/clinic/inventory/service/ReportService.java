package com.clinic.inventory.service;

import com.clinic.inventory.dto.ReportDtos;
import com.clinic.inventory.entity.*;
import com.clinic.inventory.enums.*;
import com.clinic.inventory.repository.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
@RequiredArgsConstructor
public class ReportService {
    private final ItemRepository itemRepository;
    private final BatchRepository batchRepository;
    private final ReceivingTransactionRepository receivingRepository;
    private final IssuanceTransactionRepository issuanceRepository;
    private final DisposalRecordRepository disposalRepository;
    private final EquipmentUnitRepository equipmentRepository;
    private final TransactionLogRepository logRepository;
    private final ReportRecordRepository reportRecordRepository;
    private final ObjectMapper objectMapper;

    @Transactional
    public ReportDtos.GeneratedReport generate(ReportDtos.GenerateRequest request, UserAccount user) {
        ReportDtos.GeneratedReport preview = preview(request);
        String params = json(Map.of(
                "from", preview.from().toString(), "to", preview.to().toString(),
                "transactionType", Objects.toString(request.transactionType(), "ALL"),
                "itemCategory", Objects.toString(request.itemCategory(), "ALL"),
                "locationId", Objects.toString(request.locationId(), "ALL")));
        ReportRecord record = reportRecordRepository.save(ReportRecord.builder().reportType(preview.reportType()).generatedBy(user)
                .generatedAt(OffsetDateTime.now()).parametersJson(params).build());
        return new ReportDtos.GeneratedReport(record.getId(), preview.reportType(), preview.title(), preview.from(), preview.to(), preview.rows());
    }

    @Transactional(readOnly = true)
    public ReportDtos.GeneratedReport preview(ReportDtos.GenerateRequest request) {
        ReportType type = Objects.requireNonNull(request.reportType(), "reportType is required");
        LocalDate from = request.from() == null ? LocalDate.now().withDayOfMonth(1) : request.from();
        LocalDate to = request.to() == null ? LocalDate.now() : request.to();
        if (to.isBefore(from)) throw new com.clinic.inventory.exception.BusinessRuleException("Report end date cannot be before start date");
        List<?> rows = switch (type) {
            case STOCK_BALANCE -> stockBalance(from, to);
            case TRANSACTION_HISTORY -> transactionHistory(from, to, request.transactionType(), request.itemCategory());
            case EQUIPMENT_REGISTRY -> equipmentReport(request.locationId(), to);
        };
        return new ReportDtos.GeneratedReport(null, type, title(type), from, to, rows);
    }

    @Transactional(readOnly = true)
    public ReportDtos.GeneratedReport previewRecord(Long id) {
        ReportRecord record = reportRecordRepository.findById(id)
                .orElseThrow(() -> new com.clinic.inventory.exception.ResourceNotFoundException("Report record not found"));
        Map<String,String> params = parseParams(record.getParametersJson());
        LocalDate from = LocalDate.parse(params.getOrDefault("from", LocalDate.now().withDayOfMonth(1).toString()));
        LocalDate to = LocalDate.parse(params.getOrDefault("to", LocalDate.now().toString()));
        TransactionType transactionType = enumOrNull(TransactionType.class, params.get("transactionType"));
        ItemCategory itemCategory = enumOrNull(ItemCategory.class, params.get("itemCategory"));
        Long locationId = longOrNull(params.get("locationId"));
        ReportDtos.GeneratedReport preview = preview(new ReportDtos.GenerateRequest(record.getReportType(), from, to,
                transactionType, itemCategory, locationId));
        return new ReportDtos.GeneratedReport(record.getId(), preview.reportType(), preview.title(), preview.from(), preview.to(), preview.rows());
    }

    @Transactional(readOnly = true)
    public Page<ReportDtos.RecordResponse> history(Pageable pageable) {
        return reportRecordRepository.findAll(pageable).map(r -> new ReportDtos.RecordResponse(r.getId(), r.getReportType(),
                r.getGeneratedAt(), r.getGeneratedBy().getFullName(), r.getParametersJson()));
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.StockBalanceRow> stockBalance(LocalDate from, LocalDate to) {
        List<Item> items = itemRepository.findByStatusOrderByCodeAsc(ItemStatus.ACTIVE).stream()
                .filter(i -> i.getCategory() != ItemCategory.EQUIPMENT)
                .sorted(Comparator.comparing(Item::getCode, String.CASE_INSENSITIVE_ORDER)).toList();
        var receiving = receivingRepository.findByStatusAndDateReceivedBetweenOrderByDateReceivedAsc(ReceivingStatus.APPROVED, from, to);
        var issuances = issuanceRepository.findByDateIssuedBetweenOrderByDateIssuedAsc(from, to);
        var disposals = disposalRepository.findByDisposalDateBetweenOrderByDisposalDateAsc(from, to);
        List<ReportDtos.StockBalanceRow> rows = new ArrayList<>();
        for (Item item : items) {
            int current = batchRepository.findByItemIdAndStatus(item.getId(), BatchStatus.ACTIVE).stream().mapToInt(Batch::getOnHand).sum();
            int monthlyDelivery = receiving.stream().flatMap(r -> r.getLines().stream())
                    .filter(l -> l.getItem().getId().equals(item.getId())).mapToInt(ReceivingLine::getQuantity).sum();
            int monthlyDispensed = issuances.stream().flatMap(i -> i.getLines().stream())
                    .filter(l -> l.getItem().getId().equals(item.getId())).mapToInt(IssuanceLine::getQuantity).sum();
            int monthlyDisposal = disposals.stream().filter(d -> d.getItem().getId().equals(item.getId())).mapToInt(DisposalRecord::getQuantity).sum();
            int beginning = current - monthlyDelivery + monthlyDispensed + monthlyDisposal;
            int running = beginning;
            List<ReportDtos.Week> weeks = new ArrayList<>();
            for (int week = 1; week <= 5; week++) {
                final int w = week;
                int delivery = receiving.stream().filter(r -> weekOfMonth(r.getDateReceived()) == w).flatMap(r -> r.getLines().stream())
                        .filter(l -> l.getItem().getId().equals(item.getId())).mapToInt(ReceivingLine::getQuantity).sum();
                int dispensed = issuances.stream().filter(i -> weekOfMonth(i.getDateIssued()) == w).flatMap(i -> i.getLines().stream())
                        .filter(l -> l.getItem().getId().equals(item.getId())).mapToInt(IssuanceLine::getQuantity).sum();
                int ending = running + delivery - dispensed; // Pull Out / Return is always 0 by requirement.
                boolean finalWeek = week == Math.min(5, weekOfMonth(to));
                int actual = finalWeek ? current : ending;
                weeks.add(new ReportDtos.Week(week, delivery, 0, dispensed, ending, actual, actual - ending));
                running = ending;
            }
            rows.add(new ReportDtos.StockBalanceRow(item.getId(), item.getCode(), item.getName(), item.getCategory(),
                    item.getUnitOfMeasure().getName(), current, monthlyDispensed, beginning, weeks));
        }
        return rows;
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.TransactionHistoryRow> transactionHistory(LocalDate from, LocalDate to,
                                                                      TransactionType transactionType,
                                                                      ItemCategory itemCategory) {
        OffsetDateTime start = from.atStartOfDay().atOffset(ZoneOffset.ofHours(8));
        OffsetDateTime end = to.plusDays(1).atStartOfDay().atOffset(ZoneOffset.ofHours(8)).minusNanos(1);
        return logRepository.reportRows(transactionType, itemCategory, start, end).stream()
                .map(t -> new ReportDtos.TransactionHistoryRow(t.getTransactionDate(), t.getTransactionType(), t.getReferenceNumber(),
                        t.getUser().getFullName(), t.getAffectedItem() == null ? null : t.getAffectedItem().getName(),
                        t.getAffectedItem() == null ? null : t.getAffectedItem().getCategory(), t.getDetail()))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<ReportDtos.EquipmentReportRow> equipmentReport(Long locationId, LocalDate asOf) {
        List<EquipmentUnit> equipment = locationId == null
                ? equipmentRepository.findAll(Sort.by("assetTag").ascending())
                : equipmentRepository.findByLocationIdOrderByAssetTagAsc(locationId);
        return equipment.stream().map(e -> {
            List<Integer> months = new ArrayList<>(12);
            for (int month = 1; month <= 12; month++) {
                LocalDate monthEnd = LocalDate.of(asOf.getYear(), month, 1).with(java.time.temporal.TemporalAdjusters.lastDayOfMonth());
                if (monthEnd.isAfter(asOf)) months.add(null);
                else months.add(!e.getAcquiredDate().isAfter(monthEnd) ? 1 : 0);
            }
            String remarks = "Status: " + e.getStatus() + "; Location: " + e.getLocation().getName() +
                    (e.getBrand() == null ? "" : "; Brand: " + e.getBrand()) + (e.getModel() == null ? "" : "; Model: " + e.getModel());
            return new ReportDtos.EquipmentReportRow(e.getAssetTag(), e.getItem().getName(), e.getSerialNumber(), e.getBrand(),
                    e.getModel(), e.getLocation().getName(), months, remarks);
        }).toList();
    }

    private int weekOfMonth(LocalDate date) { return Math.min(5, ((date.getDayOfMonth() - 1) / 7) + 1); }
    private String title(ReportType type) { return switch (type) { case STOCK_BALANCE -> "Stock Balance Report"; case TRANSACTION_HISTORY -> "Transaction History Report"; case EQUIPMENT_REGISTRY -> "Monthly Clinic Equipment/Items Inventory"; }; }
    private String json(Map<String,String> values) { try { return objectMapper.writeValueAsString(values); } catch (JsonProcessingException e) { throw new IllegalStateException(e); } }
    @SuppressWarnings("unchecked")
    private Map<String,String> parseParams(String value) {
        if (value == null || value.isBlank()) return Map.of();
        try { return objectMapper.readValue(value, Map.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("Unable to read report parameters", e); }
    }
    private <E extends Enum<E>> E enumOrNull(Class<E> type, String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
        return Enum.valueOf(type, value);
    }
    private Long longOrNull(String value) {
        if (value == null || value.isBlank() || "ALL".equalsIgnoreCase(value)) return null;
        return Long.valueOf(value);
    }

}
