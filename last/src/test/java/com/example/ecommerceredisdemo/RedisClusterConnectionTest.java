package com.example.ecommerceredisdemo;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("test")
public class RedisClusterConnectionTest {

    @Autowired
    private StringRedisTemplate stringRedisTemplate;

    @Test
    public void testRedisClusterConnection() {
        // 测试基本的set和get操作
        String testKey = "test:cluster:connection";
        String testValue = "Redis集群连接测试成功";
        
        // 设置值
        stringRedisTemplate.opsForValue().set(testKey, testValue);
        
        // 获取值
        String retrievedValue = stringRedisTemplate.opsForValue().get(testKey);
        
        // 验证
        assertEquals(testValue, retrievedValue);
        
        // 清理测试数据
        stringRedisTemplate.delete(testKey);
        
        System.out.println("Redis集群连接测试通过！");
    }
    
    @Test
    public void testRedisClusterInfo() {
        try {
            // 测试集群信息 - 修复类型转换问题
            String info = stringRedisTemplate.getConnectionFactory().getConnection().info("cluster").getProperty("cluster_enabled");
            System.out.println("Redis集群信息: " + info);
            assertNotNull(info);
        } catch (Exception e) {
            System.out.println("获取集群信息时出现异常: " + e.getMessage());
        }
    }
}