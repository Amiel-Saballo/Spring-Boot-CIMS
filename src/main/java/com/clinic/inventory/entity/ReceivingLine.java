package com.clinic.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "receiving_line")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReceivingLine extends BaseEntity {
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "receiving_transaction_id", nullable = false)
    private ReceivingTransaction transaction;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @Column(nullable = false)
    private int quantity;

    @Column(length = 120)
    private String brand;

    @Column(length = 120)
    private String batchNumber;

    private LocalDate expiryDate;

    @Column(length = 120)
    private String model;

    @Column(length = 160)
    private String serialNumber;

    @Column(length = 160)
    private String assetTag;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private ClinicLocation location;
}
