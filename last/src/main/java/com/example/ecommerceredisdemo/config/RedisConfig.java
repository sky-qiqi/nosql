package com.example.ecommerceredisdemo.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

@Configuration
public class RedisConfig {

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
}