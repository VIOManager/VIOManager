-- MySQL dump 10.13  Distrib 8.4.11, for Linux (x86_64)
--
-- Host: localhost    Database: viomanager
-- ------------------------------------------------------
-- Server version	8.4.11

/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!50503 SET NAMES utf8mb4 */;
/*!40103 SET @OLD_TIME_ZONE=@@TIME_ZONE */;
/*!40103 SET TIME_ZONE='+00:00' */;
/*!40014 SET @OLD_UNIQUE_CHECKS=@@UNIQUE_CHECKS, UNIQUE_CHECKS=0 */;
/*!40014 SET @OLD_FOREIGN_KEY_CHECKS=@@FOREIGN_KEY_CHECKS, FOREIGN_KEY_CHECKS=0 */;
/*!40101 SET @OLD_SQL_MODE=@@SQL_MODE, SQL_MODE='NO_AUTO_VALUE_ON_ZERO' */;
/*!40111 SET @OLD_SQL_NOTES=@@SQL_NOTES, SQL_NOTES=0 */;

--
-- Table structure for table `achievement`
--

DROP TABLE IF EXISTS `achievement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `achievement` (
  `id` bigint NOT NULL COMMENT '主键',
  `team_id` bigint DEFAULT NULL COMMENT '所属赛队 ID。个人成果可为空',
  `contest_id` bigint DEFAULT NULL COMMENT '关联比赛 ID',
  `semester_id` bigint NOT NULL COMMENT '所属学期 ID',
  `org_id` bigint NOT NULL COMMENT '所属组织',
  `owner_member_id` bigint NOT NULL COMMENT '成果归属人人员 ID（提交时为队长）',
  `owner_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '归属人姓名冗余',
  `achievement_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型：AWARD 获奖 / RATING 评级 / QUALIFICATION 资质 / ACTIVITY 活动成果 / OTHER 其他',
  `name` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '成果名称',
  `level` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '级别：国家级 / 省级 / 市级 / 校级 / 院级 等',
  `award_date` date DEFAULT NULL COMMENT '获奖/取得日期',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '成果描述',
  `evidence_file_id` bigint DEFAULT NULL COMMENT '佐证材料文件 ID（成果生效后计入材料必交项）',
  `evidence_file_name` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '佐证文件名冗余',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待审批 / APPROVED 已通过 / REJECTED 已驳回',
  `submit_channel` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'USER' COMMENT '提交渠道：USER 队长提交 / ADMIN 管理员录入',
  `approved_at` datetime DEFAULT NULL COMMENT '审批通过时间',
  `approved_by` bigint DEFAULT NULL COMMENT '审批人',
  `reject_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '驳回原因',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_achievement_team` (`team_id`,`status`,`deleted`),
  KEY `idx_achievement_owner` (`owner_member_id`,`status`,`deleted`),
  KEY `idx_achievement_semester` (`semester_id`,`status`,`deleted`),
  KEY `idx_achievement_contest` (`contest_id`,`deleted`),
  KEY `idx_achievement_org` (`org_id`,`deleted`),
  KEY `idx_achievement_type` (`achievement_type`,`level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成果记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `achievement`
--

LOCK TABLES `achievement` WRITE;
/*!40000 ALTER TABLE `achievement` DISABLE KEYS */;
INSERT INTO `achievement` VALUES (701,NULL,NULL,301,2,103,NULL,'AWARD','蓝桥杯省赛软件类一等奖',NULL,NULL,NULL,NULL,NULL,'APPROVED','USER',NULL,NULL,NULL,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL);
/*!40000 ALTER TABLE `achievement` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `achievement_file`
--

DROP TABLE IF EXISTS `achievement_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `achievement_file` (
  `id` bigint NOT NULL COMMENT '主键',
  `achievement_id` bigint NOT NULL COMMENT '成果 ID',
  `file_id` bigint NOT NULL COMMENT '文件对象 ID',
  `file_name` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名冗余',
  `file_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'EVIDENCE' COMMENT '附件类型：EVIDENCE 佐证材料 / ANNEX 附加材料',
  `is_current` tinyint NOT NULL DEFAULT '1' COMMENT '是否当前生效版本',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '版本号',
  `uploaded_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '上传时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_achievement_file_biz` (`achievement_id`,`file_type`,`is_current`,`deleted`),
  KEY `idx_achievement_file_id` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='成果附件';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `achievement_file`
--

LOCK TABLES `achievement_file` WRITE;
/*!40000 ALTER TABLE `achievement_file` DISABLE KEYS */;
/*!40000 ALTER TABLE `achievement_file` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_conversation`
--

DROP TABLE IF EXISTS `ai_conversation`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_conversation` (
  `id` bigint NOT NULL COMMENT '主键',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '新对话' COMMENT '会话标题（首条提问自动截取）',
  `user_id` bigint NOT NULL COMMENT '所属用户 sys_user.id',
  `member_id` bigint DEFAULT NULL COMMENT '所属人员 ID',
  `org_id` bigint DEFAULT NULL COMMENT '所属组织',
  `provider` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ollama' COMMENT 'AI 提供方：ollama / openai',
  `model` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模型名快照',
  `message_count` int NOT NULL DEFAULT '0' COMMENT '消息数',
  `last_message_at` datetime DEFAULT NULL COMMENT '最后消息时间',
  `archived` tinyint NOT NULL DEFAULT '0' COMMENT '是否归档：1 已归档 0 正常',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_conv_user` (`user_id`,`archived`,`deleted`,`last_message_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 会话';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_conversation`
--

LOCK TABLES `ai_conversation` WRITE;
/*!40000 ALTER TABLE `ai_conversation` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_conversation` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `ai_message`
--

DROP TABLE IF EXISTS `ai_message`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `ai_message` (
  `id` bigint NOT NULL COMMENT '主键',
  `conversation_id` bigint NOT NULL COMMENT '会话 ID',
  `role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色：user 用户 / assistant 助手 / tool 工具结果 / system 系统',
  `content` longtext COLLATE utf8mb4_unicode_ci COMMENT '消息内容',
  `tool_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '调用的工具名',
  `tool_arguments` text COLLATE utf8mb4_unicode_ci COMMENT '工具入参 JSON',
  `tool_result` longtext COLLATE utf8mb4_unicode_ci COMMENT '工具返回结果 JSON（已按权限裁剪）',
  `tool_call_id` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '工具调用 ID，关联请求与结果',
  `incomplete` tinyint NOT NULL DEFAULT '0' COMMENT '是否不完整：1 是（中断）0 否',
  `prompt_tokens` int DEFAULT NULL COMMENT '提示 token 数',
  `completion_tokens` int DEFAULT NULL COMMENT '生成 token 数',
  `cost_millis` bigint DEFAULT NULL COMMENT '生成耗时毫秒',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_ai_msg_conv` (`conversation_id`,`deleted`,`created_at`),
  KEY `idx_ai_msg_tool` (`tool_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 消息';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `ai_message`
--

LOCK TABLES `ai_message` WRITE;
/*!40000 ALTER TABLE `ai_message` DISABLE KEYS */;
/*!40000 ALTER TABLE `ai_message` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `approval`
--

DROP TABLE IF EXISTS `approval`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `approval` (
  `id` bigint NOT NULL COMMENT '主键',
  `biz_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务类型：TEAM_CREATE / TEAM_ADD_MEMBER / TEAM_REMOVE_MEMBER / TEAM_ADD_ADVISOR / TEAM_DISSOLVE / ACHIEVEMENT_SUBMIT / PROJECT_VIEW / MILESTONE_APPROVE / SEMESTER_SWITCH',
  `biz_id` bigint DEFAULT NULL COMMENT '关联业务 ID，批准后据此创建/更新正式数据',
  `biz_title` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务标题，列表展示用（如队名 + 比赛名）',
  `biz_payload` json DEFAULT NULL COMMENT '申请内容快照 JSON',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待处理 / APPROVED 已批准 / REJECTED 已驳回 / CANCELLED 已取消 / EXPIRED 已过期',
  `applicant_id` bigint NOT NULL COMMENT '发起人人员 ID',
  `applicant_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发起人姓名冗余',
  `target_member_id` bigint DEFAULT NULL COMMENT '目标人人员 ID（如被邀请入队的队员），需其同意/拒绝',
  `target_member_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标人姓名冗余',
  `approver_org_id` bigint NOT NULL COMMENT '审批责任组织 ID，决定哪个管理员收到待办',
  `approver_id` bigint DEFAULT NULL COMMENT '实际审批人 sys_user.id',
  `approver_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批人姓名冗余',
  `team_id` bigint DEFAULT NULL COMMENT '关联赛队 ID（赛队类审批填，便于权限判定）',
  `contest_id` bigint DEFAULT NULL COMMENT '关联比赛 ID（冗余，便于筛选）',
  `semester_id` bigint DEFAULT NULL COMMENT '所属学期 ID',
  `org_id` bigint DEFAULT NULL COMMENT '归属组织（数据范围过滤依据）',
  `apply_reason` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '申请说明',
  `handle_comment` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理意见',
  `handled_at` datetime DEFAULT NULL COMMENT '处理时间',
  `expire_at` datetime DEFAULT NULL COMMENT '过期时间，到期未处理自动转 EXPIRED',
  `applicant_read` tinyint NOT NULL DEFAULT '0' COMMENT '发起人是否已读结果：1 已读 0 未读',
  `target_read` tinyint NOT NULL DEFAULT '0' COMMENT '目标人是否已读邀请：1 已读 0 未读',
  `approver_read` tinyint NOT NULL DEFAULT '0' COMMENT '审批人是否已读：1 已读 0 未读',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发起时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_approval_status_org` (`status`,`approver_org_id`,`deleted`),
  KEY `idx_approval_biz` (`biz_type`,`biz_id`,`status`),
  KEY `idx_approval_applicant` (`applicant_id`,`status`,`deleted`),
  KEY `idx_approval_target` (`target_member_id`,`status`,`deleted`),
  KEY `idx_approval_approver` (`approver_id`,`status`,`deleted`),
  KEY `idx_approval_team` (`team_id`,`status`,`deleted`),
  KEY `idx_approval_unread` (`applicant_id`,`applicant_read`,`target_member_id`,`target_read`),
  KEY `idx_approval_expire` (`status`,`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批单';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `approval`
--

LOCK TABLES `approval` WRITE;
/*!40000 ALTER TABLE `approval` DISABLE KEYS */;
INSERT INTO `approval` VALUES (801,'TEAM_CREATE',NULL,'创建赛队申请 · 星辰算法队（第十六届蓝桥杯）','{\"teamName\": \"星辰算法队\", \"leaderName\": \"王小明\", \"contestName\": \"第十六届蓝桥杯程序设计大赛\", \"projectName\": \"智能排课小助手\"}','PENDING',103,'王小明',NULL,NULL,2,NULL,NULL,NULL,402,301,2,'和两名队友组队参加蓝桥杯，队名定为星辰算法队，请老师审批',NULL,NULL,'2026-10-19 11:20:37',0,0,0,0,'2026-10-12 09:15:00',NULL,'2026-10-12 11:20:37',NULL),(802,'TEAM_ADD_MEMBER',NULL,'邀请孙小雨加入 代码先锋队','{\"teamName\": \"代码先锋队\", \"maxMembers\": 5, \"targetName\": \"孙小雨\", \"memberCount\": 2}','PENDING',103,'王小明',105,'孙小雨',2,NULL,NULL,501,402,301,2,'队里缺一名前端，孙小雨技能匹配，邀请入队',NULL,NULL,'2026-10-19 11:20:37',0,0,0,0,'2026-10-11 16:40:00',NULL,'2026-10-12 11:20:37',NULL),(803,'ACHIEVEMENT_SUBMIT',NULL,'获奖成果录入 · 蓝桥杯省赛一等奖','{\"evidenceFile\": \"证书扫描件.pdf\", \"achievementName\": \"蓝桥杯省赛软件类一等奖\", \"achievementType\": \"AWARD\"}','PENDING',104,'赵晓亮',NULL,NULL,3,NULL,NULL,NULL,NULL,301,3,'队长提交获奖成果，证书扫描件已上传，请审核',NULL,NULL,'2026-10-19 11:20:37',0,0,0,0,'2026-10-12 10:20:00',NULL,'2026-10-12 11:20:37',NULL),(804,'TEAM_CREATE',501,'创建赛队申请 · 代码先锋队（第十六届蓝桥杯）','{\"teamName\": \"代码先锋队\", \"leaderName\": \"王小明\", \"contestName\": \"第十六届蓝桥杯程序设计大赛\"}','APPROVED',103,'王小明',NULL,NULL,2,202,'陈志远',501,402,301,2,'报名蓝桥杯，申请创建代码先锋队','同意，注意报名截止时间','2026-10-09 20:05:00',NULL,0,0,1,0,'2026-10-09 19:30:00',NULL,'2026-10-12 10:34:51',NULL),(805,'TEAM_DISSOLVE',501,'解散赛队申请 · 代码先锋队','{\"reason\": \"队内方向调整\", \"teamName\": \"代码先锋队\"}','REJECTED',103,'王小明',NULL,NULL,2,202,'陈志远',501,402,301,2,'队伍方向调整，申请解散','比赛尚未结束，暂不能解散','2026-10-10 11:45:00',NULL,0,0,1,0,'2026-10-10 11:20:00',NULL,'2026-10-12 10:34:51',NULL),(806,'TEAM_ADD_MEMBER',NULL,'邀请孙小雨加入 芯火电子队','{\"teamName\": \"芯火电子队\", \"targetName\": \"孙小雨\"}','CANCELLED',104,'赵晓亮',105,'孙小雨',3,NULL,NULL,502,402,301,3,'嵌入式方向缺人，邀请孙小雨',NULL,'2026-10-10 15:10:00',NULL,1,1,0,0,'2026-10-10 14:50:00',NULL,'2026-10-12 10:34:51',NULL),(807,'PROJECT_VIEW',NULL,'项目查看申请 · 智能小车竞速项目','{\"days\": 7, \"grantType\": \"LIMITED\", \"projectName\": \"智能小车竞速\"}','PENDING',105,'孙小雨',NULL,NULL,3,NULL,NULL,NULL,NULL,301,3,'想参考该项目的传感器选型方案，申请限时查看',NULL,NULL,'2026-10-19 11:20:37',0,0,0,0,'2026-10-12 08:30:00',NULL,'2026-10-12 11:20:37',NULL);
/*!40000 ALTER TABLE `approval` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `approval_record`
--

DROP TABLE IF EXISTS `approval_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `approval_record` (
  `id` bigint NOT NULL COMMENT '主键',
  `approval_id` bigint NOT NULL COMMENT '审批单 ID',
  `action` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '动作：SUBMIT 发起 / CANCEL 取消 / APPROVE 批准 / REJECT 驳回 / AGREE 同意 / REFUSE 拒绝 / EXPIRE 过期',
  `operator_id` bigint DEFAULT NULL COMMENT '操作人 sys_user.id',
  `operator_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人姓名',
  `operator_role` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人角色：APPLICANT 发起人 / TARGET 目标人 / APPROVER 审批人',
  `comment_text` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '意见 / 备注',
  `from_status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '变更前状态',
  `to_status` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '变更后状态',
  `ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作 IP',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '操作时间',
  PRIMARY KEY (`id`),
  KEY `idx_approval_record` (`approval_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审批操作流水';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `approval_record`
--

LOCK TABLES `approval_record` WRITE;
/*!40000 ALTER TABLE `approval_record` DISABLE KEYS */;
INSERT INTO `approval_record` VALUES (811,801,'SUBMIT',204,'王小明','APPLICANT','和两名队友组队参加蓝桥杯',NULL,'PENDING',NULL,'2026-10-12 09:15:00'),(812,802,'SUBMIT',204,'王小明','APPLICANT','队里缺一名前端，邀请入队',NULL,'PENDING',NULL,'2026-10-11 16:40:00'),(813,803,'SUBMIT',205,'赵晓亮','APPLICANT','获奖成果录入，证书已上传',NULL,'PENDING',NULL,'2026-10-12 10:20:00'),(814,804,'SUBMIT',204,'王小明','APPLICANT','报名蓝桥杯，申请建队',NULL,'PENDING',NULL,'2026-10-09 19:30:00'),(815,804,'APPROVE',202,'陈志远','APPROVER','同意，注意报名截止时间','PENDING','APPROVED',NULL,'2026-10-09 20:05:00'),(816,805,'SUBMIT',204,'王小明','APPLICANT','方向调整，申请解散',NULL,'PENDING',NULL,'2026-10-10 11:20:00'),(817,805,'REJECT',202,'陈志远','APPROVER','比赛尚未结束，暂不能解散','PENDING','REJECTED',NULL,'2026-10-10 11:45:00'),(818,806,'SUBMIT',205,'赵晓亮','APPLICANT','嵌入式方向缺人，邀请入队',NULL,'PENDING',NULL,'2026-10-10 14:50:00'),(819,806,'CANCEL',205,'赵晓亮','APPLICANT','改邀请其他同学，先取消','PENDING','CANCELLED',NULL,'2026-10-10 15:10:00'),(820,807,'SUBMIT',NULL,'孙小雨','APPLICANT','参考传感器方案，限时查看',NULL,'PENDING',NULL,'2026-10-12 08:30:00');
/*!40000 ALTER TABLE `approval_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `audit_log`
--

DROP TABLE IF EXISTS `audit_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `audit_log` (
  `id` bigint NOT NULL COMMENT '主键',
  `user_id` bigint DEFAULT NULL COMMENT '操作人 sys_user.id，匿名时为空',
  `login_name` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人登录名',
  `real_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人姓名',
  `org_id` bigint DEFAULT NULL COMMENT '操作人所属组织，部门管理员按此过滤',
  `role` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作人角色',
  `ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户端 IP（已解析代理头）',
  `user_agent` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'User-Agent',
  `http_method` varchar(10) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求方法',
  `uri` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求路径',
  `biz_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务类型，对应 @AuditLog.BizType',
  `biz_type_label` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '业务类型中文名',
  `summary` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作摘要，SpEL 模板渲染结果',
  `request_params` varchar(2000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求参数摘要，敏感接口为空',
  `success` tinyint NOT NULL DEFAULT '1' COMMENT '是否成功：1 成功 0 失败',
  `error_message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '失败原因',
  `cost_millis` bigint DEFAULT NULL COMMENT '耗时毫秒',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_audit_user_time` (`user_id`,`created_at`),
  KEY `idx_audit_org_time` (`org_id`,`created_at`),
  KEY `idx_audit_biz_time` (`biz_type`,`created_at`),
  KEY `idx_audit_time` (`created_at`),
  KEY `idx_audit_success` (`success`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='审计日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `audit_log`
--

LOCK TABLES `audit_log` WRITE;
/*!40000 ALTER TABLE `audit_log` DISABLE KEYS */;
INSERT INTO `audit_log` VALUES (2109899467338293250,204,'stu_wang','王小明',2,'STUDENT','127.0.0.1','curl/8.17.0','POST','/api/approvals/99901/approve','APPROVAL_APPROVE','批准审批','批准审批单：99901','[99901,{\"comment\":\"hack\"}]',0,'该操作仅限：SUPER_ADMIN / DEPT_ADMIN',18,'2026-10-13 14:50:08'),(2109899703137869825,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','curl/8.17.0','POST','/api/approvals/99901/approve','APPROVAL_APPROVE','批准审批','批准审批单：99901','[99901,{\"comment\":\"ok\"}]',0,'缺少请求头 X-Idempotency-Key',0,'2026-10-13 14:51:04'),(2109900099730284545,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','curl/8.17.0','POST','/api/approvals/99901/approve','APPROVAL_APPROVE','批准审批','批准审批单：99901','[99901,{\"comment\":\"approved by sec test\"}]',1,NULL,68,'2026-10-13 14:52:39'),(2109900100246183937,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','curl/8.17.0','POST','/api/approvals/99901/approve','APPROVAL_APPROVE','批准审批','批准审批单：99901','[99901,{\"comment\":\"approved by sec test\"}]',0,'重复提交，请勿重复操作',5,'2026-10-13 14:52:39'),(2109900100434927618,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','curl/8.17.0','POST','/api/approvals/99901/approve','APPROVAL_APPROVE','批准审批','批准审批单：99901','[99901,{\"comment\":\"again\"}]',0,'当前审批状态不允许该操作',36,'2026-10-13 14:52:39'),(2109900311806877698,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','curl/8.17.0','POST','/api/files','FILE_UPLOAD','上传文件','上传文件：ok.pdf，业务：sec-test','[org.springframework.web.multipart.support.StandardMultipartHttpServletRequest$StandardMultipartFile@3cb137fc, sec-test, null]',0,'缺少请求头 X-Idempotency-Key',9,'2026-10-13 14:53:29'),(2109900312066924545,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','curl/8.17.0','POST','/api/files','FILE_UPLOAD','上传文件','上传文件：evil.exe，业务：sec-test','[org.springframework.web.multipart.support.StandardMultipartHttpServletRequest$StandardMultipartFile@3fffa87a, sec-test, null]',0,'不支持的文件格式：.exe',23,'2026-10-13 14:53:29'),(2109900312771567618,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','curl/8.17.0','POST','/api/files','FILE_UPLOAD','上传文件','上传文件：big.pdf，业务：sec-test','[org.springframework.web.multipart.support.StandardMultipartHttpServletRequest$StandardMultipartFile@23f367d6, sec-test, null]',0,'文件超过 20MB 上限',220,'2026-10-13 14:53:30'),(2109900320296148994,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','node','POST','/api/files','FILE_UPLOAD','上传文件','上传文件：eicar.pdf，业务：sec-test','[org.springframework.web.multipart.support.StandardMultipartHttpServletRequest$StandardMultipartFile@5e758066, sec-test, null]',1,NULL,390,'2026-10-13 14:53:31'),(2109900322254888962,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','curl/8.17.0','POST','/api/files','FILE_UPLOAD','上传文件','上传文件：<script>alert(1)</script>.pdf，业务：sec-test','[org.springframework.web.multipart.support.StandardMultipartHttpServletRequest$StandardMultipartFile@378bb250, sec-test, null]',1,NULL,81,'2026-10-13 14:53:32'),(2109900322837897218,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','curl/8.17.0','POST','/api/files','FILE_UPLOAD','上传文件','上传文件：ok.pdf，业务：sec-test','[org.springframework.web.multipart.support.StandardMultipartHttpServletRequest$StandardMultipartFile@53549f98, sec-test, null]',1,NULL,60,'2026-10-13 14:53:32'),(2109900323932610562,204,'stu_wang','王小明',2,'STUDENT','127.0.0.1','curl/8.17.0','GET','/api/files/2109900322900811777/download','FILE_DOWNLOAD','下载文件','下载文件：2109900322900811777','[2109900322900811777, org.springframework.web.context.request.async.StandardServletAsyncWebRequest$LifecycleHttpServletResponse@60af0a4d]',0,'无权下载该文件',13,'2026-10-13 14:53:32'),(2109900324188463106,203,'dept_liu','刘慧',3,'DEPT_ADMIN','127.0.0.1','curl/8.17.0','GET','/api/files/2109900322900811777/download','FILE_DOWNLOAD','下载文件','下载文件：2109900322900811777','[2109900322900811777, org.springframework.web.context.request.async.StandardServletAsyncWebRequest$LifecycleHttpServletResponse@7a81f825]',0,'无权下载该文件',3,'2026-10-13 14:53:32'),(2109900324448509953,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','curl/8.17.0','GET','/api/files/2109900322900811777/download','FILE_DOWNLOAD','下载文件','下载文件：2109900322900811777','[2109900322900811777, org.springframework.web.context.request.async.StandardServletAsyncWebRequest$LifecycleHttpServletResponse@39fe2831]',1,NULL,28,'2026-10-13 14:53:32'),(2109900775457824769,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','curl/8.17.0','GET','/api/files/2109900321214701569/download','FILE_DOWNLOAD','下载文件','下载文件：2109900321214701569','[2109900321214701569, org.springframework.web.context.request.async.StandardServletAsyncWebRequest$LifecycleHttpServletResponse@4ee295ba]',1,NULL,25,'2026-10-13 14:55:20'),(2109901034036666370,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','node','POST','/api/files','FILE_UPLOAD','上传文件','上传文件：eicar2.pdf，业务：sec-test','[org.springframework.web.multipart.support.StandardMultipartHttpServletRequest$StandardMultipartFile@13d125e3, sec-test, null]',0,'上传文件不能为空',32,'2026-10-13 14:56:21'),(2109901273405595649,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','node','POST','/api/files','FILE_UPLOAD','上传文件','上传文件：benign-68.pdf，业务：sec-test','[org.springframework.web.multipart.support.StandardMultipartHttpServletRequest$StandardMultipartFile@7945a9a6, sec-test, null]',1,NULL,108,'2026-10-13 14:57:18'),(2109901273921495042,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','node','POST','/api/files','FILE_UPLOAD','上传文件','上传文件：eicar3.pdf，业务：sec-test','[org.springframework.web.multipart.support.StandardMultipartHttpServletRequest$StandardMultipartFile@49565dba, sec-test, null]',0,'上传文件不能为空',25,'2026-10-13 14:57:18'),(2109901274051518465,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','node','POST','/api/files','FILE_UPLOAD','上传文件','上传文件：eicar-split.pdf，业务：sec-test','[org.springframework.web.multipart.support.StandardMultipartHttpServletRequest$StandardMultipartFile@19b7c125, sec-test, null]',1,NULL,77,'2026-10-13 14:57:19'),(2109901735567564801,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','curl/7.76.1','POST','/api/files','FILE_UPLOAD','上传文件','上传文件：eicar-e2e.pdf，业务：sec-test','[org.springframework.web.multipart.support.StandardMultipartHttpServletRequest$StandardMultipartFile@67bcdfb, sec-test, null]',0,'上传文件不能为空',32,'2026-10-13 14:59:09'),(2109902253668966401,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','curl/8.17.0','GET','/api/files/2109900322384912386/download','FILE_DOWNLOAD','下载文件','下载文件：2109900322384912386','[2109900322384912386, org.springframework.web.context.request.async.StandardServletAsyncWebRequest$LifecycleHttpServletResponse@4bf7c0e3]',1,NULL,30,'2026-10-13 15:01:12'),(2109902558058049537,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','curl/7.76.1','POST','/api/files','FILE_UPLOAD','上传文件','上传文件：eicar-final.pdf，业务：sec-test','[org.springframework.web.multipart.support.StandardMultipartHttpServletRequest$StandardMultipartFile@2911cb84, sec-test, null]',0,'文件未通过病毒检测，已拒绝并记录',617,'2026-10-13 15:02:25'),(2109902563489673218,202,'dept_chen','陈志远',2,'DEPT_ADMIN','127.0.0.1','curl/7.76.1','GET','/api/files/2109902560310390785/download','FILE_DOWNLOAD','下载文件','下载文件：2109902560310390785','[2109902560310390785, org.springframework.web.context.request.async.StandardServletAsyncWebRequest$LifecycleHttpServletResponse@950e057]',0,'文件未通过病毒检测，暂不可下载',35,'2026-10-13 15:02:26');
/*!40000 ALTER TABLE `audit_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `class_mapping`
--

DROP TABLE IF EXISTS `class_mapping`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `class_mapping` (
  `id` bigint NOT NULL COMMENT '主键',
  `semester_id` bigint NOT NULL COMMENT '学期 ID',
  `import_id` bigint DEFAULT NULL COMMENT '来源导入批次 ID',
  `class_key` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课表班级归一化键',
  `class_name_raw` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课表班级原始名',
  `member_class_name` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '档案侧班级名（member.class_name）',
  `grade` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '年级，如 2022',
  `grade_source` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '年级来源：STUDENT_NO 学号推导 / CLASS_NAME 班级名兜底',
  `confirmed` tinyint NOT NULL DEFAULT '0' COMMENT '是否已确认映射：1 已确认（参与空课统计）0 未确认',
  `confirmed_by` bigint DEFAULT NULL COMMENT '确认人',
  `confirmed_at` datetime DEFAULT NULL COMMENT '确认时间',
  `org_id` bigint DEFAULT NULL COMMENT '所属组织',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_class_mapping` (`semester_id`,`class_key`,`deleted`),
  KEY `idx_class_mapping_confirmed` (`semester_id`,`confirmed`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课表班级与档案班级映射';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `class_mapping`
--

LOCK TABLES `class_mapping` WRITE;
/*!40000 ALTER TABLE `class_mapping` DISABLE KEYS */;
/*!40000 ALTER TABLE `class_mapping` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `contest`
--

DROP TABLE IF EXISTS `contest`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contest` (
  `id` bigint NOT NULL COMMENT '主键',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '比赛名称',
  `template_id` bigint DEFAULT NULL COMMENT '来源模板 ID',
  `semester_id` bigint NOT NULL COMMENT '所属学期 ID',
  `org_id` bigint DEFAULT NULL COMMENT '归属组织（部门管理员数据范围依据）',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / PUBLISHED 已发布 / REGISTERING 报名中 / RUNNING 进行中 / FINISHED 已结束 / ARCHIVED 已归档',
  `register_start_at` datetime DEFAULT NULL COMMENT '报名开始时间',
  `register_end_at` datetime DEFAULT NULL COMMENT '报名截止时间',
  `contest_start_at` datetime DEFAULT NULL COMMENT '比赛开始时间',
  `contest_end_at` datetime DEFAULT NULL COMMENT '比赛结束时间',
  `contact_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人姓名',
  `contact_phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '联系人电话',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '比赛描述',
  `override_rules` json DEFAULT NULL COMMENT '比赛级规则覆盖 JSON，仅存被覆盖的键',
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL 手工 / CRAWLER 爬虫 / RSS / CRAWLER_POOL 待处理池转入',
  `source_ref` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '外部来源标识（URL 或来源 ID），用于去重',
  `archived_at` datetime DEFAULT NULL COMMENT '归档时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_contest_semester` (`semester_id`,`status`,`deleted`),
  KEY `idx_contest_org` (`org_id`,`deleted`),
  KEY `idx_contest_status_time` (`status`,`register_start_at`),
  KEY `idx_contest_source` (`source_type`,`source_ref`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='比赛实例';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `contest`
--

LOCK TABLES `contest` WRITE;
/*!40000 ALTER TABLE `contest` DISABLE KEYS */;
INSERT INTO `contest` VALUES (402,'第十六届蓝桥杯程序设计大赛',NULL,301,NULL,'REGISTERING',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'MANUAL',NULL,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL);
/*!40000 ALTER TABLE `contest` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `contest_source`
--

DROP TABLE IF EXISTS `contest_source`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contest_source` (
  `id` bigint NOT NULL COMMENT '主键',
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '抓取到的比赛标题',
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源类型：CRAWLER / RSS / MANUAL',
  `source_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源地址',
  `source_key` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源侧唯一标识，用于去重',
  `dedup_key` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '去重键：source_key + title + contest_time 的哈希',
  `raw_content` text COLLATE utf8mb4_unicode_ci COMMENT '原始抓取内容，供管理员判断',
  `contest_time` datetime DEFAULT NULL COMMENT '解析出的比赛时间',
  `parsed_rules` json DEFAULT NULL COMMENT '解析出的规则线索',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待处理 / CONFIRMED 已转为比赛 / IGNORED 已忽略',
  `confirmed_contest_id` bigint DEFAULT NULL COMMENT '确认后生成的比赛 ID',
  `fetched_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '抓取时间',
  `handled_at` datetime DEFAULT NULL COMMENT '处理时间',
  `handled_by` bigint DEFAULT NULL COMMENT '处理人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_contest_source_dedup` (`dedup_key`),
  KEY `idx_contest_source_status` (`status`,`fetched_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='外部比赛待处理池';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `contest_source`
--

LOCK TABLES `contest_source` WRITE;
/*!40000 ALTER TABLE `contest_source` DISABLE KEYS */;
/*!40000 ALTER TABLE `contest_source` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `contest_template`
--

DROP TABLE IF EXISTS `contest_template`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contest_template` (
  `id` bigint NOT NULL COMMENT '主键',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模板编码',
  `category` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '赛事类别，如 电控 / 建模 / 程序设计',
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模板说明',
  `default_rules` json DEFAULT NULL COMMENT '默认规则 JSON：{"memberLimit":5,"allowRepeatJoin":false,...}',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 启用 0 停用',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_template_name` (`name`,`deleted`),
  KEY `idx_template_status` (`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='比赛模板';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `contest_template`
--

LOCK TABLES `contest_template` WRITE;
/*!40000 ALTER TABLE `contest_template` DISABLE KEYS */;
INSERT INTO `contest_template` VALUES (401,'软件类竞赛通用模板',NULL,NULL,NULL,NULL,1,1,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL);
/*!40000 ALTER TABLE `contest_template` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `contest_track`
--

DROP TABLE IF EXISTS `contest_track`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `contest_track` (
  `id` bigint NOT NULL COMMENT '主键',
  `contest_id` bigint NOT NULL COMMENT '所属比赛 ID',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '赛道名称',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '赛道编码',
  `override_rules` json DEFAULT NULL COMMENT '赛道级规则覆盖 JSON',
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '赛道说明',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 启用 0 停用',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_track` (`contest_id`,`name`,`deleted`),
  KEY `idx_track_contest` (`contest_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='比赛赛道';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `contest_track`
--

LOCK TABLES `contest_track` WRITE;
/*!40000 ALTER TABLE `contest_track` DISABLE KEYS */;
/*!40000 ALTER TABLE `contest_track` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `course_timetable`
--

DROP TABLE IF EXISTS `course_timetable`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `course_timetable` (
  `id` bigint NOT NULL COMMENT '主键',
  `import_id` bigint DEFAULT NULL COMMENT '来源导入批次 ID',
  `semester_id` bigint NOT NULL COMMENT '学期 ID',
  `class_name` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '课表班级名称（原始，未归一化）',
  `class_key` varchar(150) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '归一化后的班级键，用于与人员档案班级匹配',
  `week_no` int NOT NULL COMMENT '周次，1 起',
  `weekday` tinyint NOT NULL COMMENT '星期：1 周一 ... 7 周日',
  `slot_index` tinyint NOT NULL COMMENT '节次索引：1-5 对应五大节',
  `slot_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '节次名称，如「第1-2节」',
  `course_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '课程名称',
  `teacher_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任课教师',
  `classroom` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上课地点',
  `week_type` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ALL' COMMENT '周次类型：ALL 每周 / ODD 单周 / EVEN 双周',
  `org_id` bigint DEFAULT NULL COMMENT '所属组织',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_course_semester_class` (`semester_id`,`class_key`,`week_no`,`deleted`),
  KEY `idx_course_slot` (`semester_id`,`week_no`,`weekday`,`slot_index`,`deleted`),
  KEY `idx_course_import` (`import_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课表记录';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `course_timetable`
--

LOCK TABLES `course_timetable` WRITE;
/*!40000 ALTER TABLE `course_timetable` DISABLE KEYS */;
/*!40000 ALTER TABLE `course_timetable` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `file_object`
--

DROP TABLE IF EXISTS `file_object`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `file_object` (
  `id` bigint NOT NULL COMMENT '主键',
  `object_key` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MinIO 对象 key，形如 member/photo/2026/10/07/uuid.jpg',
  `original_name` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '原始文件名，下载时回显',
  `file_ext` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '扩展名（小写，不含点）',
  `content_type` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'MIME 类型',
  `file_size` bigint NOT NULL DEFAULT '0' COMMENT '字节数',
  `sha256` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '文件 SHA-256，用于秒传与去重',
  `biz_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务类型：MEMBER_PHOTO / MEMBER_SIGNATURE / MATERIAL / ACHIEVEMENT / PROJECT',
  `biz_id` bigint DEFAULT NULL COMMENT '关联业务 ID',
  `scan_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SKIPPED' COMMENT '病毒检测：PENDING 待检 / CLEAN 通过 / INFECTED 命中 / SKIPPED 未启用 / UNAVAILABLE 扫描服务不可用',
  `scan_signature` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '命中的病毒特征名',
  `scan_at` datetime DEFAULT NULL COMMENT '检测时间',
  `download_count` int NOT NULL DEFAULT '0' COMMENT '累计下载次数',
  `org_id` bigint DEFAULT NULL COMMENT '所属组织，供数据范围过滤',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '上传人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_file_object_key` (`object_key`),
  KEY `idx_file_biz` (`biz_type`,`biz_id`,`deleted`),
  KEY `idx_file_org` (`org_id`,`deleted`),
  KEY `idx_file_sha256` (`sha256`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件对象（MinIO 引用）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `file_object`
--

LOCK TABLES `file_object` WRITE;
/*!40000 ALTER TABLE `file_object` DISABLE KEYS */;
/*!40000 ALTER TABLE `file_object` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `flyway_schema_history`
--

DROP TABLE IF EXISTS `flyway_schema_history`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `flyway_schema_history` (
  `installed_rank` int NOT NULL,
  `version` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `description` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL,
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL,
  `script` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL,
  `checksum` int DEFAULT NULL,
  `installed_by` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL,
  `installed_on` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `execution_time` int NOT NULL,
  `success` tinyint(1) NOT NULL,
  PRIMARY KEY (`installed_rank`),
  KEY `flyway_schema_history_s_idx` (`success`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `flyway_schema_history`
--

LOCK TABLES `flyway_schema_history` WRITE;
/*!40000 ALTER TABLE `flyway_schema_history` DISABLE KEYS */;
INSERT INTO `flyway_schema_history` VALUES (1,'1','init 52 tables','SQL','V1__init_52_tables.sql',-1708029282,'root','2026-10-09 15:00:19',7637,1),(2,NULL,'demo data','SQL','R__demo_data.sql',-621839457,'root','2026-10-09 15:00:19',45,1),(3,NULL,'demo data','SQL','R__demo_data.sql',326026555,'root','2026-10-12 11:20:37',146,1);
/*!40000 ALTER TABLE `flyway_schema_history` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `idempotent_record`
--

DROP TABLE IF EXISTS `idempotent_record`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `idempotent_record` (
  `id` bigint NOT NULL COMMENT '主键',
  `idem_key` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '幂等键（前端生成，含用户ID+业务标识+随机数）',
  `biz_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '业务类型',
  `user_id` bigint DEFAULT NULL COMMENT '发起用户',
  `request_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '请求体哈希，用于识别同键不同参的异常',
  `result_summary` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '首次执行结果摘要',
  `expire_at` datetime NOT NULL COMMENT '过期时间，到期可清理',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_idem_key` (`idem_key`),
  KEY `idx_idem_expire` (`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='幂等键';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `idempotent_record`
--

LOCK TABLES `idempotent_record` WRITE;
/*!40000 ALTER TABLE `idempotent_record` DISABLE KEYS */;
/*!40000 ALTER TABLE `idempotent_record` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `kb_collect`
--

DROP TABLE IF EXISTS `kb_collect`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kb_collect` (
  `id` bigint NOT NULL COMMENT '主键',
  `doc_id` bigint NOT NULL COMMENT '文档 ID',
  `member_id` bigint NOT NULL COMMENT '收藏人人员 ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_kb_collect` (`doc_id`,`member_id`),
  KEY `idx_kb_collect_member` (`member_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库收藏';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `kb_collect`
--

LOCK TABLES `kb_collect` WRITE;
/*!40000 ALTER TABLE `kb_collect` DISABLE KEYS */;
/*!40000 ALTER TABLE `kb_collect` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `kb_comment`
--

DROP TABLE IF EXISTS `kb_comment`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kb_comment` (
  `id` bigint NOT NULL COMMENT '主键',
  `doc_id` bigint NOT NULL COMMENT '文档 ID',
  `parent_id` bigint DEFAULT NULL COMMENT '父评论 ID，回复时为非空',
  `content` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论内容',
  `member_id` bigint NOT NULL COMMENT '评论人人员 ID',
  `member_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '评论人姓名冗余',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'VISIBLE' COMMENT '状态：VISIBLE 可见 / HIDDEN 已隐藏',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_kb_comment_doc` (`doc_id`,`status`,`deleted`,`created_at`),
  KEY `idx_kb_comment_parent` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库评论';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `kb_comment`
--

LOCK TABLES `kb_comment` WRITE;
/*!40000 ALTER TABLE `kb_comment` DISABLE KEYS */;
/*!40000 ALTER TABLE `kb_comment` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `kb_doc`
--

DROP TABLE IF EXISTS `kb_doc`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `kb_doc` (
  `id` bigint NOT NULL COMMENT '主键',
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题',
  `category` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类，对应字典 KB_CATEGORY',
  `is_prompt` tinyint NOT NULL DEFAULT '0' COMMENT '是否 AI 提示词：1 是 0 否',
  `prompt_schema` json DEFAULT NULL COMMENT '提示词结构模板，仅 is_prompt=1 时有值',
  `content` longtext COLLATE utf8mb4_unicode_ci COMMENT '正文内容（Markdown）',
  `summary` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '摘要',
  `cover_file_id` bigint DEFAULT NULL COMMENT '封面文件 ID',
  `attach_file_id` bigint DEFAULT NULL COMMENT '附件文件 ID',
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL 管理员上传 / LINK 外部链接收录 / AI_SUMMARY AI 总结',
  `source_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '外部来源链接',
  `tags` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签，逗号分隔',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PUBLISHED' COMMENT '状态：DRAFT 草稿 / PUBLISHED 已发布 / OFFLINE 已下架',
  `top_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否置顶：1 是 0 否',
  `view_count` int NOT NULL DEFAULT '0' COMMENT '浏览数',
  `collect_count` int NOT NULL DEFAULT '0' COMMENT '收藏数',
  `comment_count` int NOT NULL DEFAULT '0' COMMENT '评论数',
  `ai_model` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AI 总结时使用的模型名',
  `org_id` bigint DEFAULT NULL COMMENT '归属组织',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_kb_doc_category` (`category`,`status`,`deleted`),
  KEY `idx_kb_doc_prompt` (`is_prompt`,`status`,`deleted`),
  KEY `idx_kb_doc_status` (`status`,`top_flag`,`deleted`),
  FULLTEXT KEY `ft_kb_doc` (`title`,`summary`) /*!50100 WITH PARSER `ngram` */ 
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识库文档';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `kb_doc`
--

LOCK TABLES `kb_doc` WRITE;
/*!40000 ALTER TABLE `kb_doc` DISABLE KEYS */;
/*!40000 ALTER TABLE `kb_doc` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `material_requirement`
--

DROP TABLE IF EXISTS `material_requirement`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `material_requirement` (
  `id` bigint NOT NULL COMMENT '主键',
  `task_id` bigint NOT NULL COMMENT '所属材料任务 ID',
  `contest_id` bigint NOT NULL COMMENT '所属比赛 ID（冗余，便于校验赛队比赛匹配）',
  `material_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '材料类型：REGISTRATION_FORM 报名表 / PROJECT_PLAN 项目书 / COMMITMENT 承诺书 / ID_COPY 身份证复印件 / STUDENT_CARD_COPY 学生证复印件 / TEAM_PHOTO 团队合照 / DEFENSE_PPT 答辩PPT / RESEARCH_REPORT 研究报告 / SOURCE_CODE 源代码 / AWARD_CERT 获奖证书 / EVIDENCE 佐证材料 / OTHER 其他',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '要求名称，展示用',
  `required_flag` tinyint NOT NULL DEFAULT '1' COMMENT '是否必传：1 必传 0 选传。必传项决定齐全率分母',
  `allowed_formats` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '允许格式，逗号分隔，如 pdf,docx,jpg；空表示不限',
  `max_size_mb` int DEFAULT NULL COMMENT '单文件大小上限（MB），空表示用全局配置',
  `naming_rule` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '命名规则（可选），前端校验提示用',
  `system_generated` tinyint NOT NULL DEFAULT '0' COMMENT '是否系统自动追加：1 是（成果佐证材料）。系统项不参与管理端增删',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_material_req_task` (`task_id`,`deleted`),
  KEY `idx_material_req_contest` (`contest_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='材料要求';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `material_requirement`
--

LOCK TABLES `material_requirement` WRITE;
/*!40000 ALTER TABLE `material_requirement` DISABLE KEYS */;
/*!40000 ALTER TABLE `material_requirement` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `material_submission`
--

DROP TABLE IF EXISTS `material_submission`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `material_submission` (
  `id` bigint NOT NULL COMMENT '主键',
  `requirement_id` bigint NOT NULL COMMENT '材料要求 ID',
  `task_id` bigint NOT NULL COMMENT '材料任务 ID（冗余）',
  `contest_id` bigint NOT NULL COMMENT '比赛 ID（冗余）',
  `team_id` bigint NOT NULL COMMENT '赛队 ID',
  `semester_id` bigint NOT NULL COMMENT '学期 ID（冗余，便于按学期归档）',
  `org_id` bigint NOT NULL COMMENT '赛队所属组织（数据范围过滤依据）',
  `file_id` bigint NOT NULL COMMENT '文件对象 ID',
  `file_name` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名冗余，列表展示免联表',
  `file_size` bigint NOT NULL DEFAULT '0' COMMENT '文件大小冗余',
  `is_current` tinyint NOT NULL DEFAULT '1' COMMENT '是否当前生效版本：1 生效 0 已被覆盖',
  `version_no` int NOT NULL DEFAULT '1' COMMENT '版本号，每次覆盖 +1',
  `submitted_by` bigint NOT NULL COMMENT '提交人人员 ID（必须是队长或队员）',
  `submitted_by_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '提交人姓名冗余',
  `submitted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `source_zip_name` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源压缩包名（若通过 zip 批量上传）',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_submission_team` (`team_id`,`requirement_id`,`is_current`,`deleted`),
  KEY `idx_submission_contest` (`contest_id`,`deleted`),
  KEY `idx_submission_semester` (`semester_id`,`deleted`),
  KEY `idx_submission_org` (`org_id`,`deleted`),
  KEY `idx_submission_req` (`requirement_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='材料提交';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `material_submission`
--

LOCK TABLES `material_submission` WRITE;
/*!40000 ALTER TABLE `material_submission` DISABLE KEYS */;
/*!40000 ALTER TABLE `material_submission` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `material_task`
--

DROP TABLE IF EXISTS `material_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `material_task` (
  `id` bigint NOT NULL COMMENT '主键',
  `contest_id` bigint NOT NULL COMMENT '所属比赛 ID',
  `semester_id` bigint NOT NULL COMMENT '所属学期 ID',
  `org_id` bigint DEFAULT NULL COMMENT '归属组织',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务名称，如「初赛材料收集」',
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务说明',
  `deadline` datetime DEFAULT NULL COMMENT '截止时间',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 进行中 / CLOSED 已关闭',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_material_task_contest` (`contest_id`,`status`,`deleted`),
  KEY `idx_material_task_semester` (`semester_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='材料收集任务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `material_task`
--

LOCK TABLES `material_task` WRITE;
/*!40000 ALTER TABLE `material_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `material_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `member`
--

DROP TABLE IF EXISTS `member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `member` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '姓名（必填）',
  `student_no` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学号。可空、不可重复、不可修改',
  `job_no` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学工号。与学号分别独立，登录名默认取此值',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `college` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '学院',
  `major` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '专业',
  `class_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '班级',
  `gender` varchar(8) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '性别：MALE 男 / FEMALE 女 / UNKNOWN 未知',
  `nation` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '民族',
  `id_card` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '身份证号密文（AES），禁止明文入库',
  `id_card_last4` varchar(4) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '身份证号后 4 位，供列表脱敏展示，避免解密',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '邮箱',
  `photo_file_id` bigint DEFAULT NULL COMMENT '证件照，引用 file_object.id（存 MinIO）',
  `signature_file_id` bigint DEFAULT NULL COMMENT '电子签名，引用 file_object.id（存 MinIO）',
  `org_id` bigint DEFAULT NULL COMMENT '所属组织（分子组织）ID',
  `member_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'STUDENT' COMMENT '类型：STUDENT 学生 / TEACHER 教师 / OTHER 其他',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 在册 0 离册',
  `has_account` tinyint NOT NULL DEFAULT '0' COMMENT '是否已绑定登录账号：1 是 0 否',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0 未删 1 已删',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_student_no` (`student_no`,`deleted`),
  UNIQUE KEY `uk_member_job_no` (`job_no`,`deleted`),
  KEY `idx_member_org` (`org_id`,`deleted`),
  KEY `idx_member_type` (`member_type`,`status`),
  KEY `idx_member_name` (`name`),
  KEY `idx_member_phone` (`phone`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人员档案';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `member`
--

LOCK TABLES `member` WRITE;
/*!40000 ALTER TABLE `member` DISABLE KEYS */;
INSERT INTO `member` VALUES (101,'陈志远',NULL,NULL,NULL,'计算机学院',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'TEACHER',1,1,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL),(102,'刘慧',NULL,NULL,NULL,'电子信息学院',NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,NULL,'TEACHER',1,1,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL),(103,'王小明','2023010001',NULL,NULL,'计算机学院',NULL,'软件2301班',NULL,NULL,NULL,NULL,NULL,NULL,NULL,4,'STUDENT',1,1,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL),(104,'赵晓亮','2023020002',NULL,NULL,'电子信息学院',NULL,'电信2302班',NULL,NULL,NULL,NULL,NULL,NULL,NULL,5,'STUDENT',1,1,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL),(105,'孙小雨','2023020003',NULL,NULL,'电子信息学院',NULL,'电信2302班',NULL,NULL,NULL,NULL,NULL,NULL,NULL,5,'STUDENT',1,0,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL);
/*!40000 ALTER TABLE `member` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `member_skill`
--

DROP TABLE IF EXISTS `member_skill`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `member_skill` (
  `id` bigint NOT NULL COMMENT '主键',
  `member_id` bigint NOT NULL COMMENT '人员档案 ID',
  `skill_value` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '技能值，对应 sys_dict_item.item_value（SKILL 字典）',
  `skill_label` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '技能展示名，冗余避免联表',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_member_skill` (`member_id`,`skill_value`,`deleted`),
  KEY `idx_member_skill_value` (`skill_value`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='人员技能标签';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `member_skill`
--

LOCK TABLES `member_skill` WRITE;
/*!40000 ALTER TABLE `member_skill` DISABLE KEYS */;
/*!40000 ALTER TABLE `member_skill` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `milestone_node`
--

DROP TABLE IF EXISTS `milestone_node`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `milestone_node` (
  `id` bigint NOT NULL COMMENT '主键',
  `contest_id` bigint NOT NULL COMMENT '所属比赛 ID',
  `semester_id` bigint NOT NULL COMMENT '所属学期 ID',
  `org_id` bigint DEFAULT NULL COMMENT '归属组织',
  `node_type` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点类型：REGISTER_START 报名开始 / REGISTER_END 报名截止 / WORK_SUBMIT 作品提交 / OTHER 其他',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '节点名称',
  `node_time` datetime NOT NULL COMMENT '节点时间',
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '状态：DRAFT 草稿 / PENDING 待审核 / APPROVED 已通过 / REJECTED 已驳回',
  `approved_at` datetime DEFAULT NULL COMMENT '审核通过时间',
  `approved_by` bigint DEFAULT NULL COMMENT '审核人',
  `reject_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '驳回原因',
  `synced` tinyint NOT NULL DEFAULT '0' COMMENT '是否已同步给挂载项目：1 已同步 0 未同步',
  `synced_at` datetime DEFAULT NULL COMMENT '同步时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_milestone_contest` (`contest_id`,`status`,`deleted`),
  KEY `idx_milestone_sync` (`status`,`synced`,`deleted`),
  KEY `idx_milestone_time` (`node_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='比赛时间节点';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `milestone_node`
--

LOCK TABLES `milestone_node` WRITE;
/*!40000 ALTER TABLE `milestone_node` DISABLE KEYS */;
/*!40000 ALTER TABLE `milestone_node` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `performance_rule`
--

DROP TABLE IF EXISTS `performance_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `performance_rule` (
  `id` bigint NOT NULL COMMENT '主键',
  `sub_org_id` bigint NOT NULL COMMENT '子组织 ID',
  `semester_id` bigint DEFAULT NULL COMMENT '学期 ID，为空表示对所有学期生效',
  `dimension_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '打分维度键，如 TECH_OUTPUT / COLLABORATION / ATTENDANCE',
  `dimension_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '维度名称，如「技术产出」',
  `weight` decimal(6,3) NOT NULL DEFAULT '1.000' COMMENT '权重，参与总分加权计算',
  `max_score` decimal(6,2) NOT NULL DEFAULT '100.00' COMMENT '该维度满分',
  `description` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '维度说明，展示给打分人',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perf_rule` (`sub_org_id`,`semester_id`,`dimension_key`,`deleted`),
  KEY `idx_perf_rule_org` (`sub_org_id`,`enabled`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='子组织绩效规则';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `performance_rule`
--

LOCK TABLES `performance_rule` WRITE;
/*!40000 ALTER TABLE `performance_rule` DISABLE KEYS */;
/*!40000 ALTER TABLE `performance_rule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `performance_score`
--

DROP TABLE IF EXISTS `performance_score`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `performance_score` (
  `id` bigint NOT NULL COMMENT '主键',
  `weekly_id` bigint NOT NULL COMMENT '周报 ID',
  `sub_org_id` bigint NOT NULL COMMENT '子组织 ID（冗余，便于直接聚合）',
  `dimension_key` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '维度键，对应 performance_rule',
  `dimension_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '维度名称冗余',
  `weight` decimal(6,3) NOT NULL DEFAULT '1.000' COMMENT '打分时的权重快照（规则后续变更不影响历史分）',
  `max_score` decimal(6,2) NOT NULL DEFAULT '100.00' COMMENT '满分快照',
  `score` decimal(6,2) NOT NULL COMMENT '该维度得分',
  `weighted_score` decimal(8,2) DEFAULT NULL COMMENT '加权分 = score × weight',
  `comment_text` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '该项评语',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perf_score` (`weekly_id`,`dimension_key`),
  KEY `idx_perf_score_weekly` (`weekly_id`),
  KEY `idx_perf_score_org` (`sub_org_id`,`dimension_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='绩效维度得分';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `performance_score`
--

LOCK TABLES `performance_score` WRITE;
/*!40000 ALTER TABLE `performance_score` DISABLE KEYS */;
/*!40000 ALTER TABLE `performance_score` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `performance_summary`
--

DROP TABLE IF EXISTS `performance_summary`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `performance_summary` (
  `id` bigint NOT NULL COMMENT '主键',
  `member_id` bigint NOT NULL COMMENT '人员 ID',
  `member_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '姓名冗余',
  `semester_id` bigint NOT NULL COMMENT '学期 ID',
  `sub_org_id` bigint NOT NULL COMMENT '子组织 ID',
  `org_id` bigint NOT NULL COMMENT '归属组织',
  `week_count` int NOT NULL DEFAULT '0' COMMENT '已打分周数',
  `submitted_count` int NOT NULL DEFAULT '0' COMMENT '已提交周数',
  `total_score` decimal(10,2) NOT NULL DEFAULT '0.00' COMMENT '累计总分',
  `avg_score` decimal(8,2) NOT NULL DEFAULT '0.00' COMMENT '平均分',
  `best_week_score` decimal(8,2) DEFAULT NULL COMMENT '最高周分',
  `best_week_no` int DEFAULT NULL COMMENT '最高分所在周次',
  `recent_trend` decimal(8,3) DEFAULT NULL COMMENT '近4周分数趋势斜率',
  `rank_in_org` int DEFAULT NULL COMMENT '本子组织内排名',
  `activity_tags` json DEFAULT NULL COMMENT '活跃度/功劳标签 JSON 数组',
  `last_calc_at` datetime DEFAULT NULL COMMENT '最后聚合计算时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perf_summary` (`member_id`,`semester_id`,`sub_org_id`,`deleted`),
  KEY `idx_perf_summary_org` (`semester_id`,`sub_org_id`,`total_score`,`deleted`),
  KEY `idx_perf_summary_member` (`member_id`,`semester_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='绩效周期汇总';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `performance_summary`
--

LOCK TABLES `performance_summary` WRITE;
/*!40000 ALTER TABLE `performance_summary` DISABLE KEYS */;
/*!40000 ALTER TABLE `performance_summary` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `performance_weekly`
--

DROP TABLE IF EXISTS `performance_weekly`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `performance_weekly` (
  `id` bigint NOT NULL COMMENT '主键',
  `member_id` bigint NOT NULL COMMENT '上报人人员 ID',
  `member_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '姓名冗余',
  `semester_id` bigint NOT NULL COMMENT '学期 ID',
  `week_no` int NOT NULL COMMENT '周次',
  `sub_org_id` bigint NOT NULL COMMENT '所属子组织 ID',
  `org_id` bigint NOT NULL COMMENT '归属组织（数据范围过滤）',
  `work_content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '本周工作内容',
  `attach_file_id` bigint DEFAULT NULL COMMENT '附件文件 ID（可选）',
  `attach_file_name` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '附件名冗余',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SUBMITTED' COMMENT '状态：SUBMITTED 待审核 / SCORED 已打分 / REJECTED 已驳回',
  `submitted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
  `total_score` decimal(8,2) DEFAULT NULL COMMENT '加权总分',
  `activity_tags` json DEFAULT NULL COMMENT '活跃度/功劳标签 JSON 数组',
  `scored_at` datetime DEFAULT NULL COMMENT '打分时间',
  `scored_by` bigint DEFAULT NULL COMMENT '打分管理员 sys_user.id',
  `scored_by_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '打分人姓名冗余',
  `score_comment` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '打分评语',
  `reject_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '驳回原因',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_perf_weekly` (`member_id`,`semester_id`,`week_no`,`sub_org_id`,`deleted`),
  KEY `idx_perf_weekly_semester` (`semester_id`,`week_no`,`sub_org_id`,`deleted`),
  KEY `idx_perf_weekly_member` (`member_id`,`semester_id`,`deleted`),
  KEY `idx_perf_weekly_status` (`status`,`sub_org_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='绩效周报';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `performance_weekly`
--

LOCK TABLES `performance_weekly` WRITE;
/*!40000 ALTER TABLE `performance_weekly` DISABLE KEYS */;
/*!40000 ALTER TABLE `performance_weekly` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project`
--

DROP TABLE IF EXISTS `project`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project` (
  `id` bigint NOT NULL COMMENT '主键',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '项目描述',
  `owner_member_id` bigint NOT NULL COMMENT '项目所有人人员 ID',
  `owner_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '项目所有人姓名冗余',
  `contest_id` bigint DEFAULT NULL COMMENT '所属比赛 ID（可选挂载）',
  `track_id` bigint DEFAULT NULL COMMENT '所属赛道 ID（可选）',
  `semester_id` bigint NOT NULL COMMENT '所属学期 ID',
  `org_id` bigint NOT NULL COMMENT '所属子组织 ID（数据范围与绩效隔离依据）',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 进行中 / FINISHED 已结束 / ARCHIVED 已归档',
  `milestone_anchor` datetime DEFAULT NULL COMMENT '比赛时间节点锚点日期',
  `start_date` date DEFAULT NULL COMMENT '项目开始日期',
  `end_date` date DEFAULT NULL COMMENT '项目结束日期',
  `visibility` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ORG' COMMENT '可见范围：PRIVATE 仅所有人 / ORG 本部门 / PUBLIC 全平台',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_name` (`semester_id`,`org_id`,`name`,`deleted`),
  KEY `idx_project_owner` (`owner_member_id`,`status`,`deleted`),
  KEY `idx_project_org` (`org_id`,`semester_id`,`deleted`),
  KEY `idx_project_contest` (`contest_id`,`deleted`),
  KEY `idx_project_status` (`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project`
--

LOCK TABLES `project` WRITE;
/*!40000 ALTER TABLE `project` DISABLE KEYS */;
/*!40000 ALTER TABLE `project` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_advisor`
--

DROP TABLE IF EXISTS `project_advisor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_advisor` (
  `id` bigint NOT NULL COMMENT '主键',
  `project_id` bigint NOT NULL COMMENT '项目 ID',
  `member_id` bigint NOT NULL COMMENT '教师人员 ID',
  `advisor_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '教师姓名冗余',
  `title` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '职称',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 在任 / REMOVED 已移除',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_advisor` (`project_id`,`member_id`,`deleted`),
  KEY `idx_project_advisor_member` (`member_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目指导教师';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_advisor`
--

LOCK TABLES `project_advisor` WRITE;
/*!40000 ALTER TABLE `project_advisor` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_advisor` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_file`
--

DROP TABLE IF EXISTS `project_file`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_file` (
  `id` bigint NOT NULL COMMENT '主键',
  `project_id` bigint NOT NULL COMMENT '项目 ID',
  `file_id` bigint NOT NULL COMMENT '文件对象 ID（file_object 中已含 scan_status）',
  `file_name` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名冗余',
  `file_size` bigint NOT NULL DEFAULT '0' COMMENT '文件大小冗余',
  `file_category` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DOC' COMMENT '文件分类：DOC 文档 / IMAGE 图片 / CODE 代码 / OTHER 其他',
  `scan_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '检测状态：CLEAN 通过 / INFECTED 命中 / UNAVAILABLE 服务不可用被拒',
  `scan_signature` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '命中特征名（被拒时记录）',
  `uploaded_by` bigint NOT NULL COMMENT '上传人人员 ID',
  `uploaded_by_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上传人姓名冗余',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_project_file_project` (`project_id`,`deleted`),
  KEY `idx_project_file_scan` (`scan_status`,`created_at`),
  KEY `idx_project_file_id` (`file_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目文件';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_file`
--

LOCK TABLES `project_file` WRITE;
/*!40000 ALTER TABLE `project_file` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_file` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_member`
--

DROP TABLE IF EXISTS `project_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_member` (
  `id` bigint NOT NULL COMMENT '主键',
  `project_id` bigint NOT NULL COMMENT '项目 ID',
  `member_id` bigint NOT NULL COMMENT '人员档案 ID',
  `member_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '姓名冗余',
  `member_org_id` bigint DEFAULT NULL COMMENT '所属组织冗余（跨部门成员识别）',
  `project_role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEMBER' COMMENT '项目角色：OWNER 所有人 / MEMBER 成员',
  `join_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_member` (`project_id`,`member_id`,`deleted`),
  KEY `idx_project_member_member` (`member_id`,`deleted`),
  KEY `idx_project_member_role` (`project_id`,`project_role`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目成员';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_member`
--

LOCK TABLES `project_member` WRITE;
/*!40000 ALTER TABLE `project_member` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_member` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_task`
--

DROP TABLE IF EXISTS `project_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_task` (
  `id` bigint NOT NULL COMMENT '主键',
  `project_id` bigint NOT NULL COMMENT '项目 ID',
  `milestone_node_id` bigint DEFAULT NULL COMMENT '来源时间节点 ID，手工任务为空',
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务标题',
  `description` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '任务说明',
  `assignee_member_id` bigint DEFAULT NULL COMMENT '负责人人员 ID',
  `assignee_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '负责人姓名冗余',
  `due_date` datetime DEFAULT NULL COMMENT '截止时间',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TODO' COMMENT '状态：TODO 未开始 / DOING 进行中 / DONE 已完成 / BLOCKED 受阻',
  `priority` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NORMAL' COMMENT '优先级：HIGH / NORMAL / LOW',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间',
  `completed_by` bigint DEFAULT NULL COMMENT '完成人',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_project_task_project` (`project_id`,`status`,`deleted`),
  KEY `idx_project_task_assignee` (`assignee_member_id`,`status`,`deleted`),
  KEY `idx_project_task_milestone` (`milestone_node_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目任务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_task`
--

LOCK TABLES `project_task` WRITE;
/*!40000 ALTER TABLE `project_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `project_view_grant`
--

DROP TABLE IF EXISTS `project_view_grant`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `project_view_grant` (
  `id` bigint NOT NULL COMMENT '主键',
  `project_id` bigint NOT NULL COMMENT '项目 ID',
  `applicant_id` bigint NOT NULL COMMENT '申请人人员 ID',
  `applicant_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '申请人姓名冗余',
  `applicant_org_id` bigint DEFAULT NULL COMMENT '申请人所属组织（跨部门判定）',
  `approval_id` bigint DEFAULT NULL COMMENT '关联审批单 ID',
  `grant_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ONCE' COMMENT '授权类型：ONCE 一次性 / LIMITED 限时',
  `expire_at` datetime DEFAULT NULL COMMENT '限时授权的到期时间；一次性授权用掉后置为 used',
  `used_count` int NOT NULL DEFAULT '0' COMMENT '已使用次数',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待审批 / ACTIVE 生效中 / EXPIRED 已失效 / REVOKED 已撤销',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_pvg_project` (`project_id`,`status`,`deleted`),
  KEY `idx_pvg_applicant` (`applicant_id`,`status`,`deleted`),
  KEY `idx_pvg_expire` (`status`,`expire_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目查看授权';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `project_view_grant`
--

LOCK TABLES `project_view_grant` WRITE;
/*!40000 ALTER TABLE `project_view_grant` DISABLE KEYS */;
/*!40000 ALTER TABLE `project_view_grant` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `schedule`
--

DROP TABLE IF EXISTS `schedule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schedule` (
  `id` bigint NOT NULL COMMENT '主键',
  `semester_id` bigint NOT NULL COMMENT '学期 ID',
  `week_no` int NOT NULL COMMENT '周次',
  `sub_org_id` bigint NOT NULL COMMENT '子组织 ID（排班隔离维度）',
  `member_id` bigint NOT NULL COMMENT '排班人员 ID',
  `member_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '人员姓名冗余',
  `member_class_name` varchar(150) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '人员班级冗余（展示用）',
  `weekday` tinyint NOT NULL COMMENT '星期：1-7',
  `slot_index` tinyint NOT NULL COMMENT '节次索引：1-5',
  `slot_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '节次名称，如「第1-2节」',
  `duty_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '值班岗位/内容，如「机房值班」',
  `course_conflict` tinyint NOT NULL DEFAULT '0' COMMENT '是否与课表冲突：1 有课冲突（管理员强制保存）0 空课',
  `conflict_note` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '冲突提示，如「第3-4节有课：高等数学」',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PLANNED' COMMENT '状态：PLANNED 已排 / CANCELLED 已取消',
  `org_id` bigint NOT NULL COMMENT '归属组织（数据范围过滤依据）',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule_conflict` (`semester_id`,`week_no`,`sub_org_id`,`member_id`,`slot_index`,`deleted`),
  KEY `idx_schedule_member` (`member_id`,`semester_id`,`week_no`,`deleted`),
  KEY `idx_schedule_suborg` (`sub_org_id`,`semester_id`,`week_no`,`deleted`),
  KEY `idx_schedule_org` (`org_id`,`semester_id`,`deleted`),
  KEY `idx_schedule_slot` (`semester_id`,`week_no`,`sub_org_id`,`weekday`,`slot_index`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='排班（学期+周次+子组织三维）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `schedule`
--

LOCK TABLES `schedule` WRITE;
/*!40000 ALTER TABLE `schedule` DISABLE KEYS */;
/*!40000 ALTER TABLE `schedule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `schedule_rule`
--

DROP TABLE IF EXISTS `schedule_rule`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `schedule_rule` (
  `id` bigint NOT NULL COMMENT '主键',
  `sub_org_id` bigint NOT NULL COMMENT '子组织 ID',
  `semester_id` bigint DEFAULT NULL COMMENT '学期 ID，为空表示对该子组织所有学期生效',
  `weekday` tinyint NOT NULL COMMENT '可排班星期：1-7',
  `slot_index` tinyint NOT NULL COMMENT '可排班节次：1-5',
  `min_person` int NOT NULL DEFAULT '1' COMMENT '该时段最少人数',
  `max_person` int NOT NULL DEFAULT '5' COMMENT '该时段最多人数',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用该时段',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_schedule_rule` (`sub_org_id`,`semester_id`,`weekday`,`slot_index`,`deleted`),
  KEY `idx_schedule_rule_org` (`sub_org_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='子组织排班规则';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `schedule_rule`
--

LOCK TABLES `schedule_rule` WRITE;
/*!40000 ALTER TABLE `schedule_rule` DISABLE KEYS */;
/*!40000 ALTER TABLE `schedule_rule` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `semester`
--

DROP TABLE IF EXISTS `semester`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `semester` (
  `id` bigint NOT NULL COMMENT '主键',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '学期名称，不可重复，如 2026-2027学年第一学期',
  `start_date` date DEFAULT NULL COMMENT '开始日期',
  `end_date` date DEFAULT NULL COMMENT '结束日期',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PREPARING' COMMENT '状态：PREPARING 未开始 / ACTIVE 进行中 / ARCHIVED 已归档',
  `is_current` tinyint NOT NULL DEFAULT '0' COMMENT '是否当前学期：1 是 0 否，全平台仅一个为 1',
  `total_weeks` int NOT NULL DEFAULT '20' COMMENT '教学周数，排班与绩效周次的取值范围',
  `archived_at` datetime DEFAULT NULL COMMENT '归档时间',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_semester_name` (`name`,`deleted`),
  KEY `idx_semester_current` (`is_current`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学期';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `semester`
--

LOCK TABLES `semester` WRITE;
/*!40000 ALTER TABLE `semester` DISABLE KEYS */;
INSERT INTO `semester` VALUES (301,'2026-2027学年第一学期',NULL,NULL,'ACTIVE',1,20,NULL,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL);
/*!40000 ALTER TABLE `semester` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `semester_switch_task`
--

DROP TABLE IF EXISTS `semester_switch_task`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `semester_switch_task` (
  `id` bigint NOT NULL COMMENT '主键',
  `from_semester_id` bigint NOT NULL COMMENT '源学期 ID',
  `from_semester_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '源学期名冗余',
  `to_semester_id` bigint NOT NULL COMMENT '目标学期 ID',
  `to_semester_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标学期名冗余',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING_EXECUTE' COMMENT '状态：PENDING_EXECUTE 待执行窗口内 / CANCELLED 已取消 / RUNNING 执行中 / SUCCESS 成功 / FAILED 失败',
  `window_minutes` int NOT NULL DEFAULT '30' COMMENT '待执行窗口分钟数',
  `execute_at` datetime NOT NULL COMMENT '计划的执行时间',
  `started_at` datetime DEFAULT NULL COMMENT '实际开始执行时间',
  `finished_at` datetime DEFAULT NULL COMMENT '执行完成时间',
  `impact_snapshot` json DEFAULT NULL COMMENT '影响范围快照：待归档比赛数/待继承赛队数/成员关系数',
  `archived_contests` int NOT NULL DEFAULT '0' COMMENT '归档比赛数',
  `inherited_teams` int NOT NULL DEFAULT '0' COMMENT '继承赛队数',
  `inherited_members` int NOT NULL DEFAULT '0' COMMENT '继承成员关系数',
  `error_message` text COLLATE utf8mb4_unicode_ci COMMENT '失败原因',
  `version` int NOT NULL DEFAULT '0' COMMENT '乐观锁版本号',
  `initiated_by` bigint NOT NULL COMMENT '发起人 sys_user.id',
  `initiated_by_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发起人姓名冗余',
  `cancelled_by` bigint DEFAULT NULL COMMENT '取消人',
  `cancelled_at` datetime DEFAULT NULL COMMENT '取消时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_switch_status` (`status`,`execute_at`),
  KEY `idx_switch_semester` (`from_semester_id`,`to_semester_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='学期切换任务';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `semester_switch_task`
--

LOCK TABLES `semester_switch_task` WRITE;
/*!40000 ALTER TABLE `semester_switch_task` DISABLE KEYS */;
/*!40000 ALTER TABLE `semester_switch_task` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_config`
--

DROP TABLE IF EXISTS `sys_config`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_config` (
  `id` bigint NOT NULL COMMENT '主键',
  `config_key` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置键',
  `config_value` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '配置值',
  `config_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '配置说明',
  `editable` tinyint NOT NULL DEFAULT '1' COMMENT '是否允许界面修改：1 可改 0 只读',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_config_key` (`config_key`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_config`
--

LOCK TABLES `sys_config` WRITE;
/*!40000 ALTER TABLE `sys_config` DISABLE KEYS */;
/*!40000 ALTER TABLE `sys_config` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_item`
--

DROP TABLE IF EXISTS `sys_dict_item`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_item` (
  `id` bigint NOT NULL COMMENT '主键',
  `dict_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属字典编码',
  `item_value` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典值',
  `item_label` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示文案',
  `source` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PRESET' COMMENT '来源：PRESET 预设 / USER 用户补充',
  `audit_status` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'APPROVED' COMMENT '审核状态：APPROVED 已入库 / PENDING 待审核 / REJECTED 已驳回',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '排序号',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 启用 0 停用',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_item` (`dict_code`,`item_value`,`deleted`),
  KEY `idx_dict_item_code` (`dict_code`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典项';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_item`
--

LOCK TABLES `sys_dict_item` WRITE;
/*!40000 ALTER TABLE `sys_dict_item` DISABLE KEYS */;
INSERT INTO `sys_dict_item` VALUES (902,'MATERIAL_TYPE','photo','证件照','PRESET','APPROVED',1,1,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL),(903,'MATERIAL_TYPE','form','报名表','PRESET','APPROVED',2,1,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL),(904,'MATERIAL_TYPE','certificate','获奖证书','PRESET','APPROVED',3,1,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL);
/*!40000 ALTER TABLE `sys_dict_item` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_dict_type`
--

DROP TABLE IF EXISTS `sys_dict_type`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL COMMENT '主键',
  `dict_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典编码，如 SKILL / MATERIAL_TYPE',
  `dict_name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字典名称',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dict_type_code` (`dict_code`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='字典类型';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_dict_type`
--

LOCK TABLES `sys_dict_type` WRITE;
/*!40000 ALTER TABLE `sys_dict_type` DISABLE KEYS */;
INSERT INTO `sys_dict_type` VALUES (901,'MATERIAL_TYPE','材料类型','比赛材料分类',0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL);
/*!40000 ALTER TABLE `sys_dict_type` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_login_log`
--

DROP TABLE IF EXISTS `sys_login_log`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_login_log` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `user_id` bigint DEFAULT NULL COMMENT '用户 ID，登录名不存在时为空',
  `login_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '尝试登录的登录名',
  `login_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PASSWORD' COMMENT '登录方式：PASSWORD 账密',
  `success` tinyint NOT NULL COMMENT '是否成功：1 成功 0 失败',
  `fail_reason` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '失败原因',
  `ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '客户端 IP',
  `user_agent` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'User-Agent',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发生时间',
  PRIMARY KEY (`id`),
  KEY `idx_login_log_user` (`user_id`,`created_at`),
  KEY `idx_login_log_name` (`login_name`,`created_at`),
  KEY `idx_login_log_time` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录日志';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_login_log`
--

LOCK TABLES `sys_login_log` WRITE;
/*!40000 ALTER TABLE `sys_login_log` DISABLE KEYS */;
INSERT INTO `sys_login_log` VALUES (2108573356763459586,2108573356310474754,'audit_async_user','PASSWORD',1,NULL,'127.0.0.1',NULL,'2026-10-09 23:00:38'),(2108573818434703361,2108573817851695105,'audit_async_user','PASSWORD',1,NULL,'127.0.0.1',NULL,'2026-10-09 23:02:28'),(2108909966344437762,204,'stu_wang','PASSWORD',1,NULL,'0:0:0:0:0:0:0:1','curl/8.17.0','2026-10-10 21:18:12'),(2108915350241067009,2108915349918105601,'audit_async_user','PASSWORD',1,NULL,'127.0.0.1',NULL,'2026-10-10 21:39:36'),(2109593706787774465,202,'dept_chen','PASSWORD',1,NULL,'127.0.0.1','curl/8.17.0','2026-10-12 18:35:09'),(2109593890775113729,202,'dept_chen','PASSWORD',1,NULL,'127.0.0.1','curl/8.17.0','2026-10-12 18:35:53'),(2109593892012433409,204,'stu_wang','PASSWORD',1,NULL,'127.0.0.1','curl/8.17.0','2026-10-12 18:35:53'),(2109593931174649857,202,'dept_chen','PASSWORD',1,NULL,'127.0.0.1','curl/8.17.0','2026-10-12 18:36:02'),(2109594068936564738,204,'stu_wang','PASSWORD',1,NULL,'127.0.0.1','curl/8.17.0','2026-10-12 18:36:35'),(2109629818730295298,2109629818411528194,'audit_async_user','PASSWORD',1,NULL,'127.0.0.1',NULL,'2026-10-12 20:58:38'),(2109636898245652482,2109636897008332801,'audit_async_user','PASSWORD',1,NULL,'127.0.0.1',NULL,'2026-10-12 21:26:46'),(2109899060096540673,NULL,'\' OR \'1\'=\'1','PASSWORD',0,'登录名或密码错误','127.0.0.1','curl/8.17.0','2026-10-13 14:48:31'),(2109899060763435009,NULL,'no_such_user','PASSWORD',0,'登录名或密码错误','127.0.0.1','curl/8.17.0','2026-10-13 14:48:31'),(2109899061354831873,204,'stu_wang','PASSWORD',0,'登录名或密码错误','127.0.0.1','curl/8.17.0','2026-10-13 14:48:31'),(2109899062080446465,204,'stu_wang','PASSWORD',1,NULL,'127.0.0.1','curl/8.17.0','2026-10-13 14:48:31'),(2109899062931890178,201,'admin','PASSWORD',1,NULL,'127.0.0.1','curl/8.17.0','2026-10-13 14:48:31'),(2109899063707836418,202,'dept_chen','PASSWORD',1,NULL,'127.0.0.1','curl/8.17.0','2026-10-13 14:48:31'),(2109899258629726209,205,'stu_zhao','PASSWORD',1,NULL,'127.0.0.1','curl/8.17.0','2026-10-13 14:49:18'),(2109899466503626753,203,'dept_liu','PASSWORD',1,NULL,'127.0.0.1','curl/8.17.0','2026-10-13 14:50:08'),(2109917202424369154,2109917202042687489,'audit_async_user','PASSWORD',1,NULL,'127.0.0.1',NULL,'2026-10-13 16:00:36'),(2109984729133314049,2109984728743243777,'audit_async_user','PASSWORD',1,NULL,'127.0.0.1',NULL,'2026-10-13 20:28:56'),(2109985243325562881,2109985243002601473,'audit_async_user','PASSWORD',1,NULL,'127.0.0.1',NULL,'2026-10-13 20:30:58'),(2109994507331002369,2109994506152402945,'audit_async_user','PASSWORD',1,NULL,'127.0.0.1',NULL,'2026-10-13 21:07:47'),(2109999028719419394,2109999028203520002,'audit_async_user','PASSWORD',1,NULL,'127.0.0.1',NULL,'2026-10-13 21:25:45'),(2110001008577675266,2110001008061775874,'audit_async_user','PASSWORD',1,NULL,'127.0.0.1',NULL,'2026-10-13 21:33:37'),(2110005323144982529,2110005322033491969,'audit_async_user','PASSWORD',1,NULL,'127.0.0.1',NULL,'2026-10-13 21:50:46'),(2110015903255826433,2110015902677012481,'audit_async_user','PASSWORD',1,NULL,'127.0.0.1',NULL,'2026-10-13 22:32:48');
/*!40000 ALTER TABLE `sys_login_log` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_org`
--

DROP TABLE IF EXISTS `sys_org`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_org` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `parent_id` bigint DEFAULT '0' COMMENT '上级组织 ID，顶层的父为 0',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '组织名称',
  `code` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '组织编码，同一父节点下唯一，用于 Excel 导入匹配',
  `org_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '组织类型：COLLEGE 学院 / DEPARTMENT 部门 / CLASS 班级 / CLUB 社团部门 / SUB_ORG 分子组织 / OTHER 其他',
  `is_sub_org` tinyint NOT NULL DEFAULT '0' COMMENT '是否分子组织：1 是 0 否。排班与绩效按此维度隔离',
  `manager_id` bigint DEFAULT NULL COMMENT '该组织的部门管理员人员 ID',
  `leader_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '负责人姓名（仅展示用，可无账号）',
  `sort_no` int NOT NULL DEFAULT '0' COMMENT '同级排序号',
  `path` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '祖先路径，形如 /0/1/12/，用于子树查询',
  `level` int NOT NULL DEFAULT '1' COMMENT '层级，顶层为 1',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 启用 0 停用',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0 未删 1 已删',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人 sys_user.id',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人 sys_user.id',
  PRIMARY KEY (`id`),
  KEY `idx_org_parent` (`parent_id`,`deleted`),
  KEY `idx_org_path` (`path`(191)),
  KEY `idx_org_type` (`org_type`,`is_sub_org`,`deleted`),
  KEY `idx_org_manager` (`manager_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='组织架构（树形，含分子组织）';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_org`
--

LOCK TABLES `sys_org` WRITE;
/*!40000 ALTER TABLE `sys_org` DISABLE KEYS */;
INSERT INTO `sys_org` VALUES (1,0,'大学生科创实践中心','CENTER','OTHER',0,NULL,NULL,1,'/0/1/',1,1,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL),(2,1,'计算机学院','CS','DEPARTMENT',0,NULL,NULL,1,'/0/1/2/',2,1,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL),(3,1,'电子信息学院','EE','DEPARTMENT',0,NULL,NULL,2,'/0/1/3/',2,1,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL),(4,2,'智能软件工作室','AI_LAB','SUB_ORG',1,NULL,NULL,1,'/0/1/2/4/',3,1,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL),(5,3,'嵌入式创客空间','EMBED_LAB','SUB_ORG',1,NULL,NULL,1,'/0/1/3/5/',3,1,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL);
/*!40000 ALTER TABLE `sys_org` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `sys_user`
--

DROP TABLE IF EXISTS `sys_user`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `sys_user` (
  `id` bigint NOT NULL COMMENT '主键（雪花 ID）',
  `member_id` bigint DEFAULT NULL COMMENT '关联人员档案 ID；纯管理账号可为空',
  `login_name` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登录名。默认 = 学工号，可修改',
  `password` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码，BCrypt 加密',
  `role` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色：SUPER_ADMIN / DEPT_ADMIN / STUDENT',
  `org_id` bigint DEFAULT NULL COMMENT '所属组织 ID，部门管理员的数据范围根节点',
  `real_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '姓名冗余，避免每次展示都关联 member',
  `default_password` tinyint NOT NULL DEFAULT '1' COMMENT '是否仍为初始密码：1 是（前端弹改密提示）0 否',
  `status` tinyint NOT NULL DEFAULT '1' COMMENT '状态：1 启用 0 停用',
  `last_login_at` datetime DEFAULT NULL COMMENT '最后登录时间',
  `last_login_ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '最后登录 IP',
  `pwd_updated_at` datetime DEFAULT NULL COMMENT '密码最后修改时间',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除：0 未删 1 已删',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_login_name` (`login_name`,`deleted`),
  KEY `idx_user_member` (`member_id`),
  KEY `idx_user_role_org` (`role`,`org_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='登录账号';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `sys_user`
--

LOCK TABLES `sys_user` WRITE;
/*!40000 ALTER TABLE `sys_user` DISABLE KEYS */;
INSERT INTO `sys_user` VALUES (201,NULL,'admin','$2a$10$1CyCl3pemy9eNuDvX..fieH9NB9DSPegS5oJUrBccHLu0i/c30IBa','SUPER_ADMIN',NULL,'系统管理员',1,1,'2026-10-13 14:48:31','127.0.0.1',NULL,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL),(202,101,'dept_chen','$2a$10$qBft5NRU6teDwhlbdQr3z.TLEasgrOLUmnDNXeHByG5IO/I4ZmvlO','DEPT_ADMIN',2,'陈志远',0,1,'2026-10-13 14:48:31','127.0.0.1',NULL,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL),(203,102,'dept_liu','$2a$10$qBft5NRU6teDwhlbdQr3z.TLEasgrOLUmnDNXeHByG5IO/I4ZmvlO','DEPT_ADMIN',3,'刘慧',0,1,'2026-10-13 14:50:08','127.0.0.1',NULL,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL),(204,103,'stu_wang','$2a$10$fKAcAgbQ.hboh5iVxtGS2O8Rh7IGIAyMAM5oK5tnzPTPVLSmi8IiW','STUDENT',2,'王小明',0,1,'2026-10-13 14:49:18','8.8.8.8',NULL,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL),(205,104,'stu_zhao','$2a$10$fKAcAgbQ.hboh5iVxtGS2O8Rh7IGIAyMAM5oK5tnzPTPVLSmi8IiW','STUDENT',3,'赵晓亮',0,1,'2026-10-13 14:49:18','127.0.0.1',NULL,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL);
/*!40000 ALTER TABLE `sys_user` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `team`
--

DROP TABLE IF EXISTS `team`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `team` (
  `id` bigint NOT NULL COMMENT '主键',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '队名，同比赛下唯一',
  `project_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '项目名',
  `contest_id` bigint NOT NULL COMMENT '所属比赛 ID',
  `track_id` bigint DEFAULT NULL COMMENT '所属赛道 ID',
  `semester_id` bigint NOT NULL COMMENT '所属学期 ID',
  `org_id` bigint NOT NULL COMMENT '所属组织（部门管理员数据范围依据）',
  `leader_member_id` bigint DEFAULT NULL COMMENT '队长人员 ID，冗余便于快速查询与唯一性校验',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 正常 / DISSOLVED 已解散',
  `material_status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NOT_STARTED' COMMENT '材料状态：NOT_STARTED 未开始 / PENDING 材料待补 / SUBMITTED 材料已交',
  `material_complete_rate` decimal(5,2) NOT NULL DEFAULT '0.00' COMMENT '材料齐全率百分数，如 66.67',
  `dissolved_at` datetime DEFAULT NULL COMMENT '解散时间',
  `dissolve_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '解散原因',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_name_contest` (`contest_id`,`name`,`deleted`),
  KEY `idx_team_contest` (`contest_id`,`status`,`deleted`),
  KEY `idx_team_semester` (`semester_id`,`deleted`),
  KEY `idx_team_org` (`org_id`,`deleted`),
  KEY `idx_team_leader` (`leader_member_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赛队';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `team`
--

LOCK TABLES `team` WRITE;
/*!40000 ALTER TABLE `team` DISABLE KEYS */;
INSERT INTO `team` VALUES (501,'代码先锋队',NULL,402,NULL,301,2,NULL,'ACTIVE','SUBMITTED',100.00,NULL,NULL,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL),(502,'芯火电子队',NULL,402,NULL,301,3,NULL,'ACTIVE','NOT_STARTED',0.00,NULL,NULL,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL);
/*!40000 ALTER TABLE `team` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `team_advisor`
--

DROP TABLE IF EXISTS `team_advisor`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `team_advisor` (
  `id` bigint NOT NULL COMMENT '主键',
  `team_id` bigint NOT NULL COMMENT '赛队 ID',
  `member_id` bigint NOT NULL COMMENT '指导教师人员档案 ID（member_type=TEACHER）',
  `advisor_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '教师姓名冗余',
  `title` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '职称，如 教授 / 副教授',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 在任 / REMOVED 已移除',
  `removed_at` datetime DEFAULT NULL COMMENT '移除时间',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_advisor` (`team_id`,`member_id`,`deleted`),
  KEY `idx_team_advisor_team` (`team_id`,`status`,`deleted`),
  KEY `idx_team_advisor_member` (`member_id`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赛队指导教师';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `team_advisor`
--

LOCK TABLES `team_advisor` WRITE;
/*!40000 ALTER TABLE `team_advisor` DISABLE KEYS */;
/*!40000 ALTER TABLE `team_advisor` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `team_member`
--

DROP TABLE IF EXISTS `team_member`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `team_member` (
  `id` bigint NOT NULL COMMENT '主键',
  `team_id` bigint NOT NULL COMMENT '赛队 ID',
  `member_id` bigint NOT NULL COMMENT '人员档案 ID',
  `member_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '人员姓名冗余',
  `member_org_id` bigint DEFAULT NULL COMMENT '人员所属组织冗余',
  `is_leader` tinyint NOT NULL DEFAULT '0' COMMENT '是否队长：1 是 0 否',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNCONFIRMED' COMMENT '状态：UNCONFIRMED 未确认(橙) / CONFIRMED 已确认(绿) / REJECTED 已拒绝(红) / EXITED 已退出 / REMOVED 已被移除',
  `joined_at` datetime DEFAULT NULL COMMENT '加入时间（确认时写入）',
  `left_at` datetime DEFAULT NULL COMMENT '离开时间',
  `left_reason` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '离开原因',
  `join_snapshot` json DEFAULT NULL COMMENT '加入时的人数与规则快照',
  `remark` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_member` (`team_id`,`member_id`,`deleted`),
  KEY `idx_team_member_team` (`team_id`,`status`,`deleted`),
  KEY `idx_team_member_member` (`member_id`,`status`,`deleted`),
  KEY `idx_team_member_leader` (`team_id`,`is_leader`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赛队成员';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `team_member`
--

LOCK TABLES `team_member` WRITE;
/*!40000 ALTER TABLE `team_member` DISABLE KEYS */;
INSERT INTO `team_member` VALUES (601,501,103,'王小明',NULL,1,'CONFIRMED',NULL,NULL,NULL,NULL,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL),(602,501,104,'赵晓亮',NULL,0,'UNCONFIRMED',NULL,NULL,NULL,NULL,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL),(603,502,105,'孙小雨',NULL,1,'UNCONFIRMED',NULL,NULL,NULL,NULL,NULL,0,'2026-10-09 15:00:19',NULL,'2026-10-09 15:00:19',NULL);
/*!40000 ALTER TABLE `team_member` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `team_project_ref`
--

DROP TABLE IF EXISTS `team_project_ref`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `team_project_ref` (
  `id` bigint NOT NULL COMMENT '主键',
  `team_id` bigint NOT NULL COMMENT '赛队 ID',
  `project_id` bigint NOT NULL COMMENT '项目 ID',
  `contest_id` bigint NOT NULL COMMENT '比赛 ID（冗余）',
  `track_id` bigint DEFAULT NULL COMMENT '挂载时选择的赛道 ID',
  `semester_id` bigint NOT NULL COMMENT '学期 ID（校验同项目同学期）',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态：ACTIVE 挂载中 / RELEASED 已解绑',
  `attached_by` bigint NOT NULL COMMENT '挂载操作人人员 ID（队长）',
  `attached_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '挂载时间',
  `released_at` datetime DEFAULT NULL COMMENT '解绑时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_team_project` (`team_id`,`project_id`,`track_id`,`deleted`),
  KEY `idx_team_project_team` (`team_id`,`status`,`deleted`),
  KEY `idx_team_project_project` (`project_id`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='赛队挂载项目';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `team_project_ref`
--

LOCK TABLES `team_project_ref` WRITE;
/*!40000 ALTER TABLE `team_project_ref` DISABLE KEYS */;
/*!40000 ALTER TABLE `team_project_ref` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tech_collect`
--

DROP TABLE IF EXISTS `tech_collect`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tech_collect` (
  `id` bigint NOT NULL COMMENT '主键',
  `post_id` bigint NOT NULL COMMENT '内容 ID',
  `member_id` bigint NOT NULL COMMENT '收藏人人员 ID',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '收藏时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tech_collect` (`post_id`,`member_id`),
  KEY `idx_tech_collect_member` (`member_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技术交流收藏';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tech_collect`
--

LOCK TABLES `tech_collect` WRITE;
/*!40000 ALTER TABLE `tech_collect` DISABLE KEYS */;
/*!40000 ALTER TABLE `tech_collect` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tech_post`
--

DROP TABLE IF EXISTS `tech_post`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tech_post` (
  `id` bigint NOT NULL COMMENT '主键',
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题',
  `category` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分类，对应字典 TECH_CATEGORY',
  `content_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ARTICLE' COMMENT '内容形态：VIDEO 视频 / ARTICLE 文章 / TUTORIAL 教程链接',
  `summary` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '摘要',
  `content` text COLLATE utf8mb4_unicode_ci COMMENT '正文内容（文章类）',
  `cover_file_id` bigint DEFAULT NULL COMMENT '封面图文件 ID',
  `source_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原始链接（视频/教程/外部文章）',
  `source_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '来源名称，如「B站」「公众号XXX」',
  `source_type` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL 管理员录入 / RSS 外部订阅 / AI_GENERATED AI 生成 / CRAWLER 抓取',
  `dedup_key` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '去重键：source_url 的哈希（手工录入为空）',
  `tags` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '标签，逗号分隔，用于订阅推送与筛选',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待审核 / PUBLISHED 已发布 / WITHDRAWN 已撤回',
  `top_flag` tinyint NOT NULL DEFAULT '0' COMMENT '是否置顶：1 是 0 否',
  `view_count` int NOT NULL DEFAULT '0' COMMENT '浏览数',
  `collect_count` int NOT NULL DEFAULT '0' COMMENT '收藏数',
  `published_at` datetime DEFAULT NULL COMMENT '发布时间',
  `ai_model` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'AI 生成时使用的模型名',
  `org_id` bigint DEFAULT NULL COMMENT '归属组织',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tech_post_dedup` (`dedup_key`,`deleted`),
  KEY `idx_tech_post_status` (`status`,`top_flag`,`published_at`,`deleted`),
  KEY `idx_tech_post_category` (`category`,`status`,`deleted`),
  KEY `idx_tech_post_type` (`content_type`,`status`,`deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技术交流内容';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tech_post`
--

LOCK TABLES `tech_post` WRITE;
/*!40000 ALTER TABLE `tech_post` DISABLE KEYS */;
/*!40000 ALTER TABLE `tech_post` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `tech_subscription`
--

DROP TABLE IF EXISTS `tech_subscription`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `tech_subscription` (
  `id` bigint NOT NULL COMMENT '主键',
  `member_id` bigint NOT NULL COMMENT '订阅人人员 ID',
  `category` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '订阅分类',
  `tag` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '订阅标签，为空表示订阅整个分类',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用：1 启用 0 关闭',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_tech_subscription` (`member_id`,`category`,`tag`),
  KEY `idx_tech_subscription_member` (`member_id`,`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='技术交流订阅';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `tech_subscription`
--

LOCK TABLES `tech_subscription` WRITE;
/*!40000 ALTER TABLE `tech_subscription` DISABLE KEYS */;
/*!40000 ALTER TABLE `tech_subscription` ENABLE KEYS */;
UNLOCK TABLES;

--
-- Table structure for table `timetable_import`
--

DROP TABLE IF EXISTS `timetable_import`;
/*!40101 SET @saved_cs_client     = @@character_set_client */;
/*!50503 SET character_set_client = utf8mb4 */;
CREATE TABLE `timetable_import` (
  `id` bigint NOT NULL COMMENT '主键',
  `semester_id` bigint DEFAULT NULL COMMENT '归一化后关联的学期 ID，归一化失败时为空待人工指定',
  `semester_raw_name` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '导入文件中的原始学期名称',
  `file_id` bigint DEFAULT NULL COMMENT '原始课表文件 ID',
  `file_name` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '原始文件名',
  `org_id` bigint DEFAULT NULL COMMENT '上传操作人所属组织',
  `total_rows` int NOT NULL DEFAULT '0' COMMENT '总行数',
  `success_rows` int NOT NULL DEFAULT '0' COMMENT '成功行数',
  `failed_rows` int NOT NULL DEFAULT '0' COMMENT '失败行数',
  `error_detail` text COLLATE utf8mb4_unicode_ci COMMENT '每行错误原因 JSON',
  `status` varchar(32) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING 待归一化 / MAPPING 待确认映射 / COMPLETED 已完成 / FAILED 失败',
  `imported_by` bigint DEFAULT NULL COMMENT '导入人 sys_user.id',
  `imported_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '导入时间',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `created_by` bigint DEFAULT NULL COMMENT '创建人',
  `updated_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `updated_by` bigint DEFAULT NULL COMMENT '更新人',
  PRIMARY KEY (`id`),
  KEY `idx_tt_import_semester` (`semester_id`,`status`,`deleted`),
  KEY `idx_tt_import_status` (`status`,`imported_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='课表导入批次';
/*!40101 SET character_set_client = @saved_cs_client */;

--
-- Dumping data for table `timetable_import`
--

LOCK TABLES `timetable_import` WRITE;
/*!40000 ALTER TABLE `timetable_import` DISABLE KEYS */;
/*!40000 ALTER TABLE `timetable_import` ENABLE KEYS */;
UNLOCK TABLES;
/*!40103 SET TIME_ZONE=@OLD_TIME_ZONE */;

/*!40101 SET SQL_MODE=@OLD_SQL_MODE */;
/*!40014 SET FOREIGN_KEY_CHECKS=@OLD_FOREIGN_KEY_CHECKS */;
/*!40014 SET UNIQUE_CHECKS=@OLD_UNIQUE_CHECKS */;
/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
/*!40111 SET SQL_NOTES=@OLD_SQL_NOTES */;

-- Dump completed on 2026-10-14  6:38:08
