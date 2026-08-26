package com.clinic.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "supplier")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Supplier extends BaseEntity {
    @Column(nullable = false, unique = true, length = 180)
    private String name;

    @Column(length = 160)
    private String contactPerson;

    @Column(length = 80)
    private String contactNo;

    @Column(length = 255)
    private String address;

    @Column(nullable = false)
    @Builder.Default
    private boolean active = true;
}
