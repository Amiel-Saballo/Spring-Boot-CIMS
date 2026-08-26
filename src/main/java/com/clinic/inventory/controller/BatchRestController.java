package com.clinic.inventory.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.inventory.dto.BatchDtos;
import com.clinic.inventory.service.BatchService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/batches")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_BATCHES')")
public class BatchRestController {
    private final BatchService service;

    @GetMapping
    public Page<BatchDtos.Response> list(
            @PageableDefault(size = 10, sort = "batchNumber", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    public BatchDtos.Response get(@PathVariable Long id) {
        return service.get(id);
    }
}
