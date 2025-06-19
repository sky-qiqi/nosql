package com.example.ecommerceredisdemo.repository;

import com.example.ecommerceredisdemo.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface ProductRepository extends JpaRepository<Product, String> {
}