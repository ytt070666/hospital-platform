# 本地开发说明

本项目要求 JDK 21。不要用 JDK 17 降级编译，也不要将 `.env` 纳入 Git。

1. `Copy-Item .env.example .env`，填入强密码和随机 Base64 JWT 密钥。
2. `docker compose up -d` 启动 MySQL 8、Redis、RabbitMQ、MinIO；服务均配置卷、网络和 healthcheck。
3. 在 `services/hospital-backend` 执行 `mvn clean test package`。Flyway 会从空数据库创建 STEP 2 表；启动时仅在用户表为空且两个 Bootstrap 环境变量有效时创建管理员。
4. 后端默认端口 8080，Swagger 为 `http://localhost:8080/swagger-ui.html`。
5. Admin/Web/MiniProgram 分别安装依赖并执行其 `build` 命令。三端均指向同一 `VITE_API_BASE_URL`，没有独立数据库或 Mock API。

Docker Compose 是本地开发环境而非生产编排。生产需要独立数据库凭据、TLS/WAF、监控、备份与恢复演练。
