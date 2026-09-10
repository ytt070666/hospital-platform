# 模块设计

## STEP 5.2 预约核心

`appointment` 模块只编排患者预约资格、状态机和既有排班号源服务，不复制 Patient、Schedule 或 Quota 领域。患者端只有锁号、查询、确认与取消 API；不包含预约页面、后台预约管理、支付、收费、病历或医疗流程。

创建 HOLD 在单个 MySQL 事务中完成成员/排班校验、库存保留、预约插入和历史插入。确认将保留库存提交为已预约；取消或过期按原库存状态释放。Redis 仅在提交后从 MySQL 更新，故障时由排班重建能力恢复。
# STEP 5.3 预约体验模块

患者 Web、小程序预约页面和后台预约管理中心均复用 `AppointmentService`。体验层不实现库存或状态机；它们只读取公开排班、患者就诊人和预约 API。后台使用 `AdminAppointmentController` 提供受权限、数据范围和审计保护的管理操作。

## STEP 6.1 支付模块

新增 bootstrap 的独立 payment 包：Money、定价接口与本地规则、收费订单、支付尝试、提供方 registry、测试提供方、原始字节签名回调、超时恢复与审计。
仅患者确认 HTTP 编排在 BOOKED 后同步 ensure 收费订单；不修改 AppointmentService 状态机、配额或库存算法。确认后中断可通过显式 ensure/确认重试恢复。
三端仅接入费用与订单状态，Admin 新增基础费用管理及只读支付查询。详见 PAYMENT_DESIGN.md。

STEP 6.3 将患者端扩展为“预约详情支付提示 + 我的缴费 + 我的退款”，小程序使用同一患者 API；管理端新增财务订单中心，涵盖看板、挂号订单、支付订单、退款订单与对账异常入口。
## STEP 6.4 支付适配模块

Payment 模块包含 Provider Registry、TEST 适配器、微信适配器、支付宝适配器、回调处理器、退款处理器及恢复任务。适配器负责协议、签名、查询、关闭和退款；领域服务负责订单状态、幂等、金额边界、预约联动和审计。这样生产 Provider 失败、重试或重启恢复不会绕过资金领域约束。

## STEP 7.1 门诊就诊模块

新增 `visit` 包承载诊室、签到、Encounter、候诊队列、医生工作台和未签到记录任务。控制器只处理认证与 DTO；`OutpatientVisitService` 集中状态机、条件更新、DataScope 和对象归属校验。模块不引入病历、诊断、处方、药品或检查模型。

## STEP 7.2 临床文书模块

`clinical` 包独立承载病历容器与版本、生命体征、过敏史、诊断目录及签署完整性。`visit` 只在完成接诊时查询当前已签署病历，不复制临床状态。控制器暴露显式临床 DTO；签名与医院字典由接口隔离，当前不接真实 CA 或全国疾病字典。

## STEP 7.3 药品与药房模块

新增 bootstrap 的 `medication` 包。`MedicationService` 集中处方状态机、治疗关系、签署摘要、安全重检、审方、库存预占、发药、退药、范围校验与审计；控制器只处理明确 DTO 与权限入口。`MedicationSafetyProvider` 和未来 `MedicationBillingGateway` 是外部知识库/收费系统的边界，当前测试实现不形成真实医疗结论。

## STEP 7.4 检验检查影像模块

新增 bootstrap 的 `diagnostic` 包。`DiagnosticService` 管理统一医嘱、标本状态机、数值结果、报告修订/签署、危急告警、影像 Study、患者发布和审计；控制器只接受明确 DTO，操作者永远由 SecurityContext 取得。真实 LIS/RIS/PACS/设备通过 Provider 边界隔离，生产环境不会回退到测试实现。
