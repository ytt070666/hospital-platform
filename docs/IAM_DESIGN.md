# IAM 认证设计

Access Token 是 15 分钟 HS256 JWT，包含用户 ID、用户名、权限集合、客户端类型和 token version，并限定 audience 为 `hospital-api`。Refresh Token 为 7 天随机高熵字符串，只保存 SHA-256 摘要；使用一次即撤销并轮换。退出登录、密码修改、账户停用都会撤销或使 Refresh Token 失效；Access Token 校验数据库内的 token version 与账户状态，防止“已签发即永远有效”。

登录使用 BCrypt（cost 12）校验密码。连续五次错误密码锁定 15 分钟；登录和刷新接口使用 Redis 的固定窗口基础限流。登录日志不记录密码、令牌或请求正文，仅记录账户、结果、IP、客户端类型、原因和 Trace ID。

本阶段采用 `Authorization: Bearer <access token>`，前端 token 不使用 Cookie，因此 Spring Security CSRF 防护禁用是有边界的；CORS 仅允许显式开发源。若未来切换 Cookie，必须同时评估 `HttpOnly`、`Secure`、`SameSite` 和 CSRF Token。
