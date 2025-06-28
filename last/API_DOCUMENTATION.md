# 电商系统 Redis 基础功能 API 文档

## 概述
本系统基于 Spring Boot + Redis 实现电商核心功能，包括商品库存管理、购物车管理、商品详情缓存和订单号生成。

## 基础信息
- 基础URL: `http://localhost:8080`
- 数据格式: JSON
- 字符编码: UTF-8

---

## 1. 商品库存管理

### 1.1 获取商品详情（带缓存）
```http
GET /product/detail/{productId}
```

**响应示例：**
```json
{
  "productId": "P001",
  "name": "iPhone 15",
  "stock": 100,
  "createdAt": "2024-12-01T10:00:00"
}
```

### 1.2 获取商品库存
```http
GET /product/stock/{productId}
```

**响应示例：**
```json
{
  "productId": "P001",
  "stock": 100
}
```

### 1.3 检查库存是否充足
```http
GET /product/stock/check?productId=P001&quantity=5
```

**响应示例：**
```json
{
  "productId": "P001",
  "requiredQuantity": 5,
  "sufficient": true
}
```

### 1.4 设置商品库存（管理员）
```http
POST /product/stock/set?productId=P001&stock=200
```

**响应示例：**
```json
{
  "productId": "P001",
  "stock": 200,
  "success": true
}
```

### 1.5 强制同步库存到数据库
```http
POST /product/stock/sync/{productId}
```

### 1.6 获取库存统计信息
```http
GET /product/stock/stats
```

---

## 2. 购物车管理

### 2.1 添加商品到购物车
```http
POST /cart/add?userId=U001&skuId=S001&quantity=2
```

### 2.2 移除购物车商品
```http
DELETE /cart/remove?userId=U001&skuId=S001
```

### 2.3 获取购物车内容
```http
GET /cart/items/{userId}
```

**响应示例：**
```json
{
  "S001": 2,
  "S002": 1
}
```

### 2.4 清空购物车
```http
DELETE /cart/clear/{userId}
```

### 2.5 批量添加商品
```http
POST /cart/batch-add/{userId}
Content-Type: application/json

{
  "S001": 2,
  "S002": 1,
  "S003": 3
}
```

### 2.6 批量移除商品
```http
DELETE /cart/batch-remove/{userId}
Content-Type: application/json

["S001", "S002"]
```

### 2.7 获取购物车商品总数
```http
GET /cart/count/{userId}
```

### 2.8 获取购物车商品总数量
```http
GET /cart/total-quantity/{userId}
```

### 2.9 检查商品是否在购物车中
```http
GET /cart/contains/{userId}/{skuId}
```

### 2.10 获取购物车中商品数量
```http
GET /cart/quantity/{userId}/{skuId}
```

### 2.11 设置购物车中商品数量
```http
PUT /cart/quantity/{userId}/{skuId}?quantity=5
```

### 2.12 获取购物车统计信息
```http
GET /cart/stats/{userId}
```

**响应示例：**
```json
{
  "itemCount": 3,
  "totalQuantity": 6,
  "isEmpty": false
}
```

---

## 3. 商品详情缓存管理

### 3.1 更新商品缓存
```http
POST /product/cache/update
Content-Type: application/json

{
  "productId": "P001",
  "name": "iPhone 15",
  "stock": 100
}
```

### 3.2 删除商品缓存
```http
DELETE /product/cache/{productId}
```

### 3.3 预热热门商品缓存
```http
POST /product/cache/preload
Content-Type: application/json

["P001", "P002", "P003"]
```

### 3.4 获取缓存统计信息
```http
GET /product/cache/stats
```

---

## 4. 订单号生成

### 4.1 生成单个订单号
```http
POST /order/generate
```

**响应示例：**
```json
{
  "orderId": "20241201000001",
  "success": true
}
```

### 4.2 批量生成订单号
```http
POST /order/generate/batch?count=5
```

**响应示例：**
```json
{
  "orderIds": [
    "20241201000001",
    "20241201000002",
    "20241201000003",
    "20241201000004",
    "20241201000005"
  ],
  "count": 5,
  "success": true
}
```

### 4.3 验证订单号格式
```http
GET /order/validate/{orderId}
```

### 4.4 从订单号提取日期
```http
GET /order/extract-date/{orderId}
```

### 4.5 从订单号提取序列号
```http
GET /order/extract-counter/{orderId}
```

### 4.6 解析订单号详细信息
```http
GET /order/parse/{orderId}
```

**响应示例：**
```json
{
  "orderId": "20241201000001",
  "date": "20241201",
  "counter": 1,
  "valid": true,
  "success": true
}
```

### 4.7 获取订单计数器状态
```http
GET /order/counter/status
```

---

## 5. 秒杀功能

### 5.1 购买秒杀商品
```http
POST /flash-sale/purchase?userId=U001&productId=P001&quantity=1
```

### 5.2 查询商品Redis库存
```http
GET /flash-sale/stock/{productId}
```

---

## 错误处理

### 常见错误响应格式
```json
{
  "success": false,
  "error": "错误描述信息"
}
```

### HTTP状态码
- `200`: 请求成功
- `400`: 请求参数错误
- `404`: 资源不存在
- `500`: 服务器内部错误

---

## 技术特性

### 1. 库存管理
- ✅ 库存预热：系统启动时自动加载MySQL库存到Redis
- ✅ 原子扣减：使用Lua脚本确保库存操作的原子性
- ✅ 库存检查：扣减前检查库存是否充足
- ✅ 库存同步：定时将Redis库存同步回MySQL

### 2. 购物车管理
- ✅ Hash结构存储：`user:{userId}:cart`
- ✅ 增删改查：完整的购物车操作
- ✅ 批量操作：支持批量添加和删除
- ✅ 持久化策略：定时同步到数据库

### 3. 商品详情缓存
- ✅ 缓存策略：热门商品详情缓存
- ✅ TTL过期机制：1小时过期时间
- ✅ 缓存更新：商品信息变化时更新缓存
- ✅ 空查询防护：缓存不存在的查询结果

### 4. 订单号生成
- ✅ 分布式ID生成：使用Redis INCR命令
- ✅ 格式：年月日 + 6位序列号
- ✅ 唯一性保证：基于Redis原子操作
- ✅ 日期重置：每天自动重置计数器

### 5. 性能优化
- ✅ 连接池配置：Redis和MySQL连接池优化
- ✅ 批量操作：减少网络往返
- ✅ 异步同步：定时任务异步处理
- ✅ 缓存预热：系统启动时预热关键数据

---

## 部署说明

### 环境要求
- Java 17+
- Redis 6.0+
- MySQL 8.0+
- Spring Boot 3.3.0

### 配置说明
- Redis连接配置：`application.yml`
- 数据库连接配置：`application.yml`
- 连接池参数：已优化配置

### 启动步骤
1. 启动Redis服务
2. 启动MySQL服务
3. 运行Spring Boot应用
4. 系统自动执行库存预热和缓存初始化 

## 高级功能

### 1. 秒杀系统优化

#### 1.1 分布式锁（基于Redis）

- **实现目标**：防止超卖，保证同一时刻只有一个线程能扣减库存。
- **实现方式**：使用`SETNX`+`EXPIRE`或`Redisson`实现分布式锁。

#### 代码示例（RedisLock工具类，已存在，补充说明）：

```java
public boolean tryLock(String lockKey, String requestId, long expireTime) {
    // SET lockKey requestId NX PX expireTime
    return stringRedisTemplate.opsForValue().setIfAbsent(lockKey, requestId, Duration.ofMillis(expireTime));
}
public void unlock(String lockKey, String requestId) {
    // Lua脚本保证原子性删除
    String script = "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end";
    stringRedisTemplate.execute(new DefaultRedisScript<>(script, Long.class), Collections.singletonList(lockKey), requestId);
}
```

#### 1.2 限流措施（QPS控制）

- **实现目标**：防止接口被刷爆，保护后端服务。
- **实现方式**：利用Redis的`INCR`+`EXPIRE`实现滑动窗口限流。

#### 代码示例：

```java
public boolean isAllowed(String userId, int limit, int windowSeconds) {
    String key = "seckill:limit:" + userId + ":" + (System.currentTimeMillis() / 1000 / windowSeconds);
    Long count = stringRedisTemplate.opsForValue().increment(key);
    if (count == 1) {
        stringRedisTemplate.expire(key, windowSeconds, TimeUnit.SECONDS);
    }
    return count <= limit;
}
```

#### 1.3 队列削峰（异步处理）

- **实现目标**：将高并发请求写入Redis队列，后端异步消费，保护数据库。
- **实现方式**：前端请求入队，后端定时/多线程消费。

#### 代码示例：

```java
// 入队
stringRedisTemplate.opsForList().rightPush("seckill:queue", orderInfoJson);
// 消费
while (true) {
    String orderInfo = stringRedisTemplate.opsForList().leftPop("seckill:queue", 1, TimeUnit.SECONDS);
    if (orderInfo != null) {
        // 处理订单逻辑
    }
}
```

### 2. 分布式特性体现（设计文档）

#### 2.1 Redis Cluster 架构说明

- **数据分片**：Redis Cluster将数据分为16384个槽（slot），每个节点负责一部分槽，实现数据水平分片。
- **水平扩展**：通过增加节点，自动迁移槽，实现无缝扩容。
- **客户端直连**：客户端根据key的hash slot直连对应节点，减少中间层延迟。

#### 2.2 高可用方案阐述

- **主从复制**：每个主节点有一个或多个从节点，主节点故障时从节点可自动提升为主。
- **哨兵模式**：Sentinel监控主从节点状态，自动故障转移，保证服务高可用。
- **Cluster自动故障转移**：Redis Cluster内置故障检测和主从切换机制，节点失联时自动切换。

### 3. 性能优化（技术深度）

#### 3.1 Pipeline（管道）

- **应用场景**：批量写入/读取时，减少RTT（网络往返延迟）。
- **代码示例**：

```java
List<Object> results = stringRedisTemplate.executePipelined((RedisCallback<Object>) connection -> {
    for (String key : keys) {
        connection.stringCommands().get(key.getBytes());
    }
    return null;
});
```

#### 3.2 Lua脚本

- **应用场景**：原子扣减库存、复杂事务操作。
- **代码示例**（原子扣减库存）：

```lua
-- check_and_decr_stock.lua
if (redis.call('get', KEYS[1]) or '0') >= ARGV[1] then
    return redis.call('decrby', KEYS[1], ARGV[1])
else
    return -1
end
```

#### 3.3 连接池管理

- **建议**：使用`Lettuce`或`Jedis`连接池，合理配置最大连接数、超时时间，避免连接泄漏。
- **配置示例**（application.yml）：

```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 100
        max-idle: 20
        min-idle: 5
        max-wait: 1000ms
```

#### 3.4 键名设计规范

- **建议**：采用业务前缀+分隔符+唯一标识，如`seckill:stock:productId`，便于管理和监控。

#### 3.5 避免热Key集中访问

- **策略**：
  - 热点商品分片：如`seckill:stock:productId:shardId`
  - 随机过期时间：防止缓存雪崩
  - 热Key预分流：将请求分散到多个Key，后端合并统计

### 4. 设计文档补充（可直接放入`README.md`或`database/README.md`）

#### 4.1 Redis Cluster与高可用方案

- **1. Redis Cluster架构**
  - Redis Cluster通过分片(slot)机制实现数据水平扩展，支持多主多从，节点间自动分配槽位。
  - 客户端直连分片节点，提升吞吐量和可扩展性。

- **2. 高可用方案**
  - 主从复制保证数据冗余，哨兵(Sentinel)自动监控主节点健康，故障时自动切换。
  - Cluster模式下，主节点故障自动提升从节点，保证服务不中断。

---

如需具体某一部分的详细Java代码实现或文档格式，请告知！  
如需将上述设计文档内容自动补充到`README.md`，也可直接操作。 