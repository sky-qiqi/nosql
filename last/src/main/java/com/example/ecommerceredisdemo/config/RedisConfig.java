package com.example.ecommerceredisdemo.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisClusterConfiguration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.Arrays;
import java.util.List;

@Configuration
public class RedisConfig {

    @Bean
    public LettuceConnectionFactory redisConnectionFactory() {
        try {
            // 尝试使用Redis集群配置
            List<String> clusterNodes = Arrays.asList(
                "192.168.231.4:7000",
                "192.168.231.4:7001",
                "192.168.231.5:7002",
                "192.168.231.5:7003",
                "192.168.231.6:7004",
                "192.168.231.6:7005"
            );
            
            RedisClusterConfiguration clusterConfiguration = new RedisClusterConfiguration(clusterNodes);
            clusterConfiguration.setMaxRedirects(3);
            
            return new LettuceConnectionFactory(clusterConfiguration);
        } catch (Exception e) {
            // 如果集群配置失败，使用单机Redis配置
            System.out.println("Redis集群连接失败，使用单机模式: " + e.getMessage());
            RedisStandaloneConfiguration standaloneConfig = new RedisStandaloneConfiguration();
            standaloneConfig.setHostName("127.0.0.1");
            standaloneConfig.setPort(6379);
            
            return new LettuceConnectionFactory(standaloneConfig);
        }
    }

    @Bean
    public RedisTemplate<String, Object> redisTemplate(RedisConnectionFactory redisConnectionFactory) {
        RedisTemplate<String, Object> template = new RedisTemplate<>();
        template.setConnectionFactory(redisConnectionFactory);

        // 设置key的序列化器
        template.setKeySerializer(new StringRedisSerializer());
        // 设置hash key的序列化器
        template.setHashKeySerializer(new StringRedisSerializer());
        // 设置值的序列化器 (这里使用Jackson2JsonRedisSerializer，可以将Java对象序列化为JSON)
        // 也可以使用 GenericJackson2JsonRedisSerializer，它不需要指定泛型
        template.setValueSerializer(new GenericJackson2JsonRedisSerializer());
        // 设置hash值的序列化器
        template.setHashValueSerializer(new GenericJackson2JsonRedisSerializer());

        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory redisConnectionFactory) {
        StringRedisTemplate template = new StringRedisTemplate();
        template.setConnectionFactory(redisConnectionFactory);
        template.afterPropertiesSet();
        return template;
    }

    @Bean
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        // 注册JavaTimeModule以支持Java 8时间类型
        mapper.registerModule(new JavaTimeModule());
        // 禁用将日期写为时间戳
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}