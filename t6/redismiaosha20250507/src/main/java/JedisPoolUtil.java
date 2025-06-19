//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//

import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.JedisPoolConfig;

public class JedisPoolUtil {
    private static volatile JedisPool jedisPool = null;

    private JedisPoolUtil() {
    }

    public static JedisPool getJedisPoolInstance() {
        if (null == jedisPool) {
            Class var0 = JedisPoolUtil.class;
            synchronized(JedisPoolUtil.class) {
                if (null == jedisPool) {
                    JedisPoolConfig poolConfig = new JedisPoolConfig();
                    poolConfig.setMaxTotal(200);
                    poolConfig.setMaxIdle(32);
                    poolConfig.setMaxWaitMillis(100000L);
                    poolConfig.setBlockWhenExhausted(true);
                    poolConfig.setTestOnBorrow(true);
                    jedisPool = new JedisPool(poolConfig, "192.168.88.134", 6379, 60000);
                }
            }
        }

        return jedisPool;
    }

    public static void release(JedisPool jedisPool, Jedis jedis) {
        if (null != jedis) {
            jedis.close();
        }

    }
}

