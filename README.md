# 医院综合数字化服务平台

当前仓库处于 **STEP 2：工程与安全基础设施**。已实现可运行的基础认证、RBAC、审计、文件私有存储、消息测试链路和三端应用骨架；没有实现挂号、排班、支付、报告、诊疗或任何第三方医疗系统业务。

## 目录

- `apps/hospital-admin`：真实 API 驱动的管理后台基础界面。
- `apps/hospital-web`：患者 Web 技术骨架。
- `apps/hospital-miniprogram`：uni-app 微信小程序技术骨架。
- `services/hospital-backend`：Java 21 / Spring Boot 3 Maven 模块化单体。
- `docker-compose.yml`：本地 MySQL、Redis、RabbitMQ、MinIO 依赖环境。

## 本地开发

1. 安装 **JDK 21**、Node.js 22+、Docker Desktop，并启动 Docker Desktop。
2. 将 `.env.example` 复制为 `.env`，逐项替换示例密码及 JWT 密钥；`JWT_SECRET` 必须是至少 32 字节随机密钥的 Base64 值，管理员密码至少 12 位。
3. 执行 `docker compose up -d`，待四项依赖健康后进入 `services/hospital-backend` 执行 `mvn clean test package`。
4. 开发环境 Swagger 为 `http://localhost:8080/swagger-ui.html`；生产环境必须在网关层关闭其公开访问。
5. 分别在两个 Web 项目目录执行 `npm install --registry=https://registry.npmmirror.com` 后运行 `npm run build`。小程序在其目录执行相同安装后运行 `npm run build:mp-weixin`。

详见 [本地开发说明](docs/LOCAL_DEVELOPMENT.md) 与 [API 设计](docs/API_DESIGN.md)。
