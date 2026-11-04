package com.scheduletl.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "process_types")
@Getter
@Setter
@NoArgsConstructor
public class ProcessType {

    @Id
    @Column(length = 30, nullable = false)
    private String code;

    @Column(nullable = false)
    private String description;
}
