package com.clinic.inventory.service;

import com.clinic.inventory.dto.DisposalDtos;
import com.clinic.inventory.entity.*;
import com.clinic.inventory.enums.*;
import com.clinic.inventory.exception.*;
import com.clinic.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DisposalService {
    private final DisposalRecordRepository disposalRepository;
    private final BatchRepository batchRepository;
    private final EquipmentUnitRepository equipmentRepository;
    private final TransactionLogService transactionLogService;
    private final DtoMapper mapper;

    @Transactional(readOnly = true)
    public Page<DisposalDtos.Response> list(Pageable pageable) { return disposalRepository.findAll(pageable).map(mapper::disposal); }

    @Transactional
    public DisposalDtos.Response disposeBatch(DisposalDtos.BatchRequest request, UserAccount user) {
        Batch batch = batchRepository.findById(request.batchId()).orElseThrow(() -> new ResourceNotFoundException("Batch not found"));
        if (batch.getStatus() == BatchStatus.DISPOSED) throw new BusinessRuleException("Batch is already disposed");
        if (request.quantity() > batch.getOnHand()) throw new BusinessRuleException("Disposal quantity exceeds on-hand quantity");
        int before = batch.getOnHand();
        batch.setOnHand(before - request.quantity());
        if (batch.getOnHand() == 0) batch.setStatus(BatchStatus.DISPOSED);
        batchRepository.save(batch);
        String ref = ref();
        DisposalRecord record = disposalRepository.save(DisposalRecord.builder().referenceNumber(ref).disposalDate(LocalDate.now())
                .item(batch.getItem()).batch(batch).quantity(request.quantity()).reason(request.reason().trim()).remarks(trim(request.remarks())).recordedBy(user).build());
        transactionLogService.log(TransactionType.DISPOSAL, ref, user, batch.getItem(), before, batch.getOnHand(),
                "Disposed " + request.quantity() + " " + batch.getItem().getUnitOfMeasure().getName() + " of " + batch.getItem().getName() +
                        " from batch " + (batch.getBatchNumber() == null ? "(no batch number)" : batch.getBatchNumber()) + ". Quantity: " + before + " -> " + batch.getOnHand() + ".");
        return mapper.disposal(record);
    }

    @Transactional
    public DisposalDtos.Response disposeEquipment(DisposalDtos.EquipmentRequest request, UserAccount user) {
        EquipmentUnit unit = equipmentRepository.findById(request.equipmentUnitId()).orElseThrow(() -> new ResourceNotFoundException("Equipment unit not found"));
        if (unit.getStatus() == EquipmentStatus.DISPOSED) throw new BusinessRuleException("Equipment is already disposed");
        long before = equipmentRepository.findAll().stream().filter(e -> e.getItem().getId().equals(unit.getItem().getId()) && e.getStatus()!=EquipmentStatus.DISPOSED).count();
        unit.setStatus(EquipmentStatus.DISPOSED); equipmentRepository.save(unit);
        String ref = ref();
        DisposalRecord record = disposalRepository.save(DisposalRecord.builder().referenceNumber(ref).disposalDate(LocalDate.now())
                .item(unit.getItem()).equipmentUnit(unit).quantity(1).reason(request.reason().trim()).remarks(trim(request.remarks())).recordedBy(user).build());
        transactionLogService.log(TransactionType.DISPOSAL, ref, user, unit.getItem(), (int) before, (int) before - 1,
                "Disposed equipment " + unit.getItem().getName() + " (asset tag " + unit.getAssetTag() + "). Quantity: " + before + " -> " + (before - 1) + ".");
        return mapper.disposal(record);
    }

    private String ref() { return "DSP-" + LocalDate.now().toString().replace("-", "") + "-" + UUID.randomUUID().toString().substring(0,6).toUpperCase(); }
    private String trim(String v) { return v == null ? null : v.trim(); }
}
