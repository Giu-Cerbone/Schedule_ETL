package com.scheduletl.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "registered_shell_scripts")
@Getter
@Setter
@NoArgsConstructor
public class RegisteredShellScript {

    /** Identifier referenced by process configurations; the column keeps its original name for existing databases. */
    @Id
    @Column(name = "file_name", length = 128)
    private String id;

    @Column(nullable = false, unique = true, length = 2048)
    private String path;
}
