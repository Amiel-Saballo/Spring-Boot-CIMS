package com.clinic.inventory.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.inventory.dto.DisposalDtos;
import com.clinic.inventory.security.CurrentUserService;
import com.clinic.inventory.service.DisposalService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/disposals")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_DISPOSAL')")
public class DisposalRestController {
    private final DisposalService service;
    private final CurrentUserService currentUser;

    @GetMapping
    public Page<DisposalDtos.Response> list(
            @PageableDefault(size = 10, sort = "disposalDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(pageable);
    }

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    public DisposalDtos.Response batch(
            @Valid @RequestBody DisposalDtos.BatchRequest request,
            Authentication auth) {
        return service.disposeBatch(request, currentUser.require(auth));
    }

    @PostMapping("/equipment")
    @ResponseStatus(HttpStatus.CREATED)
    public DisposalDtos.Response equipment(
            @Valid @RequestBody DisposalDtos.EquipmentRequest request,
            Authentication auth) {
        return service.disposeEquipment(request, currentUser.require(auth));
    }
}
