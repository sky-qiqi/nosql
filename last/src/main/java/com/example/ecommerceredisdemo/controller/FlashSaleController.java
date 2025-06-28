package com.example.ecommerceredisdemo.controller;

import com.example.ecommerceredisdemo.service.FlashSaleService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/flash-sale")
@Slf4j
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
    public ResponseEntity<String> purchase(@RequestParam String userId,
                           @RequestParam String productId,
                           @RequestParam(defaultValue = "1") int quantity) {
        try {
            String result = flashSaleService.purchaseFlashSaleItem(userId, productId, quantity);
            if (result.contains("成功")) {
                return ResponseEntity.ok(result);
            } else {
                return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(result);
            }
        } catch (Exception e) {
            log.error("秒杀接口异常: /purchase", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("服务器内部错误，请稍后再试！");
        }
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