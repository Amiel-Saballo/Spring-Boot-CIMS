package com.clinic.inventory.entity;

import com.clinic.inventory.enums.ReportType;
import jakarta.persistence.*;
import lombok.*;

import java.time.OffsetDateTime;

@Entity
@Table(name = "report_record")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReportRecord extends BaseEntity {
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 40)
    private ReportType reportType;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "generated_by", nullable = false)
    private UserAccount generatedBy;

    @Column(nullable = false)
    private OffsetDateTime generatedAt;

    @Column(length = 1000)
    private String parametersJson;
}
