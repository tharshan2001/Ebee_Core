package com.ebee.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "agent_code_sequence")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AgentCodeSequence {

    @Id
    @Column(length = 50)
    private String district;

    @Column(name = "next_value", nullable = false)
    private Integer nextValue;
}
