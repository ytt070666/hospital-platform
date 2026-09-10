# 门诊电子病历（STEP 7.2）

`VisitEncounter` 表示真实到院就诊，`outpatient_medical_record` 是每次就诊唯一的文书容器，`outpatient_medical_record_revision` 是可追溯临床正文版本。草稿以乐观锁更新；签署后使用确定性字段顺序、空值和换行规范化的 SHA-256 内容摘要，正文和诊断集合不可原地修改。修订创建新的 `AMENDMENT` 版本，保留父版本并要求修订原因。当前签署类型为 `INTERNAL_AUTHENTICATED_SIGNATURE`，仅表示已认证医生账号操作；医院 CA/法律电子签章为 EXTERNAL，并由 `ClinicalSignatureProvider` 预留接入点。

生命体征以 `clinical_vital_sign` 保存结构化单位值；BMI 仅由后端按身高、体重计算，不生成医学结论。更正保留原测量并创建新测量。`patient_allergy_profile` 区分 `UNKNOWN`、`NO_KNOWN_ALLERGIES` 与 `HAS_ALLERGIES`；存在活动过敏时不能确认 NKA。`patient_allergy` 为纵向患者/就诊人数据，不物理删除。

诊断属于病历修订；写入时保存字典编码、名称快照和编码体系。测试环境仅使用有限 TEST 字典，生产读取医院导入的 `HOSPITAL` 字典，绝不声称内置全国 ICD。数据库生成列唯一键和服务校验共同保证每个修订最多一个有效主诊断。

临床正文访问同时需要功能权限和治疗关系：接诊医生可读写，临床审计者只能读取，技术管理员、收费和前台不因系统角色自动取得病历正文。患者端没有草稿或临床写接口。所有临床查看、草稿、签署、修订和完整性验证记录至 `clinical_access_audit`，审计和日志不保存正文、令牌或身份敏感字段。MySQL 为临床数据权威，后端及 Docker 重启后迁移数据保持。

STEP 7.3 的门诊处方只引用已签署病历版本和诊断上下文，不改变、覆盖或随病历修订自动改写旧处方。药品、审方、库存和调剂属于独立 medication 领域，详见 [OUTPATIENT_MEDICATION_DESIGN.md](OUTPATIENT_MEDICATION_DESIGN.md)。

STEP 7.4 报告和检验结果同样是独立诊断聚合，不存入病历正文。医生需要基于后续报告补充临床判断时，必须创建病历 Amendment，而不是修改原已签署版本。
