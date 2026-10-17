-- V2：吸收 lzh 2026-10-14 拍板的条目简化（链接或上传文件二选一，砍分类与正文）。
-- 规矩：V1 基线禁改，只加列不改列；本文件须与 docs/db/initsql.sql 保持一致（表结构即契约）。
-- 两列均为冗余快照：file_name 免跨服务查文件名；publisher_name 免跨服务查用户表（独立库无 sys_user）。
ALTER TABLE kb_doc
    ADD COLUMN file_name     VARCHAR(300) NULL COMMENT '附件原始文件名（FILE 形态冗余，列表展示与下载兜底）' AFTER attach_file_id,
    ADD COLUMN publisher_name VARCHAR(64) NULL COMMENT '发布人姓名（JWT realName 快照）' AFTER created_by;
