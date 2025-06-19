import requests
import time
import random
from concurrent.futures import ThreadPoolExecutor, as_completed
import threading # 新增导入 threading 模块

# --- 配置参数 ---
BASE_URL = "http://localhost:8080"
FLASH_SALE_PURCHASE_ENDPOINT = "/flash-sale/purchase"
PRODUCT_ID = "P001"
NUM_THREADS = 50       # 并发线程数
REQUESTS_PER_THREAD = 1000 # 每个线程请求数，总计 100000 (50 * 2000)
PURCHASE_QUANTITY = 1  # 每次购买数量
TIMEOUT = 5 # 请求超时时间（秒）

# 为每个线程创建一个 Session，可以复用连接
# 避免 WinError 10048 端口耗尽问题
thread_local = threading.local()

def get_session():
    """获取当前线程的 requests Session，确保连接复用。"""
    if not hasattr(thread_local, "session"):
        thread_local.session = requests.Session()
        # 可以为 session 设置连接池参数，例如：
        # adapter = requests.adapters.HTTPAdapter(pool_connections=NUM_THREADS, pool_maxsize=NUM_THREADS * 2)
        # thread_local.session.mount('http://', adapter)
    return thread_local.session

# --- 测试函数 ---
def purchase_item(thread_id, request_num):
    session = get_session() # 获取当前线程的 Session
    user_id = f"user_{thread_id}_{request_num}_{random.randint(1000, 9999)}"
    params = {
        "userId": user_id,
        "productId": PRODUCT_ID,
        "quantity": PURCHASE_QUANTITY
    }
    try:
        response = session.post(f"{BASE_URL}{FLASH_SALE_PURCHASE_ENDPOINT}", params=params, timeout=TIMEOUT)
        response_text = response.text # 即使状态码非 200，也尝试获取响应文本

        if response.status_code == 200:
            if "秒杀成功" in response_text:
                return {"type": "success", "user_id": user_id, "product_id": PRODUCT_ID}
            elif "系统繁忙" in response_text:
                return {"type": "system_busy", "user_id": user_id, "product_id": PRODUCT_ID}
            elif "库存不足" in response_text or "已售罄" in response_text:
                return {"type": "stock_insufficient", "user_id": user_id, "product_id": PRODUCT_ID}
            elif "商品不存在或未上架" in response_text or "商品信息错误" in response_text:
                return {"type": "product_error", "user_id": user_id, "product_id": PRODUCT_ID, "message": response_text}
            else:
                # 针对其他成功响应，但内容不符合预期的情况
                return {"type": "other_successful_response", "user_id": user_id, "response_text": response_text}
        else:
            # HTTP 状态码不是 200 的情况，例如 500 Internal Server Error
            return {"type": "http_error", "user_id": user_id, "product_id": PRODUCT_ID, "status_code": response.status_code, "message": response_text}

    except requests.exceptions.Timeout:
        return {"type": "timeout", "user_id": user_id, "product_id": PRODUCT_ID}
    except requests.exceptions.ConnectionError as e:
        # 更具体的捕获连接错误
        if "WinError 10048" in str(e):
            return {"type": "client_port_exhausted", "user_id": user_id, "product_id": PRODUCT_ID, "message": str(e)}
        else:
            return {"type": "connection_error", "user_id": user_id, "product_id": PRODUCT_ID, "message": str(e)}
    except requests.exceptions.RequestException as e:
        # 捕获其他 requests 库的异常
        return {"type": "other_error", "user_id": user_id, "product_id": PRODUCT_ID, "message": str(e)}

# --- 主执行逻辑 ---
if __name__ == "__main__":
    total_requests_sent = NUM_THREADS * REQUESTS_PER_THREAD
    print(f"--- 秒杀并发测试开始 ---")
    print(f"目标商品ID: {PRODUCT_ID}")
    print(f"并发线程数: {NUM_THREADS}")
    print(f"每个线程请求数: {REQUESTS_PER_THREAD}")
    print(f"总计请求数: {total_requests_sent}")
    print(f"------------------------------")

    # TODO: 这里应该从 Redis 获取初始库存，而不是硬编码
    # 可以在测试开始前调用一个 API 来获取初始库存
    # 或者直接从数据库查询，然后更新到 Redis，确保测试的初始状态一致
    initial_redis_stock = 100000 # 假设初始库存为 100000

    start_time = time.time()

    results = []
    with ThreadPoolExecutor(max_workers=NUM_THREADS) as executor:
        futures = [executor.submit(purchase_item, i, j)
                   for i in range(NUM_THREADS)
                   for j in range(REQUESTS_PER_THREAD)]

        # 实时打印进度
        completed_count = 0
        for future in as_completed(futures):
            completed_count += 1
            results.append(future.result())
            if completed_count % (total_requests_sent // 10) == 0:
                print(f"已完成 {completed_count}/{total_requests_sent} 请求...")

    end_time = time.time()
    total_duration = end_time - start_time

    # --- 结果统计 ---
    actual_purchase_success_count = 0
    stock_insufficient_count = 0
    system_busy_count = 0
    timeout_count = 0
    http_error_count = 0
    client_port_exhausted_count = 0
    connection_error_count = 0 # 新增：网络连接错误
    product_error_count = 0 # 新增：商品信息错误
    other_error_count = 0
    total_responses_processed = 0

    for result in results:
        total_responses_processed += 1
        if result["type"] == "success":
            actual_purchase_success_count += 1
        elif result["type"] == "stock_insufficient":
            stock_insufficient_count += 1
        elif result["type"] == "system_busy":
            system_busy_count += 1
        elif result["type"] == "timeout":
            timeout_count += 1
        elif result["type"] == "http_error":
            http_error_count += 1
        elif result["type"] == "client_port_exhausted":
            client_port_exhausted_count += 1
        elif result["type"] == "connection_error":
            connection_error_count += 1
        elif result["type"] == "product_error":
            product_error_count += 1
        elif result["type"] == "other_error":
            other_error_count += 1
        elif result["type"] == "other_successful_response":
            # 如果有其他成功的响应，但不在预期字符串内，也算作成功
            actual_purchase_success_count += 1
            # print(f"未知类型成功响应: 用户ID={result['user_id']}, 响应={result['response_text']}")


    print(f"------------------------------")
    print(f"--- 秒杀并发测试结果 ---")
    print(f"总耗时: {total_duration:.2f} 秒")
    print(f"总发送请求数: {total_requests_sent}")
    print(f"实际处理响应数: {total_responses_processed}") # 应等于 total_requests_sent
    print(f"--- 响应分类统计 ---")
    print(f" - 实际秒杀成功数: {actual_purchase_success_count}")
    print(f" - 库存不足响应数: {stock_insufficient_count}")
    print(f" - 系统繁忙响应数: {system_busy_count}")
    print(f" - 请求超时数: {timeout_count}")
    print(f" - HTTP错误数: {http_error_count}")
    print(f" - 客户端端口耗尽错误: {client_port_exhausted_count}") # 新增统计项
    print(f" - 网络连接错误: {connection_error_count}") # 新增统计项
    print(f" - 商品信息错误: {product_error_count}") # 新增统计项
    print(f" - 其他错误数: {other_error_count}")

    # --- 库存验证 ---
    # 可以在测试结束后通过 API 调用获取最终 Redis 库存
    try:
        final_redis_stock_response = requests.get(f"{BASE_URL}/flash-sale/stock/{PRODUCT_ID}")
        final_redis_stock_response.raise_for_status()
        final_redis_stock = int(final_redis_stock_response.text)
    except Exception as e:
        final_redis_stock = "获取失败"
        print(f"获取最终 Redis 库存失败: {e}")

    expected_total_deduction = actual_purchase_success_count
    theoretical_remaining_stock = initial_redis_stock - expected_total_deduction

    print(f"--- 库存验证 ---")
    print(f"测试前初始库存: {initial_redis_stock}")
    print(f"预期总扣减量: {expected_total_deduction}")
    print(f"测试后理论剩余库存: {theoretical_remaining_stock}")
    print(f"测试后实际Redis库存: {final_redis_stock}")

    if isinstance(final_redis_stock, int):
        actual_total_deduction = initial_redis_stock - final_redis_stock
        print(f"实际总扣减量 (初始 - 最终Redis): {actual_total_deduction}")
        if actual_total_deduction == expected_total_deduction:
            print("库存扣减结果与预期一致！无超卖发生。✅")
        else:
            diff = actual_total_deduction - expected_total_deduction
            print(f"库存扣减结果与预期不一致！可能存在超卖或数据问题。❌")
            print(f"差异: {diff}")
    else:
        print("无法验证库存一致性，因为未能获取到最终 Redis 库存。")

    print("\n进程已结束，退出代码为 0")