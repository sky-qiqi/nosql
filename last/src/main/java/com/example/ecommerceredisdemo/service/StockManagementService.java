package com.example.ecommerceredisdemo.service;

import com.example.ecommerceredisdemo.entity.Product;
import com.example.ecommerceredisdemo.repository.ProductRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class StockManagementService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductCacheService productCacheService;

    private static final String PRODUCT_STOCK_PREFIX = "product:stock:";
    private static final String STOCK_LOCK_PREFIX = "stock:lock:";
    private static final String STOCK_SYNC_FLAG_PREFIX = "stock:sync:";

    private DefaultRedisScript<Long> checkAndDecrStockScript;
    private DefaultRedisScript<Long> checkAndIncrStockScript;

    @PostConstruct
    public void init() {
        // 加载Lua脚本
        checkAndDecrStockScript = new DefaultRedisScript<>();
        checkAndDecrStockScript.setLocation(new ClassPathResource("lua/check_and_decr_stock.lua"));
        checkAndDecrStockScript.setResultType(Long.class);

        checkAndIncrStockScript = new DefaultRedisScript<>();
        checkAndIncrStockScript.setLocation(new ClassPathResource("lua/check_and_incr_stock.lua"));
        checkAndIncrStockScript.setResultType(Long.class);
    }

    /**
     * 库存预热：将商品库存数据从 MySQL 加载到 Redis 中
     */
    @PostConstruct
    public void preloadStockToRedis() {
        log.info("开始库存预热...");
        
        try {
            List<Product> products = productRepository.findAll();
            int successCount = 0;
            
            for (Product product : products) {
                try {
                    String stockKey = PRODUCT_STOCK_PREFIX + product.getProductId();
                    stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(product.getStock()));
                    successCount++;
                    log.debug("商品 {} 库存预热成功: {}", product.getProductId(), product.getStock());
                } catch (Exception e) {
                    log.error("商品 {} 库存预热失败: {}", product.getProductId(), e.getMessage());
                }
            }
            
            log.info("库存预热完成，成功预热 {} 个商品", successCount);
        } catch (Exception e) {
            log.error("库存预热过程中发生异常: {}", e.getMessage());
        }
    }

    /**
     * 原子扣减库存
     * @param productId 商品ID
     * @param quantity 扣减数量
     * @return 扣减结果：>=0表示成功并返回剩余库存，-1表示库存不足，-2表示商品不存在
     */
    public Long decrementStock(String productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("扣减数量必须大于0");
        }

        String stockKey = PRODUCT_STOCK_PREFIX + productId;
        
        try {
            Long result = stringRedisTemplate.execute(
                    checkAndDecrStockScript,
                    Collections.singletonList(stockKey),
                    String.valueOf(quantity)
            );

            if (result == null) {
                log.error("执行库存扣减脚本失败，返回结果为null. productId: {}", productId);
                throw new RuntimeException("库存扣减失败");
            }

            if (result >= 0) {
                log.info("商品 {} 库存扣减成功，扣减数量: {}，剩余库存: {}", productId, quantity, result);
                // 标记需要同步到数据库
                markStockForSync(productId);
            } else if (result == -1) {
                log.warn("商品 {} 库存不足，当前库存无法满足扣减数量: {}", productId, quantity);
            } else if (result == -2) {
                log.error("商品 {} 库存key不存在，可能未预热", productId);
            }

            return result;
        } catch (Exception e) {
            log.error("商品 {} 库存扣减异常: {}", productId, e.getMessage());
            throw new RuntimeException("库存扣减失败", e);
        }
    }

    /**
     * 原子增加库存（用于取消订单等场景）
     * @param productId 商品ID
     * @param quantity 增加数量
     * @return 增加后的库存数量
     */
    public Long incrementStock(String productId, int quantity) {
        if (quantity <= 0) {
            throw new IllegalArgumentException("增加数量必须大于0");
        }

        String stockKey = PRODUCT_STOCK_PREFIX + productId;
        
        try {
            Long result = stringRedisTemplate.execute(
                    checkAndIncrStockScript,
                    Collections.singletonList(stockKey),
                    String.valueOf(quantity)
            );

            if (result == null) {
                log.error("执行库存增加脚本失败，返回结果为null. productId: {}", productId);
                throw new RuntimeException("库存增加失败");
            }

            if (result >= 0) {
                log.info("商品 {} 库存增加成功，增加数量: {}，当前库存: {}", productId, quantity, result);
                // 标记需要同步到数据库
                markStockForSync(productId);
            } else {
                log.error("商品 {} 库存增加失败，返回结果: {}", productId, result);
            }

            return result;
        } catch (Exception e) {
            log.error("商品 {} 库存增加异常: {}", productId, e.getMessage());
            throw new RuntimeException("库存增加失败", e);
        }
    }

    /**
     * 检查库存是否充足
     * @param productId 商品ID
     * @param quantity 需要数量
     * @return 是否充足
     */
    public Boolean checkStockSufficient(String productId, int quantity) {
        if (quantity <= 0) {
            return false;
        }

        String stockKey = PRODUCT_STOCK_PREFIX + productId;
        String stockStr = stringRedisTemplate.opsForValue().get(stockKey);
        
        if (stockStr == null) {
            log.warn("商品 {} 库存key不存在", productId);
            return false;
        }

        try {
            long currentStock = Long.parseLong(stockStr);
            return currentStock >= quantity;
        } catch (NumberFormatException e) {
            log.error("商品 {} 库存值格式错误: {}", productId, stockStr);
            return false;
        }
    }

    /**
     * 获取商品当前Redis库存
     * @param productId 商品ID
     * @return 库存数量，如果不存在返回null
     */
    public Long getCurrentStock(String productId) {
        String stockKey = PRODUCT_STOCK_PREFIX + productId;
        String stockStr = stringRedisTemplate.opsForValue().get(stockKey);
        
        if (stockStr == null) {
            return null;
        }

        try {
            return Long.parseLong(stockStr);
        } catch (NumberFormatException e) {
            log.error("商品 {} 库存值格式错误: {}", productId, stockStr);
            return null;
        }
    }

    /**
     * 设置商品库存（用于管理员操作）
     * @param productId 商品ID
     * @param stock 库存数量
     * @return 是否设置成功
     */
    public Boolean setStock(String productId, int stock) {
        if (stock < 0) {
            throw new IllegalArgumentException("库存不能为负数");
        }

        String stockKey = PRODUCT_STOCK_PREFIX + productId;
        
        try {
            stringRedisTemplate.opsForValue().set(stockKey, String.valueOf(stock));
            log.info("商品 {} 库存设置为: {}", productId, stock);
            markStockForSync(productId);
            return true;
        } catch (Exception e) {
            log.error("设置商品 {} 库存失败: {}", productId, e.getMessage());
            return false;
        }
    }

    /**
     * 标记库存需要同步到数据库
     * @param productId 商品ID
     */
    private void markStockForSync(String productId) {
        String syncFlagKey = STOCK_SYNC_FLAG_PREFIX + productId;
        stringRedisTemplate.opsForValue().set(syncFlagKey, "1", 300, TimeUnit.SECONDS); // 5分钟过期
    }

    /**
     * 定时任务：将Redis库存数据异步同步回MySQL
     */
    @Scheduled(fixedRate = 30000) // 每30秒执行一次
    @Transactional
    public void syncStockToDatabase() {
        log.debug("开始同步Redis库存数据到数据库...");

        try {
            // 获取所有需要同步的库存key
            java.util.Set<String> syncFlagKeys = stringRedisTemplate.keys(STOCK_SYNC_FLAG_PREFIX + "*");
            
            if (syncFlagKeys == null || syncFlagKeys.isEmpty()) {
                return;
            }

            int syncCount = 0;
            for (String syncFlagKey : syncFlagKeys) {
                String productId = syncFlagKey.replace(STOCK_SYNC_FLAG_PREFIX, "");
                
                try {
                    // 获取Redis中的库存
                    Long redisStock = getCurrentStock(productId);
                    if (redisStock == null) {
                        log.warn("商品 {} Redis库存不存在，跳过同步", productId);
                        continue;
                    }

                    // 更新数据库库存
                    Optional<Product> productOptional = productRepository.findById(productId);
                    if (productOptional.isPresent()) {
                        Product product = productOptional.get();
                        product.setStock(redisStock.intValue());
                        productRepository.save(product);
                        
                        // 更新商品缓存
                        productCacheService.updateProductCache(product);
                        
                        // 删除同步标记
                        stringRedisTemplate.delete(syncFlagKey);
                        syncCount++;
                        
                        log.debug("商品 {} 库存同步成功: {}", productId, redisStock);
                    } else {
                        log.error("商品 {} 在数据库中不存在，但Redis中有库存数据", productId);
                    }
                } catch (Exception e) {
                    log.error("同步商品 {} 库存失败: {}", productId, e.getMessage());
                }
            }

            if (syncCount > 0) {
                log.info("库存同步完成，成功同步 {} 个商品", syncCount);
            }
        } catch (Exception e) {
            log.error("库存同步过程中发生异常: {}", e.getMessage());
        }
    }

    /**
     * 获取库存统计信息
     * @return 统计信息
     */
    public Map<String, Object> getStockStats() {
        try {
            java.util.Set<String> stockKeys = stringRedisTemplate.keys(PRODUCT_STOCK_PREFIX + "*");
            java.util.Set<String> syncFlagKeys = stringRedisTemplate.keys(STOCK_SYNC_FLAG_PREFIX + "*");
            
            int stockCount = stockKeys != null ? stockKeys.size() : 0;
            int pendingSyncCount = syncFlagKeys != null ? syncFlagKeys.size() : 0;
            
            Map<String, Object> stats = new java.util.HashMap<>();
            stats.put("stockCount", stockCount);
            stats.put("pendingSyncCount", pendingSyncCount);
            
            return stats;
        } catch (Exception e) {
            log.error("获取库存统计信息失败: {}", e.getMessage());
            return new java.util.HashMap<>();
        }
    }

    /**
     * 强制同步指定商品的库存到数据库
     * @param productId 商品ID
     * @return 是否同步成功
     */
    public Boolean forceSyncStock(String productId) {
        try {
            Long redisStock = getCurrentStock(productId);
            if (redisStock == null) {
                log.warn("商品 {} Redis库存不存在", productId);
                return false;
            }

            Optional<Product> productOptional = productRepository.findById(productId);
            if (productOptional.isPresent()) {
                Product product = productOptional.get();
                product.setStock(redisStock.intValue());
                productRepository.save(product);
                
                // 更新商品缓存
                productCacheService.updateProductCache(product);
                
                log.info("商品 {} 库存强制同步成功: {}", productId, redisStock);
                return true;
            } else {
                log.error("商品 {} 在数据库中不存在", productId);
                return false;
            }
        } catch (Exception e) {
            log.error("强制同步商品 {} 库存失败: {}", productId, e.getMessage());
            return false;
        }
    }
} 