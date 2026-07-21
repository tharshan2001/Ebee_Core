package com.ebee.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "whitelist_config")
@Getter
@Setter
@NoArgsConstructor
public class WhitelistConfig {

    @Id
    private Integer id;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
