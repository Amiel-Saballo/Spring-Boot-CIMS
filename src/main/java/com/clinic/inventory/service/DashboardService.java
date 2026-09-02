package com.clinic.inventory.service;

import java.time.LocalDate;
import java.util.ArrayList;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.clinic.inventory.dto.DashboardDtos;
import com.clinic.inventory.enums.BatchStatus;
import com.clinic.inventory.enums.EquipmentStatus;
import com.clinic.inventory.enums.ItemCategory;
import com.clinic.inventory.enums.ItemStatus;
import com.clinic.inventory.enums.ReceivingStatus;
import com.clinic.inventory.repository.BatchRepository;
import com.clinic.inventory.repository.EquipmentUnitRepository;
import com.clinic.inventory.repository.ItemRepository;
import com.clinic.inventory.repository.ReceivingTransactionRepository;
import com.clinic.inventory.repository.TransactionLogRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class DashboardService {
    private final ItemRepository itemRepository;
    private final BatchRepository batchRepository;
    private final EquipmentUnitRepository equipmentRepository;
    private final ReceivingTransactionRepository receivingRepository;
    private final TransactionLogRepository logRepository;
    private final ReferenceDataService referenceDataService;
    private final DtoMapper mapper;

    @Transactional(readOnly = true)
    public DashboardDtos.Response get() {
        var activeItems = itemRepository
                .findByStatusOrderByCodeAsc(ItemStatus.ACTIVE);
        int days = referenceDataService.nearExpiryDays();
        LocalDate today = LocalDate.now();
        var near = batchRepository
                .findByExpiryDateBetweenAndStatus(today, today.plusDays(days),
                        BatchStatus.ACTIVE)
                .stream()
                .filter(b -> b.getItem().getCategory() == ItemCategory.MEDICINE
                        && b.getOnHand() > 0)
                .toList();
        var expired = batchRepository
                .findByExpiryDateBeforeAndStatus(today, BatchStatus.ACTIVE)
                .stream()
                .filter(b -> b.getItem().getCategory() == ItemCategory.MEDICINE
                        && b.getOnHand() > 0)
                .toList();
        var needs = new ArrayList<DashboardDtos.NeedAttention>();
        long lowCount = 0;
        for (var item : activeItems) {
            if (item.getCategory() == ItemCategory.EQUIPMENT)
                continue;
            int onHand = batchRepository
                    .sumOnHandByItem(item.getId(), BatchStatus.ACTIVE)
                    .intValue();
            if (onHand <= item.getReorderLevel()) {
                lowCount++;
                needs.add(new DashboardDtos.NeedAttention("LOW_STOCK",
                        item.getId(), item.getCode(), item.getName(), onHand,
                        item.getReorderLevel(), item.getReorderQuantity(),
                        "On hand is at or below the reorder level."));
            }
        }
        for (var batch : near) {
            needs.add(new DashboardDtos.NeedAttention("NEAR_EXPIRY",
                    batch.getItem().getId(), batch.getItem().getCode(),
                    batch.getItem().getName(), batch.getOnHand(),
                    batch.getItem().getReorderLevel(),
                    batch.getItem().getReorderQuantity(),
                    "Batch " + (batch.getBatchNumber() == null
                            ? "(no batch number)"
                            : batch.getBatchNumber()) + " expires "
                            + batch.getExpiryDate()));
        }

        for (var batch : expired) {
            needs.add(new DashboardDtos.NeedAttention("EXPIRED",
                    batch.getItem().getId(), batch.getItem().getCode(),
                    batch.getItem().getName(), batch.getOnHand(),
                    batch.getItem().getReorderLevel(),
                    batch.getItem().getReorderQuantity(),
                    "Batch " + (batch.getBatchNumber() == null
                            ? "(no batch number)"
                            : batch.getBatchNumber()) + " expired "
                            + batch.getExpiryDate()));
        }

        long pending = receivingRepository
                .findByStatus(ReceivingStatus.PENDING, PageRequest.of(0, 1))
                .getTotalElements();
        long inUse = equipmentRepository.findAll().stream()
                .filter(e -> e.getStatus() == EquipmentStatus.IN_USE).count();
        var recent = logRepository
                .findAll(PageRequest.of(0, 7,
                        Sort.by(Sort.Direction.DESC, "transactionDate")))
                .map(mapper::transaction).getContent();
        return new DashboardDtos.Response(activeItems.size(), near.size(),
                lowCount, pending, inUse, needs, recent);
    }
}
