-- 新卷首次初始化时自动建知识库独立库（与主库同实例不同 schema，vio-kb 服务专用）。
-- 排序规则与 vio-kb README §7 的手工建库命令一字不差（模块按该口径测试）。
-- 只在数据卷首次创建时执行；已存在的老卷此脚本不会跑，需一次性手工补建：
--   docker exec vio-mysql mysql -uroot -pdev-root-123 -e "CREATE DATABASE IF NOT EXISTS viomanager_kb"
CREATE DATABASE IF NOT EXISTS viomanager_kb
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;
