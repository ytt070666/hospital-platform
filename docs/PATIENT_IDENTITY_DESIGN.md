# 患者、就诊人与预约身份基础（STEP 5.1）

本阶段仅交付患者账户、就诊人、身份信息、患者认证和患者管理基础；不包含预约、锁号、支付、病历或医保结算。`sys_user` 仅代表医院工作人员，患者绝不写入该表。

`patient` 是患者账户与档案主体，使用不可猜测的 `patient_no`；`patient_member` 是实际就诊人，关系代码为 `SELF`、`CHILD`、`PARENT`、`SPOUSE`、`OTHER`。数据库生成列唯一约束和服务校验共同保证每个账户最多一个本人。`patient_hospital_card`、`external_patient_reference` 为院内卡、HIS/MPI 标识预留，不伪造对接。

姓名、手机号、证件号、就诊卡号和外部患者标识使用 AES-GCM 加密；精确查询使用基于密钥的 HMAC-SHA256，并在手机号、证件号规范化后计算。密钥仅来自 `PATIENT_DATA_ENCRYPTION_KEY` 与 `PATIENT_DATA_HASH_KEY`，不落库、不写日志。

患者令牌使用 `subjectType=PATIENT` 与 `hospital-patient-api` audience；刷新令牌位于独立的 `patient_refresh_token`，不复用后台 `sys_refresh_token`。验证码仅在 Redis 保存 HMAC 值，5 分钟有效、成功即删除、错误五次失效，并有手机号和 IP 限流。开发测试码来自后端测试 Provider；生产无 Provider 时明确拒绝，绝不使用固定验证码。

患者接口仅从令牌主体获取 patientId。成员跨账户读取、更新、禁用与设默认均拒绝；后台查询需 `hospital:patient:*` 权限，DTO 默认脱敏且审计不记录完整手机号、证件号、密文、哈希或验证码。

## STEP 5.4 最终安全回归

运行时密钥只从受保护环境变量读取；测试配置同样不保留固定密钥字面量。默认运行配置不返回测试短信验证码，测试短信 Provider 仅在本地 `dev` 配置启用。患者成员与后台患者 DTO 均为显式 DTO，不能序列化 `mobileEncrypted`、`idNumberEncrypted`、对应 HMAC 或密钥字段；患者登出后，已签发的访问令牌不能继续访问私有患者接口。
