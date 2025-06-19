package com.example.ecommerceredisdemo.entity;

import jakarta.persistence.*;
import lombok.Data;
import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "cart")
@IdClass(Cart.CartPk.class) // 联合主键
public class Cart {
    @Id
    @Column(name = "user_id")
    private String userId;

    @Id
    @Column(name = "sku_id")
    private String skuId;

    private Integer quantity;

    @Column(name = "updated_at", insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    // 联合主键类
    @Data
    public static class CartPk implements Serializable {
        private String userId;
        private String skuId;
    }
}