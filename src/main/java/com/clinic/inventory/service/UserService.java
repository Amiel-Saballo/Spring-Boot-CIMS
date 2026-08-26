package com.clinic.inventory.service;

import com.clinic.inventory.dto.UserRoleDtos;
import com.clinic.inventory.entity.UserAccount;
import com.clinic.inventory.exception.*;
import com.clinic.inventory.repository.UserAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UserService {
    private final UserAccountRepository userRepository;
    private final RoleService roleService;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public Page<UserRoleDtos.UserResponse> list(Pageable pageable) { return userRepository.findAll(pageable).map(this::toDto); }

    @Transactional
    public UserRoleDtos.UserResponse create(UserRoleDtos.CreateUserRequest request) {
        if (userRepository.findByEmailIgnoreCase(request.email()).isPresent()) throw new BusinessRuleException("Email already exists");
        var role = roleService.require(request.roleId());
        if (!role.isActive()) throw new BusinessRuleException("Cannot assign an inactive role");
        UserAccount user = UserAccount.builder().email(request.email().trim().toLowerCase()).fullName(request.fullName().trim())
                .passwordHash(passwordEncoder.encode(request.temporaryPassword())).role(role).active(request.active()).build();
        return toDto(userRepository.save(user));
    }

    @Transactional
    public UserRoleDtos.UserResponse update(Long id, UserRoleDtos.UpdateUserRequest request) {
        UserAccount user = require(id);
        if (userRepository.existsByEmailIgnoreCaseAndIdNot(request.email(), id)) throw new BusinessRuleException("Email already exists");
        var role = roleService.require(request.roleId());
        if (!role.isActive() && !user.getRole().getId().equals(role.getId())) throw new BusinessRuleException("Cannot assign an inactive role");
        user.setEmail(request.email().trim().toLowerCase()); user.setFullName(request.fullName().trim()); user.setRole(role); user.setActive(request.active());
        if (request.password() != null && !request.password().isBlank()) user.setPasswordHash(passwordEncoder.encode(request.password()));
        return toDto(userRepository.save(user));
    }

    @Transactional
    public void setActive(Long id, boolean active) { UserAccount user = require(id); user.setActive(active); userRepository.save(user); }
    public UserAccount require(Long id) { return userRepository.findById(id).orElseThrow(() -> new ResourceNotFoundException("User not found")); }
    private UserRoleDtos.UserResponse toDto(UserAccount u) { return new UserRoleDtos.UserResponse(u.getId(), u.getEmail(), u.getFullName(), u.getRole().getId(), u.getRole().getName(), u.isActive()); }
}
