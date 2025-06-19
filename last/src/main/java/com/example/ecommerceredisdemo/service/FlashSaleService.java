package com.example.ecommerceredisdemo.service;

import com.example.ecommerceredisdemo.entity.Product;
import com.example.ecommerceredisdemo.repository.ProductRepository;
import com.example.ecommerceredisdemo.util.RedisLock;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.data.redis.core.RedisTemplate; // <-- 移除此行
import org.springframework.data.redis.core.StringRedisTemplate; // <-- 新增：导入 StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class FlashSaleService {

    // private RedisTemplate<String, Object> redisTemplate; // <-- 修改为 StringRedisTemplate
    @Autowired
    private StringRedisTemplate stringRedisTemplate; // <-- 注入 StringRedisTemplate

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private RedisLock redisLock;

    private static final String PRODUCT_STOCK_PREFIX = "product:stock:";
    private static final String FLASH_SALE_LOCK_PREFIX = "flash_sale_lock:";

    private DefaultRedisScript<Long> checkAndDecrStockScript;

    @PostConstruct
    public void init() {
        log.info("Initializing flash sale stock from DB to Redis and loading Lua scripts...");
        productRepository.findAll().forEach(product -> {
            String stockKey = PRODUCT_STOCK_PREFIX + product.getProductId();
            // product.getStock() 是 Integer，将其转换为 String 存储到 Redis
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(product.getStock())); // <-- 修改：使用 String.valueOf()
            log.info("Product {}: Stock preheated to Redis: {}", product.getProductId(), product.getStock());
        });

        checkAndDecrStockScript = new DefaultRedisScript<>();
        checkAndDecrStockScript.setLocation(new ClassPathResource("lua/check_and_decr_stock.lua"));
        checkAndDecrStockScript.setResultType(Long.class);
    }

    /**
     * 尝试购买秒杀商品
     * @param userId 用户ID
     * @param productId 商品ID
     * @param quantity 购买数量
     * @return 购买结果消息
     */
    @Transactional
    public String purchaseFlashSaleItem(String userId, String productId, int quantity) {
        if (quantity <= 0) {
            return "购买数量必须大于0";
        }

        String lockKey = FLASH_SALE_LOCK_PREFIX + productId;
        long expireTime = 30;
        String requestId = null;

        try {
            requestId = redisLock.tryLock(lockKey, expireTime);

            if (requestId == null) {
                log.warn("用户 {} 购买商品 {} 失败：未能获取到锁，系统繁忙。", userId, productId);
                return "系统繁忙，请稍后再试！";
            }

            String stockKey = PRODUCT_STOCK_PREFIX + productId;
            // 1. Redis 库存检查并原子扣减 (使用 Lua 脚本)
            Long result = stringRedisTemplate.execute( // <-- 修改：使用 stringRedisTemplate.execute
                    checkAndDecrStockScript,
                    Collections.singletonList(stockKey),
                    String.valueOf(quantity)
            );

            if (result == null) {
                log.error("执行 Redis 库存扣减脚本失败，返回结果为 null. key: {}", stockKey);
                throw new RuntimeException("Redis 库存操作失败");
            }

            if (result >= 0) {
                log.info("用户 {} 成功扣减商品 {} Redis 库存 {}，剩余库存：{}", userId, productId, quantity, result);

                Optional<Product> productOptional = productRepository.findById(productId);
                if (productOptional.isPresent()) {
                    Product product = productOptional.get();
                    product.setStock(result.intValue());
                    productRepository.save(product);
                    log.info("数据库商品 {} 库存更新为：{}", productId, result);
                } else {
                    log.error("数据库中未找到商品 {}，但Redis已扣减库存！", productId);
                    throw new RuntimeException("商品数据异常，数据库无对应商品");
                }

                return "秒杀成功！恭喜您购买到商品！";
            } else if (result == -1) {
                log.warn("用户 {} 购买商品 {} 失败：库存不足。", userId, productId);
                return "库存不足，秒杀失败！";
            } else if (result == -2) {
                log.error("用户 {} 购买商品 {} 失败：Redis 库存 key 不存在。可能商品未预热或ID错误。", userId, productId);
                return "商品不存在或未上架。";
            } else {
                log.error("用户 {} 购买商品 {} 失败：Redis 脚本返回未知结果：{}", userId, productId, result);
                return "购买失败，系统内部内部错误。";
            }
        } catch (Exception e) {
            log.error("秒杀购买异常，用户ID: {}, 商品ID: {}. 错误信息: {}", userId, productId, e.getMessage(), e);
            throw e;
        } finally {
            if (requestId != null) {
                boolean released = redisLock.releaseLock(lockKey, requestId);
                log.info("用户 {} 购买商品 {} 释放锁结果：{}", userId, productId, released ? "成功" : "失败 (锁可能已被抢占或过期)");
            }
        }
    }

    /**
     * 获取商品当前 Redis 库存
     * @param productId 商品ID
     * @return 库存数量
     */
    public Long getProductRedisStock(String productId) {
        // 从 StringRedisTemplate 获取的值直接就是 String
        String stockStr = stringRedisTemplate.opsForValue().get(PRODUCT_STOCK_PREFIX + productId); // <-- 修改：使用 stringRedisTemplate
        try {
            return (stockStr != null) ? Long.parseLong(stockStr) : null;
        } catch (NumberFormatException e) {
            log.error("Redis 库存值 {} 无法转换为数字 for product {}", stockStr, productId);
            return null; // 或者抛出异常，根据业务需求
        }
    }
}