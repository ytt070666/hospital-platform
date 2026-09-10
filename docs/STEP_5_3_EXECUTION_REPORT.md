# STEP 5.3 BOOKING EXPERIENCE EXECUTION REPORT

## Overall

STEP 5.3 已完成患者预约体验、后台预约管理、运行态验收与缺陷清零。后端基于最新 JAR 运行，`GET /actuator/health` 返回 `UP`；MySQL、Redis、RabbitMQ、MinIO 均为 healthy。

## Patient Web Flow

- 从公开排班进入预约页，展示真实院区、科室、门诊、号段和余号。
- 真实短信测试提供方登录后，新增本人就诊人会带回原排班、日期、号段和新成员选择。
- Web 完成 HOLD、服务端倒计时展示和确认，最终状态为 BOOKED。
- 同一预约尝试复用 Idempotency-Key；响应丢失后的重复提交返回同一预约。

## HOLD UX

倒计时锚定 `serverNow` 与 `holdExpiresAt`；刷新、页面重新可见、网络恢复及倒计时归零均会重新读取服务端详情。详情页以明确就诊人、医生和时段进行确认/取消提示，不会因网络重试创建第二个 HOLD。

## Error Handling

号源不足、停诊、失效号段、重复预约、网络失败和过期均由服务端状态或错误码决定。未认证私有访问返回 401，跨患者成员访问返回 403。患者注销会提升令牌版本并撤销刷新令牌，旧访问令牌立即不可再访问私有 API。

## MiniProgram

小程序复用真实患者、公开排班和预约 API；支持日期/号段/就诊人选择、返回路径保留、固定幂等键、预约列表分页与详情倒计时恢复。构建已通过。

WECHAT_APPID: EXTERNAL

未声明微信授权或真机验收。

## Admin

后台预约管理中心已在浏览器打开并验证：服务端分页、预约号/患者/就诊人/医生/科室/院区/门诊/日期/状态过滤、姓名 Hash 精确检索、列表脱敏、详情、库存摘要、状态历史、审计记录和工作人员取消。DataScope 与 RBAC 均由 SQL 和接口权限执行。

## Synchronization

号段模式的公开排班余号与后台库存摘要均按号段聚合；运行态验证中二者与号段库存一致。MySQL 为权威库存，Redis 仅为可重建读缓存。

## Security

- 数据库验收确认姓名、证件号码、手机号不是明文；查询 Hash 存在且不等于原值。
- 患者预约/成员 API 与后台预约详情不返回密文、Hash、加密密钥、访问令牌或刷新令牌。
- 患者加密密钥只由运行时配置提供，源码未发现硬编码密钥。
- 本次后端日志文件扫描未发现完整手机号、完整证件号、Authorization Header、Access Token 或 Refresh Token。

## E2E

- 患者短信测试登录、本人/儿童就诊人创建、默认成员、跨患者隔离、锁号、幂等重放、确认、列表、详情、注销后拦截：PASS。
- 患者 Web 从登录、创建就诊人返回、选择保留到确认预约：PASS。
- 后台浏览器列表、脱敏详情、库存/历史/审计展示：PASS。
- 后台工作人员协助取消、SQL DataScope、无权限拒绝：PASS。

## Automated Tests

Backend:

Tests run: 28
Failures: 0
Errors: 0
Skipped: 0

Package: BUILD SUCCESS

Admin:

lint: PASS
typecheck: PASS
build: PASS

Web:

lint: PASS
typecheck: PASS
build: PASS

MiniProgram:

lint: PASS
typecheck: PASS
build: PASS

## Bugs Found

- 患者注销未立即废止已签发访问令牌。
- 号段模式的排班汇总余号未随号段库存聚合。

## Bugs Fixed

- 注销原子提升患者 token version 并撤销全部刷新令牌，新增回归测试。
- 公开排班与后台库存摘要在号段模式下按号段库存聚合。

## Test Data Cleanup

已删除本次 `S53F_20260905_A` 专用排班、号段、预约、预约历史、相关审计记录、测试患者、就诊人和刷新令牌；清理后预约、排班、患者残留均为 0。

## Changed Files

- `apps/hospital-web/src/api/appointment.ts`
- `apps/hospital-web/src/api/booking-state.ts`
- `apps/hospital-web/src/views/AppointmentDetail.vue`
- `apps/hospital-web/src/views/AppointmentList.vue`
- `apps/hospital-web/src/views/BookingFlow.vue`
- `apps/hospital-web/src/views/PatientCenter.vue`
- `apps/hospital-admin/src/views/AppointmentManagement.vue`
- `apps/hospital-miniprogram/src/api/http.ts`
- `apps/hospital-miniprogram/src/api/appointment.ts`
- `apps/hospital-miniprogram/src/pages/booking/index.vue`
- `apps/hospital-miniprogram/src/pages/appointment-detail/index.vue`
- `apps/hospital-miniprogram/src/pages/appointments/index.vue`
- `apps/hospital-miniprogram/src/pages/patient/index.vue`
- `apps/hospital-miniprogram/src/pages/departments/index.vue`
- `services/hospital-backend/hospital-bootstrap/src/main/java/com/hospital/platform/appointment/AppointmentService.java`
- `services/hospital-backend/hospital-bootstrap/src/main/java/com/hospital/platform/patient/PatientCrypto.java`
- `services/hospital-backend/hospital-bootstrap/src/main/java/com/hospital/platform/patient/PatientIdentityService.java`
- `services/hospital-backend/hospital-bootstrap/src/main/java/com/hospital/platform/scheduling/SchedulingController.java`
- `services/hospital-backend/hospital-bootstrap/src/main/java/com/hospital/platform/scheduling/SchedulingService.java`
- `services/hospital-backend/hospital-iam/src/main/java/com/hospital/platform/iam/application/PatientTokenService.java`
- `services/hospital-backend/hospital-bootstrap/src/main/resources/db/migration/V15__add_patient_member_name_hash.sql`
- `services/hospital-backend/hospital-bootstrap/src/test/java/com/hospital/platform/bootstrap/PatientIdentityIntegrationTest.java`
- `services/hospital-backend/hospital-bootstrap/src/test/java/com/hospital/platform/bootstrap/SchedulingAuthorizationAndScopeIntegrationTest.java`
- `services/hospital-backend/hospital-bootstrap/src/test/java/com/hospital/platform/bootstrap/FlywayMigrationIntegrationTest.java`
- `docs/APPOINTMENT_DESIGN.md`
- `docs/APPOINTMENT_UX_FLOW.md`
- `docs/STEP_5_3_EXECUTION_REPORT.md`

## Result

STEP 5.3 PASSED
