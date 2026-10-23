# 审批中心缓存压测——方法与步骤

> 作者：ZCode（后端开发）｜创建时间：2026-10-20
> 用途：给「审批中心 Redis/RocketMQ 升级」的插入点①②出**缓存前后 QPS/P99 对比数字**。
> 分工（方案 §8 Step7 定）：脚本与步骤由后端会话备好，**压测数字由组长本人跑出并写报告**——这是个人交付物，写简历用。

## 一、测什么

| 采样器 | 接口 | 登录身份 | 走到的优化点 |
|---|---|---|---|
| P08-unread-count | GET /api/approvals/unread-count | stu_wang（学生，绑档案 103） | 插入点②红点缓存：开=Redis Hash 读，关=两条 COUNT 查库 |
| P01-TODO待办列表 | GET /api/approvals?view=TODO&status=PENDING&page=1&size=20 | admin（超管） | 插入点①组织树缓存 + 列表裁掉 biz_payload 宽列 |

默认参数：并发 20 线程、持续 60 秒（GUI 里改线程组的值，或命令行 `-Jthreads=10 -Jduration=30` 覆盖）。

## 二、两轮对比怎么跑（核心）

**第 1 轮：基线（开关全关 = 升级前现状）**

app 容器保持默认环境（不设 VIO_CACHE_ORG / VIO_CACHE_UNREAD），直接跑一轮，抄下聚合报告里两个采样器的 **Throughput（QPS）** 和 **99% Line（P99 毫秒）**。

**第 2 轮：开缓存**

1. `backend/docker-compose.apps.yml` 的 `app` 服务 `environment` 段加三行：
   ```yaml
         VIO_CACHE_ORG: "true"
         VIO_CACHE_UNREAD: "true"
   ```
2. 重建生效：`cd backend && docker compose -f docker-compose.apps.yml --profile app up -d`（不用 --build，代码没变只换环境变量）
3. 等 10 秒让组织缓存预热任务跑完第一轮（日志里有"组织树缓存预热完成"）
4. 跑第二轮，抄同样的两个指标。

**对比口径**：两轮的并发、时长、数据完全一致，唯一变量是开关——这就是干净的 A/B。

## 三、怎么跑

**GUI**：双击 `D:\apache-jmeter-5.6.3\bin\jmeter.bat` → 打开本目录的 `审批中心-缓存压测.jmx` → 点绿色启动三角 → 看左侧"聚合报告"。

**命令行（推荐，自带 HTML 报告）**：
```bash
cd D:\apache-jmeter-5.6.3\bin
jmeter -n -t D:\viomanager-backend\docs\bench\审批中心-缓存压测.jmx -l result.jtl -e -o report
# 跑完打开 report\index.html，看 Statistics 页签的 Throughput 和 99th pct
```

## 四、公平性与坑

1. **别边跑 mvn 测试边压**——测试会抢 MySQL 连接，把基线数字压虚高（对缓存不利）。
2. MySQL / Redis 容器都要在跑（`docker ps` 确认 healthy）。
3. 两轮之间**不要**清 Redis（第 2 轮要的就是命中状态；预热任务会保证组织键是满的）。
4. 红点键 stu_wang 在第一笔读后进缓存，60 秒窗口里 99.9% 都是命中——这正是要测的状态。
5. 想复测第 1 轮：把那三行环境变量删掉再 `up -d` 重建容器即可。

## 五、预期量级（写报告前的心理锚点，不是结论）

- **P08**：关 = 每请求 2 条 COUNT 走 MySQL；开 = 1 次 Redis 读（亚毫秒）。单用户视角 P99 应有数倍改善；并发上来后差距更大（MySQL 连接池 10 是瓶颈，Redis 不是）。
- **P01**：子树判定从 2 条 LIKE 查库变 Redis Set 读 + 列表少拉一个宽 JSON 列。P99 改善幅度小于 P08（列表本身还有分页查询的主干 SQL）——这个"主干不动、旁路加速"本来就是方案口径，报告里如实写反而加分。
- 如果数字不符合预期：先看 app 日志有没有"Redis 不可用…降级为直查库"的 warn（说明第 2 轮实际没走缓存），再查容器网络里 app 到 redis 的连通。
