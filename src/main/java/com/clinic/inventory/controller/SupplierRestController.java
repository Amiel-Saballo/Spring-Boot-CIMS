package com.clinic.inventory.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.inventory.dto.SupplierDtos;
import com.clinic.inventory.service.SupplierService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/suppliers")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('PERM_SUPPLIERS','PERM_RECEIVING')")
public class SupplierRestController {
    private final SupplierService service;

    @GetMapping
    public Page<SupplierDtos.Response> list(
            @RequestParam(required = false) Boolean active,
            @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.list(active, pageable);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SupplierDtos.Response create(
            @Valid @RequestBody SupplierDtos.UpsertRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_SUPPLIERS')")
    public SupplierDtos.Response update(@PathVariable Long id,
            @Valid @RequestBody SupplierDtos.UpsertRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_SUPPLIERS')")
    public void delete(@PathVariable Long id) {
        service.softDelete(id);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('PERM_SUPPLIERS')")
    public SupplierDtos.Response reactivate(@PathVariable Long id) {
        return service.reactivate(id);
    }
}
