# 医生排班与号源中心设计（STEP 4.1）

## 边界

本模块管理出诊时段、排班模板、实际排班、号源和号段。它不包含预约、挂号、订单、支付、医保或病历功能。号源的最终事实保存在 MySQL；Redis 仅缓存可用余号，失效后可以从数据库重建。

## 领域模型

```text
医院 ──< 出诊时段定义
医生 + 院区 + 门诊科室 + 出诊类型 + 时段定义 ──> 医生排班
排班模板 ──< 星期规则 ──> 批量生成医生排班
医生排班 ──< 号段 ──< 号源占用记录
医生排班 ──< 排班变更记录
```

- `schedule_session_definition`：医院级时段字典，供人工排班和模板规则复用。
- `schedule_template`、`schedule_template_rule`：模板及其星期、时段规则；重复生成由排班唯一约束和冲突检查兜底，已存在的记录会跳过。
- `doctor_schedule`：实际出诊安排，状态为草稿、已发布、停诊或取消；替诊保留原医生与现医生的可追溯关系。
- `schedule_slot`：排班下的可选时间段；`schedule_quota`：总号源、已占用号源及来源记录。
- `schedule_change_log`：创建、发布、停诊、替诊、号源调整、号段调整和模板生成的业务变更轨迹。

## 管理端 API 与权限

| 能力 | 路径 | 所需权限 |
|---|---|---|
| 时段定义 | `/api/v1/admin/schedule/sessions` | `hospital:schedule-session:manage` |
| 模板查询、预览 | `/api/v1/admin/schedule/templates`、`/{id}/preview` | `hospital:schedule-template:list` |
| 模板维护、生成 | `/api/v1/admin/schedule/templates`、`/{id}/generate` | `hospital:schedule-template:manage` |
| 排班查询 | `/api/v1/admin/schedules` | `hospital:schedule:list` |
| 创建、修改号段 | `/api/v1/admin/schedules`、`/{id}/slots` | `hospital:schedule:create`、`hospital:schedule:update` |
| 发布、停诊、替诊、调号源 | `/{id}/publish|stop|substitute|quota` | 对应 `publish`、`stop`、`substitute`、`quota` 权限 |

RBAC 在接口层强制执行，前端仅据此隐藏无权操作。排班查询将门诊科室映射到临床科室的行政部门，并通过既有 DataScope SQL 谓词过滤；请求携带筛选条件不能扩大用户可见范围。

## 患者公开 API

`GET /api/v1/public/schedules` 支持 doctor、department、campus、clinicType、日期与日期范围筛选。仅返回未来、已发布、未删除，且医生、院区、临床/门诊科室、医院和出诊类型均处于启用发布状态的记录。默认最大查询跨度为 31 天，由 `hospital.scheduling.public-max-query-days` 配置。

公开 DTO 只含展示信息、日期时间、总号源、可用号源、患者可见状态与号段；不泄露内部医生 ID、替诊医生 ID、已占用数量、版本、来源、内部状态或审计字段。患者状态只区分 `AVAILABLE` 与 `FULL`。

## 一致性、审计与错误处理

- 同一医生同日重叠时段在创建时返回 `SCHEDULE_CONFLICT`；模板批量生成对已存在的冲突记录跳过，不生成重复排班。
- 号源增减采用数据库条件更新，不能将总号源下调到已占用数量以下；并发扣减以 MySQL 为准。
- 管理写操作由审计过滤器按 `SCHEDULE` 资源关联，保存操作者、操作、资源 ID、Trace ID 与请求路径；业务变更明细另写入 `schedule_change_log`。
- 参数不合法返回业务错误码和中文提示；前端提示错误信息，不展示 SQL、异常栈或 Java 类名。

## 状态机、时区与恢复

实际排班按 `DRAFT → PUBLISHED → STOPPED` 管理；取消态不对患者端公开。停诊不是删除：排班、变更记录和审计记录均保留。替诊将原医生写入 `original_doctor_id`，当前出诊医生写入 `substitute_doctor_id`，不覆盖历史关系。

模板只在有效期和星期规则匹配的日期生成排班；同一业务键由数据库唯一键与服务层时段冲突检查双重保护。相邻时段允许，重叠时段拒绝。跨午夜时段不支持，`startTime` 必须早于 `endTime`。日期和时间使用医院的本地业务日期及 `LocalDate`/`LocalTime` 传输，不依赖 Windows 主机时区换算。

库存不变量为：`total ≥ 0`、`reserved ≥ 0`、`booked ≥ 0`、`reserved + booked ≤ total`，且 `available = total - reserved - booked`。Reserve、Release、Commit 都使用带余额条件的 MySQL 更新；Redis 更新失败、重启或篡改都不会改变数据库事实。非法 Release/Commit 会被数据库条件拒绝。

Redis key 使用 `hospital:dev:schedule:*` 命名空间，与 IAM、限流和刷新令牌隔离。`rebuild(scheduleId)` 按单个排班从 MySQL 重建；`rebuildPublishedQuotaCaches()` 只清理排班命名空间，并只重建有效已发布排班。停诊、取消或过期排班不会被重建为可用库存。

STEP 5.2 的预约锁号使用 `reserveBookable`：最终 MySQL 条件更新同时验证排班仍为已发布、未删除、未过期，且号段（如使用）仍为 ACTIVE，防止停诊或禁用号段与患者锁号的竞态。事务提交后才刷新 Redis；Redis 不可用不会回滚已提交的 MySQL 预约，后续重建可恢复缓存。

## STEP 5.4 与预约的最终边界

停诊将排班状态更新、排班变更日志、该排班有效预约的系统取消及库存释放置于同一 MySQL 事务。系统取消使用 `SCHEDULE_STOPPED` 原因和状态 CAS，重复执行不重复释放号源。存在 HOLDING/BOOKED 时禁止取消发布；存在任何预约历史时禁止生成、替换或删除号段，确保预约和号段外键、状态历史与排班追溯永久一致。

替诊校验替诊医生处于启用、发布状态并且不存在排班冲突。它不改变预约号、患者、就诊人、时段、状态或库存，只保存原医生与实际出诊医生，以供患者端提示和后台追溯。库存计数差异由预约对账服务只读报告，不以 Redis 或自动修复覆盖 MySQL。
