package com.example.ecommerceredisdemo.controller;

import com.example.ecommerceredisdemo.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/cart")
public class CartController {

    @Autowired
    private CartService cartService;

    /**
     * 添加/更新商品到购物车
     * @param userId 用户ID
     * @param skuId 商品SKU ID
     * @param quantity 增量或设置数量
     * @return 当前商品在购物车中的数量
     */
    @PostMapping("/add")
    public Long addItem(@RequestParam String userId,
                        @RequestParam String skuId,
                        @RequestParam int quantity) {
        return cartService.addItemToCart(userId, skuId, quantity);
    }

    /**
     * 移除购物车中的商品
     * @param userId 用户ID
     * @param skuId 商品SKU ID
     * @return 是否成功移除
     */
    @DeleteMapping("/remove")
    public Boolean removeItem(@RequestParam String userId,
                              @RequestParam String skuId) {
        return cartService.removeItemFromCart(userId, skuId);
    }

    /**
     * 获取用户购物车所有商品
     * @param userId 用户ID
     * @return 购物车内容 (Map<skuId, quantity>)
     */
    @GetMapping("/items/{userId}")
    public Map<String, Integer> getCartItems(@PathVariable String userId) {
        return cartService.getCartItems(userId);
    }

    /**
     * 清空用户购物车
     * @param userId 用户ID
     * @return 是否成功清空
     */
    @DeleteMapping("/clear/{userId}")
    public Boolean clearCart(@PathVariable String userId) {
        return cartService.clearCart(userId);
    }

    /**
     * 批量添加商品到购物车
     * @param userId 用户ID
     * @param items 商品列表 Map<skuId, quantity>
     * @return 添加的商品数量
     */
    @PostMapping("/batch-add/{userId}")
    public Integer batchAddItems(@PathVariable String userId,
                                @RequestBody Map<String, Integer> items) {
        return cartService.batchAddItems(userId, items);
    }

    /**
     * 批量移除购物车中的商品
     * @param userId 用户ID
     * @param skuIds 商品SKU ID列表
     * @return 成功移除的商品数量
     */
    @DeleteMapping("/batch-remove/{userId}")
    public Integer batchRemoveItems(@PathVariable String userId,
                                   @RequestBody List<String> skuIds) {
        return cartService.batchRemoveItems(userId, skuIds);
    }

    /**
     * 获取购物车商品总数
     * @param userId 用户ID
     * @return 商品总数
     */
    @GetMapping("/count/{userId}")
    public Integer getCartItemCount(@PathVariable String userId) {
        return cartService.getCartItemCount(userId);
    }

    /**
     * 获取购物车商品总数量（所有商品数量之和）
     * @param userId 用户ID
     * @return 商品总数量
     */
    @GetMapping("/total-quantity/{userId}")
    public Integer getCartTotalQuantity(@PathVariable String userId) {
        return cartService.getCartTotalQuantity(userId);
    }

    /**
     * 检查商品是否在购物车中
     * @param userId 用户ID
     * @param skuId 商品SKU ID
     * @return 是否在购物车中
     */
    @GetMapping("/contains/{userId}/{skuId}")
    public Boolean isItemInCart(@PathVariable String userId,
                               @PathVariable String skuId) {
        return cartService.isItemInCart(userId, skuId);
    }

    /**
     * 获取购物车中指定商品的数量
     * @param userId 用户ID
     * @param skuId 商品SKU ID
     * @return 商品数量
     */
    @GetMapping("/quantity/{userId}/{skuId}")
    public Integer getItemQuantity(@PathVariable String userId,
                                  @PathVariable String skuId) {
        return cartService.getItemQuantity(userId, skuId);
    }

    /**
     * 设置购物车中商品的数量（覆盖原有数量）
     * @param userId 用户ID
     * @param skuId 商品SKU ID
     * @param quantity 新数量
     * @return 设置后的数量
     */
    @PutMapping("/quantity/{userId}/{skuId}")
    public Integer setItemQuantity(@PathVariable String userId,
                                  @PathVariable String skuId,
                                  @RequestParam int quantity) {
        return cartService.setItemQuantity(userId, skuId, quantity);
    }

    /**
     * 获取购物车统计信息
     * @param userId 用户ID
     * @return 统计信息
     */
    @GetMapping("/stats/{userId}")
    public Map<String, Object> getCartStats(@PathVariable String userId) {
        return cartService.getCartStats(userId);
    }
}