package com.clinic.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "user_account")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UserAccount extends BaseEntity {
    @Column(nullable = false, unique = true, length = 190)
    private String email;

    @Column(nullable = false, length = 160)
    private String passwordHash;

    @Column(nullable = false, length = 160)
    private String fullName;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private Role role;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
