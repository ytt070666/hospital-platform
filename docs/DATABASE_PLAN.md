# 数据库与数据治理规划

## 1. 原则

MySQL 8 是一期事务主库；Redis 不作为患者、预约、订单和报告的最终事实源。所有业务表默认包含 `id`、`created_at`、`updated_at`、`created_by`、`updated_by`、`deleted`、`version`；金额使用 `DECIMAL(18,2)`，时间统一以 UTC 保存并在界面按医院时区展示，枚举字段由受控字典定义。逻辑删除不适用于必须依法留存的审计与财务流水。

生产库按领域 schema 或表前缀管理迁移；仅迁移工具拥有 DDL 权限。字段、索引、约束与数据字典都必须随 migration 版本化；禁止手工改生产表。

## 2. 实体分组与关系

| 分组 | 核心表 |
|---|---|
| 身份与组织 | `user`、`role`、`permission`、`user_role`、`role_permission`、`organization`、`department`、`menu`、`api_permission`、`data_scope` |
| 患者与人员 | `patient`、`patient_member`、`patient_identity`、`third_party_account`、`doctor`、`practitioner_assignment`、`electronic_health_card` |
| 资源预约 | `clinic`、`schedule`、`schedule_slot`、`schedule_template`、`appointment`、`appointment_waitlist`、`registration` |
| 费用支付 | `order`、`order_item`、`payment`、`payment_transaction`、`refund`、`reconciliation_record` |
| 医疗服务视图 | `visit`、`inspection`、`inspection_result`、`laboratory_report`、`laboratory_result_item`、`medical_report`、`report_file`、`prescription`、`prescription_item`、`hospitalization` |
| 内容与消息 | `article`、`banner`、`content_category`、`message_template`、`message`、`message_delivery` |
| 平台治理 | `file`、`system_config`、`operation_log`、`login_log`、`audit_log`、`integration_event`、`outbox_event` |

`patient` 与 `patient_member` 分开，后者保存就诊人关系；`user` 是认证主体而非患者/医生/员工详情表。行政组织使用 `sys_organization`、`sys_department`，临床科室主数据使用独立的 `department`，二者通过映射或主数据编码关联，避免把行政组织与医学业务结构混为一张表。`schedule` 属于医生/临床科室/日期，`schedule_slot` 是可售时段；`appointment` 以 slot、就诊人和业务规则关联。支付订单与预约通过业务关联号连接，不直接让支付表承载预约状态。

## 3. 完整性与并发约束

- `user.username/phone`、外部账户 `(provider, external_id)`、支付渠道交易号必须唯一。
- `schedule_slot` 建立 `(schedule_id, start_at)` 唯一约束，并有 `available_count >= 0` 校验（应用层与数据库条件更新双重保证）。
- 预约建立按有效状态约束的防重策略；MySQL 无部分唯一索引时，通过预约幂等键与事务内查询/锁定实现，规则须由产品明确。
- `payment_transaction.channel_transaction_no`、回调事件 ID、外部报告业务键均唯一，确保重放安全。
- 对库存、配置和同步游标使用 `version` 乐观锁；必要时使用短事务行锁，禁止长事务包裹外部调用。

## 4. 隐私、分级与生命周期

身份号码、手机号、健康卡号等在库中采用字段级加密或经批准的密钥服务保护，同时保存可检索的不可逆规范化索引/掩码字段。诊疗数据、报告内容、文件元数据按高敏感级别访问；文件本体置于私有桶，数据库只保留对象键、哈希、分类和权限上下文。备份加密、恢复演练、保留期限、归档/销毁策略由院方合规制度决定。

审计表使用追加写入、访问控制与防篡改存储策略；记录主体、对象类型/标识、字段类别、用途、结果、时间、来源和 Trace ID，严禁写入密码、令牌、完整身份证、病历正文或报告正文。
