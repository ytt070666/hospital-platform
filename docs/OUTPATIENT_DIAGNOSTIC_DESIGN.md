# 门诊检查、检验与影像设计

## 领域边界

`clinical_order`、`lab_specimen`、`lab_result`、`diagnostic_report`、`imaging_study` 和既有 `outpatient_medical_record` 为独立聚合。检查结果不会写入病历 JSON；医生如需补充病历，必须通过既有病历修订流程引用报告标识。

## 医嘱与目录

统一临床医嘱支持 `LAB`、`EXAMINATION`、`IMAGING`。目录目前仅提供 TEST 项目；真实医院检查目录、检验字典和收费目录均为外部集成。提交时会签署医嘱头及项目快照的 SHA-256 内容哈希，已提交医嘱不能普通更新，只能取消或创建新医嘱。

## 检验与标本

标本按 `PENDING_COLLECTION → COLLECTED → RECEIVED → PROCESSING → COMPLETED` 流转；拒收保留历史，并以 replacement specimen 重采。检验数值使用 Decimal，单位、参考范围、异常标记均为结果快照。相同 Provider event 通过唯一键幂等。

## 报告、危急值和发布

报告以 revision 建模。最终版本由当前认证用户签署并保存哈希；修订创建新的 amendment revision，旧 FINAL 版本始终保留。危急标记只由 TEST Provider 或医院审核后的规则产生；危急提醒必须由医生明确确认，页面打开不会自动确认。患者仅可读取已 FINAL 且已发布的精简报告 DTO。

## 影像与外部 Provider

影像 Study 保存安全的测试 accession/study UID 元数据，不模拟 DICOM 图像或医学诊断。开发环境可使用 Test Provider；生产环境关闭测试 Provider，未配置外部 LIS/RIS/PACS 时必须失败关闭，返回 `CLINICAL_INTEGRATION_NOT_CONFIGURED`。

## 访问控制、并发与恢复

医生仅能处理其治疗关系内的 Encounter；患者仅能读取本人已发布结果；检验、影像岗位需要独立权限。所有状态变更使用 MySQL 行锁、版本条件更新和唯一键；临床结果、报告与危急告警的 Source of Truth 是 MySQL，不是 Redis 或消息队列。审计只记录操作、资源标识、操作者、链路标识和时间，不复制报告正文或结果 JSON。

### STEP 7.4A 加固

从 V29 起，`clinical_execution_user_scope` 将执行人员限定为“用户、科室、服务类型”的交集；未被明确授权的检验或影像人员不能枚举、采集、接收、录入、审核或采集影像数据。报告使用 `IMMEDIATE_AFTER_FINAL`、`AFTER_DOCTOR_REVIEW`、`MANUAL_RELEASE` 三种发布策略：生产默认手工发布，患者始终只能看到已 FINAL 且已发布的精简结果。稳定测试目录代码为 `TEST_LAB_001`、`TEST_LAB_CRITICAL`、`TEST_EXAM_001`、`TEST_IMAGING_001`，仅用于非生产 Test Provider 验收。

## 外部边界

以下能力尚未接入且明确为 `EXTERNAL`：真实医院目录、LIS、RIS、PACS、DICOMweb、设备连接、真实参考范围、危急值规则、通知网关、临床服务收费、法定 CA 报告签名。
