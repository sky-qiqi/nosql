package com.example.ecommerceredisdemo.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderMessage implements Serializable {
    private static final long serialVersionUID = 1L;

    private String userId;
    private String productId;
    private int quantity;
} 