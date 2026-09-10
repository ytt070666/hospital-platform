# STEP 6.3 PAYMENT UX AND FINANCE CENTER EXECUTION REPORT

## 交付范围

- 患者 Web 与小程序新增“我的缴费”，展示本人挂号订单、金额、支付截止时间、退款金额及退款进度；预约详情在 TEST Provider 可用时可创建明确标注的测试支付单。
- 新增患者支付能力、本人挂号订单分页和本人退款订单分页 API。所有患者订单均按 patient_id 隔离。
- 管理端新增财务订单中心：财务汇总、挂号订单、支付订单、退款订单、对账异常、退款详情及失败退款重试入口。
- 新增 V21 权限与菜单：财务看板、挂号订单查询/详情、退款重试、对账详情。查询与汇总沿用行政科室 DataScope SQL；退款重试写入 `REFUND_RETRY` 审计。

## 安全与边界

- TEST 通道仅通过已认证患者的能力 API 在可用时暴露；真实微信和支付宝 SDK、商户密钥、真实扣款与真实退款均未实现。
- 订单和退款 response 使用 allowlist View，不返回支付密钥、回调原文、访问令牌或加密字段。
- 金额以分保存，患者与管理端统一以 CNY 人民币格式展示；应收金额、退款金额和支付状态均以后端结果为准。

## 验证结果

| 项目 | 结果 |
|---|---|
| Maven `clean test` | 56 tests，Failures 0，Errors 0，Skipped 0 |
| Maven `clean package -DskipTests` | BUILD SUCCESS |
| Admin lint / typecheck / build | PASS |
| Hospital Web lint / typecheck / build | PASS |
| MiniProgram lint / typecheck / build:mp-weixin | PASS |
| 最新后端包启动与 `GET /actuator/health` | UP |
| MySQL、Redis、RabbitMQ、MinIO | 全部 healthy |
| V21 迁移 | 已由真实 MySQL 与 Testcontainers 冒烟验证 |

## 延后到 STEP 6.4

以下真实浏览器异常链路按阶段定义标记为 `DEFERRED_TO_STEP_6_4`，不作为 STEP 6.3 阻塞项：已支付取消退款成功、退款失败后重试成功、超时关闭并取消预约、迟到支付后自动退款。

STEP 6.3 PASSED
