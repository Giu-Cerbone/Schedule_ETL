package com.scheduletl.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Entity
@Table(name = "user_profiles")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class UserProfile {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String username;

    @Column(nullable = false)
    private String fullName;

    @Column(nullable = false)
    private String email;

    @OneToMany(mappedBy = "ownerProfile", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Scheduler> ownedSchedulers = new ArrayList<>();

    @ManyToMany
    @JoinTable(
            name = "user_profile_groups",
            joinColumns = @JoinColumn(name = "user_profile_id"),
            inverseJoinColumns = @JoinColumn(name = "user_group_id")
    )
    private Set<UserGroup> groups = new HashSet<>();
}
