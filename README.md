# 医院综合数字化服务平台

基于 **Java 21 / Spring Boot 3 / Vue 3 / uni-app** 的医院门诊业务工程原型。采用 Maven 模块化单体架构，围绕排班号源、患者预约、测试收费、签到候诊和医生接诊组织业务流程，重点实现事务一致性、请求幂等、身份隔离和权限控制。

**当前代码已超过早期 STEP 2 工程骨架阶段。** 仓库包含排班、预约、支付退款、门诊病历、处方药房和检验影像的实现及页面；其中真实支付渠道、医院系统和部分临床权限验收仍未完成。项目用于工程学习与原型验证，尚未完成生产医院部署验收。

## 已实现的业务范围

| 业务 | 当前代码中的实现 | 完成边界 |
| --- | --- | --- |
| 身份与基础能力 | 工作人员认证、JWT/刷新令牌、RBAC、DataScope、审计、MinIO 私有文件、RabbitMQ 测试消息链路 | 通用文件功能权限不等于完整医疗文件对象授权；消息测试链路不等于完整业务通知系统 |
| 医院资源与排班 | 医院、院区、科室、医生、时段、排班模板、发布、停诊、替诊、号段及号源调整 | 使用本地维护的数据，未接入真实医院主数据系统 |
| 患者与就诊人 | 患者主体、家庭就诊人、身份信息加密与查询摘要、展示脱敏、验证码限流 | 短信是测试实现，未接通外部实名核验 |
| 预约挂号 | 锁号、确认、取消、过期释放、库存查询与对账、患者归属校验 | 当前采用 MySQL 条件更新和事务保证号源一致性；Redis 是可重建缓存 |
| 收费、支付与退款 | 独立收费订单、支付尝试、退款状态、整数分金额、回调校验与幂等、超时与异常恢复；TEST 闭环及微信/支付宝适配代码 | 患者页面主要支持测试支付；真实商户与账单联调未完成，支付宝主动退款查询仍有未实现分支 |
| 签到与接诊 | 签到、候诊队列、叫号、接诊、过号和返回队列 | 属于本地门诊流程原型 |
| 病历与药房 | 病历草稿、版本和修订、内部签署与摘要校验；处方、审方、批号库存、发药和退药 | 内部签署不是外部 CA 签章；药学安全规则为测试规则，药品真实收费未接通 |
| 检验与影像 | 医嘱、标本状态、结果复核、影像 Study 元数据、报告版本与发布、危急提醒、患者报告查询 | 使用测试 Provider；真实 LIS/RIS/PACS、DICOM 浏览及完整临床权限矩阵尚未完成 |

## 核心设计

### 预约库存与幂等

- MySQL 作为号源与预约状态的事实来源，通过带余量条件的 SQL 更新限制超卖，预占、确认、释放参与数据库事务。
- 幂等键按患者隔离，并绑定请求指纹；同键同内容重试返回原预约，同键不同内容拒绝；唯一约束限制重复有效预约。
- 状态迁移携带原状态条件，只有成功迁移的一方可以更新库存，处理确认、取消与超时之间的竞争。
- 数据库事务提交后更新 Redis，缓存异常时可以从 MySQL 重建。当前实现不能表述为 Redis Lua 承担最终库存一致性。

代码入口：[AppointmentService](services/hospital-backend/hospital-bootstrap/src/main/java/com/hospital/platform/appointment/AppointmentService.java)、[SchedulingService](services/hospital-backend/hospital-bootstrap/src/main/java/com/hospital/platform/scheduling/SchedulingService.java)。

### 权限与敏感数据

- 工作人员和患者使用不同主体与令牌边界；角色权限、部门范围、患者归属和医生治疗关系共同约束业务访问。
- 患者身份字段使用 AES-GCM 加密，精确查询使用 HMAC 摘要；接口采用显式 DTO 并脱敏展示。
- 病历采用乐观锁、版本及修订记录；签署内容保留摘要，支持完整性检查。
- 已有权限代码不代表完整攻击矩阵已通过。最新阶段报告仍保留跨科室/院区及正式前台、财务角色的验证阻塞项。

### 资金与外部适配

收费订单、支付尝试与退款分别建模，金额使用整数人民币分。外部通知验证签名、金额与币种，并通过事件幂等和恢复任务约束重复处理。生产配置禁止用测试支付 Provider 代替未配置的真实渠道；临床诊断生产配置关闭测试 Provider。

真实渠道能力以实际适配实现和联调证据为准，不能将接口定义、测试数据或配置模板视为生产接通。

## 技术架构与目录

后端使用 Java 21、Spring Boot 3.5.16、Spring Security、JdbcTemplate、Flyway；MySQL 保存业务状态，Redis 提供缓存、验证码及限流，MinIO 保存文件。RabbitMQ 当前提供可核实的测试消息生产消费链路。

```text
apps/
  hospital-admin/          Vue 3 / TypeScript 管理端
  hospital-web/            Vue 3 / TypeScript 患者 Web
  hospital-miniprogram/    uni-app 微信小程序
services/hospital-backend/
  hospital-common/         响应、异常、安全主体、敏感字段与 Trace ID
  hospital-iam/            工作人员认证、RBAC 与 DataScope
  hospital-audit/          通用操作与访问审计
  hospital-file/           私有文件及签名访问
  hospital-bootstrap/     启动装配和当前主要业务包、Flyway 迁移
docs/                     设计文档与分阶段验收记录
tests/                    验证资料
docker-compose.yml        本地 MySQL / Redis / RabbitMQ / MinIO 依赖
```

当前主要业务按包位于 `hospital-bootstrap`，并未拆分为独立微服务。根 Compose 编排四项开发依赖，不包含完整生产应用编排。

## 验证状态

截至 **2026-10-15**，核对代码版本为 [`f2fd25b`](https://github.com/ytt070666/hospital-platform/commit/f2fd25b685e79ebe5c5a3814ee8525dc5e1a9eb7)。请分别理解源码、阶段记录和公开 CI：

- 仓内已有预约争抢、重复请求、支付回调与身份权限等回归用例。测试用例存在不等于本次重新执行通过，也不等于性能或覆盖率结论。
- [STEP 5 历史报告](docs/STEP5_FINAL_ACCEPTANCE_REPORT.md)记录预约阶段验证；[STEP 6 历史报告](docs/STEP6_FINAL_ACCEPTANCE_REPORT.md)保留真实支付配置待接入的边界；[STEP 7.1 历史报告](docs/STEP_7_1_FINAL_ACCEPTANCE_REPORT.md)记录门诊接诊流程。
- 最新 [STEP 7.4 报告](docs/STEP7_4_FINAL_ACCEPTANCE_REPORT.md)结论为 **FAILED**。报告记载部分恢复、Provider 异常和隐私验证已完成，但正式前台/财务角色原权限与跨执行科室/院区的完整真实 Token 矩阵未达到验收门槛，不能称为全项目最终验收通过。
- 公开 [GitHub Actions 运行](https://github.com/ytt070666/hospital-platform/actions/runs/34447576138)结果为 `failure`。仓内历史通过记录不能代替当前公开 CI 通过状态；此处不对未确认的失败原因作推断。

仓库创建于 **2026-09-10**。GitHub 仓库创建和上传记录描述公开托管时间，不能单独证明本地研发开始日期。

## 本地开发

以下为开发指令，执行前应完成对应环境配置。

1. 准备 **JDK 21、Maven、Node.js 22+、Docker Compose**。后端不应使用 JDK 17 降级编译。
2. 将 `.env.example` 复制为 `.env`，替换占位凭据；JWT 密钥应为至少 32 字节随机值的 Base64，初始化管理员密码至少 12 位。患者数据加密/查询摘要、测试支付等配置还需按源码的配置要求准备。
3. 在项目根目录执行 `docker compose up -d`，检查 MySQL、Redis、RabbitMQ、MinIO 的健康状态。`.env` 供 Compose 读取，后端进程仍需通过环境变量或受控 IDE 配置接收相同设置。
4. 在 `services/hospital-backend` 执行 `mvn clean test package`。部分集成测试依赖 Docker/MySQL/Redis；需根据环境报告测试成功、失败或跳过情况。
5. 如需启动开发后端，将配置注入后执行 `java -jar hospital-bootstrap/target/hospital-bootstrap-0.1.0-SNAPSHOT.jar --spring.profiles.active=dev`。开发 API 文档默认位于 `http://localhost:8080/swagger-ui.html`；`dev` 包含测试短信等测试行为。
6. 在 `apps/hospital-admin`、`apps/hospital-web` 分别执行 `npm ci --registry=https://registry.npmmirror.com`，然后执行 `npm run lint`、`npm run typecheck`、`npm run build`；小程序使用相同安装方式并执行 `npm run build:mp-weixin`。本地调试可以使用各项目已有的 `dev` 或 `dev:mp-weixin` 脚本。

不要将 `.env`、真实密钥、患者数据或运行日志提交到仓库。生产部署还需独立完成真实渠道、权限验证、监控、TLS、备份恢复等验收。

## 文档索引

- [本地开发说明](docs/LOCAL_DEVELOPMENT.md)
- [模块设计](docs/MODULE_DESIGN.md)、[API 设计](docs/API_DESIGN.md)
- [预约设计](docs/APPOINTMENT_DESIGN.md)、[排班设计](docs/SCHEDULING_DESIGN.md)
- [支付设计](docs/PAYMENT_DESIGN.md)、[患者身份](docs/PATIENT_IDENTITY_DESIGN.md)
- [门诊病历](docs/OUTPATIENT_EMR_DESIGN.md)、[药房](docs/OUTPATIENT_MEDICATION_DESIGN.md)、[检验影像](docs/OUTPATIENT_DIAGNOSTIC_DESIGN.md)
- [RBAC](docs/RBAC_DESIGN.md)、[安全设计](docs/SECURITY_DESIGN.md)

部分设计和执行报告保留早期阶段记录，应结合当前代码及最新验收报告阅读。文档中的规划不自动代表已实现能力。

## 许可

当前仓库未提供独立 LICENSE 文件；本说明不新增或变更许可授权。
