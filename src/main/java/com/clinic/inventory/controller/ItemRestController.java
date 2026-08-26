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

import com.clinic.inventory.dto.ItemDtos;
import com.clinic.inventory.enums.ItemCategory;
import com.clinic.inventory.enums.ItemStatus;
import com.clinic.inventory.service.ItemService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/items")
@RequiredArgsConstructor
@PreAuthorize("hasAnyAuthority('PERM_ITEMS','PERM_RECEIVING','PERM_ISSUANCE')")
public class ItemRestController {
    private final ItemService service;

    @GetMapping
    public Page<ItemDtos.Response> list(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ItemCategory category,
            @RequestParam(required = false) ItemStatus status,
            @RequestParam(required = false) Long unitOfMeasureId,
            @PageableDefault(size = 10, sort = "code", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.search(q, category, status, unitOfMeasureId, pageable);
    }

    @GetMapping("/{id}")
    public ItemDtos.Response get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_ITEMS')")
    public ItemDtos.Response create(
            @Valid @RequestBody ItemDtos.CreateRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('PERM_ITEMS')")
    public ItemDtos.Response update(@PathVariable Long id,
            @Valid @RequestBody ItemDtos.UpdateRequest request) {
        return service.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('PERM_ITEMS')")
    public void delete(@PathVariable Long id) {
        service.softDelete(id);
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasAuthority('PERM_ITEMS')")
    public ItemDtos.Response reactivate(@PathVariable Long id) {
        return service.reactivate(id);
    }
}
