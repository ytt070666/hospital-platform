# 门诊药品、处方与药房基础（STEP 7.3）

本阶段将药品目录、医院可开立目录、处方、审方、库存批号、调剂和退药拆分为独立关系模型；绝不把药品业务写入病历 JSON。`drug_catalog` 描述药品本身，`hospital_drug_formulary` 描述医院是否可开立或发放，`pharmacy_inventory` 与 `pharmacy_inventory_lot` 描述某药房的实际库存；MySQL 是库存唯一权威，Redis 不承载库存事实。

开发环境仅使用 `TEST_DRUG_*` 测试药品和测试批号，不代表真实医院药库、国家药品字典、医保目录或药物知识库。剂量、数量与库存均采用 `DECIMAL`，单位字段明确区分剂量、规格、包装和发药单位。

处方 `outpatient_prescription` 绑定 Encounter、接诊医生、患者/就诊人以及已签署病历版本；`prescription_item` 保留药品、剂型、规格、给药途径、频次和数量快照。医生只能在治疗关系内创建草稿，签署动作只取安全上下文中的医生身份。签署按处方头和排序后的全部药品项生成确定性 SHA-256 摘要；任何签署后原地修改均被拒绝，修改必须取消或新建 superseding 处方。

`MedicationSafetyProvider` 是真实知识库的适配边界。当前 `TestMedicationSafetyProvider` 只处理显式测试规则：活动测试过敏与 `TEST_DRUG_002` 形成 BLOCK，`TEST_DRUG_003` 形成 WARNING。BLOCK 不能提交；WARNING 需要具备 `hospital:medication:safety:override` 并记录原因。每次提交重新验证目录与安全结果，不能依赖草稿创建时的旧结果。真实药物相互作用、禁忌、妊娠/肝肾剂量和特殊药品规则均为 EXTERNAL。

药师通过 `pharmacy_user_scope` 限定药房范围。审核只能处理 `SUBMITTED` 处方，并永久追加 `pharmacy_review` 历史。通过审核时使用带余额条件的原子更新预占未过期、ACTIVE 批号；发药时在一个事务中扣减实物库存、消耗预占、写入调剂记录与状态。库存盘点或纠正通过 `pharmacy_inventory_adjustment` 追加记录，并用同一条条件更新保证库存调整后实存和可用数都不会低于已预留数量。退药默认不回库，只有药师明确确认且批号仍有效时才可回库。审计仅保存动作、资源、操作者、Trace ID 和时间，不复制病历正文、令牌或内部安全细节。

当前签署类型仅是已认证内部账号签署。`MedicationBillingGateway` 仅定义后续药品收费状态的适配边界，STEP 7.3 不会向任何支付渠道发起扣款。真实法律电子处方 CA、真实医院药品目录、真实药物相互作用/禁忌库、医保规则及药品收费均为 EXTERNAL。

STEP 7.4 的临床检查结果不会自动修改处方、自动诊断或自动开药；医生如需调整治疗，必须按既有处方签署和安全复核流程另行创建受控处方。
