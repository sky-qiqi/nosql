import redis.clients.jedis.Jedis;
import redis.clients.jedis.params.SortingParams;
import redis.clients.jedis.resps.Tuple; // 正确的导入路径 для Jedis 5.x

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class JedisExperimentAdvanced {
    public static void main(String[] args) {
        Jedis jedis = new Jedis("192.168.231.128", 6379); // 替换为您的 Redis 服务器 IP 和端口

        try {
            System.out.println("--- 连接与基础操作 ---");
            System.out.println("连接 Redis 服务器...");
            System.out.println("连接状态: " + jedis.ping()); // 使用 ping 检查连接

            // --- 字符串操作 (模拟用户 Session) ---
            System.out.println("\n--- 字符串操作 (模拟用户 Session) ---");
            String userId = "user:123";
            jedis.setex(userId, 1800L, "{\"username\":\"testuser\", \"login_time\":\"2023-10-27 10:00:00\"}"); // 设置带过期时间的 Session 信息
            System.out.println("设置用户 Session 信息，Key: " + userId + ", Value: " + jedis.get(userId));
            System.out.println("Session 是否存在: " + jedis.exists(userId));
            jedis.append(userId, ", \"last_action\":\"view_product\"");
            System.out.println("更新用户 Session 信息: " + jedis.get(userId));

            // --- 整数和浮点数操作 (模拟商品库存和价格) ---
            System.out.println("\n--- 整数和浮点数操作 (模拟商品库存和价格) ---");
            String productStockKey = "product:456:stock";
            String productPriceKey = "product:456:price";
            jedis.set(productStockKey, "100");
            jedis.set(productPriceKey, "99.8");
            System.out.println("初始商品库存: " + jedis.get(productStockKey));
            System.out.println("初始商品价格: " + jedis.get(productPriceKey));
            jedis.decr(productStockKey);
            jedis.incrByFloat(productPriceKey, 0.5);
            System.out.println("购买一件商品后库存: " + jedis.get(productStockKey));
            System.out.println("价格上涨后的商品价格: " + jedis.get(productPriceKey));

            // --- 列表操作 (模拟用户消息队列) ---
            System.out.println("\n--- 列表操作 (模拟用户消息队列) ---");
            String messageQueueKey = "user:789:messages";
            jedis.lpush(messageQueueKey, "您有一条新的通知：系统升级完成");
            jedis.lpush(messageQueueKey, "您的订单已发货，运单号：ABC12345");
            System.out.println("用户消息队列内容: " + jedis.lrange(messageQueueKey, 0, -1));
            System.out.println("取出最新的消息: " + jedis.lpop(messageQueueKey));
            System.out.println("剩余消息队列内容: " + jedis.lrange(messageQueueKey, 0, -1));

            // --- 集合操作 (模拟用户关注的商品) ---
            System.out.println("\n--- 集合操作 (模拟用户关注的商品) ---");
            String followedProductsKey = "user:101:followed";
            jedis.sadd(followedProductsKey, "product:1", "product:2", "product:3");
            System.out.println("用户关注的商品: " + jedis.smembers(followedProductsKey));
            System.out.println("是否关注了 'product:2': " + jedis.sismember(followedProductsKey, "product:2"));
            jedis.srem(followedProductsKey, "product:1");
            System.out.println("取消关注 'product:1' 后，关注的商品: " + jedis.smembers(followedProductsKey));
            System.out.println("关注的商品数量: " + jedis.scard(followedProductsKey));

            // --- 有序集合操作 (模拟商品排行榜) ---
            System.out.println("\n--- 有序集合操作 (模拟商品排行榜) ---");
            String productRankingKey = "product:ranking";
            Map<String, Double> productScores = new HashMap<>();
            productScores.put("product:10", 95.5);
            productScores.put("product:11", 88.0);
            productScores.put("product:12", 99.9);
            jedis.zadd(productRankingKey, productScores);
            System.out.println("商品排行榜 (按分数从低到高): " + jedis.zrangeWithScores(productRankingKey, 0, -1));
            System.out.println("排行榜中分数最高的商品 (按分数从高到低): " + jedis.zrevrangeWithScores(productRankingKey, 0, 0));
            System.out.println("商品 'product:11' 的分数: " + jedis.zscore(productRankingKey, "product:11"));

            // --- 哈希操作 (模拟商品详情) ---
            System.out.println("\n--- 哈希操作 (模拟商品详情) ---");
            String productDetailsKey = "product:567:details";
            Map<String, String> details = new HashMap<>();
            details.put("name", "超级好吃的苹果");
            details.put("description", "新鲜多汁，口感清脆");
            details.put("price", "9.9");
            details.put("category", "水果");
            jedis.hmset(productDetailsKey, details);
            System.out.println("商品详情: " + jedis.hgetAll(productDetailsKey));
            System.out.println("商品名称: " + jedis.hget(productDetailsKey, "name"));
            System.out.println("商品价格: " + jedis.hget(productDetailsKey, "price"));

            // --- 排序操作 (对列表中的商品 ID 进行排序并获取详细信息) ---
            System.out.println("\n--- 排序操作 (对列表中的商品 ID 进行排序并获取详细信息) ---");
            jedis.lpush("product:ids", "10", "12", "11");
            SortingParams sortingParams = new SortingParams();
            sortingParams.alpha(); // 按照字母顺序排序
            List<String> sortedProductIdsAlpha = jedis.sort("product:ids", sortingParams);
            System.out.println("按字母排序的商品 ID: " + sortedProductIdsAlpha);

            List<String> sortedProductNames = new ArrayList<>();
            for (String productId : sortedProductIdsAlpha) {
                String detailsKey = "product:" + productId + ":details";
                String productName = jedis.hget(detailsKey, "name");
                sortedProductNames.add(productName);
            }
            System.out.println("按商品 ID 排序并获取商品名称: " + sortedProductNames);

            // --- 清理数据 ---
            System.out.println("\n--- 清理数据 ---");
            jedis.del(userId, productStockKey, productPriceKey, messageQueueKey, followedProductsKey, productRankingKey, productDetailsKey, "product:ids");
            System.out.println("清理所有实验数据");

        } catch (Exception e) {
            System.err.println("发生错误: " + e.getMessage());
        } finally {
            if (jedis != null && jedis.isConnected()) {
                jedis.close();
            }
        }
    }
}