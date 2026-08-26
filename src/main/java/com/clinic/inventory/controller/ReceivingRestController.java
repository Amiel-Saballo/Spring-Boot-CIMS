package com.clinic.inventory.controller;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.inventory.dto.ReceivingDtos;
import com.clinic.inventory.enums.ReceivingStatus;
import com.clinic.inventory.security.CurrentUserService;
import com.clinic.inventory.service.ReceivingService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/receiving")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_RECEIVING')")
public class ReceivingRestController {
    private final ReceivingService service;
    private final CurrentUserService currentUser;

    @GetMapping
    public Page<ReceivingDtos.Response> list(
            @RequestParam(required = false) ReceivingStatus status,
            @RequestParam(required = false) Long receivedBy,
            @PageableDefault(size = 10, sort = "dateReceived", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(status, receivedBy, pageable);
    }

    @GetMapping("/{id}")
    public ReceivingDtos.Response get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('PERM_RECEIVING') and !hasRole('SUPERVISOR')")
    public ReceivingDtos.Response create(
            @Valid @RequestBody ReceivingDtos.CreateRequest request,
            Authentication auth) {
        return service.create(request, currentUser.require(auth));
    }

    @PutMapping("/{id}/returned")
    public ReceivingDtos.Response updateReturned(@PathVariable Long id,
            @Valid @RequestBody ReceivingDtos.UpdateReturnedRequest request,
            Authentication auth) {
        return service.updateReturned(id, request, currentUser.require(auth));
    }

    @PutMapping("/{id}/returned/lines/{lineId}")
    public ReceivingDtos.Response updateReturnedLine(@PathVariable Long id,
            @PathVariable Long lineId,
            @Valid @RequestBody ReceivingDtos.LineRequest request,
            Authentication auth) {
        return service.updateReturnedLine(id, lineId, request,
                currentUser.require(auth));
    }

    @PostMapping("/{id}/resubmit")
    public ReceivingDtos.Response resubmit(@PathVariable Long id,
            Authentication auth) {
        return service.resubmit(id, currentUser.require(auth));
    }

    @PostMapping("/{id}/cancel")
    public ReceivingDtos.Response cancel(@PathVariable Long id,
            @Valid @RequestBody ReceivingDtos.ReasonRequest request,
            Authentication auth) {
        return service.cancel(id, request, currentUser.require(auth));
    }
}
