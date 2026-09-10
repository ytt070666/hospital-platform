# 数据库设计

迁移位于 `services/hospital-backend/hospital-bootstrap/src/main/resources/db/migration/`，由 Flyway 自动执行；Hibernate 不参与 DDL。所有业务主键使用 MySQL `BIGINT AUTO_INCREMENT`，API 中按字符串传递 ID，避免 JavaScript 精度丢失。

`sys_department` 是行政部门，`department` 是临床科室主数据；二者不能合并。临床科室通过 `administrative_department_id` 映射到既有行政数据权限范围。

## STEP 3 医院主数据

`V5__create_hospital_master_data.sql` 新增医院、院区、楼宇、楼层、门诊科室、出诊类型、医生职称、医学专业及医生多对多关系表；并以增量字段扩展既有临床 `department` 与 `doctor`。`V6__seed_master_data_permissions.sql` 只补充主数据权限与菜单，不触碰 STEP 2 的历史初始化。

所有主数据表使用 `status`、`published`、`deleted`、`version` 与排序字段。更新用 `version` 乐观锁；唯一编码、医生主科室生成列唯一键和外键约束由数据库兜底。患者端查询固定限定 `deleted=0 AND status=1 AND published=1`。

## STEP 4.1 排班与号源

`V8__create_scheduling_domain.sql` 新增医院级时段定义、排班模板及规则、实际排班、号段、号源与排班变更表；实际排班保存原始医生、当前出诊医生、发布/停诊状态、号源总数和已占用数。模板代码、时段代码和排班关联约束用于防止重复数据，排班时间冲突同时在服务层校验。`V9`、`V10` 以增量方式补齐排班权限及替诊权限，不修改既有阶段的历史迁移。

号源字段以 `doctor_schedule.total_quota`、`reserved_quota`、`booked_quota` 为唯一权威，约束关系为 `available = total - reserved - booked`。Reserve、Release、Commit 通过带余额条件的参数化 MySQL 更新维护该关系；`schedule_slot` 对号段采用相同字段与防超卖规则。Redis 不保存权威状态，重启或缓存被删除后由 MySQL 通过排班缓存重建入口恢复。

## STEP 5.1 患者身份

`V11` 扩展既有 `patient`、`patient_member`，不创建重复患者表。手机号、证件号及健康卡相关字段保存 AES-GCM 密文与 HMAC 精确检索值；`patient_member` 以生成列唯一键约束有效“本人”，并对有效实名证件建立重复保护。`patient_refresh_token` 与后台刷新令牌分表；`patient_hospital_card`、`external_patient_reference` 是 HIS/院内卡适配预留。`V12` 增加患者管理权限和菜单。

## STEP 5.2 预约核心

`V13__create_appointment_core.sql` 新增 `appointment` 与 `appointment_status_history`。预约号为不可推断的随机 UUID 编码；`(patient_id,idempotency_key)` 保证请求幂等，`active_booking_key` 在 `HOLDING`、`BOOKED` 时取 `member_id:schedule_id` 并由唯一键阻止重复有效预约，在取消、过期时置空以允许重新预约。过期扫描使用 `(status,hold_expires_at)` 索引，患者列表与排班查询分别使用复合索引。预约不物理删除，状态历史记录每次合法迁移及 Trace ID。

`V16__add_appointment_integrity_constraints.sql` 为 `appointment.patient_id`、`member_id`、`schedule_id`、可空 `slot_id` 和 `appointment_status_history.appointment_id` 增加外键。号段结构调整在应用层先拒绝有预约历史的排班，避免外键删除冲突和历史语义断裂；停诊只改变预约状态与库存，不删除预约或历史。

## STEP 6.1 支付核心（V17 / V18）

新增 registration_fee_rule、registration_order、payment_order、payment_transaction、payment_callback_event、payment_audit_event、test_payment_provider_order；clinic_type 新增 payment_required，默认 false。
金额为 BIGINT 分 / CNY；规则有效期与版本、订单价格快照、截止时间独立保存。
唯一约束覆盖预约一对一、订单编号、支付编号、患者幂等 key、提供方交易号、回调事件/nonce。
生成列唯一键保证每个收费订单最多一个在途支付和一个成功支付。详见 PAYMENT_DESIGN.md。

## STEP 6.2 退款与对账（V19 / V20）

`refund_order` 保存退款编号、稳定业务键、支付/预约/患者关联、金额快照、原因、提供方退款号、状态与时间；退款号、业务键、患者请求键及提供方退款号均由唯一约束保护。`registration_order.refunded_amount_cent` 与 `refund_reserved_amount_cent` 在同一行锁/条件更新中维护，构成数据库层的防超退底线。`refund_callback_event` 与支付回调分表持久化处理状态，`payment_reconciliation_record` 保存只读检测得到的资金异常。V20 只增量种子退款与对账权限。
## STEP 6.4 支付回调恢复

`payment_callback_event` 与 `refund_callback_event` 保存 Provider、事件标识、关联业务单号、金额、币种、处理状态和必要结果摘要。状态包含 `RECEIVED`、`PROCESSED`、`FAILED_RETRYABLE`、`REJECTED`，以 Provider 加 eventId 的唯一性确保重放只处理一次。原始通知 body、签名密钥、Authorization 和敏感个人数据不持久化。

退款订单与支付订单、收费订单、预约之间保持外键/业务关系；Payment 与 Refund 历史不做物理删除。金额采用 CNY 分的 BIGINT，支付回调和退款回调均在落库前校验订单号、金额和币种。

## STEP 7.1 门诊签到与候诊（V22 / V23）

`clinic_room` 为可维护诊室主数据，`doctor_schedule.clinic_room_id` 为增量可空关联。`visit_encounter` 的 `encounter_no` 与 `appointment_id` 均唯一；状态历史、队列、原子序列计数分别存入 `visit_encounter_status_history`、`queue_ticket`、`queue_sequence_counter`。V23 增量种子诊室、签到、候诊和医生工作台权限。
# STEP 7.2 临床表

V24 新增病历容器、版本、生命体征、过敏档案、过敏记录、诊断目录、诊断记录和临床访问审计表；容器的 `encounter_id`、病历编号和版本序号均由数据库唯一约束保护。有效 PRIMARY 诊断以生成列唯一键限制，SECONDARY 不受该约束影响。

## STEP 7.3 门诊药品与药房（V26）

V26 新增药品目录、医院处方目录、药房、药房用户范围、库存与批号、处方头与项目、安全提示、审方历史、库存预占、调剂、退药和药品审计表。药品、医院目录和库存严格分离；库存批号以 `DECIMAL` 的 on-hand、reserved、available 及数据库 CHECK 约束保持非负与余额关系。处方号、退药号、医院目录、批号、处方药品项与调剂记录都有唯一约束；处方项目保存历史快照。

## STEP 7.4 门诊诊断（V28）

V28 新增临床服务目录、统一医嘱及项目、标本、检验结果与项目、报告及修订、影像检查、危急值、医生复核、诊断异常和独立审计表。医嘱号、标本号、结果号、报告号、影像号、Provider 事件和报告版本均由唯一键保护；数值结果使用 `DECIMAL`，签署文书和结果不依赖 Redis。
