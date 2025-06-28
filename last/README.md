# 高性能电商秒杀系统

## 项目简介

本项目是一个基于 Spring Boot 的**高性能电商秒杀系统**。它深度整合了 Redis、RabbitMQ 与 MySQL，通过**全链路异步化**和**多级瓶颈优化**，实现了一个能够应对海量并发的、健壮的秒杀场景解决方案。

本项目的核心亮点不仅仅是功能的实现，更在于展示了如何通过科学的性能测试，一步步定位并解决从连接池、同步架构到代码逻辑的各类性能瓶颈，最终将系统性能压榨至硬件极限的全过程。

## 技术栈

- **后端框架**: Spring Boot 3.3.0
- **消息队列**: RabbitMQ
- **缓存数据库**: Redis 6.0+
- **关系数据库**: MySQL 8.0+
- **ORM框架**: Spring Data JPA
- **连接池**: HikariCP (数据库), Lettuce (Redis)
- **构建工具**: Maven

## 核心功能

### 1. 高性能秒杀核心
- **异步下单**: 秒杀请求在Redis扣减库存成功后，立即通过RabbitMQ发送异步消息创建订单，将接口响应时间压缩至几十毫秒。
- **原子扣减**: 使用Lua脚本在Redis中原子化地完成"库存检查与扣减"，将两次网络请求合并为一次，性能最大化。
- **分布式锁**: 基于Redis的分布式锁，确保在高并发下同一商品不会被超卖。

### 2. 异步订单处理
- **消息驱动**: 通过`@RabbitListener`注解实现消息驱动的订单消费者，异步地将订单信息持久化到MySQL。
- **削峰填谷**: 利用消息队列的缓冲能力，平稳地处理秒杀洪峰对数据库的写入压力，保护下游系统。
- **高效序列化**: 配置全局`Jackson2JsonMessageConverter`，由框架底层高效处理消息对象的序列化与反序列化，降低CPU开销。

### 3. 商品详情缓存
- **缓存策略**: 采用标准的"Cache-Aside"模式，优先从Redis读取商品数据。
- **缓存穿透防护**: 对数据库中不存在的商品，缓存一个短TTL的空值，防止恶意请求攻击数据库。

### 4. 购物车管理
- **Hash结构存储**: 利用Redis的Hash结构高效存储购物车数据。
- **原子操作**: 通过`HINCRBY`等命令保证购物车操作的原子性。

## 数据库设计

### 1. 商品表 (product)
```sql
CREATE TABLE `product` (
  `product_id` varchar(50) NOT NULL,
  `name` varchar(200) NOT NULL,
  `stock` int NOT NULL DEFAULT '0',
  PRIMARY KEY (`product_id`)
) ENGINE=InnoDB;
```

### 2. 购物车表 (cart)
```sql
CREATE TABLE `cart` (
  `user_id` varchar(50) NOT NULL,
  `sku_id` varchar(50) NOT NULL,
  `quantity` int NOT NULL DEFAULT '1',
  PRIMARY KEY (`user_id`,`sku_id`)
) ENGINE=InnoDB;
```

### 3. 订单表 (orders)
```sql
CREATE TABLE `orders` (
  `order_id` varchar(255) NOT NULL,
  `order_time` datetime(6) NOT NULL,
  `product_id` varchar(255) NOT NULL,
  `quantity` int NOT NULL,
  `status` enum('FAILED','PROCESSING','SUCCESS') NOT NULL,
  `user_id` varchar(255) NOT NULL,
  PRIMARY KEY (`order_id`)
) ENGINE=InnoDB;
```

## 项目结构
```
src/
└── main/
    ├── java/
    │   └── com/example/ecommerceredisdemo/
    │       ├── config/
    │       │   ├── RabbitMQConfig.java       # RabbitMQ核心配置
    │       │   └── RedisConfig.java          # Redis配置
    │       ├── controller/
    │       ├── dto/
    │       │   └── OrderMessage.java         # 订单消息DTO
    │       ├── entity/
    │       │   ├── Order.java                # 订单实体
    │       │   └── ...
    │       ├── repository/
    │       │   ├── OrderRepository.java      # 订单仓库
    │       │   └── ...
    │       ├── service/
    │       │   ├── FlashSaleService.java     # 秒杀服务 (消息生产者)
    │       │   ├── OrderConsumer.java        # 订单服务 (消息消费者)
    │       │   └── ...
    │       ├── util/
    │       │   └── RedisLock.java            # Redis分布式锁
    │       └── ...
    └── resources/
        ├── application.yml                   # 全局配置
        └── lua/
            └── ...                           # Lua脚本
```

## 环境要求
- JDK 17+
- Maven 3.6+
- Docker (推荐，用于快速启动中间件)
- RabbitMQ 3.x
- Redis 6.0+
- MySQL 8.0+

## 快速开始

### 1. 启动中间件 (推荐使用Docker)
```bash
# 启动 RabbitMQ (带管理后台)
docker run -d --name rabbitmq -p 5672:5672 -p 15672:15672 rabbitmq:3-management

# 启动 Redis
docker run -d --name redis -p 6379:6379 redis:6-alpine

# 启动 MySQL
docker run -d --name mysql -p 3306:3306 -e MYSQL_ROOT_PASSWORD=root -e MYSQL_DATABASE=nosql mysql:8.0
```

### 2. 数据库初始化
使用数据库客户端连接到MySQL，执行`database/init.sql`脚本创建表和插入初始数据。

### 3. 检查配置
确认 `src/main/resources/application.yml` 中的数据库、Redis、RabbitMQ连接信息与你的环境一致。

### 4. 启动应用
```bash
# 直接运行
mvn spring-boot:run
```

## 性能与架构亮点

经过全链路优化，系统在单机（普通开发PC）上最终达到了**1800+ TPS**的吞吐量，并将**核心写操作的中位响应时间压缩至27毫秒**。

- **全链路异步化**: 通过RabbitMQ实现订单处理的异步化，实现服务解耦和流量削峰填谷。
- **多级连接池优化**:
  - `Tomcat`: 最大工作线程数提升至 **1000**。
  - `Hikari (DB)`: 最大连接数提升至 **150**。
  - `Lettuce (Redis)`: 最大连接数提升至 **250**。
  - `RabbitMQ`: 通道缓存大小提升至 **1000**。
- **极致代码优化**:
  - **原子操作**: 通过Lua脚本将多次Redis操作合并为一次，极大减少了锁内开销。
  - **高效序列化**: 利用全局消息转换器替代手动的JSON转换，显著降低CPU负载。
- **瓶颈转移**: 成功将系统的性能瓶颈从软件层面（连接池、架构、代码）**转移到了硬件层面（CPU极限）**，达成了软件优化的最终目标。

## API文档
详细的API文档请参考：`API_DOCUMENTATION.md`
完整的性能测试报告请参考：`测试文档.md`

## 部署说明

### 生产环境配置

1. **Redis配置优化**
```yaml
spring:
  redis:
    lettuce:
      pool:
        max-active: 200
        max-idle: 100
        min-idle: 20
```

2. **数据库配置优化**
```yaml
spring:
  datasource:
    hikari:
      maximum-pool-size: 100
      minimum-idle: 20
      connection-timeout: 30000
```

3. **应用配置优化**
```yaml
server:
  tomcat:
    threads:
      max: 1000
    max-connections: 20000
```

### Docker部署

```dockerfile
FROM openjdk:17-jdk-slim
COPY target/ecommerce-redis-demo-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app.jar"]
```

```yaml
# docker-compose.yml
version: '3.8'
services:
  app:
    build: .
    ports:
      - "8080:8080"
    depends_on:
      - redis
      - mysql
    environment:
      - SPRING_PROFILES_ACTIVE=prod
  
  redis:
    image: redis:6-alpine
    ports:
      - "6379:6379"
  
  mysql:
    image: mysql:8.0
    ports:
      - "3306:3306"
    environment:
      MYSQL_ROOT_PASSWORD: root
      MYSQL_DATABASE: nosql
```

## 监控和维护

### 1. 日志监控
- 应用日志：`logs/application.log`
- 错误日志：`logs/error.log`
- 性能日志：`logs/performance.log`

### 2. 健康检查
```bash
# 应用健康状态
curl http://localhost:8080/actuator/health

# Redis连接状态
curl http://localhost:8080/actuator/redis

# 数据库连接状态
curl http://localhost:8080/actuator/db
```

### 3. 性能监控
```bash
# 缓存统计
curl http://localhost:8080/product/cache/stats

# 库存统计
curl http://localhost:8080/product/stock/stats

# 购物车统计
curl http://localhost:8080/cart/stats/U001
```

## 故障排除

### 常见问题

1. **Redis连接失败**
   - 检查Redis服务是否启动
   - 验证Redis连接配置
   - 检查防火墙设置

2. **数据库连接失败**
   - 检查MySQL服务是否启动
   - 验证数据库连接信息
   - 检查数据库权限

3. **库存同步异常**
   - 检查定时任务是否正常执行
   - 查看同步日志
   - 手动触发同步

4. **缓存穿透**
   - 检查空值缓存是否生效
   - 验证缓存预热是否完成
   - 查看缓存统计信息

### 调试模式

```bash
# 启用调试日志
java -jar app.jar --debug

# 查看详细日志
tail -f logs/application.log
```

## 贡献指南

1. Fork 项目
2. 创建功能分支 (`git checkout -b feature/AmazingFeature`)
3. 提交更改 (`git commit -m 'Add some AmazingFeature'`)
4. 推送到分支 (`git push origin feature/AmazingFeature`)
5. 打开 Pull Request

## 许可证

本项目采用 MIT 许可证 - 查看 [LICENSE](LICENSE) 文件了解详情

## 联系方式

- 项目维护者：[Your Name]
- 邮箱：[your.email@example.com]
- 项目地址：[https://github.com/yourusername/ecommerce-redis-demo]

## 更新日志

### v1.0.0 (2025-6-21)
- ✅ 实现商品库存管理功能
- ✅ 实现购物车管理功能
- ✅ 实现商品详情缓存功能
- ✅ 实现订单号生成功能
- ✅ 实现秒杀功能
- ✅ 完善API文档和部署说明 