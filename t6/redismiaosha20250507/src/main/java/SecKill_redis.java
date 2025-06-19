import java.io.IOException;
import java.util.List;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPool;
import redis.clients.jedis.Transaction;

public class SecKill_redis {

    // The main method was commented out, causing the "main method not found" error.
    // Uncommenting it allows the Java application to have an entry point.
    public static void main(String[] args) {
        // This is a simple test of the Jedis connection.
        // You might want to replace this with actual calls to doSecKill for testing purposes.
        Jedis jedis = new Jedis("192.168.88.134", 6379);
        System.out.println(jedis.ping());
        jedis.close();
    }

    /**
     * Performs a simulated second-kill (seckill) operation using Redis.
     * It checks stock, user participation, and decrements stock while adding the user to a set,
     * all within a Redis transaction with optimistic locking (WATCH).
     *
     * @param uid The user ID attempting the seckill.
     * @param prodid The product ID for the seckill.
     * @return true if the seckill was successful, false otherwise.
     * @throws IOException If an I/O error occurs (though not directly used in this implementation,
     * the signature from the original code is kept).
     */
    public static boolean doSecKill(String uid, String prodid) throws IOException {
        System.out.println("----------SecKill_redis-------------");

        // Basic validation for input parameters
        if (uid == null || prodid == null) {
            System.out.println("User ID or Product ID is null.");
            return false;
        }

        // Note: The original code used JedisPoolUtil.getJedisPoolInstance() which is commented out.
        // For this example, we are directly creating a Jedis instance.
        // In a production environment, using a JedisPool is highly recommended for connection management.
        Jedis jedis = null;
        try {
            jedis = new Jedis("192.168.88.134", 6379);

            // Define Redis keys for product quantity and user set
            String kcKey = "sk:" + prodid + ":qt"; // Key for product quantity
            String userKey = "sk:" + prodid + ":user"; // Key for set of users who successfully seckilled

            // Watch the quantity key for changes before starting the transaction.
            // If the key is modified by another client before the transaction executes,
            // the transaction will fail. This is optimistic locking.
            jedis.watch(kcKey);

            // Get the current product quantity
            String kc = jedis.get(kcKey);

            // Check if the product exists and seckill has started
            if (kc == null) {
                System.out.println("秒杀还没有开始，请等待");
                return false;
            }

            int productQuantity;
            try {
                productQuantity = Integer.parseInt(kc);
            } catch (NumberFormatException e) {
                System.err.println("Invalid quantity format in Redis for product " + prodid + ": " + kc);
                return false; // Handle cases where the quantity in Redis is not a valid integer
            }


            // Check if the user has already successfully seckilled this product
            if (jedis.sismember(userKey, uid)) {
                System.out.println("已经秒杀成功了，不能重复秒杀");
                return false;
            }

            // Check if there is stock available
            if (productQuantity <= 0) {
                System.out.println("秒杀已经结束了");
                return false;
            }

            // If all checks pass, proceed with the transaction
            Transaction multi = jedis.multi(); // Start a new transaction

            // Decrement the product quantity
            multi.decr(kcKey);

            // Add the user ID to the set of users who successfully seckilled
            multi.sadd(userKey, uid);

            // Execute the transaction.
            // If watch() detected a change on kcKey, exec() will return null.
            List<Object> results = multi.exec();

            // Check the results of the transaction
            if (results != null && !results.isEmpty()) {
                // Transaction successful
                System.out.println("秒杀成功了..");
                return true;
            } else {
                // Transaction failed (e.g., due to WATCH)
                System.out.println("秒杀失败了.... (可能由于并发修改)");
                return false;
            }
        } catch (Exception e) {
            // Catch any exceptions during the process
            System.err.println("An error occurred during seckill: " + e.getMessage());
            e.printStackTrace();
            return false;
        } finally {
            // Ensure the Jedis connection is closed
            if (jedis != null) {
                jedis.close();
            }
        }
    }
}
