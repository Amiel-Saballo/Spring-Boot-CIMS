package com.clinic.inventory.service;

import com.clinic.inventory.dto.EquipmentDtos;
import com.clinic.inventory.entity.EquipmentUnit;
import com.clinic.inventory.enums.EquipmentStatus;
import com.clinic.inventory.enums.TransactionType;
import com.clinic.inventory.exception.*;
import com.clinic.inventory.repository.EquipmentUnitRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EquipmentService {
    private final EquipmentUnitRepository repository;
    private final TransactionLogService transactionLogService;
    private final DtoMapper mapper;

    @Transactional(readOnly = true)
    public Page<EquipmentDtos.Response> list(Pageable pageable) { return repository.findAll(pageable).map(mapper::equipment); }

    @Transactional
    public EquipmentDtos.Response updateStatus(Long id, EquipmentDtos.StatusUpdateRequest request, com.clinic.inventory.entity.UserAccount user) {
        EquipmentUnit unit = repository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Equipment unit not found"));
        if (unit.getStatus() == EquipmentStatus.DISPOSED) throw new BusinessRuleException("Disposed equipment cannot be edited");
        if (request.status() == EquipmentStatus.DISPOSED) throw new BusinessRuleException("Use the disposal endpoint to dispose equipment");
        EquipmentStatus beforeStatus = unit.getStatus();
        unit.setStatus(request.status());
        repository.save(unit);
        transactionLogService.log(TransactionType.ADJUSTMENT, unit.getAssetTag(), user, unit.getItem(), null, null,
                "Updated " + unit.getItem().getName() + " (" + unit.getAssetTag() + ") status: " + beforeStatus + " -> " + request.status() + ". Reason: " + request.reason());
        return mapper.equipment(unit);
    }
}
