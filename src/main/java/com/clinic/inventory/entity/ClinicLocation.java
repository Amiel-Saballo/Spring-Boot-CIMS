package com.clinic.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "clinic_location")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ClinicLocation extends BaseEntity {
    @Column(nullable = false, unique = true, length = 120)
    private String name;
}
