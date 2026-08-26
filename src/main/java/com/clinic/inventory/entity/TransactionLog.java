package com.clinic.inventory.entity;

import com.clinic.inventory.enums.TransactionType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "transaction_log")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class TransactionLog extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionType transactionType;

    @Column(nullable = false)
    private OffsetDateTime transactionDate;

    @Column(nullable = false, length = 100)
    private String referenceNumber;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private UserAccount user;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "item_id")
    private Item affectedItem;

    private Integer quantityBefore;
    private Integer quantityAfter;

    @Column(nullable = false, length = 1000)
    private String detail;
}
