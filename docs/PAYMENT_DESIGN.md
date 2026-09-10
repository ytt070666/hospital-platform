# STEP 6.1 支付核心设计

## 边界与状态

本阶段不接真实资金，不提供退款、对账、自动取消预约或人工改成功能力。
Appointment 继续只有 HOLDING / BOOKED / CANCELLED / EXPIRED。收费义务属于
RegistrationOrder；每一次发起支付属于一个独立 PaymentOrder（Payment Attempt）。
Appointment BOOKED 不等于已支付，取消预约也不改变既有资金状态。

| 对象 | 状态 | 依据 |
| --- | --- | --- |
| RegistrationOrder | PENDING_PAYMENT → PAID / CLOSED | 本地事务、经过验证的提供方结果 |
| PaymentOrder | CREATED → PENDING → SUCCESS / FAILED / CLOSED | 提供方结果，不接受患者或管理员指定状态 |
| SUCCESS / PAID | 不允许降级 | 行锁、version 与数据库唯一键 |

收到经过验证且与提供方查询一致的迟到 SUCCESS 时，可以将本地非成功状态纠正为
SUCCESS / PAID；如果订单已由其他交易支付则记录异常并拒绝第二次业务成功。
退款与异常资金处置不在 STEP 6.1 范围内。

## Money 与价格

`Money(amountMinor, currency)` 使用 long，数据库使用 BIGINT 分。当前仅接受 CNY，
范围 0～1,000,000,000,000 分。金额输入拒绝小数 JSON；展示通过集中格式化函数
分割整数数字字符串，不执行浮点乘除。客户端 CreatePayment 的金额字段不参与计算。

`RegistrationPricingProvider` 是可替换的定价边界，默认 `LocalRegistrationPricingProvider`。
条件为医院匹配，院区/科室/门诊类型/职称可为空。规则在创建收费义务时生效，
有效期是 `[effective_from, effective_to)`，空截止时间表示不限。

确定性排序：`职称存在×8 + 门诊类型存在×4 + 科室存在×2 + 院区存在` 降序，
然后 priority 降序，最后 id 升序。不会随机取第一条。
保存规则 id、code、version、匹配条件、金额、币种和 pricedAt 作为不可变价格快照。
修改规则不重算已存在订单。

ClinicType.payment_required 默认为 false，以兼容 STEP 5。
没有匹配规则且 payment_required=false 时，明确记录 COMPATIBILITY_ZERO 来源；
payment_required=true 时返回 PRICE_NOT_CONFIGURED，不降级成免费订单。
金额为零的订单创建即 PAID，没有 PaymentOrder，也不调用提供方。

## 同步编排与可恢复创建

患者确认入口先使用现有 AppointmentService 确认，然后同步 ensureOrder。
两次本地提交之间如果进程中断，预约仍是 BOOKED，患者可重试 confirm 或
`POST /patient/appointments/{id}/registration-order`。页面在收费订单缺失时调用 ensure。
不使用可能丢失的内存事件或无 outbox 的消息发送。
首次创建收费义务时确定价格；尚未生成收费义务的历史 BOOKED 预约不会批量补收费。

ensure 先锁预约行，唯一键 appointment_id 保证最多一个订单。
order_no、payment_no 使用随机 UUID，不包含身份证、手机号等患者信息。

## 支付创建事务边界与幂等

1. 校验患者同时拥有收费订单和预约，核对幂等指纹。
2. 短事务锁 RegistrationOrder，检查状态及 deadline，插入 CREATED 并提交。
3. 无本地事务时调用 PaymentProvider.createPayment；payment_no 是提供方幂等商户引用。
4. 新短事务锁 RegistrationOrder → PaymentOrder，校验金额/币种/状态并写结果与审计。

数据库唯一约束：患者+client_request_id、payment_no、provider+transaction_id、
生成列 active_registration_id、successful_registration_id。一个收费订单最多有一个
CREATED/PENDING 和一个 SUCCESS。不同 key 并发遇到在途支付返回 PAYMENT_ALREADY_ACTIVE。
相同 key + 不同订单/provider/channel 返回 PAYMENT_IDEMPOTENCY_CONFLICT。
FAILED 后允许新 key 创建新 attempt；相同旧 key 仍返回旧 attempt。

外部调用超时/进程中断保留 CREATED，不将不确定结果误判为已失败。
相同请求重试使用同一个 payment_no；查询/后台恢复可重新定位或幂等创建提供方订单。
所有 Provider 层访问均在事务外，测试适配器主动检查该约束。

## 提供方与生产隔离

PaymentProvider 接口包含 create/query/close/verifyCallback/parseCallback。
TestPaymentProvider 使用独立的持久化模拟账本表，而不是内存 map；可以模拟成功、失败、
在途、超时，并生成用于迟到、重复、错误签名与金额篡改测试的签名事件。
测试场景入口只改变模拟提供方结果，不直接修改业务支付订单；调用方仍需提交回调或查询恢复。

Bean 和测试 HTTP 入口均限制为 `!prod & (dev | test)`；prod 与 dev/test 混合启动仍禁用。
Registry 再次检查运行 profile。默认 profile 也禁用 TEST。
测试签名密钥缺失不影响后端启动，但发起 TEST 返回 PAYMENT_PROVIDER_NOT_CONFIGURED。
WECHAT/ALIPAY 仅保留配置入口，无网络调用，无假实现或 TEST 自动回退。

## 回调验证、重放与最小化存储

`POST /api/v1/payment/callback/{provider}` 不需要 JWT，但强制验证提供方签名。
仅此 POST 路径获得 permitAll。原始字节最多 16 KiB，包括 chunked 请求；不反序列化后重组验签。
进程级每分钟有界计数限流（默认 1000），无需为任意来源创建无限 map。
生产真实网关接入时还需网关层全局限流和具体官方签名协议。

TEST 使用 HMAC-SHA256，待签名内容为 `timestamp + '\n' + nonce + '\n' + rawBody`。
验证恒定时间比较、5 分钟时间窗口、nonce 格式；eventId 和 nonce 分别按提供方唯一。
重复的相同 eventId/body 返回已处理结果；相同事件编号的不同内容或跨事件 nonce 重用拒绝。
验签后先保存 RECEIVED 事件，再在事务外查询提供方真相；金额、币种和交易号全部匹配后，
同一短事务执行业务变更、交易流水和事件 PROCESSED。拒绝结果保留摘要与异常审计。
短时查询故障保留 RECEIVED，提供方重投或本地查询恢复可继续。

数据库仅保存摘要、必要事件业务字段、交易号、处理结果和 traceId；不保存原始回调、
JWT、验证码、完整手机号/身份证或任何密钥。API 使用显式 allowlist DTO，
不返回密钥、回调原文、指纹、哈希或加密个人数据。

## 关闭、竞争与恢复

`hospital.payment.payment-timeout-seconds` 决定收费订单截止时间，支付尝试继承该时间。
后台按 `(status, expires_at)` / `(status, payment_deadline)` 索引批量扫描，批次限制 1～500。
未到期 CREATED/PENDING 也定期查询，以修复丢失回调和创建回复。

超时先查询提供方，再请求关闭（提供方返回其终态），最后提交本地结果。
回调同样查询提供方状态：本地谁先获得行锁不作为资金真相。
SUCCESS 不会被 CLOSED/FAILED/PENDING 覆盖。没有成功或在途尝试且超过 deadline 的
收费订单可 CLOSED；预约不会自动取消。未知提供方状态明确拒绝。

持久化记录在进程重建后可恢复；真实进程重启验收与服务重建测试须分别记录，不混为一谈。

## 管理、数据权限与审计

费用规则支持创建、编辑、启停、有效期和 priority；version 冲突返回 409。
ClinicType 收费策略有独立受权入口和版本审计。费用变更保存 before/after/operator/traceId。
后台收费/支付查询沿 `RegistrationOrder → Appointment → Schedule → OutpatientDepartment →
Department.administrative_department_id` 在 SQL 中应用现有 DataScope，不在内存中过滤结果。

权限：hospital:fee:list/create/update、hospital:payment:list/view；只给 SUPER_ADMIN 默认授权，
普通运营无财务权限。无退款权限、手动修改 SUCCESS 接口或按钮。
`payment_transaction` 是提供方调用/状态流水；`payment_audit_event` 是业务及安全审计，二者独立。

## 前端最小接入

Admin 提供真实费用规则管理、门诊收费策略和只读订单列表/详情。
Web、MiniProgram 预约详情展示费用及支付义务状态，使用同一患者 API；
不会把 BOOKED 当作付款成功，不提供真实支付渠道入口，不调用 wx.requestPayment。

## Secret Management / EXTERNAL

`.env.example` 仅列配置项与空值。测试签名密钥由环境注入至少 32 字节 Base64 随机值，
测试进程内部生成，不写入测试源码或前端。真实私钥使用文件路径配置，后续适配器读取受限文件。
真实微信支付、支付宝商户凭证及 WECHAT_APPID 均是 EXTERNAL，不代表真实支付已接入。

## 验证入口

`PaymentCoreIntegrationTest`：真实隔离 MySQL/Redis、随机端口 HTTP、100 并发订单/支付/回调、
定价、快照、免费、失败重试、IDOR、RBAC、DataScope、金额/签名、查询与关闭竞争。
`PaymentProductionIsolationTest`：默认/生产/混合 profile 无测试提供方或测试端点。
`FlywayMigrationIntegrationTest`：空库 V1→V18、原有 30 张关键表与新增 7 张表。
集成测试容器自动回收，不删除现有 development 数据或 Docker volumes。

## STEP 6.2：超时、退款与对账

超时任务按截止时间索引扫描收费义务，先查询提供方的最终状态，再决定关闭；未知或不可达状态保持待核验，绝不释放预约。确认未支付后，支付尝试关闭、收费义务 CLOSED、预约以 `PAYMENT_TIMEOUT` 取消并仅释放一次号源。零元订单已 PAID，不参与超时关闭。

退款是独立 `RefundOrder`：`CREATED → PENDING → SUCCESS / FAILED / CLOSED`。Payment SUCCESS 永不改写；收费义务以 `refunded_amount_cent`、`refund_reserved_amount_cent` 聚合计算 PAID、PARTIALLY_REFUNDED 或 REFUNDED。数据库行锁、金额条件更新和业务唯一键共同保证累计成功退款不超过已付金额，自动取消退款键为 `APPOINTMENT_CANCEL:{appointmentId}:{paymentId}`。

## STEP 6.3：患者支付体验与财务中心

`GET /patient/payment/capabilities` 返回当前环境可用的支付通道；生产环境不暴露 TEST。患者可分页查询自己的挂号订单和退款订单，后台财务看板、订单列表与退款列表全部经过同一行政科室 DataScope SQL 过滤。失败退款的重试是独立受控动作并审计 `REFUND_RETRY`。

患者取消、工作人员取消和停诊在预约提交后异步创建默认剩余全额退款；退款暂时失败不回滚预约取消。取消后迟到支付被验证为 SUCCESS 时，保留真实支付事实并立即建立 `LATE_PAYMENT` 自动退款。退款回调与查询都验签、核对退款号/支付号/金额/币种并幂等落库。

对账只自动记录明确差异，包含支付成功但收费义务未结清、已结清无成功支付、退款超额及取消已付预约无退款；默认标记 `MANUAL_REVIEW_REQUIRED`，不猜测资金状态。真实微信/支付宝账单和凭证仍为 EXTERNAL，未在本阶段接入。

## STEP 6.4：生产适配与恢复

`PaymentProvider` 现有 TEST、`WeChatPayProvider` 与 `AlipayPaymentProvider` 三个实现。TEST 仅在 `dev` / `test` profile 并且已注入测试签名密钥时注册；`prod` 与混合 prod profile 均不注册测试 Bean、测试路由或测试能力。生产适配器只有在完整外部配置有效时才报告可用，未配置时创建收费支付明确失败为 `PAYMENT_PROVIDER_NOT_CONFIGURED`，不会回退 TEST 或自动成功。

微信与支付宝适配器均使用受控 HTTPS 目标、有限连接/请求超时及统一错误码，不接受客户端提供的目标 URL。微信回调保留原始 body，使用时间戳、nonce、平台证书签名验签并对 AES-GCM 资源解密；支付宝按签名原文验签并校验订单号、金额、币种与应用身份。支付和退款回调先持久化为 `RECEIVED`，随后经 Provider 查询确认；进程中断后定时恢复器只从已持久化事件恢复，处理成功后写为 `PROCESSED`。原始回调、密钥和授权头不入库、不回显、不写日志。
