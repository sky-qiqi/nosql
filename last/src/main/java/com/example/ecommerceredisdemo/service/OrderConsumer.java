package com.example.ecommerceredisdemo.service;

import com.example.ecommerceredisdemo.config.RabbitMQConfig;
import com.example.ecommerceredisdemo.dto.OrderMessage;
import com.example.ecommerceredisdemo.entity.Order;
import com.example.ecommerceredisdemo.repository.OrderRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@Slf4j
public class OrderConsumer {

    @Autowired
    private OrderRepository orderRepository;

    @Autowired
    private OrderIdGeneratorService orderIdGeneratorService;

    @Autowired
    private ObjectMapper objectMapper;

    @RabbitListener(queues = RabbitMQConfig.ORDER_QUEUE)
    @Transactional
    public void receiveOrderMessage(OrderMessage orderMessage) {
        try {
            log.info("接收到订单消息: {}", orderMessage);

            // 创建订单并保存到数据库
            Order order = new Order();
            order.setOrderId(orderIdGeneratorService.generateOrderId());
            order.setUserId(orderMessage.getUserId());
            order.setProductId(orderMessage.getProductId());
            order.setQuantity(orderMessage.getQuantity());
            order.setOrderTime(LocalDateTime.now());
            order.setStatus(Order.OrderStatus.SUCCESS); // 假设消息能到这里就是成功

            orderRepository.save(order);
            log.info("订单创建成功，订单号: {}", order.getOrderId());

        } catch (Exception e) {
            log.error("处理订单消息失败: {}", orderMessage, e);
            // 这里可以加入消息重试或死信队列逻辑
        }
    }
} 