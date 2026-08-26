package com.clinic.inventory.entity;

import com.clinic.inventory.enums.BatchStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "batch")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Batch extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "receiving_transaction_id", nullable = false)
    private ReceivingTransaction receivingTransaction;

    @Column(length = 120)
    private String batchNumber;

    @Column(nullable = false)
    private int quantityReceived;

    @Column(nullable = false)
    private int onHand;

    private LocalDate expiryDate;

    @Column(length = 120)
    private String brand;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "location_id", nullable = false)
    private ClinicLocation location;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private BatchStatus status = BatchStatus.ACTIVE;
}
