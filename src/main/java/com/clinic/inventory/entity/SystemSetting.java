package com.clinic.inventory.entity;

import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "system_setting")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class SystemSetting extends BaseEntity {
    @Column(name = "setting_key", nullable = false, unique = true, length = 100)
    private String key;

    @Column(name = "setting_value", nullable = false, length = 255)
    private String value;
}
