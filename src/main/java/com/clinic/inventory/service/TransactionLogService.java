package com.clinic.inventory.service;

import com.clinic.inventory.dto.TransactionDtos;
import com.clinic.inventory.entity.*;
import com.clinic.inventory.enums.*;
import com.clinic.inventory.repository.TransactionLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
public class TransactionLogService {
    private final TransactionLogRepository repository;
    private final DtoMapper mapper;

    @Transactional
    public void log(TransactionType type, String reference, UserAccount user, Item item,
                    Integer before, Integer after, String detail) {
        repository.save(TransactionLog.builder().transactionType(type).transactionDate(OffsetDateTime.now())
                .referenceNumber(reference).user(user).affectedItem(item).quantityBefore(before)
                .quantityAfter(after).detail(detail).build());
    }

    @Transactional(readOnly = true)
    public Page<TransactionDtos.Response> search(TransactionType type, ItemCategory category,
                                                 OffsetDateTime from, OffsetDateTime to, Pageable pageable) {
        return repository.search(type, category, from, to, pageable).map(mapper::transaction);
    }
}
