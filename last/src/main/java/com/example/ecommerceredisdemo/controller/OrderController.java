package com.example.ecommerceredisdemo.controller;

import com.example.ecommerceredisdemo.service.OrderIdGeneratorService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/order")
public class OrderController {

    @Autowired
    private OrderIdGeneratorService orderIdGeneratorService;

    /**
     * 生成单个订单号
     * @return 订单号
     */
    @PostMapping("/generate")
    public Map<String, Object> generateOrderId() {
        try {
            String orderId = orderIdGeneratorService.generateOrderId();
            return Map.of(
                "orderId", orderId,
                "success", true
            );
        } catch (Exception e) {
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        }
    }

    /**
     * 批量生成订单号
     * @param count 生成数量
     * @return 订单号列表
     */
    @PostMapping("/generate/batch")
    public Map<String, Object> generateOrderIds(@RequestParam int count) {
        try {
            List<String> orderIds = orderIdGeneratorService.generateOrderIds(count);
            return Map.of(
                "orderIds", orderIds,
                "count", orderIds.size(),
                "success", true
            );
        } catch (IllegalArgumentException e) {
            return Map.of(
                "success", false,
                "error", e.getMessage()
            );
        } catch (Exception e) {
            return Map.of(
                "success", false,
                "error", "生成订单号失败: " + e.getMessage()
            );
        }
    }

    /**
     * 验证订单号格式
     * @param orderId 订单号
     * @return 验证结果
     */
    @GetMapping("/validate/{orderId}")
    public Map<String, Object> validateOrderId(@PathVariable String orderId) {
        boolean isValid = orderIdGeneratorService.isValidOrderId(orderId);
        return Map.of(
            "orderId", orderId,
            "valid", isValid
        );
    }

    /**
     * 从订单号中提取日期
     * @param orderId 订单号
     * @return 日期信息
     */
    @GetMapping("/extract-date/{orderId}")
    public Map<String, Object> extractDateFromOrderId(@PathVariable String orderId) {
        try {
            String date = orderIdGeneratorService.extractDateFromOrderId(orderId);
            return Map.of(
                "orderId", orderId,
                "date", date,
                "success", true
            );
        } catch (IllegalArgumentException e) {
            return Map.of(
                "orderId", orderId,
                "success", false,
                "error", e.getMessage()
            );
        }
    }

    /**
     * 从订单号中提取序列号
     * @param orderId 订单号
     * @return 序列号信息
     */
    @GetMapping("/extract-counter/{orderId}")
    public Map<String, Object> extractCounterFromOrderId(@PathVariable String orderId) {
        try {
            int counter = orderIdGeneratorService.extractCounterFromOrderId(orderId);
            return Map.of(
                "orderId", orderId,
                "counter", counter,
                "success", true
            );
        } catch (IllegalArgumentException e) {
            return Map.of(
                "orderId", orderId,
                "success", false,
                "error", e.getMessage()
            );
        }
    }

    /**
     * 获取订单计数器状态
     * @return 计数器状态信息
     */
    @GetMapping("/counter/status")
    public Map<String, Object> getCounterStatus() {
        String status = orderIdGeneratorService.getCounterStatus();
        return Map.of("status", status);
    }

    /**
     * 解析订单号详细信息
     * @param orderId 订单号
     * @return 详细信息
     */
    @GetMapping("/parse/{orderId}")
    public Map<String, Object> parseOrderId(@PathVariable String orderId) {
        try {
            String date = orderIdGeneratorService.extractDateFromOrderId(orderId);
            int counter = orderIdGeneratorService.extractCounterFromOrderId(orderId);
            
            return Map.of(
                "orderId", orderId,
                "date", date,
                "counter", counter,
                "valid", true,
                "success", true
            );
        } catch (IllegalArgumentException e) {
            return Map.of(
                "orderId", orderId,
                "valid", false,
                "success", false,
                "error", e.getMessage()
            );
        }
    }
} 