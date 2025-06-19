package com.example.ecommerceredisdemo.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "product")
public class Product {
    @Id
    private String productId;
    private String name;
    private Integer stock;

    @Column(name = "created_at", insertable = false, updatable = false)
    private LocalDateTime createdAt;
}