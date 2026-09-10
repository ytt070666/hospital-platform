# STEP 5 APPOINTMENT SYSTEM FINAL ACCEPTANCE REPORT

## Overall

STEP 5 PASSED

本报告依据 STEP 5.1、5.2、5.3 已验收基线，以及本次 STEP 5.4 的真实 MySQL、Redis、RabbitMQ、MinIO、浏览器和最新构建复验。未进入 STEP 6；微信正式 AppID 保持外部配置，不构成 STEP 5 阻塞。

## STEP Results

| 阶段 | 结果 |
| --- | --- |
| STEP 5.1 | PASS |
| STEP 5.2 | PASS |
| STEP 5.3 | PASS |
| STEP 5.4 | PASS |

## Patient Identity

- 患者与工作人员主体隔离、患者短信测试登录、刷新/登出令牌失效、本人及家庭就诊人创建、默认就诊人切换、编辑和跨患者成员 IDOR 均为 PASS。
- 本轮真实浏览器以本地 `dev` 测试 Provider 完成患者登录、本人/儿童就诊人创建、默认项切换和编辑；登出后 `GET /api/v1/patient/me` 返回 401。
- 后台患者列表和详情仅显示服务端脱敏字段；详情读取已产生 2 条成功的 PATIENT 审计记录。

## Appointment Domain

- HOLDING、BOOKED、CANCELLED、EXPIRED 状态机、幂等重放、重复有效预约保护、确认/取消/过期竞态、状态历史和预约号唯一性均为 PASS。
- `AppointmentCoreIntegrationTest` 在真实 MySQL/Redis 上复验 1,000 位患者抢 10 个号源：10 成功、990 拒绝、10 个 HOLDING、reserved=10、available=0。
- 同一幂等键 100 并发、同一就诊人不同幂等键 100 并发、100 次确认、100 次取消、10 个过期 Worker 竞态和 100/3 号段抢号均为 PASS。

## Schedule Integration

| 项目 | 结果 |
| --- | --- |
| Stop | PASS：HOLDING/BOOKED 在停诊时转 CANCELLED，原因 `SCHEDULE_STOPPED`，库存仅释放一次 |
| Substitute | PASS：保留预约号、状态和库存；详情返回实际医生、原医生及替诊标识 |
| Unpublish Protection | PASS：存在有效预约返回 `SCHEDULE_HAS_ACTIVE_APPOINTMENTS` |
| Slot Protection | PASS：存在有效预约/历史时拒绝变更，使用受控错误码 |

停诊 120 笔（60 HOLDING、60 BOOKED）批量回归通过；状态历史恰好 120 条，重复恢复调用不重复取消或释放库存。`AppointmentInventoryReconciliationService` 只读报告 MySQL 预约事实和库存差异，不自动覆盖 MySQL。

## Cancellation Rules

| 项目 | 结果 |
| --- | --- |
| Patient | PASS：仅患者本人可取消自己的预约 |
| Deadline | PASS：由服务端时间和可配置 `appointment.cancel.deadline-minutes-before-start` 判定，超时返回 `APPOINTMENT_CANCEL_DEADLINE_PASSED` |
| Staff | PASS：合法权限与 SQL DataScope 下可协助取消，必须给出原因，复用同一库存释放核心 |

## Expiration

| 项目 | 结果 |
| --- | --- |
| Worker | PASS |
| Batch | PASS：条件 LIMIT 循环，不使用边更新边 OFFSET 的分页方式 |
| Concurrency | PASS：多 Worker 对同一过期预约最多释放一次 |
| Restart | PASS：服务端详情/过期任务以 MySQL 时间和状态为最终依据 |

## Concurrency

| 场景 | 结果 |
| --- | --- |
| 1,000 / 10 | PASS：Initial=10，Requests=1,000，Success=10，Rejected=990，Reserved=10，Booked=0，Available=0 |
| Slot 100 / 3 | PASS |
| Same Idempotency | PASS |
| Same Member | PASS |
| Confirm | PASS |
| Cancel | PASS |
| Expire | PASS |
| Race Conditions | PASS |

## Inventory

| 项目 | 结果 |
| --- | --- |
| MySQL Source of Truth | PASS |
| Redis | PASS：仅作缓存，不绕过 MySQL 条件库存更新 |
| Rebuild | PASS：Redis 丢失或错误库存可由 MySQL 重建 |
| Reconciliation | PASS：只读差异检查，不盲目自动修复 |
| Lifecycle | PASS：reserve → commit → cancel/release → reserve 不丢失或凭空增加库存 |

## Recovery

| 项目 | 结果 |
| --- | --- |
| Zero-State | PASS：Testcontainers 空 MySQL 从 V1 迁移至 V16，0 failures / 0 errors / 0 skipped |
| Backend Restart | PASS：最新 JAR 重启后 `/actuator/health` = UP |
| Docker Restart | PASS：对当前项目四项依赖执行无卷删除重启后均 healthy，已有患者/成员仍可由后台读取 |
| Redis Loss | PASS |
| Expiration Restart | PASS |

## Web

| 项目 | 结果 |
| --- | --- |
| Booking / HOLD / Countdown / Confirm / List / Detail / Cancel / Refresh / Expiration | PASS：继承 STEP 5.3 真实流程回归，且本轮后端/构建/DTO 兼容性复验通过 |
| Patient Identity E2E | PASS：真实浏览器完成测试短信登录、创建 SELF/CHILD、设默认、编辑、登出私有 API 401 |

## MiniProgram

| 项目 | 结果 |
| --- | --- |
| Booking / Appointments / Detail / Cancel / Sync | PASS：继承 STEP 5.3 已验收真实 API 流程 |
| Build | PASS：`build:mp-weixin` 完成 |

WECHAT_APPID: EXTERNAL

## Admin

| 项目 | 结果 |
| --- | --- |
| Appointment Center / Search / Detail | PASS |
| Patient Center | PASS：真实后台登录、脱敏列表、详情和同一成员数据已验证 |
| RBAC | PASS：只读预约角色不能取消；无权限操作返回 403 |
| DataScope | PASS：跨行政科室预约详情在服务端拒绝 |
| Audit | PASS：患者详情真实访问产生 PATIENT 审计；预约操作保留预约维度审计/Trace ID |
| Staff Cancel | PASS |

## Security

| 项目 | 结果 |
| --- | --- |
| IDOR | PASS：患者跨成员读、改、禁用、设默认均为 403；预约跨患者访问受拒绝 |
| Token Isolation | PASS：患者/管理端令牌主体分离，登出后私有患者 API=401 |
| Sensitive DTO | PASS：实测管理列表字段仅为 `id`、`patientNo`、脱敏姓名/手机、状态、成员数和创建时间；不含密文、HMAC 或密钥 |
| SQL Binding | PASS：查询参数采用 JDBC 参数绑定，成员姓名查询转 HMAC 后绑定 |
| Audit | PASS |
| Trace ID | PASS：公共 API 响应含 `X-Trace-Id` |
| Secret Scan | PASS：源码不存在固定测试或生产密钥字面量；运行时和测试配置均使用环境变量 |
| Logging | PASS：已扫描本轮非空后端运行日志，完整手机号、Access Token、Refresh Token、Authorization Header、SMS Code 命中均为 0 |

数据库抽查结果：手机号明文/明文 Hash 命中为 0；已存在患者同时具有加密手机号与查询 Hash；身份证密文字段与 Hash 不存在明文等价值；当前 Flyway 版本为 V16。

## Synchronization

| 项目 | 结果 |
| --- | --- |
| Appointment → Quota | PASS |
| Admin → Patient | PASS |
| Web → MiniProgram | PASS |
| Stop → Appointment | PASS |
| Substitute → Appointment | PASS |

## Automated Tests

### Backend

`mvn clean test`

- Tests run: 31
- Failures: 0
- Errors: 0
- Skipped: 0

`mvn clean package`: BUILD SUCCESS。

### Admin

- lint: PASS
- typecheck: PASS
- build: PASS

### Web

- lint: PASS
- typecheck: PASS
- build: PASS

### MiniProgram

- lint: PASS
- typecheck: PASS
- build:mp-weixin: PASS

### Docker

- config: PASS（`docker compose --project-name hospital-platform config --quiet`）
- health: PASS（MySQL、Redis、RabbitMQ、MinIO）

## E2E

本轮本机浏览器完成患者短信测试 Provider 登录、患者中心成员操作、登出私有接口拒绝，以及管理端登录、患者脱敏列表、患者详情与成员一致性。预约选排班、HOLD、Confirm、BOOKED、取消和库存恢复，以及停诊、替诊、过期、刷新恢复和后台协助取消，均由 STEP 5.3 已验收真实前端流程和本轮真实 MySQL 集成回归共同覆盖。

## Bugs Found

1. 患者身份集成测试未在其测试上下文显式开启短信测试 Provider，导致默认安全配置下 3 个用例报短信不可用。
2. `application-test.yml` 保留了固定测试密钥字面量，不符合密钥不进入源码的最终审计要求。

## Bugs Fixed

1. 将短信测试开关限定为 `PatientIdentityIntegrationTest` 的测试上下文属性，最终全量测试为 0 error。
2. 移除测试配置中的固定密钥字面量，统一改为受保护环境变量。
3. 补齐停诊批量幂等取消、替诊可追溯 DTO、取消截止规则、号段/取消发布保护、预约库存只读对账和 V16 外键完整性约束。

## Remaining Non-Blocking Technical Debt

- 管理后台主包仍有 Vite 大包警告，未影响功能、类型检查或生产构建；可在后续阶段进行按需拆包。
- 微信正式 `WECHAT_APPID` 由外部平台配置，当前为 EXTERNAL，按验收规则不阻塞 STEP 5。

## Test Data Cleanup

自动化集成测试的临时预约、排班、患者与成员均在测试清理钩子中删除。本轮浏览器患者身份冒烟数据保留在开发库中，未删除任何用户 Development 数据、数据库、Docker Volume 或迁移记录。

## Final Acceptance Matrix

Patient Identity、Patient Member、Patient Ownership、Patient IDOR、Patient Authentication、Patient Token Isolation、Schedule Selection、Slot Selection、Quota Inventory、Appointment HOLD、HOLD TTL、Appointment Confirm、Appointment Cancel、Appointment Expire、Appointment Idempotency、Duplicate Active Protection、Appointment History、Expiration Worker、Expiration Batch、Restart Expiration、Patient Cancel Deadline、Staff Cancel、Schedule Stop Handling、Schedule Substitute Handling、Schedule Unpublish Protection、Slot Disable Protection、RBAC、DataScope、Admin Audit、Sensitive DTO、1000 / 10 Anti-Oversell、100 / 3 Slot、Same Idempotency Concurrency、Same Member Concurrency、Confirm Concurrency、Cancel Concurrency、Expire Concurrency、Race Conditions、Appointment Number Uniqueness、Inventory Lifecycle、Appointment/Inventory Reconciliation、Redis Loss Recovery、Redis Wrong Value Recovery、Backend Restart、Docker Restart、Zero-State Migration、Web Booking Flow、Web Refresh Recovery、Web Expiration、Web Cancel、MiniProgram Booking Flow、MiniProgram Appointments、Admin Appointment Center、Admin DataScope、Admin Permission、Admin → Patient Sync、Web → MiniProgram Sync、Schedule Stop Cross-Client Sync、Substitute Cross-Client Sync、Backend Tests、Backend Package、Admin Build、Web Build、MiniProgram Build、Docker Health、Security Regression：PASS。

WECHAT_APPID：EXTERNAL。

## Result

STEP 5 PASSED
