package com.clinic.inventory.controller;

import java.time.OffsetDateTime;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.inventory.dto.TransactionDtos;
import com.clinic.inventory.enums.ItemCategory;
import com.clinic.inventory.enums.TransactionType;
import com.clinic.inventory.service.TransactionLogService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/transaction-logs")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_TRANSACTION_LOG')")
public class TransactionLogRestController {
    private final TransactionLogService service;

    @GetMapping
    public Page<TransactionDtos.Response> list(
            @RequestParam(required = false) TransactionType transactionType,
            @RequestParam(required = false) ItemCategory itemCategory,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime to,
            @PageableDefault(size = 10, sort = "transactionDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.search(transactionType, itemCategory, from, to,
                pageable);
    }
}
