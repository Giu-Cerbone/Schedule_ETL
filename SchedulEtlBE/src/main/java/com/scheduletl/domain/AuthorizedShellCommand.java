package com.scheduletl.domain;

import jakarta.persistence.Column;
import jakarta.persistence.ElementCollection;
import jakarta.persistence.CollectionTable;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OrderColumn;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "authorized_shell_commands")
@Getter
@Setter
@NoArgsConstructor
public class AuthorizedShellCommand {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String type;

    @Column(nullable = false, length = 2048)
    private String executable;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(name = "authorized_shell_command_arguments",
            joinColumns = @JoinColumn(name = "command_id"))
    @OrderColumn(name = "argument_order")
    @Column(name = "argument_value", nullable = false, length = 2048)
    private List<String> arguments = new ArrayList<>();
}
