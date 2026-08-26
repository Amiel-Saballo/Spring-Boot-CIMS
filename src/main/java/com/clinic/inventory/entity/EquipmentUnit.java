package com.clinic.inventory.entity;

import com.clinic.inventory.enums.EquipmentStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "equipment_unit")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class EquipmentUnit extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "receiving_transaction_id", nullable = false)
    private ReceivingTransaction receivingTransaction;

    @Column(nullable = false, unique = true, length = 160)
    private String assetTag;

    @Column(nullable = false, unique = true, length = 160)
    private String serialNumber;

    @Column(length = 120)
    private String brand;

    @Column(length = 120)
    private String model;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private ClinicLocation location;

    @Column(nullable = false)
    private LocalDate acquiredDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private EquipmentStatus status = EquipmentStatus.IN_USE;
}
