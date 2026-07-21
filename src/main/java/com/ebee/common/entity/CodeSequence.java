package com.ebee.common.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "code_sequence")
public class CodeSequence {

    @Id
    @Column(name = "code", length = 20, nullable = false)
    private String code;

    protected CodeSequence() {}

    public CodeSequence(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
