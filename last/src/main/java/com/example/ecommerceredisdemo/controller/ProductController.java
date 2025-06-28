package com.example.ecommerceredisdemo.controller;

import com.example.ecommerceredisdemo.entity.Product;
import com.example.ecommerceredisdemo.service.ProductCacheService;
import com.example.ecommerceredisdemo.service.StockManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/product")
public class ProductController {

    @Autowired
    private ProductCacheService productCacheService;

    @Autowired
    private StockManagementService stockManagementService;

    /**
     * 获取商品详情（带缓存）
     * @param productId 商品ID
     * @return 商品信息
     */
    @GetMapping("/detail/{productId}")
    public Object getProductDetail(@PathVariable String productId) {
        Optional<Product> product = productCacheService.getProductDetail(productId);
        if (product.isPresent()) {
            return product.get();
        } else {
            return Map.of("message", "商品不存在");
        }
    }

    /**
     * 获取商品库存
     * @param productId 商品ID
     * @return 库存信息
     */
    @GetMapping("/stock/{productId}")
    public Map<String, Object> getProductStock(@PathVariable String productId) {
        Long stock = stockManagementService.getCurrentStock(productId);
        if (stock != null) {
            return Map.of(
                "productId", productId,
                "stock", stock
            );
        } else {
            return Map.of(
                "productId", productId,
                "message", "商品库存不存在"
            );
        }
    }

    /**
     * 检查库存是否充足
     * @param productId 商品ID
     * @param quantity 需要数量
     * @return 检查结果
     */
    @GetMapping("/stock/check")
    public Map<String, Object> checkStock(@RequestParam String productId, 
                                         @RequestParam int quantity) {
        Boolean sufficient = stockManagementService.checkStockSufficient(productId, quantity);
        return Map.of(
            "productId", productId,
            "requiredQuantity", quantity,
            "sufficient", sufficient
        );
    }

    /**
     * 设置商品库存（管理员接口）
     * @param productId 商品ID
     * @param stock 库存数量
     * @return 设置结果
     */
    @PostMapping("/stock/set")
    public Map<String, Object> setStock(@RequestParam String productId, 
                                       @RequestParam int stock) {
        try {
            Boolean success = stockManagementService.setStock(productId, stock);
            return Map.of(
                "productId", productId,
                "stock", stock,
                "success", success
            );
        } catch (IllegalArgumentException e) {
            return Map.of(
                "productId", productId,
                "success", false,
                "error", e.getMessage()
            );
        }
    }

    /**
     * 强制同步库存到数据库
     * @param productId 商品ID
     * @return 同步结果
     */
    @PostMapping("/stock/sync/{productId}")
    public Map<String, Object> forceSyncStock(@PathVariable String productId) {
        Boolean success = stockManagementService.forceSyncStock(productId);
        return Map.of(
            "productId", productId,
            "success", success
        );
    }

    /**
     * 更新商品缓存
     * @param product 商品信息
     * @return 更新结果
     */
    @PostMapping("/cache/update")
    public Map<String, Object> updateProductCache(@RequestBody Product product) {
        productCacheService.updateProductCache(product);
        return Map.of(
            "productId", product.getProductId(),
            "message", "缓存更新成功"
        );
    }

    /**
     * 删除商品缓存
     * @param productId 商品ID
     * @return 删除结果
     */
    @DeleteMapping("/cache/{productId}")
    public Map<String, Object> deleteProductCache(@PathVariable String productId) {
        productCacheService.deleteProductCache(productId);
        return Map.of(
            "productId", productId,
            "message", "缓存删除成功"
        );
    }

    /**
     * 预热热门商品缓存
     * @param productIds 商品ID列表
     * @return 预热结果
     */
    @PostMapping("/cache/preload")
    public Map<String, Object> preloadHotProducts(@RequestBody java.util.List<String> productIds) {
        productCacheService.preloadHotProducts(productIds);
        return Map.of(
            "productIds", productIds,
            "message", "缓存预热完成"
        );
    }

    /**
     * 获取缓存统计信息
     * @return 统计信息
     */
    @GetMapping("/cache/stats")
    public Map<String, Object> getCacheStats() {
        String stats = productCacheService.getCacheStats();
        return Map.of("stats", stats);
    }

    /**
     * 获取库存统计信息
     * @return 统计信息
     */
    @GetMapping("/stock/stats")
    public Map<String, Object> getStockStats() {
        Map<String, Object> stats = stockManagementService.getStockStats();
        return stats;
    }
} 