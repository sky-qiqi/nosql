package com.example.ecommerceredisdemo.service;

import com.example.ecommerceredisdemo.entity.Cart;
import com.example.ecommerceredisdemo.repository.CartRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@Slf4j
public class CartService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private CartRepository cartRepository;

    private static final String CART_PREFIX = "user:"; // user:<userId>:cart

    /**
     * 添加或更新商品到购物车
     * @param userId 用户ID
     * @param skuId 商品SKU ID
     * @param quantity 数量，如果是增量，则为正数；如果是设置最终数量，则直接传
     * @return 更新后的数量
     */
    public Long addItemToCart(String userId, String skuId, int quantity) {
        String cartKey = CART_PREFIX + userId + ":cart";
        // 使用 HINCRBY 来原子性地增加或减少数量
        Long currentQuantity = redisTemplate.opsForHash().increment(cartKey, skuId, quantity);
        log.info("用户 {} 购物车更新：SKU {} 数量 {}", userId, skuId, currentQuantity);
        return currentQuantity;
    }

    /**
     * 移除购物车中的商品
     * @param userId 用户ID
     * @param skuId 商品SKU ID
     * @return 是否成功移除
     */
    public Boolean removeItemFromCart(String userId, String skuId) {
        String cartKey = CART_PREFIX + userId + ":cart";
        Long deletedCount = redisTemplate.opsForHash().delete(cartKey, skuId);
        log.info("用户 {} 购物车移除：SKU {}，删除数量 {}", userId, skuId, deletedCount);
        return deletedCount > 0;
    }

    /**
     * 获取用户购物车所有商品
     * @param userId 用户ID
     * @return Map<skuId, quantity>
     */
    public Map<String, Integer> getCartItems(String userId) {
        String cartKey = CART_PREFIX + userId + ":cart";
        Map<Object, Object> rawCart = redisTemplate.opsForHash().entries(cartKey);
        Map<String, Integer> cartItems = new HashMap<>();
        rawCart.forEach((k, v) -> cartItems.put(k.toString(), (Integer) v));
        return cartItems;
    }

    /**
     * 清空用户购物车
     * @param userId 用户ID
     * @return 是否成功清空
     */
    public Boolean clearCart(String userId) {
        String cartKey = CART_PREFIX + userId + ":cart";
        Boolean result = redisTemplate.delete(cartKey);
        log.info("用户 {} 购物车已清空", userId);
        return result;
    }

    /**
     * 批量添加商品到购物车
     * @param userId 用户ID
     * @param items Map<skuId, quantity>
     * @return 添加的商品数量
     */
    public Integer batchAddItems(String userId, Map<String, Integer> items) {
        if (items == null || items.isEmpty()) {
            return 0;
        }

        String cartKey = CART_PREFIX + userId + ":cart";
        int addedCount = 0;

        for (Map.Entry<String, Integer> entry : items.entrySet()) {
            String skuId = entry.getKey();
            Integer quantity = entry.getValue();
            
            if (quantity > 0) {
                redisTemplate.opsForHash().increment(cartKey, skuId, quantity);
                addedCount++;
            }
        }

        log.info("用户 {} 批量添加 {} 个商品到购物车", userId, addedCount);
        return addedCount;
    }

    /**
     * 批量移除购物车中的商品
     * @param userId 用户ID
     * @param skuIds 商品SKU ID列表
     * @return 成功移除的商品数量
     */
    public Integer batchRemoveItems(String userId, List<String> skuIds) {
        if (skuIds == null || skuIds.isEmpty()) {
            return 0;
        }

        String cartKey = CART_PREFIX + userId + ":cart";
        Long removedCount = redisTemplate.opsForHash().delete(cartKey, skuIds.toArray());
        
        log.info("用户 {} 批量移除 {} 个商品", userId, removedCount);
        return removedCount.intValue();
    }

    /**
     * 获取购物车商品总数
     * @param userId 用户ID
     * @return 商品总数
     */
    public Integer getCartItemCount(String userId) {
        String cartKey = CART_PREFIX + userId + ":cart";
        Long count = redisTemplate.opsForHash().size(cartKey);
        return count != null ? count.intValue() : 0;
    }

    /**
     * 获取购物车商品总数量（所有商品数量之和）
     * @param userId 用户ID
     * @return 商品总数量
     */
    public Integer getCartTotalQuantity(String userId) {
        String cartKey = CART_PREFIX + userId + ":cart";
        Map<Object, Object> cartItems = redisTemplate.opsForHash().entries(cartKey);
        
        return cartItems.values().stream()
                .mapToInt(item -> (Integer) item)
                .sum();
    }

    /**
     * 检查商品是否在购物车中
     * @param userId 用户ID
     * @param skuId 商品SKU ID
     * @return 是否在购物车中
     */
    public Boolean isItemInCart(String userId, String skuId) {
        String cartKey = CART_PREFIX + userId + ":cart";
        return redisTemplate.opsForHash().hasKey(cartKey, skuId);
    }

    /**
     * 获取购物车中指定商品的数量
     * @param userId 用户ID
     * @param skuId 商品SKU ID
     * @return 商品数量，如果不存在返回0
     */
    public Integer getItemQuantity(String userId, String skuId) {
        String cartKey = CART_PREFIX + userId + ":cart";
        Object quantity = redisTemplate.opsForHash().get(cartKey, skuId);
        return quantity != null ? (Integer) quantity : 0;
    }

    /**
     * 设置购物车中商品的数量（覆盖原有数量）
     * @param userId 用户ID
     * @param skuId 商品SKU ID
     * @param quantity 新数量
     * @return 设置后的数量
     */
    public Integer setItemQuantity(String userId, String skuId, int quantity) {
        String cartKey = CART_PREFIX + userId + ":cart";
        
        if (quantity <= 0) {
            // 数量为0或负数，从购物车移除
            redisTemplate.opsForHash().delete(cartKey, skuId);
            log.info("用户 {} 购物车商品 {} 数量设为0，已移除", userId, skuId);
            return 0;
        } else {
            // 设置新数量
            redisTemplate.opsForHash().put(cartKey, skuId, quantity);
            log.info("用户 {} 购物车商品 {} 数量设置为 {}", userId, skuId, quantity);
            return quantity;
        }
    }

    /**
     * 获取购物车统计信息
     * @param userId 用户ID
     * @return 统计信息
     */
    public Map<String, Object> getCartStats(String userId) {
        String cartKey = CART_PREFIX + userId + ":cart";
        Map<Object, Object> cartItems = redisTemplate.opsForHash().entries(cartKey);
        
        Map<String, Object> stats = new HashMap<>();
        stats.put("itemCount", cartItems.size()); // 商品种类数
        stats.put("totalQuantity", cartItems.values().stream()
                .mapToInt(item -> (Integer) item)
                .sum()); // 商品总数量
        stats.put("isEmpty", cartItems.isEmpty());
        
        return stats;
    }

    /**
     * 定时任务：将 Redis 购物车数据同步到 MySQL
     * 实际应用中可能需要更复杂的冲突解决和批量处理逻辑
     */
    @Scheduled(fixedRate = 60000) // 每隔60秒执行一次
    @Transactional
    public void syncCartsToDatabase() {
        log.info("开始同步 Redis 购物车数据到数据库...");

        // 获取所有用户的购物车 Key (这里只是示例，生产环境需要更高效的扫描方式，如SCAN命令)
        Set<String> cartKeys = redisTemplate.keys(CART_PREFIX + "*:cart");
        if (cartKeys == null || cartKeys.isEmpty()) {
            log.info("没有活跃的购物车需要同步。");
            return;
        }

        for (String cartKey : cartKeys) {
            String userId = cartKey.replace(CART_PREFIX, "").replace(":cart", "");
            Map<Object, Object> redisCartItems = redisTemplate.opsForHash().entries(cartKey);

            if (redisCartItems.isEmpty()) {
                // 如果 Redis 购物车为空，删除数据库中对应用户的购物车项
                List<Cart> existingCarts = cartRepository.findByUserId(userId);
                cartRepository.deleteAll(existingCarts);
                log.info("用户 {} 购物车在 Redis 中为空，已从数据库清除。", userId);
                continue;
            }

            // 获取数据库中该用户已有的购物车项
            List<Cart> existingCartsInDb = cartRepository.findByUserId(userId);
            Map<String, Cart> dbCartMap = existingCartsInDb.stream()
                    .collect(Collectors.toMap(Cart::getSkuId, cart -> cart));

            for (Map.Entry<Object, Object> entry : redisCartItems.entrySet()) {
                String skuId = (String) entry.getKey();
                Integer quantity = (Integer) entry.getValue();

                if (quantity <= 0) { // 数量为0或负数，从购物车移除
                    cartRepository.deleteById(new Cart.CartPk() {{ setUserId(userId); setSkuId(skuId); }});
                    redisTemplate.opsForHash().delete(cartKey, skuId); // 同时从Redis删除
                    log.info("用户 {} 购物车 SKU {} 数量为0，已从数据库和Redis移除。", userId, skuId);
                    continue;
                }

                Cart cart;
                if (dbCartMap.containsKey(skuId)) {
                    // 更新现有项
                    cart = dbCartMap.get(skuId);
                    cart.setQuantity(quantity);
                } else {
                    // 新增项
                    cart = new Cart();
                    cart.setUserId(userId);
                    cart.setSkuId(skuId);
                    cart.setQuantity(quantity);
                }
                cartRepository.save(cart);
            }

            // 处理在数据库中存在但在 Redis 中已不存在的商品 (表示从购物车中移除的)
            for (Cart dbCart : existingCartsInDb) {
                if (!redisCartItems.containsKey(dbCart.getSkuId())) {
                    cartRepository.delete(dbCart);
                    log.info("用户 {} 购物车 SKU {} 在Redis中不存在，已从数据库移除。", userId, dbCart.getSkuId());
                }
            }
        }
        log.info("Redis 购物车数据同步到数据库完成。");
    }
}