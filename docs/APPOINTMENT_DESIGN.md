# 预约挂号核心设计（STEP 5.2 / 5.3）

## STEP 5.3 体验层补充

患者 Web 与小程序只把服务端预约状态、库存和截止时间视为最终真相。后台预约管理通过 RBAC、SQL DataScope 和审计日志控制工作人员查询与协助取消；取消仍复用原子库存释放规则，不引入支付、退款或就诊完成状态。

## 状态机

`HOLDING → BOOKED`、`HOLDING → CANCELLED`、`HOLDING → EXPIRED` 与 `BOOKED → CANCELLED` 是唯一合法迁移。每个迁移采用 `WHERE status = expected` 的条件更新；状态历史保存前后状态、原因、操作者类型、操作者、Trace ID 和时间。重复确认、重复取消返回已达到的稳定状态，库存不会重复移动。

## 锁号、资格与幂等

锁号仅接受当前患者名下的 ACTIVE 就诊人；可通过 `hospital.appointment.require-verified-identity` 要求实名。排班必须是已发布、未删除、未来且在 `max-days-ahead`/`min-advance-minutes` 窗口内；号段模式要求同排班下的 ACTIVE 号段，非号段模式拒绝 `slotId`。

`Idempotency-Key` 由 `(patient_id,idempotency_key)` 数据库唯一键保证。相同规范化请求指纹重放原预约；同键不同请求返回幂等冲突。有效预约使用唯一 `active_booking_key=memberId:scheduleId`，因此同一就诊人无法对同一排班创建两个 HOLDING/BOOKED 预约；取消和过期清空该键，允许按当前规则重新预约。

## 库存、一致性与恢复

MySQL 是预约及库存权威。锁号调用既有条件更新 `reserveBookable`，它在同一条 SQL 中再次验证排班/号段仍可预约；确认执行 Reserve→Booked，取消 HOLDING 释放 reserved，取消 BOOKED 释放 booked。号段模式下，公开排班余号与后台排班库存摘要由有效号段的 `total/reserved/booked` 聚合得出，不使用未参与扣减的排班行计数。库存不变量始终为 `reserved + booked ≤ total`。

HOLD 到期由批量、幂等的定时任务扫描，条件更新获胜者才释放库存。确认会在 Worker 尚未来得及运行时主动过期并拒绝确认。确认/过期、确认/取消、取消/过期竞争均由状态 CAS 决定唯一胜者，库存动作处在同一 MySQL 事务中。Redis 仅在事务提交后更新；缓存丢失或失败后通过既有按排班重建恢复，不会影响 MySQL 事实。服务重启后，未过期 HOLD 仍有效，已过期 HOLD 会由扫描任务回收。

## 隐私与边界

患者所有权来自 Token，而非请求体。预约 API 和历史不返回身份号码、手机号、密文、Hash、验证码或 Token；SQL 均为参数绑定。STEP 5.3 交付 Web、小程序预约体验和后台预约管理，不交付支付、退款、收费、病历或就诊完成流程。

## STEP 5.4 医院规则、恢复与对账

排班由 `PUBLISHED` 进入 `STOPPED` 时，系统在同一数据库事务内批量将该排班全部 `HOLDING`、`BOOKED` 预约迁移为 `CANCELLED`，历史原因固定为 `SCHEDULE_STOPPED`，并按原状态恰好释放一次 reserved 或 booked 库存。迁移采用状态条件更新；重试、并发停诊和服务恢复不会重复释放库存。

存在有效预约的已发布排班禁止取消发布；任意存在预约历史的排班禁止重新切分或替换号段。替诊保留预约号、成员、状态与库存，将 `original_doctor_id` 保留为原始出诊医生并以 `substitute_doctor_id` 作为实际出诊医生。患者响应明确提供实际医生、原医生和替诊标记；后台详情同样保留完整追溯信息。

患者仅可在配置的 `hospital.appointment.cancel.deadline-minutes-before-start` 截止时间前取消 BOOKED 预约；工作人员协助取消不受该患者自助窗口限制，但仍须 RBAC、DataScope 和必填原因。HOLD 过期扫描和详情按 MySQL 的状态条件更新恢复，Redis 丢失、错误值或服务重启均不改变 MySQL 事实。

`AppointmentInventoryReconciliationService` 只读核对预约事实与排班/号段的 reserved、booked 计数，返回差异而不自动修复；差异必须作为运行事件人工处置。`V16` 对预约、就诊人、排班、可选号段及状态历史补齐数据库外键，防止孤儿预约与历史记录。
