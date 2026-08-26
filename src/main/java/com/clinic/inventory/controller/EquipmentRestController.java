package com.clinic.inventory.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.inventory.dto.EquipmentDtos;
import com.clinic.inventory.security.CurrentUserService;
import com.clinic.inventory.service.EquipmentService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/equipment")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_EQUIPMENT')")
public class EquipmentRestController {
    private final EquipmentService service;
    private final CurrentUserService currentUser;

    @GetMapping
    public Page<EquipmentDtos.Response> list(
            @PageableDefault(size = 10, sort = "assetTag", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.list(pageable);
    }

    @PatchMapping("/{id}/status")
    @PreAuthorize("hasAuthority('PERM_EQUIPMENT') and !hasRole('SUPERVISOR')")
    public EquipmentDtos.Response updateStatus(@PathVariable Long id,
            @Valid @RequestBody EquipmentDtos.StatusUpdateRequest request,
            Authentication auth) {
        return service.updateStatus(id, request, currentUser.require(auth));
    }
}
