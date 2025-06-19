//
// Source code recreated from a .class file by IntelliJ IDEA
// (powered by Fernflower decompiler)
//



import java.io.IOException;
import java.util.HashSet;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;

public class SecKill_redisByScript {
    private static final Logger logger = LoggerFactory.getLogger(SecKill_redisByScript.class);
    static String secKillScript = "local userid=KEYS[1];\r\nlocal prodid=KEYS[2];\r\nlocal qtkey='sk:'..prodid..\":qt\";\r\nlocal usersKey='sk:'..prodid..\":usr\";\r\nlocal userExists=redis.call(\"sismember\",usersKey,userid);\r\nif tonumber(userExists)==1 then \r\n   return 2;\r\nend\r\nlocal num= redis.call(\"get\" ,qtkey);\r\nif tonumber(num)<=0 then \r\n   return 0;\r\nelse \r\n   redis.call(\"decr\",qtkey);\r\n   redis.call(\"sadd\",usersKey,userid);\r\nend\r\nreturn 1";
    static String secKillScript2 = "local userExists=redis.call(\"sismember\",\"{sk}:0101:usr\",userid);\r\n return 1";

    public SecKill_redisByScript() {
    }

    public static void main(String[] args) {
        JedisPool jedispool = JedisPoolUtil.getJedisPoolInstance();
        Jedis jedis = jedispool.getResource();
        System.out.println(jedis.ping());
        new HashSet();
    }

    public static boolean doSecKill(String uid, String prodid) throws IOException {
        JedisPool jedispool = JedisPoolUtil.getJedisPoolInstance();
        Jedis jedis = jedispool.getResource();
        String sha1 = jedis.scriptLoad(secKillScript);
        Object result = jedis.evalsha(sha1, 2, new String[]{uid, prodid});
        String reString = String.valueOf(result);
        if ("0".equals(reString)) {
            System.err.println("已抢空！！");
        } else if ("1".equals(reString)) {
            System.out.println("抢购成功！！！！");
        } else if ("2".equals(reString)) {
            System.err.println("该用户已抢过！！");
        } else {
            System.err.println("抢购异常！！");
        }

        jedis.close();
        return true;
    }
}

