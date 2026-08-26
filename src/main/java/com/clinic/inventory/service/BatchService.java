package com.clinic.inventory.service;

import com.clinic.inventory.dto.BatchDtos;
import com.clinic.inventory.enums.ReceivingStatus;
import com.clinic.inventory.repository.BatchRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BatchService {
    private final BatchRepository repository;
    private final DtoMapper mapper;

    @Transactional(readOnly = true)
    public Page<BatchDtos.Response> list(Pageable pageable) { return repository.findAll(pageable).map(mapper::batch); }

    @Transactional(readOnly = true)
    public BatchDtos.Response get(Long id) {
        var batch = repository.findById(id).orElseThrow(() -> new com.clinic.inventory.exception.ResourceNotFoundException("Batch not found"));
        return mapper.batch(batch);
    }

    public boolean isEditable(Long id) {
        return repository.findById(id).map(b -> b.getReceivingTransaction().getStatus() != ReceivingStatus.APPROVED).orElse(false);
    }
}
