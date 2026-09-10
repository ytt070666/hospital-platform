# API 设计

公共前缀为 `/api/v1`。成功响应为 `{ code, message, data, traceId }`；失败响应不携带堆栈。所有写接口受 RBAC、审计和乐观锁保护。

## STEP 3 管理端

| 资源 | 路径 | 权限 |
|---|---|---|
| 医院 | `GET/POST/PUT /admin/master/hospitals` | `hospital:info:list/update` |
| 院区、楼宇、楼层 | `/admin/master/campuses|buildings|floors` | `hospital:campus:list/manage` |
| 临床科室、门诊科室 | `/admin/master/departments|outpatient-departments` | `hospital:department:list/manage` |
| 医生 | `/admin/master/doctors`、`/{id}/publish` | `hospital:doctor:list/manage/publish` |
| 职称、专业、出诊类型 | `/admin/master/titles|specialties|clinic-types` | 对应 `manage` 权限 |

停用入口为 `/admin/master/{resource}/{id}/disable`。关联中的父级资源禁止停用；编码冲突和乐观锁冲突返回 `409`。

## STEP 4.1 排班与号源

管理端以 `/api/v1/admin/schedule/sessions`、`/templates` 和 `/api/v1/admin/schedules` 提供时段、模板和排班工作台能力；发布、停诊、替诊、调号源、生成或修改号段均是排班 ID 下的显式动作接口，并受细粒度 `hospital:schedule:*` 权限保护。患者端通过 `GET /api/v1/public/schedules` 查询已发布的未来出诊，支持医生、科室、院区、出诊类型和日期范围筛选，最长范围由服务端配置控制。完整的字段边界、权限和状态规则见 [SCHEDULING_DESIGN.md](SCHEDULING_DESIGN.md)。

库存动作的成功语义以 MySQL 提交为准；Redis 只提供可重建的余号读缓存，既不是写入前置条件，也不作为公开 API 的数据来源。排班缓存维护入口只会影响 `hospital:dev:schedule:*` 命名空间，并只恢复有效已发布排班。

## STEP 5.1 患者身份

患者认证为 `/api/v1/patient/auth/send-code|login|refresh|logout`；私有资料与就诊人为 `/api/v1/patient/me`、`/members`。患者 ID 只从患者令牌主体获得。后台查询为 `/api/v1/admin/patients` 及详情、成员和禁用动作，受 `hospital:patient:*` 权限约束，默认只返回脱敏 DTO。

## STEP 5.2 预约核心

患者预约接口为 `POST /patient/appointments/holds`、`GET /patient/appointments/{id}`、`GET /patient/appointments`、`POST /patient/appointments/{id}/confirm`、`POST /patient/appointments/{id}/cancel`。创建锁号必须提供 `Idempotency-Key`，请求体仅允许 `memberId`、`scheduleId` 和可选 `slotId`；患者身份和来源客户端均来自患者令牌，不能由请求体或任意 Header 伪造。DTO 只返回预约编号、状态、关联 ID 与时间，不含患者敏感字段、加密字段、哈希、令牌或库存内部字段。

## STEP 3 患者公开端

`/public/hospital`、`/public/campuses`、`/public/departments`、`/public/doctors`、`/public/clinic-types` 仅返回已启用、已发布、未删除数据。医生公开 DTO 不含内部用户 ID、账号、证件、手机号、状态、版本或审计字段。`/public/files/{id}/url` 仅能为已公开医院、科室或医生关联的文件签发短时地址。
## STEP 5.3 预约管理接口

`GET /api/v1/admin/appointments`：预约列表，支持预约编号、患者编号、就诊人、医生、科室、院区、门诊类型、排班日期、状态及创建日期过滤，使用后端分页和 SQL DataScope。

`GET /api/v1/admin/appointments/{id}`：预约详情，返回专用 DTO、状态历史与库存摘要。

`POST /api/v1/admin/appointments/{id}/cancel`：需 `hospital:appointment:cancel`，必须提供取消原因，复用预约领域取消和库存释放。

患者预约响应额外提供 `serverNow`，仅用于以服务端时间锚定 HOLD 倒计时。

## STEP 5.4 行为约束

无需新增患者预约接口。停诊接口在成功返回时已完成有效预约取消和库存释放；存在有效预约的取消发布返回 `409`，存在有效预约或历史的号段替换返回 `409`。患者预约详情/列表额外返回 `originalDoctorName` 与 `substituted`，用于展示实际替诊医生和原出诊提示；后台预约 DTO 也保留该追溯信息。患者超过取消截止时间的 BOOKED 取消返回预约取消截止错误，工作人员协助取消保持既有后台权限与原因要求。

## STEP 6.1 挂号与支付 API

患者：GET/POST `/patient/appointments/{id}/registration-order`（获取/幂等创建），GET `/patient/registration-orders/{id}`，POST `/patient/registration-orders/{id}/payments`（Idempotency-Key，body 仅 provider/channel），GET `/patient/payments/{id}`、`/status`，POST `/patient/payments/{id}/query`。统一前缀 `/api/v1`。

管理：GET/POST `/admin/fee-rules`，PUT `/admin/fee-rules/{id}`（完整规则，含 status/version），GET `/admin/fee-rules/clinic-policies`，PUT `/admin/fee-rules/clinic-policies/{id}`（paymentRequired/version）；GET `/admin/registration-orders`、`/admin/payment-orders` 及 `/{id}`。订单列表支持 page/pageSize/status，按 SQL DataScope 过滤。

回调：POST `/payment/callback/{provider}`，原始 JSON bytes + `X-Payment-Timestamp`、`X-Payment-Nonce`、`X-Payment-Signature`；不使用 JWT，必须验签。
仅 dev/test（且不含 prod）：POST `/patient/payments/{id}/test-provider`，body scenario，返回待投递测试签名事件；不直接更新业务状态。
业务错误含 PRICE_NOT_CONFIGURED、PAYMENT_PROVIDER_NOT_CONFIGURED、PAYMENT_IDEMPOTENCY_CONFLICT、PAYMENT_ALREADY_ACTIVE、PAYMENT_AMOUNT_MISMATCH、PAYMENT_CURRENCY_MISMATCH、FEE_RULE_VERSION_CONFLICT。

## STEP 6.2 退款与对账 API

患者可通过 `GET /patient/refunds/{id}` 查看本人退款状态。退款回调为 `POST /refund/callback/{provider}`，仅允许验签后的提供方请求。管理员提供 `GET /admin/refund-orders`、`GET /admin/refund-orders/{id}`、`POST /admin/refund-orders/{id}/retry` 与 `GET/POST /admin/reconciliation-anomalies|run`；退款、订单和对账查询均以预约排班所属行政科室应用 SQL DataScope。完整退款工作台与患者退款交互留在 STEP 6.3。

## STEP 6.3 患者体验与财务中心 API

- `GET /patient/payment/capabilities`：当前可用支付通道（不返回商户配置）。
- `GET /patient/registration-orders`、`GET /patient/refunds`：患者本人分页订单。
- `GET /admin/finance/dashboard`：受 DataScope 限制的财务汇总。
- `GET /admin/refund-orders`、`GET /admin/refund-orders/{id}`：退款查询与详情；`POST /admin/refund-orders/{id}/retry` 只接受 `hospital:refund:retry`。
## STEP 6.4 Provider Callback Contract

`POST /api/v1/payment/callback/{provider}` 与退款通知路由不要求患者 JWT，但只接受受控 Provider 的签名通知。服务端在最大 16 KiB 原始 body 上验签，拒绝过期、重放、金额或币种不匹配的通知。测试模拟接口仅存在于 `dev` / `test` profile，生产 profile 不暴露该路由。

患者支付能力接口只返回当前有效配置的通道。生产没有有效商户配置时返回空能力集，创建指定 TEST 或未配置 Provider 返回明确业务错误，响应不包含商户私钥、API v3 Key、回调原文或签名材料。

## STEP 7.1 门诊签到与候诊 API

患者使用 `POST /patient/appointments/{id}/check-in` 与 `GET /patient/visits/queues`；工作人员使用 `/admin/visits` 签到、查询、叫号、重叫、过号和回队列动作，均受 RBAC 与 SQL DataScope 约束。医生使用 `/doctor/visits/workbench`、详情、开始与完成接口，身份只能由安全上下文解析。公共队列只返回候诊号、状态和诊室。
# STEP 7.2 临床接口

医生工作台通过 `/api/v1/doctor/visits/{encounterId}/clinical` 读取受控临床详情，并通过同一路径下的 `record/draft`、`record/sign`、`record/amendments`、`vitals`、`allergies` 与 `allergy-status` 写入。草稿保存和签署请求均带版本号，旧版本返回 HTTP 409。`record/revisions/{revisionId}/integrity` 只返回完整性布尔结果，不暴露 Hash 或正文以外的内部签名材料。

## STEP 7.3 处方与药房接口

医生处方位于 `/doctor/visits/{encounterId}/prescriptions`：可查询医院可开立 TEST 药品目录、创建草稿、增删草稿项目、签署、提交、取消和验证摘要完整性。药师接口位于 `/pharmacy/prescriptions`，用于查询已授权药房、待审队列、库存批号、审方、发药和完成退药。患者仅能通过 `/patient/prescriptions` 读取非草稿处方摘要或申请退药；响应不包含药师内部备注、库存、审计或安全规则细节。

## STEP 7.4 诊断接口

医生通过 `/doctor/visits/{encounterId}/orders` 开立、提交、取消和校验医嘱；检验工作流在 `/lab/specimens` 与 `/lab/results`，影像执行与报告在 `/imaging/orders` 和 `/diagnostic-reports`。患者仅通过 `/patient/diagnostic-reports` 查询已 FINAL 且已发布的精简报告，不能修改结果、签署报告或确认危急值。
