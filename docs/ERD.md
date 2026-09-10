# 实际数据库 ERD（截至 STEP 5）

```mermaid
erDiagram
  SYS_ORGANIZATION ||--o{ SYS_DEPARTMENT : contains
  SYS_DEPARTMENT ||--o{ DEPARTMENT : data_scope_mapping
  HOSPITAL ||--o{ HOSPITAL_CAMPUS : has
  HOSPITAL_CAMPUS ||--o{ HOSPITAL_BUILDING : has
  HOSPITAL_BUILDING ||--o{ HOSPITAL_FLOOR : has
  HOSPITAL ||--o{ DEPARTMENT : owns
  DEPARTMENT ||--o{ DEPARTMENT : parent
  DEPARTMENT ||--o{ OUTPATIENT_DEPARTMENT : offers
  CLINIC_TYPE ||--o{ OUTPATIENT_DEPARTMENT : categorizes
  DOCTOR_TITLE ||--o{ DOCTOR : titles
  DOCTOR ||--o{ DOCTOR_DEPARTMENT : belongs
  DEPARTMENT ||--o{ DOCTOR_DEPARTMENT : includes
  DOCTOR ||--o{ DOCTOR_SPECIALTY : specializes
  MEDICAL_SPECIALTY ||--o{ DOCTOR_SPECIALTY : classifies
  HOSPITAL ||--o{ SCHEDULE_SESSION_DEFINITION : defines
  SCHEDULE_TEMPLATE ||--o{ SCHEDULE_TEMPLATE_RULE : contains
  SCHEDULE_TEMPLATE ||--o{ DOCTOR_SCHEDULE : generates
  DOCTOR ||--o{ DOCTOR_SCHEDULE : attends
  DOCTOR_SCHEDULE ||--o{ SCHEDULE_SLOT : partitions
  DOCTOR_SCHEDULE ||--o{ SCHEDULE_CHANGE_LOG : records
  HOSPITAL { bigint id PK string hospital_code UK }
  HOSPITAL_CAMPUS { bigint id PK bigint hospital_id FK string campus_code }
  HOSPITAL_BUILDING { bigint id PK bigint campus_id FK string building_code }
  HOSPITAL_FLOOR { bigint id PK bigint building_id FK string floor_code }
  DEPARTMENT { bigint id PK bigint hospital_id FK bigint parent_id FK }
  DOCTOR { bigint id PK string doctor_no UK bigint title_id FK }
  SCHEDULE_SESSION_DEFINITION { bigint id PK bigint hospital_id string code }
  SCHEDULE_TEMPLATE { bigint id PK string template_code UK bigint doctor_id bigint outpatient_department_id }
  SCHEDULE_TEMPLATE_RULE { bigint id PK bigint template_id bigint session_definition_id }
  DOCTOR_SCHEDULE { bigint id PK string schedule_no UK string schedule_status int total_quota int reserved_quota int booked_quota }
  SCHEDULE_SLOT { bigint id PK bigint schedule_id string slot_no int total_quota int reserved_quota int booked_quota }
  SCHEDULE_CHANGE_LOG { bigint id PK bigint schedule_id string action string trace_id }
  PATIENT ||--o{ PATIENT_MEMBER : manages
  PATIENT ||--o{ PATIENT_REFRESH_TOKEN : owns
  PATIENT_MEMBER ||--o{ PATIENT_HOSPITAL_CARD : holds
  PATIENT ||--o{ EXTERNAL_PATIENT_REFERENCE : maps
  PATIENT { bigint id PK string patient_no UK string mobile_hash }
  PATIENT_MEMBER { bigint id PK bigint patient_id FK string member_no UK string relationship_code boolean is_self }
  PATIENT ||--o{ APPOINTMENT : creates
  PATIENT_MEMBER ||--o{ APPOINTMENT : attends
  DOCTOR_SCHEDULE ||--o{ APPOINTMENT : reserves
  SCHEDULE_SLOT ||--o{ APPOINTMENT : optional_slot
  APPOINTMENT ||--o{ APPOINTMENT_STATUS_HISTORY : transitions
  APPOINTMENT { bigint id PK string appointment_no UK bigint patient_id bigint member_id bigint schedule_id bigint slot_id string status datetime hold_expires_at string idempotency_key UK string active_booking_key UK }
  APPOINTMENT_STATUS_HISTORY { bigint id PK bigint appointment_id FK string from_status string to_status string trace_id }
}
```

STEP 5.4 的 `V16` 将图中的患者、就诊人、排班、可选号段与预约历史关系落实为真实数据库外键；预约记录和状态历史不能因排班结构调整而成为孤儿数据。

## STEP 6.1 资金关系

```text
Appointment 1 ── 0..1 RegistrationOrder ── N PaymentOrder（支付尝试）
                         │                       ├── N PaymentTransaction
                  PricingSnapshot                └── N CallbackEvent（payment_no 关联）
                         │
                  RegistrationFeeRule
```

PaymentAuditEvent 独立记录业务/安全审计；TEST 提供方账本只在 dev/test 使用。
成功交易、在途尝试与回调事件均有数据库唯一约束；预约不引入支付状态。

## STEP 6.2 退款关系

```text
PaymentOrder (SUCCESS) ──< RefundOrder ──< RefundCallbackEvent
RegistrationOrder ── refund aggregate (successful / reserved)
Payment / RegistrationOrder / RefundOrder ──> PaymentReconciliationRecord
```
## STEP 6.4 回调关系

`PaymentOrder ← PaymentCallbackEvent` 与 `RefundOrder ← RefundCallbackEvent` 通过 Provider 业务单号和事件标识关联；回调事件的唯一键阻断重复通知。`RefundOrder → PaymentOrder → RegistrationOrder → Appointment` 保留完整资金追溯链，成功退款累计额受收费订单金额边界约束。

## STEP 7.1 就诊关系

`ClinicRoom ← DoctorSchedule ← Appointment (BOOKED)`；一个 Appointment 最多一个 VisitEncounter，一个 VisitEncounter 最多一个 QueueTicket，并拥有多条状态历史；排班日期序号由 QueueSequenceCounter 原子管理。
# STEP 7.2 临床关系

`VisitEncounter 1—1 OutpatientMedicalRecord 1—N OutpatientMedicalRecordRevision 1—N EncounterDiagnosis`；`VisitEncounter 1—N ClinicalVitalSign`；`Patient/PatientMember 1—1 PatientAllergyProfile 1—N PatientAllergy`。病历容器的当前版本为受控引用，诊断保留版本快照。

## STEP 7.3 药品关系

`DrugCatalog 1—N HospitalDrugFormulary`；`PharmacyLocation 1—N PharmacyInventory 1—N PharmacyInventoryLot`；`VisitEncounter 1—N OutpatientPrescription 1—N PrescriptionItem`。已提交处方经 `PharmacyReview` 与 `PharmacyStockReservation` 进入 `DispensingRecord/DispensingItem`；退药单引用调剂记录和调剂项目。处方项目、审方和调剂均保留历史，不通过更新旧签署处方覆盖。

## STEP 7.4 诊断关系

`VisitEncounter 1—N ClinicalOrder 1—N ClinicalOrderItem`；LAB 医嘱关联 `LabSpecimen 1—1 LabResult 1—N LabResultItem`，结果生成 `DiagnosticReport 1—N DiagnosticReportRevision`。IMAGING 医嘱关联一个 `ImagingStudy` 和同一报告模型。危急结果项目最多一个 `CriticalResultAlert`；医生已读是独立 `ClinicalResultReview`，不更新报告正文。
