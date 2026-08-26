package com.clinic.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "issuance_transaction")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class IssuanceTransaction extends BaseEntity {
    @Column(nullable = false, unique = true, length = 100)
    private String referenceNumber;

    @Column(nullable = false)
    private LocalDate dateIssued;

    @Column(nullable = false, length = 80)
    private String employeeNumber;

    @Column(nullable = false, length = 160)
    private String employeeName;

    @Column(length = 120)
    private String department;

    @Column(length = 160)
    private String supervisor;

    @Column(nullable = false, length = 255)
    private String chiefComplaint;

    @Column(nullable = false, length = 120)
    private String disposition;

    @Column(length = 500)
    private String remarks;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "recorded_by", nullable = false)
    private UserAccount recordedBy;

    @OneToMany(mappedBy = "transaction", cascade = CascadeType.ALL, orphanRemoval = true)
    @Builder.Default
    private List<IssuanceLine> lines = new ArrayList<>();

    public void replaceLines(List<IssuanceLine> newLines) {
        lines.clear();
        for (IssuanceLine line : newLines) {
            line.setTransaction(this);
            lines.add(line);
        }
    }
}
