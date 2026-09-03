package com.clinic.inventory.service;

import com.clinic.inventory.dto.IssuanceDtos;
import com.clinic.inventory.entity.*;
import com.clinic.inventory.enums.*;
import com.clinic.inventory.exception.*;
import com.clinic.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.*;

@Service
@RequiredArgsConstructor
public class IssuanceService {
    private final IssuanceTransactionRepository issuanceRepository;
    private final ItemRepository itemRepository;
    private final BatchRepository batchRepository;
    private final TransactionLogService transactionLogService;
    private final DtoMapper mapper;

    @Transactional(readOnly = true)
    public Page<IssuanceDtos.Response> list(Pageable pageable) { return issuanceRepository.findAll(pageable).map(mapper::issuance); }

    @Transactional(readOnly = true)
    public IssuanceDtos.Response get(Long id) { return mapper.issuance(require(id)); }

    @Transactional
    public IssuanceDtos.Response create(IssuanceDtos.CreateRequest request, UserAccount user) {
        batchRepository.markExpiredActiveBatches(BatchStatus.EXPIRED, BatchStatus.ACTIVE);
        String ref = "ISS-" + request.dateIssued().toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        IssuanceTransaction tx = IssuanceTransaction.builder().referenceNumber(ref).dateIssued(request.dateIssued())
                .employeeNumber(request.employeeNumber().trim()).employeeName(request.employeeName().trim())
                .department(trim(request.department())).supervisor(trim(request.supervisor())).chiefComplaint(request.chiefComplaint().trim())
                .disposition(request.disposition().trim()).remarks(trim(request.remarks())).recordedBy(user).build();

        List<IssuanceLine> lines = new ArrayList<>();
        Map<Long,Integer> beforeTotals = new LinkedHashMap<>();
        for (IssuanceDtos.ItemRequest itemRequest : request.items()) {
            Item item = itemRepository.findById(itemRequest.itemId()).orElseThrow(() -> new ResourceNotFoundException("Item not found"));
            if (item.getCategory() == ItemCategory.EQUIPMENT) throw new BusinessRuleException("Equipment cannot be issued through the medicine/supply issuance workflow");
            if (item.getStatus() != ItemStatus.ACTIVE) throw new BusinessRuleException("Inactive item cannot be issued");
            beforeTotals.putIfAbsent(item.getId(), totalOnHand(item.getId()));
            allocateFefo(tx, item, itemRequest.quantity(), lines);
        }
        tx.replaceLines(lines);
        IssuanceTransaction saved = issuanceRepository.save(tx);
        for (Long itemId : beforeTotals.keySet()) {
            Item item = itemRepository.findById(itemId).orElseThrow();
            int before = beforeTotals.get(itemId), after = totalOnHand(itemId);
            transactionLogService.log(TransactionType.ISSUANCE, ref, user, item, before, after,
                    "Issued " + item.getName() + ". Quantity: " + before + " -> " + after + ". Recipient: " + request.employeeName() + ".");
        }
        return mapper.issuance(saved);
    }

    @Transactional
    public IssuanceDtos.Response update(Long id, IssuanceDtos.UpdateRequest request, UserAccount user) {
        IssuanceTransaction tx = require(id);
        Map<Long,Integer> beforeTotals = new LinkedHashMap<>();
        for (IssuanceLine line : tx.getLines()) beforeTotals.putIfAbsent(line.getItem().getId(), totalOnHand(line.getItem().getId()));

        // Restore old stock first, then apply the replacement lines.
        for (IssuanceLine old : tx.getLines()) {
            Batch batch = old.getBatch();
            batch.setOnHand(batch.getOnHand() + old.getQuantity());
            if (batch.getOnHand() > 0 && batch.getStatus() == BatchStatus.DEPLETED) batch.setStatus(BatchStatus.ACTIVE);
            batchRepository.save(batch);
        }

        List<IssuanceLine> newLines = new ArrayList<>();
        for (IssuanceDtos.LineUpdateRequest lineRequest : request.lines()) {
            Batch batch = batchRepository.findById(lineRequest.batchId()).orElseThrow(() -> new ResourceNotFoundException("Batch not found"));
            if (batch.getStatus() == BatchStatus.DISPOSED) throw new BusinessRuleException("Disposed batch cannot be used");
            if (batch.getOnHand() < lineRequest.quantity()) throw new BusinessRuleException("Insufficient stock in batch " + batch.getBatchNumber());
            beforeTotals.putIfAbsent(batch.getItem().getId(), totalOnHand(batch.getItem().getId()));
            batch.setOnHand(batch.getOnHand() - lineRequest.quantity());
            if (batch.getOnHand() == 0) batch.setStatus(BatchStatus.DEPLETED);
            batchRepository.save(batch);
            newLines.add(IssuanceLine.builder().transaction(tx).item(batch.getItem()).batch(batch).quantity(lineRequest.quantity()).build());
        }
        tx.setDateIssued(request.dateIssued()); tx.setEmployeeNumber(request.employeeNumber().trim()); tx.setEmployeeName(request.employeeName().trim());
        tx.setDepartment(trim(request.department())); tx.setSupervisor(trim(request.supervisor())); tx.setChiefComplaint(request.chiefComplaint().trim());
        tx.setDisposition(request.disposition().trim()); tx.setRemarks(trim(request.remarks())); tx.replaceLines(newLines);
        IssuanceTransaction saved = issuanceRepository.save(tx);

        Set<Long> itemIds = new LinkedHashSet<>(beforeTotals.keySet());
        newLines.forEach(l -> itemIds.add(l.getItem().getId()));
        for (Long itemId : itemIds) {
            Item item = itemRepository.findById(itemId).orElseThrow();
            int after = totalOnHand(itemId);
            Integer before = beforeTotals.get(itemId);
            if (before == null) before = after;
            transactionLogService.log(TransactionType.ISSUANCE, tx.getReferenceNumber(), user, item, before, after,
                    "Edited issuance for " + item.getName() + ". Quantity: " + before + " -> " + after + ". Recipient: " + request.employeeName() + ".");
        }
        return mapper.issuance(saved);
    }

    private void allocateFefo(IssuanceTransaction tx, Item item, int requested, List<IssuanceLine> lines) {
        int remaining = requested;
        List<Batch> batches = batchRepository.findByItemIdAndStatusAndOnHandGreaterThanOrderByExpiryDateAscIdAsc(item.getId(), BatchStatus.ACTIVE, 0);
        for (Batch batch : batches) {
            if (batch.getExpiryDate() != null && !batch.getExpiryDate().isAfter(LocalDate.now())) {
                batch.setStatus(BatchStatus.EXPIRED);
                batchRepository.save(batch);
                continue;
            }
            if (remaining <= 0) break;
            int take = Math.min(remaining, batch.getOnHand());
            batch.setOnHand(batch.getOnHand() - take);
            if (batch.getOnHand() == 0) batch.setStatus(BatchStatus.DEPLETED);
            batchRepository.save(batch);
            lines.add(IssuanceLine.builder().transaction(tx).item(item).batch(batch).quantity(take).build());
            remaining -= take;
        }
        if (remaining > 0) throw new BusinessRuleException("Insufficient stock for " + item.getName() + " by " + remaining + " " + item.getUnitOfMeasure().getName());
    }

    private int totalOnHand(Long itemId) { return batchRepository.sumOnHandByItem(itemId, BatchStatus.ACTIVE).intValue(); }
    private IssuanceTransaction require(Long id) { return issuanceRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Issuance not found")); }
    private String trim(String v) { return v == null ? null : v.trim(); }
}
