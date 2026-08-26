package com.clinic.inventory.controller;

import java.util.Set;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.clinic.inventory.dto.UserRoleDtos;
import com.clinic.inventory.service.RoleService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/roles")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('PERM_ROLES')")
public class RoleRestController {
    private final RoleService service;

    @GetMapping
    public Page<UserRoleDtos.RoleResponse> list(
            @PageableDefault(size = 10, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return service.list(pageable);
    }

    @GetMapping("/permissions")
    public Set<String> permissions() {
        return service.allPermissions();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserRoleDtos.RoleResponse create(
            @Valid @RequestBody UserRoleDtos.RoleRequest request) {
        return service.create(request);
    }

    @PutMapping("/{id}")
    public UserRoleDtos.RoleResponse update(@PathVariable Long id,
            @Valid @RequestBody UserRoleDtos.RoleRequest request) {
        return service.update(id, request);
    }

    @PatchMapping("/{id}/active")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void active(@PathVariable Long id, @RequestParam boolean active) {
        service.setActive(id, active);
    }
}
