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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.inventory.dto.IssuanceDtos;
import com.clinic.inventory.security.CurrentUserService;
import com.clinic.inventory.service.IssuanceService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/issuances")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_ISSUANCE')")
public class IssuanceRestController {
    private final IssuanceService service;
    private final CurrentUserService currentUser;

    @GetMapping
    public Page<IssuanceDtos.Response> list(
            @PageableDefault(size = 10, sort = "dateIssued", direction = Sort.Direction.DESC) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/{id}")
    public IssuanceDtos.Response get(@PathVariable Long id) {
        return service.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public IssuanceDtos.Response create(
            @Valid @RequestBody IssuanceDtos.CreateRequest request,
            Authentication auth) {
        return service.create(request, currentUser.require(auth));
    }

    @PutMapping("/{id}")
    public IssuanceDtos.Response update(@PathVariable Long id,
            @Valid @RequestBody IssuanceDtos.UpdateRequest request,
            Authentication auth) {
        return service.update(id, request, currentUser.require(auth));
    }
}
