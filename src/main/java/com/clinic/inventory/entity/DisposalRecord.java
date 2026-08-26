package com.clinic.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

@Entity
@Table(name = "disposal_record")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class DisposalRecord extends BaseEntity {
    @Column(nullable = false, unique = true, length = 100)
    private String referenceNumber;

    @Column(nullable = false)
    private LocalDate disposalDate;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "item_id", nullable = false)
    private Item item;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "batch_id")
    private Batch batch;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "equipment_unit_id")
    private EquipmentUnit equipmentUnit;

    @Column(nullable = false)
    private int quantity;

    @Column(nullable = false, length = 180)
    private String reason;

    @Column(length = 500)
    private String remarks;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false)
    private UserAccount recordedBy;
}
