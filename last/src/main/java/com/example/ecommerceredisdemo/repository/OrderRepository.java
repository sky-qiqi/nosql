package com.example.ecommerceredisdemo.repository;

import com.example.ecommerceredisdemo.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface OrderRepository extends JpaRepository<Order, String> {
} 