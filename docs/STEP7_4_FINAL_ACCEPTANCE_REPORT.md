# STEP 7.4 OUTPATIENT DIAGNOSTIC FINAL ACCEPTANCE REPORT

## Overall

STEP 7.4 FAILED

本报告只覆盖 STEP 7.4D；未进入 STEP 7 FINAL ACCEPTANCE、STEP 7.4E 或 STEP 8。

## Real Token Clinical Access Matrix

所有下列项目均使用运行中的最终 JAR、真实 Login API、真实 access token 与 MySQL；未使用 MockMvc、伪造 JWT 或管理员替代角色。

| 角色/场景 | 直接 API 证据 |
| --- | --- |
| Treating Doctor | 自有医嘱读取 200、Review 200、Ack 200；Lab Verify 403 |
| Other Doctor | 他人医嘱读取 403、他人 Review 403 |
| Lab Collector | 标本列表 200、Verify 403 |
| Lab Technician | 标本列表 200、Verify 403 |
| Lab Reviewer | 结果列表 200、影像签署 403 |
| Radiology Technician | 影像工作列表 200、报告签署 403 |
| Radiology Doctor | 报告创建/读取/签署 200、Lab Verify 403 |
| Examination Staff | Lab、Imaging 写入均 403 |
| Front Desk / Finance | 临床端点均 403 |
| Patient A / B IDOR | own FINAL feed 200；跨患者报告 404 |
| Clinical Auditor | FINAL audit 读取 200、签署 403 |
| Super Admin | audit-only 端点 403，无自动临床绕过 |

## Docker Recovery

- 使用真实 HTTP 建立非空 `recovery74d_*` 集：PLACED order、收集/接收标本、FINAL result/report、NEW critical alert、FINAL Imaging R1/R2、Doctor Review。
- 重启前安全快照见 [STEP7_4D_RECOVERY74D_SNAPSHOT.md](C:/Users/yttt/Desktop/codex工作内容/前后端/docs/STEP7_4D_RECOVERY74D_SNAPSHOT.md)，仅含 ID、状态、版本、哈希。
- 精确停止本项目 JAR 后执行 `docker compose -p hospital-platform down`（未使用 `-v`），四个命名数据卷保留；随后 `up -d`。
- 重启后 MySQL、Redis、RabbitMQ、MinIO 均 healthy，最新 JAR health 为 UP。
- 真实 HTTP 读回三份 order、specimen、FINAL result、两份 FINAL report、两条 revision、NEW alert、两条 review；MySQL 计数 `3/1/1/2/2/1/2`。
- 两份 FINAL report 的 `verifyIntegrity` 均为 PASS；哈希、版本和影像修订父链与重启前一致。

## Provider Failure Handling

- Timeout 与 Unknown State 均通过真实 HTTP 触发，均返回受控 `400 / DIAGNOSTIC_007`，订单保持 PLACED，未生成报告。
- 两条脱敏 Integration Anomaly 已持久化；同一业务键重试均为 200。
- 同一 order 的 100 并发 HTTP submit：100/100 成功，数据库业务提交键计数 1，重复业务单 0。
- Production Test Provider isolation / fail-closed 自动化套件保持 PASS；本轮没有将 Test Provider 注册到 production profile。

## Runtime Privacy Evidence

- 两次真实 Backend stdout/stderr 日志扫描，四个 `E2E74D_PRIVACY_*` 正文标记、Authorization、Bearer、access/refresh token、JWT、provider/PACS/MinIO secret、患者密钥的命中数均为 0。
- 实际 `clinical_diagnostic_audit` 仅含 `id, actor_id, action, resource_type, resource_id, trace_id, created_at`；审计中未命中临床正文标记。
- 真实 Patient、Radiology Doctor、Treating Doctor DTO 响应均为 200，且不含 provider payload/secret、内部 QC、staff note、audit/data-scope metadata、content hash、credential 或 token。
- Front Desk/Finance 的临床 DTO 请求均 403；敏感接口使用显式 DTO，不返回 entity。

## Automated Tests and Runtime

- `mvn clean test`：Tests run 69，Failures 0，Errors 0，Skipped 0。
- `mvn clean package` 聚合报告：Tests run 71，Failures 0，Errors 0，Skipped 0；最新 JAR 已生成并运行。
- 已验收的 Admin、Hospital Web、MiniProgram lint/typecheck/build 保持 PASS；本轮未变更三端源码。
- Health：UP；MySQL、Redis、RabbitMQ、MinIO：healthy。

## Cleanup

- `access74d_*` users = 0；六类隔离前缀 clinical orders = 0；provider anomalies、recovery reports、隔离 encounter 均 = 0。
- 未执行 TRUNCATE、Docker volume 删除或正常开发数据删除；四个数据卷仍存在。

## External

REAL_LIS、REAL_RIS、REAL_PACS、REAL_DICOMWEB_ENDPOINT、REAL_DEVICE_CONNECTIVITY、REAL_LAB_REFERENCE_RANGE、REAL_CRITICAL_VALUE_RULES、REAL_CLINICAL_NOTIFICATION_GATEWAY、REAL_CLINICAL_SERVICE_BILLING、LEGAL_DIAGNOSTIC_REPORT_CA_SIGNATURE：EXTERNAL。

## Real Blocking Items

1. **完整真实 Token Clinical Access Matrix 未达到题设硬门槛。** 已取得核心角色的直接 API 允许/拒绝证据，但项目当前没有正式 `FRONT_DESK`、`FINANCE` 角色及其 Visit/Payment 原权限，无法真实验证“原权限正常”；也未建立第二个执行 Department/Campus 的真实业务资源，不能如实证明 Lab、Radiology、Examination 的跨 Department/Campus DataScope 篡改攻击被拒绝。不得以空数据、菜单或超级管理员替代。

除该真实阻塞项外，本轮 Docker recovery、Provider failure path、运行态日志/Audit/DTO privacy 已完成。

## Changed Files

- `services/hospital-backend/hospital-common/src/main/java/com/hospital/platform/common/error/ErrorCode.java`
- `services/hospital-backend/hospital-bootstrap/src/main/java/com/hospital/platform/diagnostic/DiagnosticService.java`
- `docs/STEP7_4D_RECOVERY74D_SNAPSHOT.md`
- `docs/STEP7_4_FINAL_ACCEPTANCE_REPORT.md`

## Result

STEP 7.4 FAILED
