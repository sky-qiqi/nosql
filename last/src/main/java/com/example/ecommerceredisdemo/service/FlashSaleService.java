package com.example.ecommerceredisdemo.service;

import com.example.ecommerceredisdemo.config.RabbitMQConfig;
import com.example.ecommerceredisdemo.dto.OrderMessage;
import com.example.ecommerceredisdemo.entity.Product;
import com.example.ecommerceredisdemo.repository.ProductRepository;
import com.example.ecommerceredisdemo.util.RedisLock;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.data.redis.core.RedisTemplate; // <-- 移除此行
import org.springframework.data.redis.core.StringRedisTemplate; // <-- 新增：导入 StringRedisTemplate
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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

    @Autowired
    private RabbitTemplate rabbitTemplate;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private StockManagementService stockManagementService;

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
        // 移除分布式锁，直接使用Lua脚本原子操作
        Long remainingStock = stockManagementService.decrementStock(productId, quantity);
        
        if (remainingStock >= 0) {
            // 库存扣减成功，异步发送订单消息
            try {
                OrderMessage orderMessage = new OrderMessage(userId, productId, quantity);
                rabbitTemplate.convertAndSend(RabbitMQConfig.ORDER_EXCHANGE, 
                                            RabbitMQConfig.ORDER_ROUTING_KEY, 
                                            orderMessage);
                log.info("成功发送订单消息: userId={}, productId={}", userId, productId);
                return "抢购成功，订单正在处理中！";
            } catch (Exception e) {
                // 消息发送失败，需要回滚库存
                stockManagementService.incrementStock(productId, quantity);
                log.error("发送订单消息失败，已回滚库存: userId={}, productId={}", userId, productId, e);
                return "系统繁忙，请稍后再试";
            }
        } else if (remainingStock == -1) {
            return "商品库存不足";
        } else {
            return "商品不存在";
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