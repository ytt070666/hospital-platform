# STEP 6.1 PAYMENT CORE EXECUTION REPORT

验收时间：2026-09-05 23:40，Asia/Shanghai。

## Overall

STEP 6.1 FAILED

支付核心实现和当前完整测试已通过，但最终清理打包、新 JAR 启动与部署后页面验收尚未完成。
真实阻塞：PID 171016 正在运行当前项目 `services/hospital-backend/hospital-bootstrap/target/hospital-bootstrap-0.1.0-SNAPSHOT.jar`，Windows 文件锁使 `mvn clean test` 在 bootstrap clean 阶段失败。
已询问是否允许仅安全重启该项目后端，尚未收到本轮授权；未停止该进程或其他服务。

## Money & Pricing

- Money: PASS。BIGINT/long 分，CNY，范围校验，拒绝 JSON 小数截断。
- Fee Rule: PASS。规则创建、编辑、启停、引用校验、version 409、前后审计已测试。
- Priority: PASS。职称/门诊类型/科室/院区确定性精细度优先，priority 降序，id 升序。
- Effective Date: PASS。有效期包含起点、不含终点，未来/失效规则不命中。
- Snapshot: PASS。规则变更后旧订单仍保留原金额与快照。
- Zero Fee: PASS。PAID，0 个 PaymentOrder，不调用提供方。
- Required Price: PASS。必须收费但无有效规则时 PRICE_NOT_CONFIGURED，不误降为免费。

## Registration Order

Creation / Appointment Mapping / Idempotency / Status / Deadline: PASS。
确认预约后同步 ensure；已有 BOOKED 预约可通过显式 ensure 恢复。数据库 appointment_id UNIQUE。
不修改 STEP 5 预约状态机或配额算法，不批量给历史预约补收费。

## Payment Order

Create / Attempts / State Machine / Idempotency / Duplicate Protection / Timeout: PASS。
失败后新 key 可重试；旧 key 返回旧尝试；同 key 不同请求冲突。
客户端 amountCent=1 的真实 HTTP 请求仍按订单 1000 分创建支付。
数据库患者请求 key、支付编号、提供方交易号、在途/成功生成列均唯一。

## Provider

Abstraction / Test Provider / Production Isolation / Create / Query / Close: PASS。
TEST 使用独立持久化模拟账本。默认、prod、prod+dev、prod+test 无测试提供方 Bean/端点。
真实渠道未配置明确 PAYMENT_PROVIDER_NOT_CONFIGURED；没有微信/支付宝网络调用。
第三方操作处于本地数据库事务之外。

## Callback

Signature / Replay / Idempotency / Wrong Amount / Wrong Currency / Transaction Uniqueness: PASS。
覆盖原始字节改变、过期签名、事件冲突、nonce 跨事件重用、16 KiB 上限、限流、100 次 HTTP 回调重放。
错误金额/币种不能改变支付义务，保留安全审计。
回调原文不保存；事件仅保存摘要、必要业务字段与处理结果。

## Recovery

- Provider Success / Local Pending: PASS。
- Provider Create Success / Local Reply Lost: PASS，保留 CREATED 并复用原 payment_no 恢复。
- Query Recovery: PASS。
- Callback vs Timeout / Query: PASS，按提供方终态收敛；SUCCESS 不被覆盖。
- Provider Settlement vs Close: PASS，12 轮真实并发竞争，最终只出现 SUCCESS/PAID 或 CLOSED/CLOSED。
- Restart: 服务对象重建后恢复 PASS；实际已部署后端进程重启尚未执行，不冒充已通过。

## Security

Patient Ownership / IDOR / Admin RBAC / SQL DataScope / Callback Security / Sensitive DTO: PASS。
真实 HTTP 验证患者隔离、Admin/Patient token 隔离、运营 403、心内科不能看到神经科订单。
Secret Management: PASS（本轮范围）。新增支付源码和三端源码未发现私钥/JWT 字面量；三端构建产物未发现当前配置的患者加密/哈希密钥。
Logging: 本轮 HTTP E2E 的捕获日志不含测试完整手机号、Access/Refresh Token、签名密钥或 Authorization Bearer。
数据库 callback 记录不含原始 body 或上述敏感值。

## Concurrency

| 项目 | 请求数 | 最终结果 |
| --- | ---: | --- |
| 同预约 Ensure Registration Order | 100 | 1 个 RegistrationOrder，1 次创建审计 |
| 相同 key Create Payment | 100 | 1 个 PaymentOrder，1 个提供方账本记录 |
| 不同 key Create Payment | 100 | 1 个 active Payment，其余明确冲突 |
| 相同签名 SUCCESS 回调（真实 HTTP） | 100 | 1 次业务成功、1 条成功交易流水、1 个回调事件 |

## Admin

Fee Rules: 基础页面与真实 API 已实现，创建/停用/version/收费策略 HTTP 验证 PASS。
Payment List / Detail: 只读页面已实现，HTTP 权限与 SQL DataScope 验证 PASS。
无手工成功按钮。部署后的浏览器页面验收待新版后端启动，不计为 PASS。

## Web

Registration Fee / Payment Status: 已实现预约详情组件及订单/支付 API 类型；构建 PASS。
跨端同一收费订单 PAID 状态已通过真实 HTTP 验证。
新版页面与部署后端的浏览器联调尚未执行。

## MiniProgram

Registration Fee / Payment Status: 已实现，复用患者订单 API；未调用 wx.requestPayment。
Build: PASS。
WECHAT_APPID: EXTERNAL。

## External Production Configuration

WECHAT PAY: EXTERNAL。
ALIPAY: EXTERNAL。
真实凭证均未填写；这不是当前阻塞项，不代表生产支付已经接通。

## Automated Tests

Backend：最后执行 `mvn test`，2026-09-05 23:38:55 BUILD SUCCESS。
按各 Maven 模块 Surefire XML 汇总，不能只读 bootstrap 小计：

```text
Tests run: 57
Failures: 0
Errors: 0
Skipped: 0
```

其中 common 2、bootstrap 55；新增支付集成 20、生产隔离 3、回调限流 1。
所有 STEP 6.1 测试均实际执行，没有跳过。
Flyway：空 MySQL V1→V18，原 30 张关键表和新增 7 张表验证 PASS。

Clean Test：FAILED（文件占用，不是测试断言失败）。
Package：BLOCKED，尚未取得最新打包 BUILD SUCCESS，不以 compile/test 代替 package。

| 前端 | lint | typecheck | build |
| --- | --- | --- | --- |
| Admin | PASS | PASS | PASS |
| Hospital Web | PASS | PASS | PASS |
| MiniProgram | PASS | PASS | build:mp-weixin PASS |

Docker Compose：`docker compose -p hospital-platform config --quiet` PASS。
MySQL / Redis / RabbitMQ / MinIO：均 healthy。
现有旧版后端 `/actuator/health`：UP；不是新版 JAR 验收证据。

## Bugs Found

1. JSON 小数可能被默认数值转换截断，影响金额严格校验。
2. 当前运行的旧 JAR 文件锁阻止 clean/package 最终闭环。

## Bugs Fixed

1. 费用金额使用严格整数反序列化；回调禁用浮点到整数自动转换，回归 PASS。
2. 旧 JAR 占用未强行处理，等待本轮服务重启授权。

## Test Data Cleanup

新增支付测试使用独立 Testcontainers MySQL/Redis，测试结束自动回收。
开发库已迁移 V18，检查 registration_fee_rule / registration_order / payment_order 均为 0 条。
未删除既有 Development 数据、数据库或 Docker Volume；未停止 Docker 或基础设施。

## Changed Files

- 新增 `hospital-bootstrap/.../payment/` 21 个核心类。
- 新增 V17 支付数据结构、V18 权限与菜单迁移；未修改 V1～V16。
- 修改 PatientAppointmentController 的确认后同步 ensure 编排。
- 修改 SecurityConfig 的回调 POST 白名单、application.yml 支付配置和 `.env.example` 空配置入口。
- 新增 PaymentCoreIntegrationTest、PaymentProductionIsolationTest、PaymentCallbackRateTest；更新 FlywayMigrationIntegrationTest。
- Admin：api/payment.ts、RegistrationFees.vue、PaymentOrders.vue、router.ts。
- Web：api/payment.ts、RegistrationPaymentStatus.vue、AppointmentDetail.vue。
- MiniProgram：api/payment.ts、RegistrationPaymentStatus.vue、appointment-detail/index.vue。
- 新增 PAYMENT_DESIGN.md；更新 DATABASE_DESIGN、API_DESIGN、ERD、MODULE_DESIGN、RBAC_DESIGN、SECURITY_DESIGN 和本报告。

## Result

仍需：本轮仅重启 hospital-backend 的授权 → 安全停止该项目进程 → clean test/package → 最新 JAR 启动 UP → 部署后页面验收。
禁止进入 STEP 6.2。

STEP 6.1 FAILED
