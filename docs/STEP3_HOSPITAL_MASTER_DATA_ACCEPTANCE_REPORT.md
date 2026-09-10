# STEP 3 HOSPITAL MASTER DATA ACCEPTANCE REPORT

## 范围结论

STEP 3 只交付医院主数据中心：医院、院区、楼宇、楼层、临床科室、门诊科室、医生、职称、专业、出诊类型及关系管理。未新增排班、号源、预约、挂号、订单、支付、退款、病历或报告模块。

## 实现与安全

- Flyway 已从 V1 连续升级至 V7；临床 `department` 与行政 `sys_department` 保持独立。
- 医院、院区、楼宇、楼层、科室、门诊科室、字典和医生具备真实管理 API、状态/发布控制与乐观锁。
- 科室树阻止自身/后代循环；医生至少一个科室且数据库保障每人最多一个主科室。
- RBAC 使用 `hospital:*` 权限；科室/医生读取和写入范围复用既有行政 DataScope。
- 公开 API 强制启用、发布、未删除过滤；医生公开 DTO 已排除内部账号、医生编号、状态、发布和版本字段。
- 文件公开读取只允许已发布医院、科室或医生引用的对象；管理写操作审计包含资源类型、资源 ID、操作者、结果和 Trace ID。

## 验收证据

| 项目 | 结果 |
|---|---|
| Maven `clean package` 与后端测试 | 通过，5 个测试类、7 个测试均为 0 failures / 0 errors |
| Flyway/Testcontainers | 通过，真实 MySQL 从零执行 7 个迁移 |
| 管理端、Web、小程序构建 | 全部通过 |
| 真实接口 E2E | 创建医院、院区、楼宇、楼层、科室、职称、专业、医生、门诊科室成功；公开 DTO 脱敏成功；陈旧版本更新和关联楼宇停用均返回 409 |
| 浏览器 E2E | 通过，公开 Web 首页与医生详情展示真实 API 数据，预约按钮保持禁用 |
| 运行态 | 后端 `/actuator/health` 为 `UP`；MySQL、Redis、RabbitMQ、MinIO 全部 healthy；Flyway 当前版本 7 |

## 交付物

- `docs/MASTER_DATA_DESIGN.md`
- `docs/DATABASE_DESIGN.md`
- `docs/API_DESIGN.md`
- `docs/ERD.md`
- `docs/MODULE_DESIGN.md`

STEP 3 PASSED
