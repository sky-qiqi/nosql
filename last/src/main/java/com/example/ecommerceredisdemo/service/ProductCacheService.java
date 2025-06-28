package com.example.ecommerceredisdemo.service;

import com.example.ecommerceredisdemo.entity.Product;
import com.example.ecommerceredisdemo.repository.ProductRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class ProductCacheService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private static final String PRODUCT_CACHE_PREFIX = "product:detail:";
    private static final String PRODUCT_NULL_PREFIX = "product:null:";
    private static final long CACHE_TTL = 3600; // 1小时
    private static final long NULL_CACHE_TTL = 300; // 5分钟（空值缓存时间短一些）

    /**
     * 获取商品详情（带缓存）
     * @param productId 商品ID
     * @return 商品信息
     */
    public Optional<Product> getProductDetail(String productId) {
        String cacheKey = PRODUCT_CACHE_PREFIX + productId;
        String nullCacheKey = PRODUCT_NULL_PREFIX + productId;

        try {
            // 1. 先检查空值缓存
            String nullFlag = stringRedisTemplate.opsForValue().get(nullCacheKey);
            if (nullFlag != null) {
                log.debug("商品 {} 在空值缓存中找到，返回null", productId);
                return Optional.empty();
            }

            // 2. 检查商品详情缓存
            String cachedProduct = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedProduct != null) {
                Product product = objectMapper.readValue(cachedProduct, Product.class);
                log.debug("商品 {} 从缓存中获取", productId);
                return Optional.of(product);
            }

            // 3. 缓存未命中，从数据库查询
            Optional<Product> productOptional = productRepository.findById(productId);
            
            if (productOptional.isPresent()) {
                // 4. 商品存在，缓存商品详情
                Product product = productOptional.get();
                String productJson = objectMapper.writeValueAsString(product);
                stringRedisTemplate.opsForValue().set(cacheKey, productJson, CACHE_TTL, TimeUnit.SECONDS);
                log.info("商品 {} 已缓存到Redis，TTL: {}秒", productId, CACHE_TTL);
                return productOptional;
            } else {
                // 5. 商品不存在，缓存空值（防止缓存穿透）
                stringRedisTemplate.opsForValue().set(nullCacheKey, "null", NULL_CACHE_TTL, TimeUnit.SECONDS);
                log.info("商品 {} 不存在，已缓存空值标记，TTL: {}秒", productId, NULL_CACHE_TTL);
                return Optional.empty();
            }

        } catch (JsonProcessingException e) {
            log.error("商品 {} 缓存序列化/反序列化失败: {}", productId, e.getMessage());
            // 缓存异常时，直接从数据库查询
            return productRepository.findById(productId);
        } catch (Exception e) {
            log.error("获取商品 {} 详情时发生异常: {}", productId, e.getMessage());
            return Optional.empty();
        }
    }

    /**
     * 更新商品缓存
     * @param product 商品信息
     */
    public void updateProductCache(Product product) {
        if (product == null || product.getProductId() == null) {
            return;
        }

        try {
            String cacheKey = PRODUCT_CACHE_PREFIX + product.getProductId();
            String nullCacheKey = PRODUCT_NULL_PREFIX + product.getProductId();

            // 删除空值缓存
            stringRedisTemplate.delete(nullCacheKey);

            // 更新商品详情缓存
            String productJson = objectMapper.writeValueAsString(product);
            stringRedisTemplate.opsForValue().set(cacheKey, productJson, CACHE_TTL, TimeUnit.SECONDS);
            
            log.info("商品 {} 缓存已更新", product.getProductId());
        } catch (JsonProcessingException e) {
            log.error("更新商品 {} 缓存时序列化失败: {}", product.getProductId(), e.getMessage());
        }
    }

    /**
     * 删除商品缓存
     * @param productId 商品ID
     */
    public void deleteProductCache(String productId) {
        if (productId == null) {
            return;
        }

        String cacheKey = PRODUCT_CACHE_PREFIX + productId;
        String nullCacheKey = PRODUCT_NULL_PREFIX + productId;

        // 删除商品详情缓存和空值缓存
        stringRedisTemplate.delete(cacheKey);
        stringRedisTemplate.delete(nullCacheKey);
        
        log.info("商品 {} 缓存已删除", productId);
    }

    /**
     * 预热热门商品缓存
     * @param productIds 商品ID列表
     */
    public void preloadHotProducts(java.util.List<String> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return;
        }

        log.info("开始预热 {} 个热门商品缓存", productIds.size());
        
        for (String productId : productIds) {
            try {
                Optional<Product> productOptional = productRepository.findById(productId);
                if (productOptional.isPresent()) {
                    updateProductCache(productOptional.get());
                }
            } catch (Exception e) {
                log.error("预热商品 {} 缓存失败: {}", productId, e.getMessage());
            }
        }
        
        log.info("热门商品缓存预热完成");
    }

    /**
     * 获取缓存统计信息
     * @return 缓存统计信息
     */
    public String getCacheStats() {
        try {
            // 统计商品详情缓存数量
            java.util.Set<String> productKeys = stringRedisTemplate.keys(PRODUCT_CACHE_PREFIX + "*");
            java.util.Set<String> nullKeys = stringRedisTemplate.keys(PRODUCT_NULL_PREFIX + "*");
            
            int productCacheCount = productKeys != null ? productKeys.size() : 0;
            int nullCacheCount = nullKeys != null ? nullKeys.size() : 0;
            
            return String.format("商品详情缓存: %d, 空值缓存: %d", productCacheCount, nullCacheCount);
        } catch (Exception e) {
            log.error("获取缓存统计信息失败: {}", e.getMessage());
            return "获取缓存统计信息失败";
        }
    }
} 