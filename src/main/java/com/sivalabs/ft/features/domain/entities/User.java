package com.sivalabs.ft.features.domain.entities;

import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Size;

@Entity
@Table(name = "users")
public class User {
    @Size(max = 50)

    private String username;
}
