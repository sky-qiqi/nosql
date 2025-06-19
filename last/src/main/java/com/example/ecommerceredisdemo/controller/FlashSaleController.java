package com.example.ecommerceredisdemo.controller;

import com.example.ecommerceredisdemo.service.FlashSaleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/flash-sale")
public class FlashSaleController {

    @Autowired
    private FlashSaleService flashSaleService;

    /**
     * 购买秒杀商品接口
     * @param userId 用户ID
     * @param productId 商品ID
     * @param quantity 购买数量
     * @return 购买结果
     */
    @PostMapping("/purchase")
    public String purchase(@RequestParam String userId,
                           @RequestParam String productId,
                           @RequestParam int quantity) {
        return flashSaleService.purchaseFlashSaleItem(userId, productId, quantity);
    }

    /**
     * 查询商品当前 Redis 库存
     * @param productId 商品ID
     * @return 库存数量
     */
    @GetMapping("/stock/{productId}")
    public Long getStock(@PathVariable String productId) {
        return flashSaleService.getProductRedisStock(productId);
    }
}