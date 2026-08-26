package com.clinic.inventory.dto;

import jakarta.validation.constraints.*;
import java.util.Set;

public final class UserRoleDtos {
    private UserRoleDtos() {}

    public record RoleRequest(@NotBlank @Size(max=80) String name,
                              @NotBlank @Size(max=255) String description,
                              boolean active,
                              Set<@NotBlank String> permissionCodes) {}
    public record RoleResponse(Long id, String name, String description, boolean active,
                               Set<String> permissionCodes, long activeUserCount) {}

    public record CreateUserRequest(@Email @NotBlank @Size(max=190) String email,
                                    @NotBlank @Size(max=160) String fullName,
                                    @NotBlank @Size(min=8,max=100) String temporaryPassword,
                                    @NotNull Long roleId,
                                    boolean active) {}
    public record UpdateUserRequest(@Email @NotBlank @Size(max=190) String email,
                                    @NotBlank @Size(max=160) String fullName,
                                    @Size(min=8,max=100) String password,
                                    @NotNull Long roleId,
                                    boolean active) {}
    public record UserResponse(Long id, String email, String fullName, Long roleId,
                               String roleName, boolean active) {}
}
