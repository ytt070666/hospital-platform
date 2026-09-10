# RBAC 与数据权限设计

RBAC 基于 `sys_user`、`sys_role`、`sys_permission`、`sys_user_role`、`sys_role_permission`、`sys_menu`。后端以 `@PreAuthorize("hasAuthority('permission:code')")` 强制校验；前端动态菜单只改善体验，不能构成安全控制。

当前内置角色为 `SUPER_ADMIN`，供本地 Bootstrap 管理。当前基础权限涵盖用户、角色、权限、菜单、组织、文件和消息测试。首次管理员只能在 `BOOTSTRAP_ADMIN_USERNAME` 与 `BOOTSTRAP_ADMIN_PASSWORD` 均安全配置后创建。

数据权限为可扩展基础模型：`ALL`、`SELF`、`DEPARTMENT`、`DEPARTMENT_AND_CHILDREN`、`ORGANIZATION`、`CUSTOM`。角色保存 scope 和可选自定义范围；后续患者、报告等领域必须在应用服务/查询规范中统一使用此策略，不能在 Controller 堆叠 if 条件。本阶段没有开放任何医疗数据查询接口，因此没有伪造医疗数据权限实现。
# STEP 5.3 预约权限

- `hospital:appointment:list`：预约列表
- `hospital:appointment:view`：预约详情
- `hospital:appointment:cancel`：协助取消（必须填写原因）
- `hospital:appointment:history`：预约历史

RBAC 决定是否能访问接口；DataScope 决定 SQL 返回的排班所属行政科室范围。无取消权限的工作人员即使前端不显示按钮，直接调用取消接口仍返回 403。

## STEP 5.4 停诊、替诊与取消边界

停诊、替诊、取消发布和号段变更继续使用既有 `hospital:schedule:*` 细粒度权限；预约历史不因拥有排班编辑权限而自动可见。患者自助取消只以患者令牌主体判定对象归属，工作人员协助取消同时要求 `hospital:appointment:cancel`、排班所属行政科室的 SQL DataScope 与取消原因。跨患者 ID、跨数据范围预约 ID 和无权限内容运营账号均在服务端拒绝。

## STEP 6.1 支付权限

V18 新增 `hospital:fee:list`、`hospital:fee:create`、`hospital:fee:update`、`hospital:payment:list`、`hospital:payment:view`，仅默认授予 SUPER_ADMIN。Content Operator 未获授权返回 403。
后台收费与支付记录经预约、排班、门诊科室映射到行政科室，查询 SQL 使用现有 DataScope 约束。无数据范围时列表为空、详情 403。
患者只可访问自己同时拥有的 Appointment / RegistrationOrder / PaymentOrder，跨患者统一 404；Admin JWT 不能作为患者身份，Patient JWT 无管理权限。

## STEP 6.2 退款权限

- `hospital:refund:list`、`hospital:refund:view`：退款记录查询
- `hospital:refund:create`：受控退款重试
- `hospital:reconciliation:list`：对账异常查询与执行

默认只授予 SUPER_ADMIN。所有后台退款查询沿 `RefundOrder → Appointment → Schedule → Department` 在 SQL 中套用既有 DataScope；患者退款详情只按患者令牌主体匹配。

## STEP 6.3 财务中心权限

`hospital:finance:dashboard` 控制财务汇总；`hospital:registration-order:list/view` 控制挂号订单；`hospital:refund:retry` 与退款查询权限分离。所有后台资金查询继续按行政科室数据范围过滤，SUPER_ADMIN 在迁移 V21 中获得默认授权。
## STEP 6.4 财务支付边界

患者仅可读取自己的支付、挂号订单和退款状态。财务查询继续在 SQL 层按行政科室 DataScope 过滤挂号、支付与退款；Finance Viewer 仅查看，Finance Operator 只能对失败退款执行审计化重试。内容运营角色访问财务或患者资金接口返回 403，系统不存在人工将 Payment 直接置为 SUCCESS 的接口或按钮。

## STEP 7.1 门诊权限

`hospital:clinic-room:list/manage` 管理诊室；`hospital:visit:queue:list`、`hospital:visit:checkin`、`hospital:visit:queue:call` 分离队列职责；`hospital:visit:payment-override` 是签到缴费豁免专用权限；医生工作台要求 `hospital:visit:doctor:workbench` 并额外校验医生对象归属。

## STEP 7.2 临床病历权限

病历写入继续要求医生工作台权限和 Encounter 接诊医生关系；`hospital:clinical:record:amend` 是修订能力，`hospital:clinical:record:audit` 为明确授予的只读临床审计能力。技术超级管理员、前台与财务人员不因后台权限自动读取病历正文。

## STEP 7.3 处方与药房权限

医生处方能力拆分为 `hospital:clinical:prescription:create/view/sign/cancel`，并始终叠加接诊医生关系。药房能力拆分为审方查询、通过、拒绝、发药、退药及库存查看/维护；除了 RBAC 之外，药师必须拥有 `pharmacy_user_scope` 中的目标药房授权。患者只按令牌 patient_id 读取本人非草稿处方；前台、财务及技术管理员不会因为后台角色自动获得开方、审方或发药能力。

## STEP 7.4 诊断权限

诊断权限独立为医嘱创建/查看/取消、结果查看/复核、检验工作台/审核、影像工作台和报告签署。医生仍必须满足治疗关系；患者仅按 patient_id 读取已发布报告。技术 Super Admin 不会因后台身份自动拥有上述临床权限。
