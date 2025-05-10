CREATE TABLE IF NOT EXISTS `user` (
                                      `uid` VARCHAR(36) NOT NULL COMMENT '用户唯一标识',
    `name` VARCHAR(50) NOT NULL COMMENT '用户名',
    `category` VARCHAR(10) NOT NULL COMMENT '用户类别',
    `password` VARCHAR(100) NOT NULL COMMENT '用户密码',
    `create_time` DATETIME NOT NULL COMMENT '创建时间',
    PRIMARY KEY (`uid`),
    UNIQUE INDEX `name_UNIQUE` (`name` ASC)
    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';