CREATE TABLE `access_log_info`
(
    `id`            bigint(20) NOT NULL AUTO_INCREMENT COMMENT '日志ID',
    `tenant_id`     varchar(200) CHARACTER SET utf8mb4 DEFAULT NULL COMMENT '租户ID',
    `operator_id`   varchar(200) CHARACTER SET utf8mb4 DEFAULT NULL COMMENT '操作人ID',
    `operator`      varchar(200) CHARACTER SET utf8mb4 DEFAULT NULL COMMENT '操作人',
    `ip`            varchar(45) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '操作人IP',
    `operate_time`  datetime(3)                        DEFAULT NULL COMMENT '操作时间',
    `elapsed_time`  bigint(20)                         DEFAULT NULL COMMENT '耗时（单位：毫秒）',
    `title`         varchar(200) CHARACTER SET utf8mb4 DEFAULT NULL COMMENT '日志标题',
    `method`        varchar(10) CHARACTER SET utf8mb4  DEFAULT NULL COMMENT '请求方法（POST/GET/DELETE/PUT）',
    `url`           varchar(500) CHARACTER SET utf8mb4 DEFAULT NULL COMMENT '访问地址',
    `request_body`  longtext CHARACTER SET utf8mb4     DEFAULT NULL COMMENT '请求消息体',
    `response_body` longtext CHARACTER SET utf8mb4     DEFAULT NULL COMMENT '应答消息体',
    PRIMARY KEY (`id`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET utf8mb4;
