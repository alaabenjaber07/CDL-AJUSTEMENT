package com.cdl.ajustement.entity;

import lombok.Data;
import javax.persistence.*;

@Entity
@Table(name = "CDL_APP_USER")
@Data
public class AppUser {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    @Column(nullable = false)
    private String password;

    @Column(nullable = false)
    private String role; // ADMIN ou USER
}
