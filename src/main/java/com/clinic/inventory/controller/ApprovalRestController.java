package com.clinic.inventory.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.inventory.dto.ReceivingDtos;
import com.clinic.inventory.enums.ReceivingStatus;
import com.clinic.inventory.security.CurrentUserService;
import com.clinic.inventory.service.ReceivingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/approvals")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_APPROVALS')")
public class ApprovalRestController {
    private final ReceivingService service;
    private final CurrentUserService currentUser;

    @GetMapping
    public Page<ReceivingDtos.Response> pending(
            @PageableDefault(size = 10, sort = "dateReceived", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(ReceivingStatus.PENDING, null, pageable);
    }

    @GetMapping("/{id}")
    public ReceivingDtos.Response review(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping("/{id}/approve")
    public ReceivingDtos.Response approve(@PathVariable Long id,
            Authentication auth) {
        return service.approve(id, currentUser.require(auth));
    }

    @PostMapping("/{id}/return")
    public ReceivingDtos.Response returnToNurse(@PathVariable Long id,
            @Valid @RequestBody ReceivingDtos.ReasonRequest request,
            Authentication auth) {
        return service.returnToNurse(id, request, currentUser.require(auth));
    }
}
