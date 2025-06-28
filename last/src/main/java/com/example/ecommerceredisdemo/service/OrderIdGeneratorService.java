package com.example.ecommerceredisdemo.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.TimeUnit;

@Service
@Slf4j
public class OrderIdGeneratorService {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    private static final String ORDER_ID_COUNTER_KEY = "order:id:counter";
    private static final String ORDER_ID_DATE_KEY = "order:id:date";
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final int COUNTER_MAX = 999999; // 每天最多999999个订单

    /**
     * 生成分布式唯一订单号
     * 格式：年月日 + 6位序列号，如：20241201000001
     * @return 订单号
     */
    public String generateOrderId() {
        String currentDate = LocalDateTime.now().format(DATE_FORMATTER);
        
        try {
            // 使用Redis的INCR命令原子性递增计数器
            Long counter = stringRedisTemplate.opsForValue().increment(ORDER_ID_COUNTER_KEY);
            
            // 检查是否需要重置计数器（新的一天）
            String lastDate = stringRedisTemplate.opsForValue().get(ORDER_ID_DATE_KEY);
            if (lastDate == null || !currentDate.equals(lastDate)) {
                // 新的一天，重置计数器
                stringRedisTemplate.opsForValue().set(ORDER_ID_COUNTER_KEY, "1");
                stringRedisTemplate.opsForValue().set(ORDER_ID_DATE_KEY, currentDate, 2, TimeUnit.DAYS);
                counter = 1L;
                log.info("新的一天，订单计数器已重置，日期: {}", currentDate);
            } else {
                // 检查计数器是否超出范围
                if (counter > COUNTER_MAX) {
                    log.error("订单计数器超出最大值: {}", counter);
                    throw new RuntimeException("订单生成失败：计数器超出范围");
                }
            }

            // 格式化订单号：年月日 + 6位序列号
            String orderId = currentDate + String.format("%06d", counter);
            log.debug("生成订单号: {}", orderId);
            return orderId;

        } catch (Exception e) {
            log.error("生成订单号失败: {}", e.getMessage());
            throw new RuntimeException("订单号生成失败", e);
        }
    }

    /**
     * 批量生成订单号
     * @param count 生成数量
     * @return 订单号列表
     */
    public java.util.List<String> generateOrderIds(int count) {
        if (count <= 0 || count > 1000) {
            throw new IllegalArgumentException("生成数量必须在1-1000之间");
        }

        java.util.List<String> orderIds = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            orderIds.add(generateOrderId());
        }
        
        log.info("批量生成 {} 个订单号完成", count);
        return orderIds;
    }

    /**
     * 验证订单号格式是否正确
     * @param orderId 订单号
     * @return 是否有效
     */
    public boolean isValidOrderId(String orderId) {
        if (orderId == null || orderId.length() != 14) {
            return false;
        }

        try {
            // 检查日期部分
            String datePart = orderId.substring(0, 8);
            LocalDateTime.parse(datePart, DATE_FORMATTER);
            
            // 检查序列号部分
            String counterPart = orderId.substring(8);
            int counter = Integer.parseInt(counterPart);
            return counter >= 1 && counter <= COUNTER_MAX;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 从订单号中提取日期
     * @param orderId 订单号
     * @return 日期字符串
     */
    public String extractDateFromOrderId(String orderId) {
        if (!isValidOrderId(orderId)) {
            throw new IllegalArgumentException("无效的订单号格式");
        }
        return orderId.substring(0, 8);
    }

    /**
     * 从订单号中提取序列号
     * @param orderId 订单号
     * @return 序列号
     */
    public int extractCounterFromOrderId(String orderId) {
        if (!isValidOrderId(orderId)) {
            throw new IllegalArgumentException("无效的订单号格式");
        }
        return Integer.parseInt(orderId.substring(8));
    }

    /**
     * 获取当前订单计数器状态
     * @return 计数器信息
     */
    public String getCounterStatus() {
        try {
            String currentDate = LocalDateTime.now().format(DATE_FORMATTER);
            String lastDate = stringRedisTemplate.opsForValue().get(ORDER_ID_DATE_KEY);
            String counterStr = stringRedisTemplate.opsForValue().get(ORDER_ID_COUNTER_KEY);
            
            long counter = counterStr != null ? Long.parseLong(counterStr) : 0;
            
            return String.format("当前日期: %s, 最后计数日期: %s, 当前计数: %d", 
                    currentDate, lastDate, counter);
        } catch (Exception e) {
            log.error("获取计数器状态失败: {}", e.getMessage());
            return "获取计数器状态失败";
        }
    }
} 