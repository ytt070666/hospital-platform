# STEP 2 FINAL ACCEPTANCE REPORT

## Overall

STEP 2 ACCEPTED WITH EXTERNAL CONFIGURATION PENDING

唯一待补齐的外部业务配置为 `WECHAT_APPID`。本报告不进入 STEP 3，且没有新增任何医疗业务模块。

## STEP Results

| 项目 | 结果 | 验收证据 |
| --- | --- | --- |
| STEP 2.7A | PASSED | RBAC 权限闭环、401/403 语义及接口校验已通过。 |
| STEP 2.7B / 2.7B.1 | PASSED | DataScope 六种范围、角色合并、真实 SQL 约束及集成测试通过。 |
| STEP 2.7C | PASSED | Admin、Web、小程序三端运行态、浏览器 E2E 与构建验收通过。 |
| STEP 2.7D 零状态恢复 | PASSED | 仅删除并重建 `hospital-platform-dev_mysql-data`；Flyway V1–V4 在空库全部成功；Bootstrap、RBAC、DataScope、私有 MinIO 上传/签名下载均通过。 |
| STEP 2.7D 重启恢复 | PASSED | `docker compose down` 后（不删除卷）重启：MySQL、Redis、RabbitMQ、MinIO 均健康；Flyway 显示 schema 已处于 V4；Redis PONG 与 RabbitMQ 生产/消费验证通过。 |
| 最终后端构建 | PASSED | JDK 21 下 `mvn -q clean test`、`mvn -q clean package` 均通过；测试使用隔离 MySQL 并完整应用 V1–V4。 |
| 最终前端构建 | PASSED | Admin、Web：lint、typecheck、build 均通过；小程序：lint、typecheck、`build:mp-weixin` 均通过。 |
| 最终运行态 | PASSED | 最终打包后的后端健康检查为 `UP`；Compose 配置校验通过，四项基础设施均为 healthy。 |

## Infrastructure Recovery

- Compose 项目名固定为 `hospital-platform-dev`；四个命名卷和专用网络均已确认。
- MySQL 零状态重建后，`flyway_schema_history` 仅有 V1、V2、V3、V4，全部 `success=1`。
- 重启后再次确认 V1–V4 均成功，未出现重复迁移或丢失迁移。
- 临时验收用户、角色、角色授权、文件元数据和两项 MinIO 测试对象已精确删除并复核为零。
- MinIO 未签名匿名读取返回 403；受保护短期签名 URL 的读取验收通过。

## Security Audit

- `.env` 与 `.env.*` 已由 `.gitignore` 忽略；Compose 仅引用环境变量，不内嵌真实凭据。
- 源码密钥模式扫描及敏感环境变量值扫描均通过；未发现私钥、云访问密钥、GitHub/Slack token 或真实本地密钥泄漏到非测试源码。
- SQL 审查确认业务参数使用占位符绑定；DataScope 仅按受控数量生成 `?` 占位符，不拼接用户输入。
- 密码使用 BCrypt cost 12；JWT 密钥无默认值且少于 32 字节会拒绝启动；生产 profile 关闭 OpenAPI/Swagger，CORS 必须显式配置。
- 在 JDK 21 下，生产 profile 缺少 JWT 密钥的启动验证已按预期拒绝。
- 当前交付目录没有 `.git` 元数据：`git status`、`git ls-files` 均返回 128。因此无法在此副本执行“已跟踪文件”层面的复核；这不影响已完成的工作区与 `.gitignore` 扫描，但交付到正式仓库前应在目标仓库再次运行该检查。

### Dependency Audit

- Admin 与 Web 的生产依赖审计均为 0 high / 0 critical。
- 小程序生产依赖审计为 10 high / 0 critical，均来自 `@dcloudio/uni-*` 工具链及其 `@intlify`、`postcss`、`ws`、图像处理等上游传递依赖。
- `npm audit fix --dry-run` 不会降低上述漏洞数量，只会新增 47 个跨平台可选包；为避免在验收阶段进行不受控的 DCloud 主版本替换，未执行自动修复。
- 发布小程序前的修复路径：选择 DCloud 官方提供的兼容 uni-app 版本，锁定升级后重跑小程序构建、真机验证与 `npm audit --omit=dev`。该上游依赖风险已记录，不掩盖为已修复。

## Runtime and Tooling

- 已安装 Temurin JDK 21.0.12.1 至用户目录，并配置用户级 `JAVA_HOME` 及 `Path`；Maven 已确认使用 Java 21。
- 本地开发暴露端口：MySQL 3307、Redis 6379、RabbitMQ 5672/15672、MinIO 9000/9001、后端 8080。它们仅为开发环境端口，不构成生产部署方案。
- Docker Compose 仅用于本地开发。生产仍需独立凭据、TLS/WAF、监控、备份和恢复演练。

## Final Decision

STEP 2 ACCEPTED WITH EXTERNAL CONFIGURATION PENDING

外部待办仅为提供并在微信小程序平台配置 `WECHAT_APPID`。在进入真实小程序发布前，还必须按上文路径完成 uni-app 上游依赖升级与复审；不得把该风险误报为已修复。
