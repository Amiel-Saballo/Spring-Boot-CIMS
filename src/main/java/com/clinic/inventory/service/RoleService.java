package com.clinic.inventory.service;

import com.clinic.inventory.dto.UserRoleDtos;
import com.clinic.inventory.entity.*;
import com.clinic.inventory.exception.*;
import com.clinic.inventory.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashSet;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RoleService {
    private final RoleRepository roleRepository;
    private final PermissionRepository permissionRepository;
    private final UserAccountRepository userRepository;

    @Transactional(readOnly = true)
    public Page<UserRoleDtos.RoleResponse> list(Pageable pageable) { return roleRepository.findAll(pageable).map(this::toDto); }

    @Transactional(readOnly = true)
    public Set<String> allPermissions() {
        return permissionRepository.findAll(Sort.by("code").ascending()).stream().map(Permission::getCode)
                .collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new));
    }

    @Transactional
    public UserRoleDtos.RoleResponse create(UserRoleDtos.RoleRequest request) {
        if (roleRepository.findByNameIgnoreCase(request.name()).isPresent()) throw new BusinessRuleException("Role name already exists");
        Role role = Role.builder().name(request.name().trim()).description(request.description().trim()).active(request.active())
                .permissions(resolvePermissions(request.permissionCodes())).build();
        return toDto(roleRepository.save(role));
    }

    @Transactional
    public UserRoleDtos.RoleResponse update(Long id, UserRoleDtos.RoleRequest request) {
        Role role = require(id);
        roleRepository.findByNameIgnoreCase(request.name()).filter(x -> !x.getId().equals(id))
                .ifPresent(x -> { throw new BusinessRuleException("Role name already exists"); });
        if (!request.active() && userRepository.existsByRoleIdAndActiveTrue(id))
            throw new BusinessRuleException("Cannot deactivate a role assigned to active users");
        role.setName(request.name().trim()); role.setDescription(request.description().trim());
        role.setActive(request.active()); role.setPermissions(resolvePermissions(request.permissionCodes()));
        return toDto(roleRepository.save(role));
    }

    @Transactional
    public void setActive(Long id, boolean active) {
        Role role = require(id);
        if (!active && userRepository.existsByRoleIdAndActiveTrue(id)) throw new BusinessRuleException("Cannot deactivate a role assigned to active users");
        role.setActive(active); roleRepository.save(role);
    }

    public Role require(Long id) { return roleRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("Role not found")); }

    private Set<Permission> resolvePermissions(Set<String> codes) {
        Set<Permission> out = new LinkedHashSet<>();
        if (codes == null) return out;
        for (String code : codes) out.add(permissionRepository.findByCodeIgnoreCase(code)
                .orElseThrow(() -> new ResourceNotFoundException("Permission not found: " + code)));
        return out;
    }

    private UserRoleDtos.RoleResponse toDto(Role role) {
        long users = userRepository.findAll().stream().filter(u -> u.isActive() && u.getRole().getId().equals(role.getId())).count();
        return new UserRoleDtos.RoleResponse(role.getId(), role.getName(), role.getDescription(), role.isActive(),
                role.getPermissions().stream().map(Permission::getCode).collect(java.util.stream.Collectors.toCollection(LinkedHashSet::new)), users);
    }
}
