-- detect_event 库：事件管理后台 schema（对齐《事件管理后台 API 接口文档》§3 数据模型）
-- 5 表：event_records / alert_rule / alert_handle_record / sys_notification / camera_manage
-- 说明：均可重复执行（IF NOT EXISTS + INSERT IGNORE）。
USE detect_event;

-- ----------------------------------------------------------------------
-- §3.2 event_records 事件主表（Python 推送 + 本设计预警扩展）
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `event_records` (
  `id`                  BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_num`          VARCHAR(64)  NOT NULL               COMMENT '设备编号(Python device_id)',
  `device_name`         VARCHAR(128) DEFAULT NULL           COMMENT '设备名称(查camera_manage补全)',
  `event_type`          INT          NOT NULL               COMMENT '事件大类:100人脸 200车辆 300聚集',
  `snap_time`           DATETIME(3)  NOT NULL               COMMENT '抓拍时间(毫秒)',
  `snap_url`            VARCHAR(512) DEFAULT NULL           COMMENT '主图MinIO URL(由snapImage base64转存)',
  `source_data`         JSON         NOT NULL               COMMENT '检测元数据JSON(不含图片)',
  `status`              TINYINT      NOT NULL DEFAULT 0     COMMENT '预警推送状态:0未推送',
  `name`                VARCHAR(64)  DEFAULT NULL           COMMENT '姓名(人脸库比对)',
  `cardno`              VARCHAR(32)  DEFAULT NULL           COMMENT '身份证号',
  `lib_name`            VARCHAR(128) DEFAULT NULL           COMMENT '车辆库/人脸库名称',
  `similarity`          INT          DEFAULT NULL           COMMENT '相似度0~100',
  `identify_face_url`   VARCHAR(512) DEFAULT NULL           COMMENT '库底图URL',
  `visible_light_url`   VARCHAR(512) DEFAULT NULL           COMMENT '热成像可见光图URL',
  `plate_num`           VARCHAR(32)  DEFAULT NULL           COMMENT '车牌号',
  `vehicle_type`        INT          DEFAULT NULL           COMMENT '特殊车辆类别code(VehicleTypeEnum)',
  `vehicle_normal_type` VARCHAR(32)  DEFAULT NULL           COMMENT '车辆类别枚举名(VehicleNormalTypeEnum)',
  `vehicle_logo`        VARCHAR(64)  DEFAULT NULL           COMMENT '车辆品牌(VLM补全)',
  `vehicle_sub_logo`    VARCHAR(64)  DEFAULT NULL           COMMENT '车辆子品牌(VLM补全)',
  `vehicle_color`       VARCHAR(32)  DEFAULT NULL           COMMENT '车身颜色(VLM补全)',
  `vehicle_model`       VARCHAR(64)  DEFAULT NULL           COMMENT '车辆年款(VLM补全)',
  `height_permitted`    DECIMAL(5,2) DEFAULT NULL           COMMENT '限高(米)',
  `crowd_num`           INT          DEFAULT NULL           COMMENT '聚集人数',
  -- 本设计扩展(预警处理)
  `handle_status`       TINYINT      NOT NULL DEFAULT 0     COMMENT '处理状态:0未处理 1处理中 2已处理 3误报',
  `priority`            TINYINT      NOT NULL DEFAULT 0     COMMENT '优先级:0普通 1重要 2紧急',
  `hit_rule_id`         BIGINT       DEFAULT NULL           COMMENT '命中的布控规则ID',
  `create_time`         DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`         DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`            CHAR(1)      NOT NULL DEFAULT '0'   COMMENT '逻辑删除:0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_device_num`    (`device_num`),
  KEY `idx_event_type`    (`event_type`),
  KEY `idx_snap_time`     (`snap_time`),
  KEY `idx_handle_status` (`handle_status`),
  KEY `idx_priority`      (`priority`),
  KEY `idx_plate_num`     (`plate_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='事件记录表(对齐Java EventRecords)';

-- ----------------------------------------------------------------------
-- §3.3 alert_rule 布控预警规则表
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `alert_rule` (
  `id`             BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `rule_name`      VARCHAR(128) NOT NULL               COMMENT '规则名称',
  `rule_type`      VARCHAR(32)  NOT NULL               COMMENT '规则类型:PLATE_BLACKLIST/VEHICLE_TYPE/CROWD_THRESHOLD/DEVICE_TIME',
  `event_type`     INT          DEFAULT NULL           COMMENT '适用事件大类,NULL=全部',
  `match_config`   JSON         NOT NULL               COMMENT '匹配配置,如{"crowdNum":">10"}',
  `priority`       TINYINT      NOT NULL DEFAULT 1     COMMENT '命中后优先级:0普通 1重要 2紧急',
  `notify_enabled` TINYINT      NOT NULL DEFAULT 1     COMMENT '是否生成站内通知:0否 1是',
  `device_scope`   JSON         DEFAULT NULL           COMMENT '生效设备范围,NULL=全部,如["dev01","dev02"]',
  `time_scope`     JSON         DEFAULT NULL           COMMENT '生效时段,NULL=不限,如{"start":"22:00","end":"06:00"}',
  `enabled`        TINYINT      NOT NULL DEFAULT 1     COMMENT '启用状态:0停用 1启用',
  `remark`         VARCHAR(255) DEFAULT NULL           COMMENT '备注',
  `create_by`      VARCHAR(64)  DEFAULT NULL           COMMENT '创建人',
  `create_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time`    DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`       CHAR(1)      NOT NULL DEFAULT '0'   COMMENT '逻辑删除:0正常 1删除',
  PRIMARY KEY (`id`),
  KEY `idx_rule_type` (`rule_type`),
  KEY `idx_enabled`   (`enabled`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='布控预警规则表';

-- ----------------------------------------------------------------------
-- §3.4 alert_handle_record 预警处理记录表（无 del_flag/create_time）
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `alert_handle_record` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `event_id`      BIGINT       NOT NULL               COMMENT '关联事件ID',
  `from_status`   TINYINT      DEFAULT NULL           COMMENT '原处理状态',
  `to_status`     TINYINT      NOT NULL               COMMENT '新处理状态',
  `handler_id`    BIGINT       DEFAULT NULL           COMMENT '处理人ID',
  `handler_name`  VARCHAR(64)  DEFAULT NULL           COMMENT '处理人名称',
  `handle_remark` VARCHAR(500) DEFAULT NULL           COMMENT '处理备注',
  `handle_time`   DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '处理时间',
  PRIMARY KEY (`id`),
  KEY `idx_event_id` (`event_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='预警处理记录表';

-- ----------------------------------------------------------------------
-- §3.5 sys_notification 站内通知表（无 del_flag/update_time）
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `sys_notification` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `user_id`     BIGINT       NOT NULL               COMMENT '接收人ID',
  `title`       VARCHAR(128) NOT NULL               COMMENT '标题',
  `content`     VARCHAR(500) DEFAULT NULL           COMMENT '内容',
  `type`        VARCHAR(16)  NOT NULL DEFAULT 'alert' COMMENT '类型:alert预警 system系统',
  `biz_id`      BIGINT       DEFAULT NULL           COMMENT '关联业务ID(如event_id)',
  `priority`    TINYINT      NOT NULL DEFAULT 0     COMMENT '优先级:0普通 1重要 2紧急',
  `read_flag`   TINYINT      NOT NULL DEFAULT 0     COMMENT '已读:0未读 1已读',
  `read_time`   DATETIME     DEFAULT NULL           COMMENT '已读时间',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_read` (`user_id`, `read_flag`),
  KEY `idx_biz_id`    (`biz_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='站内通知表';

-- ----------------------------------------------------------------------
-- camera_manage 摄像头设备（最小版）：文档 §3.2 引用但未给结构，
-- 按平台约定建最小字段（device_num 唯一）+ 继承 BaseEntity 三件套，供 receive 补全 device_name。
-- ----------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `camera_manage` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键ID',
  `device_num`  VARCHAR(64)  NOT NULL               COMMENT '设备编号(对齐Python device_id)',
  `device_name` VARCHAR(128) DEFAULT NULL           COMMENT '设备名称',
  `location`    VARCHAR(255) DEFAULT NULL           COMMENT '安装位置',
  `status`      TINYINT      NOT NULL DEFAULT 1     COMMENT '状态:1在线 0离线',
  `create_time` DATETIME     DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME     DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `del_flag`    CHAR(1)      NOT NULL DEFAULT '0'   COMMENT '逻辑删除:0正常 1删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_device_num` (`device_num`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='摄像头设备(最小版)';

-- camera_manage 样例设备（幂等）；device_num 与接口文档示例(dev01)一致，联调时可对齐 Python 实际 device_id。
INSERT IGNORE INTO `camera_manage` (`device_num`, `device_name`, `location`, `status`, `create_time`, `update_time`, `del_flag`) VALUES
  ('dev01', '南河湫水闸',   '南河湫水闸东岸',   1, NOW(), NOW(), '0'),
  ('dev02', '城北出城卡口', '城北出城卡口',     1, NOW(), NOW(), '0'),
  ('dev03', '港区集装箱堆场', '港区集装箱堆场', 1, NOW(), NOW(), '0');

