# 门诊签到、候诊与就诊基础（STEP 7.1）

`Appointment` 只表示预约号源，`VisitEncounter` 表示到院后的真实就诊过程；二者状态严格分离。本阶段不包含病历正文、诊断、处方、药品或检查。

`clinic_room` 是院区、楼宇、楼层、门诊科室下的真实诊室主数据；排班可通过 `clinic_room_id` 关联。`visit_encounter` 使用独立随机编号和唯一 `appointment_id`，`queue_ticket` 与 Encounter 一对一，状态历史持久化在 `visit_encounter_status_history`。

签到只允许本人 BOOKED 预约，按医院时区验证配置化窗口和支付策略。队列使用 MySQL 原子计数而非 MAX 或数据库 ID；叫号、开始接诊与完成均用条件更新。患者只能查询自己，公共大屏只显示候诊号、状态和诊室；医生由安全主体绑定的 `doctor.user_id` 解析。所有权、DataScope、操作审计和医疗访问审计在服务端执行，MySQL 是恢复后的状态权威。

STEP 7.4 的检查、检验和影像医嘱引用 Encounter 与已签署病历版本，但不会阻止 Encounter 在报告尚未出具时完成。后续报告完成不会把已完成 Encounter 恢复为接诊中。
