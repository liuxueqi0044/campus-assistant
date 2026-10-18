-- M01.01 B fixture for the optional upstream member module.
--
-- The fixed sql/mysql/ruoyi-vue-pro.sql baseline contains the system and infrastructure
-- tables used by OAuth2, but deliberately omits optional business-module tables. This
-- MySQL form is kept aligned with yudao-module-member's upstream H2 test fixture and
-- MemberUserDO; it is imported only into the guarded disposable acceptance schema.
DROP TABLE IF EXISTS `member_user`;
CREATE TABLE `member_user` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '编号',
  `nickname` varchar(30) NOT NULL DEFAULT '' COMMENT '用户昵称',
  `name` varchar(30) NULL DEFAULT '' COMMENT '真实名字',
  `sex` tinyint NULL DEFAULT 0 COMMENT '用户性别',
  `birthday` datetime NULL DEFAULT NULL COMMENT '出生日期',
  `area_id` int NULL DEFAULT NULL COMMENT '所在地',
  `mark` varchar(255) NULL DEFAULT NULL COMMENT '用户备注',
  `point` int NOT NULL DEFAULT 0 COMMENT '积分',
  `avatar` varchar(512) NOT NULL DEFAULT '' COMMENT '头像',
  `status` tinyint NOT NULL COMMENT '状态',
  `mobile` varchar(11) NULL DEFAULT NULL COMMENT '手机号',
  `email` varchar(50) NULL DEFAULT NULL COMMENT '邮箱',
  `password` varchar(100) NOT NULL DEFAULT '' COMMENT '密码',
  `register_ip` varchar(32) NOT NULL COMMENT '注册 IP',
  `register_terminal` tinyint NULL DEFAULT NULL COMMENT '注册终端',
  `login_ip` varchar(50) NULL DEFAULT '' COMMENT '最后登录 IP',
  `login_date` datetime NULL DEFAULT NULL COMMENT '最后登录时间',
  `tag_ids` varchar(255) NULL DEFAULT NULL COMMENT '用户标签编号列表',
  `level_id` bigint NULL DEFAULT NULL COMMENT '等级编号',
  `experience` int NOT NULL DEFAULT 0 COMMENT '经验',
  `group_id` bigint NULL DEFAULT NULL COMMENT '用户分组编号',
  `creator` varchar(64) NULL DEFAULT '' COMMENT '创建者',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updater` varchar(64) NULL DEFAULT '' COMMENT '更新者',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted` bit(1) NOT NULL DEFAULT b'0' COMMENT '是否删除',
  `tenant_id` bigint NOT NULL DEFAULT 0 COMMENT '租户编号',
  PRIMARY KEY (`id`)
) ENGINE = InnoDB DEFAULT CHARACTER SET = utf8mb4 COLLATE = utf8mb4_unicode_ci COMMENT = '会员用户';
