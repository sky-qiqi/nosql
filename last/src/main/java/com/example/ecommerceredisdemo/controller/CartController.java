package com.example.ecommerceredisdemo.controller;

import com.example.ecommerceredisdemo.service.CartService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

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
}