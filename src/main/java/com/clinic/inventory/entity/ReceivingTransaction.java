package com.clinic.inventory.entity;

import com.clinic.inventory.enums.ReceivingStatus;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "receiving_transaction")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReceivingTransaction extends BaseEntity {
    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "supplier_id", nullable = false)
    private Supplier supplier;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "received_by", nullable = false)
    private UserAccount receivedBy;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "approved_by")
    private UserAccount approvedBy;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private ReceivingStatus status = ReceivingStatus.PENDING;

    @Column(name = "ref_no", nullable = false, unique = true, length = 100)
    private String referenceNumber;

    @Column(nullable = false)
    private LocalDate dateReceived;

    @Column(length = 150)
    private String remarks;

    @Column(length = 150)
    private String returnReason;

    @Column(length = 150)
    private String cancellationReason;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<ReceivingLine> lines = new ArrayList<>();

    public void replaceLines(List<ReceivingLine> newLines) {
        lines.clear();
        for (ReceivingLine line : newLines) {
            line.setTransaction(this);
            lines.add(line);
        }
    }
}
