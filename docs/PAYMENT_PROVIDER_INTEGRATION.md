# Payment Provider Integration

## 适配器与能力

系统实现 `WeChatPayProvider` 与 `AlipayPaymentProvider` 的创建支付、查询、关闭、创建退款、退款查询、支付通知和退款通知能力。适配器只在完整配置校验通过后返回可用；未配置时系统可启动，但支付创建返回 `PAYMENT_PROVIDER_NOT_CONFIGURED`。没有任何 TEST 回退或自动成功逻辑。

TEST Provider 仅在 `dev` / `test` profile 且非 prod 混合 profile 注册。生产 profile 不暴露 TEST capability、测试模拟支付、测试退款或测试回调入口。

## 外部配置

以下均为部署侧 EXTERNAL CONFIGURATION，不能提交到仓库：

- `WECHAT_PAY_MCH_ID`、`WECHAT_PAY_APP_ID`、`WECHAT_PAY_API_V3_KEY`
- `WECHAT_PAY_PRIVATE_KEY_PATH`、`WECHAT_PAY_CERT_SERIAL_NO`、`WECHAT_PAY_PLATFORM_CERT`
- `WECHAT_PAY_NOTIFY_URL`、`WECHAT_PAY_REFUND_NOTIFY_URL`
- `ALIPAY_APP_ID`、`ALIPAY_PRIVATE_KEY_PATH`、`ALIPAY_PUBLIC_KEY_PATH`
- `ALIPAY_NOTIFY_URL`、可选受控 HTTPS `ALIPAY_GATEWAY`

密钥使用 Secret Store 或受限 Secret Mount；配置路径不得指向仓库中的真实密钥文件。当前没有真实微信、支付宝凭证或生产账单授权，因此仅验证适配器契约和签名 fixture，绝不向生产网络发起交易或退款。

## 通知与恢复

微信通知使用原始 body、时间戳、nonce、平台证书签名和 AES-GCM 解密；支付宝通知使用原始参数签名，并校验订单、金额、币种及应用身份。两类通知先持久化事件，再以 Provider 查询确认真实状态；收到后在处理前崩溃的 `RECEIVED` / `FAILED_RETRYABLE` 事件会由恢复任务继续处理。重放通知只会产生一次业务状态变更。

Provider HTTP 客户端设置连接和请求超时，固定 HTTPS 目标并映射为领域错误。日志仅记录必要业务标识和金额，不记录密钥、完整签名、Authorization 或原始通知。
