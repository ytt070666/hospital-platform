package com.hospital.platform.payment;

import static org.assertj.core.api.Assertions.*;
import static com.hospital.platform.payment.PaymentSupport.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.hospital.platform.bootstrap.HospitalApplication;
import com.hospital.platform.appointment.AppointmentService;
import com.hospital.platform.iam.application.PatientTokenService;
import com.hospital.platform.iam.application.TokenService;
import com.hospital.platform.iam.infrastructure.UserRepository;
import com.hospital.platform.patient.PatientCrypto;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Statement;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.IntFunction;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** All SQL and HTTP use disposable MySQL + Redis, never development patient data or inventory. */
@Testcontainers
@org.junit.jupiter.api.extension.ExtendWith(org.springframework.boot.test.system.OutputCaptureExtension.class)
@ActiveProfiles("dev")
@DirtiesContext(classMode=DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(classes=HospitalApplication.class,webEnvironment=SpringBootTest.WebEnvironment.RANDOM_PORT,
  properties={"hospital.payment.worker-enabled=false","hospital.payment.callback-per-minute=10000","hospital.bootstrap.admin-username="})
class PaymentCoreIntegrationTest {
  @Container static final MySQLContainer<?> mysql=new MySQLContainer<>("mysql:8.4");
  @Container static final GenericContainer<?> redis=new GenericContainer<>("redis:7.4-alpine").withExposedPorts(6379);
  static final String KEY=randomKey();
  static String randomKey(){byte[] bytes=new byte[48];new SecureRandom().nextBytes(bytes);return Base64.getEncoder().encodeToString(bytes);}
  @DynamicPropertySource static void properties(DynamicPropertyRegistry r) {
    r.add("spring.datasource.url",mysql::getJdbcUrl);r.add("spring.datasource.username",mysql::getUsername);r.add("spring.datasource.password",mysql::getPassword);
    r.add("spring.data.redis.host",redis::getHost);r.add("spring.data.redis.port",()->redis.getMappedPort(6379));r.add("spring.data.redis.password",()->"");
    r.add("hospital.security.jwt-secret",()->KEY);r.add("hospital.patient.encryption-key",()->KEY);r.add("hospital.patient.hash-key",()->KEY);r.add("hospital.payment.test-signing-key",()->KEY);
  }
  @Autowired PaymentSupport s;
  @Autowired RegistrationOrderService orders;
  @Autowired PaymentService payments;
  @Autowired PaymentCallbackService callbacks;
  @Autowired RefundCallbackService refundCallbacks;
  @Autowired TestPaymentProvider provider;
  @Autowired PaymentTimeoutService worker;
  @Autowired RefundService refunds;
  @Autowired AppointmentService appointments;
  @Autowired LocalRegistrationPricingProvider pricing;
  @Autowired FeeRuleService fees;
  @Autowired PatientTokenService patientTokens;
  @Autowired TokenService adminTokens;
  @Autowired UserRepository users;
  @Autowired PaymentAdminQueryService adminQueries;
  @Autowired PatientCrypto crypto;
  @LocalServerPort int port;
  final HttpClient client=HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(5)).build();
  String prefix;long hospital,campus,organization,adminDepartment,department,clinic,title,outpatient,doctor,session,schedule,patient,member;

  @BeforeEach void fixture() {
    prefix="p61_"+UUID.randomUUID().toString().replace("-","").substring(0,16);
    hospital=insert("insert into hospital(hospital_code,name,published) values(?,?,1)",prefix,"支付测试医院");
    campus=insert("insert into hospital_campus(hospital_id,campus_code,name,published) values(?,?,?,1)",hospital,prefix,"测试院区");
    organization=insert("insert into sys_organization(org_code,org_name,org_type) values(?,?,'TEST')",prefix,"测试组织");
    adminDepartment=insert("insert into sys_department(organization_id,dept_code,dept_name,dept_type) values(?,?,?,'TEST')",organization,prefix,"心内行政科室");
    department=insert("insert into department(organization_id,administrative_department_id,hospital_id,campus_id,department_code,department_name,published) values(?,?,?,?,?,?,1)",organization,adminDepartment,hospital,campus,prefix,"心内科");
    clinic=insert("insert into clinic_type(code,name,published) values(?,?,1)",prefix,"支付测试门诊");
    title=insert("insert into doctor_title(code,name) values(?,?)",prefix,"测试职称");
    outpatient=insert("insert into outpatient_department(department_id,clinic_type_id,outpatient_code,name,published) values(?,?,?,?,1)",department,clinic,prefix,"测试门诊部");
    doctor=insert("insert into doctor(doctor_no,name_ciphertext,name,title_id,published) values(?,?,?,?,1)",prefix,crypto.encrypt("测试医生"),"测试医生",title);
    session=insert("insert into schedule_session_definition(hospital_id,code,name,start_time,end_time) values(?,?,?,'08:00:00','09:00:00')",hospital,prefix,"上午");
    schedule=insert("insert into doctor_schedule(schedule_no,doctor_id,campus_id,outpatient_department_id,clinic_type_id,schedule_date,session_definition_id,start_time,end_time,schedule_status,source,total_quota) values(?,?,?,?,?,date_add(current_date(),interval 2 day),?,'08:00:00','09:00:00','PUBLISHED','MANUAL',1000)",prefix,doctor,campus,outpatient,clinic,session);
    patient=insert("insert into patient(patient_no,name_ciphertext) values(?,?)",prefix,crypto.encrypt("支付测试患者"));
    member=insert("insert into patient_member(patient_id,member_no,relationship_code,name_encrypted) values(?,?,'SELF',?)",patient,prefix,crypto.encrypt("支付测试患者"));
  }
  long insert(String sql,Object...args) {
    var keys=new GeneratedKeyHolder();s.db.update(c->{var ps=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)ps.setObject(i+1,args[i]);return ps;},keys);return Objects.requireNonNull(keys.getKey()).longValue();
  }
  long appointment() { return appointment(patient,member); }
  long appointment(long p,long m) {
    return insert("insert into appointment(appointment_no,patient_id,member_id,schedule_id,status,source_client,idempotency_key,request_fingerprint,booked_at) values(?,?,?,?,'BOOKED','WEB_PATIENT',?,?,current_timestamp(3))",number("AT"),p,m,schedule,number("IK"),hash("fixture"));
  }
  FeeRuleService.Input rule(String code,long amount,Long campus,Long department,Long clinic,Long title,int priority,Instant from,Instant to,long version) {
    return new FeeRuleService.Input(code,hospital,campus,department,clinic,title,amount,"CNY",from,to,1,priority,version);
  }
  long fee(long cents) {return id(fees.save(1,null,rule(number("F"),cents,null,null,null,null,0,Instant.now().minusSeconds(60),null,0)),"id");}
  PaymentViews.Registration payable(){fee(1000);return orders.ensure(patient,appointment());}
  PaymentViews.Payment create(long id,String key){return payments.create(patient,id,key,new PaymentService.Create("TEST","TEST"));}
  TestPaymentProvider.SignedCallback event(PaymentViews.Payment pay,String status){var r=provider.simulate(pay.paymentNo(),status);return provider.signed(new PaymentProvider.Callback(number("EV"),r.paymentNo(),r.transactionId(),r.amountCent(),r.currency(),r.status()));}
  void accept(TestPaymentProvider.SignedCallback event){callbacks.accept("TEST",event.body().getBytes(StandardCharsets.UTF_8),event.headers());}
  long count(String sql,Object...args){return s.db.queryForObject(sql,Long.class,args);}
  void code(Runnable action,String code){assertThatThrownBy(action::run).isInstanceOf(PaymentException.class).hasMessage(code);}
  <T> List<T> concurrent(int count,IntFunction<T> work)throws Exception {
    try(var executor=Executors.newFixedThreadPool(24)){
      List<Callable<T>> tasks=new ArrayList<>();for(int i=0;i<count;i++){final int n=i;tasks.add(()->work.apply(n));}
      List<T> values=new ArrayList<>();for(var result:executor.invokeAll(tasks,60,TimeUnit.SECONDS))values.add(result.get());return values;
    }
  }

  @Test void moneyIsIntegralBoundedAndCnyOnly(){assertThat(Money.ofCent(1001,"CNY").display()).isEqualTo("10.01 CNY");code(()->Money.ofCent(-1,"CNY"),"INVALID_MONEY");code(()->Money.ofCent(1,"USD"),"INVALID_MONEY");code(()->Money.ofCent(Long.MAX_VALUE,"CNY"),"INVALID_MONEY");}
  @Test void jsonFractionalMoneyCannotBeSilentlyTruncated(){
    String callback="{\"eventId\":\"event\",\"paymentNo\":\"pay\",\"transactionId\":\"tx\",\"amountCent\":1000.9,\"currency\":\"CNY\",\"status\":\"SUCCESS\"}";
    code(()->provider.parseCallback(callback.getBytes(StandardCharsets.UTF_8)),"INVALID_CALLBACK_BODY");
    var r=rule(number("F"),1000,null,null,null,null,0,Instant.now(),null,0);
    String json=s.json(r).replace("\"amountCent\":1000","\"amountCent\":1000.9");
    assertThatThrownBy(()->s.json.readValue(json,FeeRuleService.Input.class)).hasRootCauseInstanceOf(PaymentException.class);
  }
  @Test void deterministicPriorityEffectiveDatesAndImmutableSnapshot(){
    long a=appointment();fee(1000);Instant now=Instant.now();
    fees.save(1,null,rule(number("F"),2000,null,department,null,null,999,now.minusSeconds(60),null,0));
    long chosen=id(fees.save(1,null,rule(number("F"),3000,null,null,clinic,null,1,now.minusSeconds(60),null,0)),"id");
    fees.save(1,null,rule(number("F"),3100,null,null,clinic,null,1,now.minusSeconds(60),null,0));
    fees.save(1,null,rule(number("F"),4000,campus,department,clinic,title,999,now.plusSeconds(60),null,0));
    fees.save(1,null,rule(number("F"),5000,campus,department,clinic,title,999,now.minusSeconds(120),now.minusSeconds(1),0));
    assertThat(pricing.quote(a,now).ruleId()).isEqualTo(chosen);
    var order=orders.ensure(patient,a);assertThat(order.amountCent()).isEqualTo(3000);
    s.db.update("update registration_fee_rule set amount_cent=9000,version=version+1 where id=?",chosen);
    assertThat(orders.ensure(patient,a).amountCent()).isEqualTo(3000);
    assertThat(s.db.queryForObject("select json_extract(pricing_snapshot,'$.amount_cent') from registration_order where id=?",String.class,order.id())).isEqualTo("3000");
    assertThat(pricing.quote(a,now.plusSeconds(61)).money().amountMinor()).isEqualTo(4000);
  }
  @Test void zeroFeeAndMissingRequiredPriceNeverCallProvider(){
    var free=orders.ensure(patient,appointment());assertThat(free.status()).isEqualTo("PAID");assertThat(free.amountCent()).isZero();
    code(()->create(free.id(),number("K")),"REGISTRATION_NOT_PAYABLE");
    assertThat(count("select count(*) from payment_order where registration_order_id=?",free.id())).isZero();
    s.db.update("update clinic_type set payment_required=1 where id=?",clinic);
    code(()->orders.ensure(patient,appointment()),"PRICE_NOT_CONFIGURED");
  }
  @Test void feeVersionAndReferenceValidationHaveDurableAudit(){
    String code=number("F");var r=rule(code,1000,null,null,null,null,0,Instant.now().minusSeconds(1),null,0);long id=id(fees.save(71,null,r),"id");
    fees.save(71,id,r);code(()->fees.save(71,id,r),"FEE_RULE_VERSION_CONFLICT");
    code(()->fees.save(71,null,rule(number("F"),10,-1L,null,null,null,0,Instant.now(),null,0)),"INVALID_FEE_REFERENCE");
    assertThat(count("select count(*) from payment_audit_event where resource_type='FEE_RULE' and resource_id=? and actor_id=71 and after_snapshot is not null",id)).isEqualTo(2);
  }
  @Test void hundredEnsureRequestsCreateExactlyOneOrder()throws Exception {
    fee(1000);long a=appointment();var ids=concurrent(100,n->orders.ensure(patient,a).id());assertThat(new HashSet<>(ids)).hasSize(1);
    assertThat(count("select count(*) from registration_order where appointment_id=?",a)).isEqualTo(1);
    assertThat(count("select count(*) from payment_audit_event where action='REGISTRATION_CREATED' and resource_id=?",ids.getFirst())).isEqualTo(1);
  }
  @Test void hundredSameKeysCreateExactlyOnePaymentAndProviderOrder()throws Exception {
    var reg=payable();var ids=concurrent(100,n->create(reg.id(),"same-key-"+reg.id()).id());assertThat(new HashSet<>(ids)).hasSize(1);
    assertThat(count("select count(*) from payment_order where registration_order_id=?",reg.id())).isEqualTo(1);
    var pay=payments.detail(patient,ids.getFirst());assertThat(count("select count(*) from test_payment_provider_order where payment_no=?",pay.paymentNo())).isEqualTo(1);
    code(()->payments.create(patient,reg.id(),"same-key-"+reg.id(),new PaymentService.Create("TEST","OTHER")),"PAYMENT_IDEMPOTENCY_CONFLICT");
  }
  @Test void hundredDifferentKeysAllowOnlyOneActivePayment()throws Exception {
    var reg=payable();var results=concurrent(100,n->{try{return create(reg.id(),"diff-"+reg.id()+"-"+n).id();}catch(PaymentException e){assertThat(e.getMessage()).isEqualTo("PAYMENT_ALREADY_ACTIVE");return -1L;}});
    assertThat(results.stream().filter(n->n>0).count()).isEqualTo(1);assertThat(count("select count(*) from payment_order where active_registration_id=?",reg.id())).isEqualTo(1);
  }
  @Test void hundredSignedHttpCallbacksHaveOneBusinessSuccess()throws Exception {
    var reg=payable();var pay=create(reg.id(),number("K"));var event=event(pay,"SUCCESS");
    concurrent(100,n->{assertThat(callbackHttp(event).status()).isEqualTo(200);return 0;});
    assertThat(payments.detail(patient,pay.id()).status()).isEqualTo("SUCCESS");assertThat(orders.detail(patient,reg.id()).status()).isEqualTo("PAID");
    assertThat(count("select count(*) from payment_transaction where payment_order_id=? and provider_status='SUCCESS'",pay.id())).isEqualTo(1);
    assertThat(count("select count(*) from payment_callback_event where payment_no=?",pay.paymentNo())).isEqualTo(1);
    code(()->create(reg.id(),number("K")),"REGISTRATION_NOT_PAYABLE");
    assertThat(s.db.queryForObject("select status from appointment where id=?",String.class,reg.appointmentId())).isEqualTo("BOOKED");
  }
  @Test void signatureRawBytesSizeTimestampNonceAndAmountCurrencyAreProtected(){
    var reg=payable();var pay=create(reg.id(),number("K"));var r=provider.simulate(pay.paymentNo(),"SUCCESS");
    var valid=provider.signed(new PaymentProvider.Callback(number("EV"),r.paymentNo(),r.transactionId(),1000,"CNY","SUCCESS"));
    code(()->callbacks.accept("TEST",(valid.body()+" ").getBytes(StandardCharsets.UTF_8),valid.headers()),"INVALID_CALLBACK_SIGNATURE");
    code(()->callbacks.accept("TEST",valid.body().getBytes(StandardCharsets.UTF_8),new PaymentProvider.Signature("1000000000",valid.headers().nonce(),valid.headers().signature())),"INVALID_CALLBACK_SIGNATURE");
    var wrongAmount=provider.signed(new PaymentProvider.Callback(number("EV"),r.paymentNo(),r.transactionId(),1,"CNY","SUCCESS"));code(()->accept(wrongAmount),"PAYMENT_AMOUNT_MISMATCH");
    var wrongCurrency=provider.signed(new PaymentProvider.Callback(number("EV"),r.paymentNo(),r.transactionId(),1000,"USD","SUCCESS"));code(()->accept(wrongCurrency),"PAYMENT_CURRENCY_MISMATCH");
    code(()->callbacks.accept("TEST",new byte[16385],valid.headers()),"CALLBACK_TOO_LARGE");
    assertThat(payments.detail(patient,pay.id()).status()).isEqualTo("PENDING");assertThat(orders.detail(patient,reg.id()).status()).isEqualTo("PENDING_PAYMENT");
    assertThat(count("select count(*) from payment_audit_event where resource_id=? and result_code in ('PAYMENT_AMOUNT_MISMATCH','PAYMENT_CURRENCY_MISMATCH')",pay.id())).isEqualTo(2);
    accept(valid);accept(valid);
    var parsed=provider.parseCallback(valid.body().getBytes(StandardCharsets.UTF_8));
    var conflict=provider.signed(new PaymentProvider.Callback(parsed.eventId(),r.paymentNo(),r.transactionId(),999,"CNY","SUCCESS"));code(()->accept(conflict),"CALLBACK_REPLAY_CONFLICT");
    var reusedNonce=provider.signed(new PaymentProvider.Callback(number("EV"),r.paymentNo(),r.transactionId(),1000,"CNY","SUCCESS"));
    try {
      var mac=javax.crypto.Mac.getInstance("HmacSHA256");mac.init(new javax.crypto.spec.SecretKeySpec(Base64.getDecoder().decode(KEY),"HmacSHA256"));
      mac.update((valid.headers().timestamp()+"\n"+valid.headers().nonce()+"\n").getBytes(StandardCharsets.UTF_8));
      String signature=Base64.getEncoder().encodeToString(mac.doFinal(reusedNonce.body().getBytes(StandardCharsets.UTF_8)));
      var nonceEvent=new TestPaymentProvider.SignedCallback(reusedNonce.body(),new PaymentProvider.Signature(valid.headers().timestamp(),valid.headers().nonce(),signature));
      code(()->accept(nonceEvent),"CALLBACK_REPLAY_CONFLICT");
    }catch(java.security.GeneralSecurityException e){throw new AssertionError(e);}
  }
  @Test void failureAllowsNewAttemptAndSuccessCannotRegress(){
    var reg=payable();var failed=create(reg.id(),number("K"));accept(event(failed,"FAILED"));
    assertThat(payments.detail(patient,failed.id()).status()).isEqualTo("FAILED");assertThat(orders.detail(patient,reg.id()).status()).isEqualTo("PENDING_PAYMENT");
    var second=create(reg.id(),number("K"));accept(event(second,"SUCCESS"));
    var old=provider.signed(new PaymentProvider.Callback(number("EV"),second.paymentNo(),null,1000,"CNY","FAILED"));accept(old);
    assertThat(payments.detail(patient,second.id()).status()).isEqualTo("SUCCESS");assertThat(count("select count(*) from payment_order where registration_order_id=? and status='SUCCESS'",reg.id())).isEqualTo(1);
  }
  @Test void duplicateProviderTransactionAndSecondBusinessSuccessAreRejected(){
    var reg=payable();var first=create(reg.id(),number("K"));accept(event(first,"SUCCESS"));
    String transaction=s.db.queryForObject("select provider_transaction_id from payment_order where id=?",String.class,first.id());
    var otherReg=orders.ensure(patient,appointment());var other=create(otherReg.id(),number("K"));
    code(()->payments.apply("TEST",new PaymentProvider.Result(other.paymentNo(),other.paymentNo(),transaction,1000,"CNY","SUCCESS"),"QUERY"),"PROVIDER_TRANSACTION_CONFLICT");
    code(()->payments.apply("TEST",new PaymentProvider.Result(first.paymentNo(),first.paymentNo(),number("TX"),1000,"CNY","SUCCESS"),"QUERY"),"DUPLICATE_PAYMENT_ANOMALY");
    assertThat(orders.detail(patient,otherReg.id()).status()).isEqualTo("PENDING_PAYMENT");
  }
  @Test void queryRecoversExternalSuccessAndLostLocalCreateWithoutNewAttempt(){
    var reg=payable();var pay=create(reg.id(),number("K"));provider.simulate(pay.paymentNo(),"SUCCESS");
    s.db.update("update payment_order set status='CREATED',provider_order_no=null where id=?",pay.id());
    // Reconstruct services: no process-local map is needed to recover the persisted provider reference.
    var freshProvider=new TestPaymentProvider(s,KEY);var env=new org.springframework.mock.env.MockEnvironment();env.setActiveProfiles("dev");
    var freshService=new PaymentService(s,orders,new PaymentProviderRegistry(List.of(freshProvider),env));
    assertThat(freshService.query(patient,pay.id()).status()).isEqualTo("SUCCESS");
    assertThat(orders.detail(patient,reg.id()).status()).isEqualTo("PAID");
    assertThat(count("select count(*) from payment_order where registration_order_id=?",reg.id())).isEqualTo(1);
  }
  @Test void unknownProviderStatusFailsClosed(){
    var reg=payable();var pay=create(reg.id(),number("K"));s.db.update("update test_payment_provider_order set status='MYSTERY' where payment_no=?",pay.paymentNo());
    code(()->payments.query(patient,pay.id()),"UNKNOWN_PROVIDER_STATUS");assertThat(orders.detail(patient,reg.id()).status()).isEqualTo("PENDING_PAYMENT");
  }
  @Test void timeoutAndCallbackQueryRacesConvergeToProviderTruth()throws Exception {
    var reg=payable();var pay=create(reg.id(),number("K"));var success=event(pay,"SUCCESS");expire(reg.id(),pay.id());
    concurrent(30,n->{if(n%3==0)accept(success);else if(n%3==1)worker.runBatch();else payments.query(patient,pay.id());return 0;});
    assertThat(payments.detail(patient,pay.id()).status()).isEqualTo("SUCCESS");assertThat(orders.detail(patient,reg.id()).status()).isEqualTo("PAID");
    long closingAppointment=appointment();s.db.update("update doctor_schedule set booked_quota=booked_quota+1 where id=(select schedule_id from appointment where id=?)",closingAppointment);var closeReg=orders.ensure(patient,closingAppointment);var closePay=create(closeReg.id(),number("K"));expire(closeReg.id(),closePay.id());worker.runBatch();worker.runBatch();
    assertThat(payments.detail(patient,closePay.id()).status()).isEqualTo("CLOSED");assertThat(orders.detail(patient,closeReg.id()).status()).isEqualTo("CLOSED");
    var forgedLate=provider.signed(new PaymentProvider.Callback(number("EV"),closePay.paymentNo(),number("TX"),1000,"CNY","SUCCESS"));code(()->accept(forgedLate),"CALLBACK_PROVIDER_DISAGREEMENT");
    assertThat(s.db.queryForObject("select status from appointment where id=?",String.class,closeReg.appointmentId())).isEqualTo("CANCELLED");
    long unpaidAppointment=appointment();s.db.update("update doctor_schedule set booked_quota=booked_quota+1 where id=(select schedule_id from appointment where id=?)",unpaidAppointment);var noAttempt=orders.ensure(patient,unpaidAppointment);s.db.update("update registration_order set payment_deadline=date_sub(now(),interval 1 second) where id=?",noAttempt.id());worker.runBatch();assertThat(orders.detail(patient,noAttempt.id()).status()).isEqualTo("CLOSED");
  }
  @Test void paymentTimeoutDoesNotCancelCheckedInAppointment(){
    fee(1000);long checkedInAppointment=appointment();s.db.update("update doctor_schedule set booked_quota=booked_quota+1 where id=?",schedule);var registration=orders.ensure(patient,checkedInAppointment);var payment=create(registration.id(),number("K"));
    insert("insert into visit_encounter(encounter_no,appointment_id,patient_id,member_id,doctor_id,department_id,outpatient_department_id,campus_id,schedule_id,encounter_date,status) values(?,?,?,?,?,?,?,?,?,current_date(),'WAITING')",number("EN"),checkedInAppointment,patient,member,doctor,department,outpatient,campus,schedule);
    expire(registration.id(),payment.id());worker.runBatch();
    assertThat(orders.detail(patient,registration.id()).status()).isEqualTo("CLOSED");assertThat(s.db.queryForObject("select status from appointment where id=?",String.class,checkedInAppointment)).isEqualTo("BOOKED");
  }
  @Test void paidCancellationCreatesOneRefundAndPreservesPaymentFact()throws Exception {
    long a=appointment();s.db.update("update doctor_schedule set booked_quota=booked_quota+1 where id=?",schedule);fee(1000);var reg=orders.ensure(patient,a);var pay=create(reg.id(),number("K"));accept(event(pay,"SUCCESS"));
    appointments.cancel(patient,a,"PATIENT_CANCEL","患者取消");
    concurrent(30,n->refunds.ensureCancelled(a,"PATIENT_CANCEL","患者取消","PATIENT",patient));
    assertThat(count("select count(*) from refund_order where appointment_id=?",a)).isEqualTo(1);
    var refund=s.db.queryForMap("select * from refund_order where appointment_id=?",a);var providerRefund=provider.simulateRefund(string(refund,"refund_no"),"SUCCESS");var callback=provider.signedRefund(new PaymentProvider.RefundCallback(number("REV"),providerRefund.refundNo(),providerRefund.providerRefundNo(),providerRefund.paymentNo(),providerRefund.amountCent(),providerRefund.currency(),providerRefund.status()));
    concurrent(100,n->{refundCallbacks.accept("TEST",callback.body().getBytes(StandardCharsets.UTF_8),callback.headers());return 0;});
    assertThat(s.db.queryForObject("select status from payment_order where id=?",String.class,pay.id())).isEqualTo("SUCCESS");
    assertThat(s.db.queryForObject("select refunded_amount_cent from registration_order where id=?",Long.class,reg.id())).isEqualTo(1000L);
    assertThat(s.db.queryForObject("select status from appointment where id=?",String.class,a)).isEqualTo("CANCELLED");
    assertThat(count("select count(*) from refund_callback_event where refund_no=? and processing_status='PROCESSED'",refund.get("refund_no"))).isEqualTo(1);
  }
  @Test void verifiedPaymentCallbackReceivedBeforeCrashRecoversFromProviderTruth(){
    var reg=payable();var pay=create(reg.id(),number("K"));provider.simulate(pay.paymentNo(),"SUCCESS");
    s.db.update("insert into payment_callback_event(provider,event_id,nonce,payment_no,signature_verified,processing_status,payload_hash,trace_id) values('TEST',?,?,?,true,'RECEIVED',?,?)",number("EV"),number("N"),pay.paymentNo(),hash("recovery"),trace());
    callbacks.recoverReceived();assertThat(payments.detail(patient,pay.id()).status()).isEqualTo("SUCCESS");assertThat(s.db.queryForObject("select count(*) from payment_callback_event where payment_no=? and processing_status='PROCESSED'",Long.class,pay.paymentNo())).isEqualTo(1);
  }
  @Test void verifiedRefundCallbackReceivedBeforeCrashRecoversFromProviderTruth(){
    long appointment=appointment();s.db.update("update doctor_schedule set booked_quota=booked_quota+1 where id=?",schedule);fee(1000);var reg=orders.ensure(patient,appointment);var pay=create(reg.id(),number("K"));accept(event(pay,"SUCCESS"));appointments.cancel(patient,appointment,"PATIENT_CANCEL","患者取消");var refund=refunds.ensureCancelled(appointment,"PATIENT_CANCEL","患者取消","PATIENT",patient);var providerRefund=provider.simulateRefund(refund.refundNo(),"SUCCESS");
    s.db.update("insert into refund_callback_event(provider,event_id,nonce,refund_no,provider_refund_no,signature_verified,processing_status,payload_hash,trace_id) values('TEST',?,?,?,?,true,'RECEIVED',?,?)",number("REV"),number("N"),refund.refundNo(),providerRefund.providerRefundNo(),hash("refund-recovery"),trace());
    refundCallbacks.recoverReceived();assertThat(refunds.patientDetail(patient,refund.id()).status()).isEqualTo("SUCCESS");assertThat(s.db.queryForObject("select refunded_amount_cent from registration_order where id=?",Long.class,reg.id())).isEqualTo(1000L);
  }
  void expire(long reg,long pay){s.db.update("update registration_order set payment_deadline=date_sub(now(),interval 1 second) where id=?",reg);s.db.update("update payment_order set expires_at=date_sub(now(),interval 1 second) where id=?",pay);}

  record Response(int status,JsonNode body) {}
  Response http(String method,String path,String token,Object body,Map<String,String> headers) {
    try {
      var builder=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+port+"/api/v1"+path)).timeout(java.time.Duration.ofSeconds(30)).header("Content-Type","application/json");
      if(token!=null)builder.header("Authorization","Bearer "+token);headers.forEach(builder::header);
      builder.method(method,body==null?HttpRequest.BodyPublishers.noBody():HttpRequest.BodyPublishers.ofString(body instanceof String raw?raw:s.json(body)));
      var response=client.send(builder.build(),HttpResponse.BodyHandlers.ofString());
      return new Response(response.statusCode(),response.body().isBlank()?s.json.nullNode():s.json.readTree(response.body()));
    }catch(Exception e){throw new AssertionError("HTTP test transport failed: "+path,e);}
  }
  Response callbackHttp(TestPaymentProvider.SignedCallback event){return http("POST","/payment/callback/TEST",null,event.body(),Map.of("X-Payment-Timestamp",event.headers().timestamp(),"X-Payment-Nonce",event.headers().nonce(),"X-Payment-Signature",event.headers().signature()));}
  JsonNode ok(String method,String path,String token,Object body){var result=http(method,path,token,body,Map.of());assertThat(result.status()).as(path).isEqualTo(200);return result.body().path("data");}
  @Test void realHttpLoginBookingFreePaidFailureRetryAndCrossClientFlow(org.springframework.boot.test.system.CapturedOutput output){
    String mobile="199"+String.format("%08d",new SecureRandom().nextInt(100_000_000));
    String code=ok("POST","/patient/auth/send-code",null,Map.of("mobile",mobile)).path("testCode").asText();
    var login=ok("POST","/patient/auth/login",null,Map.of("mobile",mobile,"code",code,"clientType","WEB_PATIENT"));String token=login.path("accessToken").asText();
    long p=patientTokens.authenticate(token).patientId();
    long m=ok("POST","/patient/members",token,Map.of("relationship","SELF","name","支付流程测试","gender","UNKNOWN","birthDate","1990-01-01")).asLong();
    long freeAppointment=bookHttp(token,m);
    var free=ok("GET","/patient/appointments/"+freeAppointment+"/registration-order",token,null);
    assertThat(free.path("status").asText()).isEqualTo("PAID");assertThat(count("select count(*) from payment_order where registration_order_id=?",free.path("id").asLong())).isZero();
    // Another member can book the same schedule without violating STEP 5's active booking key.
    long child=ok("POST","/patient/members",token,Map.of("relationship","CHILD","name","测试儿童","gender","UNKNOWN","birthDate","2020-01-01","guardianMemberId",m)).asLong();
    fee(1000);long a=bookHttp(token,child);var reg=ok("GET","/patient/appointments/"+a+"/registration-order",token,null);long regId=reg.path("id").asLong();
    assertThat(reg.path("status").asText()).isEqualTo("PENDING_PAYMENT");
    var attempt=http("POST","/patient/registration-orders/"+regId+"/payments",token,Map.of("provider","TEST","channel","TEST","amountCent",1),Map.of("Idempotency-Key",number("KEY")));
    assertThat(attempt.status()).isEqualTo(200);long failed=attempt.body().path("data").path("id").asLong();assertThat(attempt.body().path("data").path("amountCent").asLong()).isEqualTo(1000);
    sendSimulation(token,failed,"FAIL");assertThat(ok("GET","/patient/payments/"+failed,token,null).path("status").asText()).isEqualTo("FAILED");
    var retry=http("POST","/patient/registration-orders/"+regId+"/payments",token,Map.of("provider","TEST","channel","TEST"),Map.of("Idempotency-Key",number("KEY")));assertThat(retry.status()).isEqualTo(200);
    long paid=retry.body().path("data").path("id").asLong();sendSimulation(token,paid,"SUCCESS");
    assertThat(ok("GET","/patient/payments/"+paid+"/status",token,null).path("status").asText()).isEqualTo("SUCCESS");
    String miniToken=patientTokens.issue(p,"MINI_PROGRAM_PATIENT","127.0.0.1","test").accessToken();assertThat(ok("GET","/patient/registration-orders/"+regId,miniToken,null).path("status").asText()).isEqualTo("PAID");
    assertThat(ok("GET","/patient/appointments/"+a,token,null).path("status").asText()).isEqualTo("BOOKED");
    long admin=adminUser("ALL",true);String adminToken=adminTokens.issue(users.findById(admin).orElseThrow(),"ADMIN","127.0.0.1","test").accessToken();
    assertThat(ok("GET","/admin/registration-orders/"+regId,adminToken,null).path("amountCent").asLong()).isEqualTo(1000);
    String dto=ok("GET","/patient/payments/"+paid,token,null).toString();assertThat(dto).doesNotContain("Encrypted","Hash","privateKey","payload","signature",mobile,KEY);
    ok("POST","/patient/payments/"+paid+"/query",token,null);
    assertThat(http("POST","/patient/auth/logout",null,Map.of("refreshToken",login.path("refreshToken").asText()),Map.of()).status()).isEqualTo(204);
    assertThat(http("GET","/patient/payments/"+paid,token,null,Map.of()).status()).isEqualTo(401);
    assertThat(output.getAll()).doesNotContain(mobile,token,login.path("refreshToken").asText(),KEY,"Authorization: Bearer");
    String callbackStorage=s.json(s.db.queryForList("select * from payment_callback_event where payment_no=?",retry.body().path("data").path("paymentNo").asText()));
    assertThat(callbackStorage).doesNotContain("amountCent","rawBody",mobile,KEY,token);
  }
  long bookHttp(String token,long member){var hold=http("POST","/patient/appointments/holds",token,Map.of("memberId",member,"scheduleId",schedule),Map.of("Idempotency-Key",number("HOLD")));assertThat(hold.status()).isEqualTo(200);long a=hold.body().path("data").path("id").asLong();assertThat(ok("POST","/patient/appointments/"+a+"/confirm",token,null).path("status").asText()).isEqualTo("BOOKED");return a;}
  void sendSimulation(String token,long payment,String scenario){var signed=ok("POST","/patient/payments/"+payment+"/test-provider",token,Map.of("scenario",scenario));var h=signed.path("headers");var response=http("POST","/payment/callback/TEST",null,signed.path("body").asText(),Map.of("X-Payment-Timestamp",h.path("timestamp").asText(),"X-Payment-Nonce",h.path("nonce").asText(),"X-Payment-Signature",h.path("signature").asText()));assertThat(response.status()).isEqualTo(200);}
  long adminUser(String scope,boolean financial){long user=insert("insert into sys_user(username,password_hash,display_name) values(?,?,'支付测试职员')",number("U"),"not-a-login-password");long role=insert("insert into sys_role(role_code,role_name,data_scope) values(?,'支付测试角色',?)",number("R"),scope);s.db.update("insert into sys_user_role(user_id,role_id) values(?,?)",user,role);s.db.update("insert into sys_user_department(user_id,department_id) values(?,?)",user,adminDepartment);if(financial)s.db.update("insert into sys_role_permission(role_id,permission_id) select ?,id from sys_permission where permission_code like 'hospital:payment:%' or permission_code like 'hospital:fee:%'",role);return user;}
  @Test void patientOwnershipTokenIsolationAdminRbacAndSqlScope(){
    var reg=payable();var pay=create(reg.id(),number("K"));String token=patientTokens.issue(patient,"WEB_PATIENT","127.0.0.1","test").accessToken();
    long other=insert("insert into patient(patient_no,name_ciphertext) values(?,?)",number("PAT"),crypto.encrypt("其他患者"));String otherToken=patientTokens.issue(other,"WEB_PATIENT","127.0.0.1","test").accessToken();
    for(String path:List.of("/patient/payments/"+pay.id(),"/patient/registration-orders/"+reg.id(),"/patient/appointments/"+reg.appointmentId()+"/registration-order"))assertThat(http("GET",path,otherToken,null,Map.of()).status()).isEqualTo(404);
    assertThat(http("POST","/patient/registration-orders/"+reg.id()+"/payments",otherToken,Map.of("provider","TEST","channel","TEST"),Map.of("Idempotency-Key",number("K"))).status()).isEqualTo(404);
    assertThat(http("POST","/patient/payments/"+pay.id()+"/query",otherToken,null,Map.of()).status()).isEqualTo(404);
    assertThat(http("POST","/patient/payments/"+pay.id()+"/test-provider",otherToken,Map.of("scenario","SUCCESS"),Map.of()).status()).isEqualTo(404);
    long staff=adminUser("DEPARTMENT",true),operator=adminUser("ALL",false);
    String staffToken=adminTokens.issue(users.findById(staff).orElseThrow(),"ADMIN","127.0.0.1","test").accessToken();String operatorToken=adminTokens.issue(users.findById(operator).orElseThrow(),"ADMIN","127.0.0.1","test").accessToken();
    for(String path:List.of("/admin/payment-orders","/admin/registration-orders","/admin/fee-rules")){
      assertThat(http("GET",path,token,null,Map.of()).status()).isEqualTo(403);assertThat(http("GET",path,operatorToken,null,Map.of()).status()).isEqualTo(403);
    }
    assertThat(http("GET","/patient/payments/"+pay.id(),staffToken,null,Map.of()).status()).isEqualTo(403);
    assertThat(ok("GET","/admin/payment-orders",staffToken,null).path("total").asLong()).isEqualTo(1);
    assertThat(ok("GET","/admin/payment-orders/"+pay.id(),staffToken,null).path("id").asLong()).isEqualTo(pay.id());
    long otherAdmin=insert("insert into sys_department(organization_id,dept_code,dept_name,dept_type) values(?,?,'神经行政科室','TEST')",organization,number("D"));
    long otherDepartment=insert("insert into department(organization_id,administrative_department_id,hospital_id,campus_id,department_code,department_name,published) values(?,?,?,?,?,'神经科',1)",organization,otherAdmin,hospital,campus,number("D"));
    long otherOutpatient=insert("insert into outpatient_department(department_id,clinic_type_id,outpatient_code,name,published) values(?,?,?,'神经门诊',1)",otherDepartment,clinic,number("OD"));
    long otherSchedule=insert("insert into doctor_schedule(schedule_no,doctor_id,campus_id,outpatient_department_id,clinic_type_id,schedule_date,session_definition_id,start_time,end_time,schedule_status,source,total_quota) values(?,?,?,?,?,date_add(current_date(),interval 2 day),?,'08:00:00','09:00:00','PUBLISHED','MANUAL',1000)",number("SCH"),doctor,campus,otherOutpatient,clinic,session);
    long originalSchedule=schedule;schedule=otherSchedule;var otherReg=orders.ensure(patient,appointment());schedule=originalSchedule;
    var otherPay=create(otherReg.id(),number("K"));
    assertThat(ok("GET","/admin/payment-orders",staffToken,null).path("total").asLong()).isEqualTo(1);
    assertThat(ok("GET","/admin/registration-orders",staffToken,null).path("total").asLong()).isEqualTo(1);
    assertThat(http("GET","/admin/payment-orders/"+otherPay.id(),staffToken,null,Map.of()).status()).isEqualTo(403);
    assertThat(http("GET","/admin/registration-orders/"+otherReg.id(),staffToken,null,Map.of()).status()).isEqualTo(403);
    s.db.update("delete from sys_user_department where user_id=?",staff);
    assertThat(ok("GET","/admin/payment-orders",staffToken,null).path("total").asLong()).isZero();
    assertThat(http("GET","/admin/payment-orders/"+pay.id(),staffToken,null,Map.of()).status()).isEqualTo(403);
    assertThat(http("GET","/admin/registration-orders/"+reg.id(),staffToken,null,Map.of()).status()).isEqualTo(403);
  }
  @Test void providerSettlementAndCloseCompeteWithoutMixedBusinessStates()throws Exception {
    fee(1000);
    for(int iteration=0;iteration<12;iteration++){
      var reg=orders.ensure(patient,appointment());var pay=create(reg.id(),number("K"));expire(reg.id(),pay.id());
      concurrent(2,n->{if(n==0)provider.simulate(pay.paymentNo(),"SUCCESS");else payments.recover(payments.one(pay.id()),true);return 0;});
      worker.runBatch();var truth=provider.queryPayment(pay.paymentNo());
      assertThat(truth.status()).isIn("SUCCESS","CLOSED");
      assertThat(payments.detail(patient,pay.id()).status()).isEqualTo(truth.status());
      assertThat(orders.detail(patient,reg.id()).status()).isEqualTo("SUCCESS".equals(truth.status())?"PAID":"CLOSED");
    }
  }
  @Test void adminFeeHttpCrudPolicyAndOptimisticConflict(){
    long admin=adminUser("ALL",true);String token=adminTokens.issue(users.findById(admin).orElseThrow(),"ADMIN","127.0.0.1","test").accessToken();
    var input=rule(number("F"),1234,null,department,clinic,title,8,Instant.now().minusSeconds(1),null,0);
    long fee=ok("POST","/admin/fee-rules",token,input).path("id").asLong();
    ok("PUT","/admin/fee-rules/"+fee,token,input);
    assertThat(http("PUT","/admin/fee-rules/"+fee,token,input,Map.of()).status()).isEqualTo(409);
    assertThat(ok("GET","/admin/fee-rules",token,null).isArray()).isTrue();
    var disabled=new FeeRuleService.Input(input.ruleCode(),hospital,null,department,clinic,title,1234,"CNY",input.effectiveFrom(),null,0,8,1);
    ok("PUT","/admin/fee-rules/"+fee,token,disabled);
    assertThat(s.db.queryForObject("select status from registration_fee_rule where id=?",Integer.class,fee)).isZero();
    ok("PUT","/admin/fee-rules/clinic-policies/"+clinic,token,Map.of("paymentRequired",true,"version",0));
    assertThat(http("PUT","/admin/fee-rules/clinic-policies/"+clinic,token,Map.of("paymentRequired",false,"version",0),Map.of()).status()).isEqualTo(409);
    code(()->orders.ensure(patient,appointment()),"PRICE_NOT_CONFIGURED");
    assertThat(http("POST","/admin/fee-rules",token,s.json(input).replace("\"amountCent\":1234","\"amountCent\":1234.8"),Map.of()).status()).isEqualTo(400);
  }
  @Test void createFailureLeavesRecoverableRecordAndNeverDuplicatesExternalReference(){
    var reg=payable();
    var environment=new org.springframework.mock.env.MockEnvironment();environment.setActiveProfiles("dev");
    PaymentProvider lostReply=new PaymentProvider(){
      public String code(){return "TEST";}public boolean configured(){return true;}
      public Result createPayment(String no,Money money){provider.createPayment(no,money);throw new PaymentException("TEST_CREATE_REPLY_LOST",503);}
      public Result queryPayment(String no){return provider.queryPayment(no);}public Result closePayment(String no){return provider.closePayment(no);}
      public boolean verifyCallback(byte[] raw,Signature headers){return provider.verifyCallback(raw,headers);}public Callback parseCallback(byte[] raw){return provider.parseCallback(raw);}
    };
    var faulting=new PaymentService(s,orders,new PaymentProviderRegistry(List.of(lostReply),environment));String key=number("K");
    code(()->faulting.create(patient,reg.id(),key,new PaymentService.Create("TEST","TEST")),"TEST_CREATE_REPLY_LOST");
    var row=s.db.queryForMap("select * from payment_order where registration_order_id=?",reg.id());assertThat(row.get("status")).isEqualTo("CREATED");
    var recovered=payments.create(patient,reg.id(),key,new PaymentService.Create("TEST","TEST"));assertThat(recovered.id()).isEqualTo(id(row,"id"));
    assertThat(count("select count(*) from test_payment_provider_order where payment_no=?",recovered.paymentNo())).isEqualTo(1);
    assertThat(count("select count(*) from payment_order where registration_order_id=?",reg.id())).isEqualTo(1);
  }
}
