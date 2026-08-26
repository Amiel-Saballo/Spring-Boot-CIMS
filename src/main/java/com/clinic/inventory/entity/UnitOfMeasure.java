package com.clinic.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "unit_of_measure")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class UnitOfMeasure extends BaseEntity {
    @Column(nullable = false, unique = true, length = 60)
    private String name;
}
