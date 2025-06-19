package com.example.ecommerceredisdemo.util;

import jakarta.annotation.PostConstruct; // 注意这里是 jakarta.annotation.PostConstruct
import jakarta.annotation.PreDestroy; // 新增：用于在 bean 销毁前执行，注意也需要导入 jakarta.annotation.PreDestroy
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.ConcurrentHashMap; // 用于管理续期任务

@Component
public class RedisLock {

    private final StringRedisTemplate stringRedisTemplate;
    private DefaultRedisScript<Boolean> releaseLockScript;
    private DefaultRedisScript<Boolean> renewLockScript; // 新增：用于锁续期的 Lua 脚本

    // 定时任务执行器，用于看门狗续期
    // 线程池大小可以根据预计的并发锁数量进行调整
    private final ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(Runtime.getRuntime().availableProcessors() * 2);

    // 存储当前正在执行的续期任务，以便在释放锁时取消
    private final ConcurrentHashMap<String, ScheduledFuture<?>> renewalTasks = new ConcurrentHashMap<>();

    public RedisLock(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @PostConstruct
    public void init() {
        // 1. 加载释放锁的 Lua 脚本
        releaseLockScript = new DefaultRedisScript<>();
        releaseLockScript.setLocation(new ClassPathResource("lua/release_lock.lua"));
        releaseLockScript.setResultType(Boolean.class);

        // 2. 新增：加载锁续期的 Lua 脚本
        renewLockScript = new DefaultRedisScript<>();
        renewLockScript.setLocation(new ClassPathResource("lua/renew_lock.lua"));
        renewLockScript.setResultType(Boolean.class);
    }

    /**
     * 尝试获取分布式锁
     * @param lockKey 锁的key
     * @param expireTime 锁的过期时间（秒）
     * @return 请求标识 (requestId)，如果获取失败返回 null
     */
    public String tryLock(String lockKey, long expireTime) {
        String requestId = UUID.randomUUID().toString(); // 使用 UUID 生成唯一的请求标识
        // SET key value EX seconds NX
        // NX: 只在key不存在时设置
        // EX: 设置过期时间
        boolean acquired = Boolean.TRUE.equals(stringRedisTemplate.opsForValue().setIfAbsent(lockKey, requestId, expireTime, TimeUnit.SECONDS));
        if (acquired) {
            // 如果成功获取锁，启动看门狗续期
            // 为了区分不同的锁任务，我们使用 lockKey + requestId 作为唯一标识
            String taskId = lockKey + ":" + requestId;
            scheduleLockRenewal(taskId, lockKey, requestId, expireTime);
            return requestId;
        }
        return null; // 获取锁失败
    }

    /**
     * 释放分布式锁 (使用Lua脚本确保原子性)
     * @param lockKey 锁的key
     * @param requestId 请求标识，用于校验
     * @return 是否释放成功
     */
    public boolean releaseLock(String lockKey, String requestId) {
        // 执行Lua脚本：判断值是否匹配，匹配则删除
        // KEYS[1] = lockKey
        // ARGV[1] = requestId
        Boolean released = stringRedisTemplate.execute(
                releaseLockScript,
                Collections.singletonList(lockKey),
                requestId
        );

        // 如果成功释放锁，取消对应的看门狗续期任务
        String taskId = lockKey + ":" + requestId;
        ScheduledFuture<?> future = renewalTasks.remove(taskId);
        if (future != null) {
            future.cancel(false); // 尝试取消任务，false表示不中断正在执行的任务
            // log.debug("Cancelled renewal task for lock: {}", lockKey); // 调试信息
        }

        return Boolean.TRUE.equals(released);
    }

    /**
     * 定时为锁续期，模拟看门狗
     */
    private void scheduleLockRenewal(String taskId, String lockKey, String requestId, long expireTime) {
        // 续期间隔：通常设置为过期时间的三分之一或一半，且不小于 1 秒
        long renewInterval = Math.max(1, expireTime / 3);

        ScheduledFuture<?> future = scheduler.scheduleAtFixedRate(() -> {
            try {
                // 执行Lua脚本：检查锁是否仍然由当前requestId持有，如果是，则重置过期时间
                Boolean renewed = stringRedisTemplate.execute(
                        renewLockScript,
                        Collections.singletonList(lockKey), // KEYS[1]
                        requestId, // ARGV[1]
                        String.valueOf(expireTime) // ARGV[2]
                );

                if (Boolean.TRUE.equals(renewed)) {
                    // log.debug("Lock renewed for key: {}, requestId: {}", lockKey, requestId);
                } else {
                    // 如果续期失败，说明锁可能已经被释放或被其他客户端抢占
                    // 此时可以取消当前续期任务
                    ScheduledFuture<?> currentFuture = renewalTasks.remove(taskId);
                    if (currentFuture != null) {
                        currentFuture.cancel(false);
                        // log.warn("Lock renewal failed or lock lost for key: {}, requestId: {}. Stopping renewal task.", lockKey, requestId);
                    }
                }
            } catch (Exception e) {
                // log.error("Error during lock renewal for key: {}, requestId: {}. Error: {}", lockKey, requestId, e.getMessage());
                // 出现异常也应该停止续期任务
                ScheduledFuture<?> currentFuture = renewalTasks.remove(taskId);
                if (currentFuture != null) {
                    currentFuture.cancel(false);
                }
            }
        }, renewInterval, renewInterval, TimeUnit.SECONDS); // 延迟 renewInterval 后开始，每 renewInterval 续期一次

        renewalTasks.put(taskId, future); // 将任务存储起来，以便后续取消
    }

    // 在应用关闭时优雅地关闭调度器
    @PreDestroy
    public void shutdownScheduler() {
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(60, TimeUnit.SECONDS)) { // 等待任务完成，最多 60 秒
                scheduler.shutdownNow(); // 强制关闭
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt(); // 恢复中断状态
        }
        // 清空所有任务
        renewalTasks.clear();
    }
}