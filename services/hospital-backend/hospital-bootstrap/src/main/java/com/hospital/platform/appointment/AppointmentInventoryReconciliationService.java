package com.hospital.platform.appointment;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Read-only reconciliation of MySQL appointment facts against quota counters.
 * It deliberately never repairs data: a mismatch is an operational incident that needs review.
 */
@Service
public class AppointmentInventoryReconciliationService {
  private final JdbcTemplate jdbc;

  public AppointmentInventoryReconciliationService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true)
  public List<Mismatch> inspectSchedule(long scheduleId) {
    Map<String, Object> schedule = jdbc.queryForMap(
        "select id,slot_mode slotMode,reserved_quota reservedQuota,booked_quota bookedQuota from doctor_schedule where id=? and deleted=0",
        scheduleId);
    if (((Number) schedule.get("slotMode")).intValue() == 0) {
      return mismatch("SCHEDULE", scheduleId, number(schedule, "reservedQuota"), number(schedule, "bookedQuota"), counts(scheduleId, null));
    }
    List<Mismatch> result = new ArrayList<>();
    for (Map<String, Object> slot : jdbc.queryForList(
        "select id,reserved_quota reservedQuota,booked_quota bookedQuota from schedule_slot where schedule_id=? and deleted=0 order by id",
        scheduleId)) {
      result.addAll(mismatch("SLOT", number(slot, "id"), number(slot, "reservedQuota"), number(slot, "bookedQuota"), counts(scheduleId, number(slot, "id"))));
    }
    return result;
  }

  @Transactional(readOnly = true)
  public List<Mismatch> inspectEffectiveSchedules() {
    List<Mismatch> result = new ArrayList<>();
    for (Long id : jdbc.queryForList(
        "select id from doctor_schedule where deleted=0 and schedule_status='PUBLISHED' and schedule_date>=current_date()", Long.class)) {
      result.addAll(inspectSchedule(id));
    }
    return result;
  }

  private Map<String, Object> counts(long scheduleId, Long slotId) {
    String slotClause = slotId == null ? "a.slot_id is null" : "a.slot_id=?";
    Object[] args = slotId == null ? new Object[] {scheduleId} : new Object[] {scheduleId, slotId};
    return jdbc.queryForMap("select "
            + "coalesce(sum(case when a.status='HOLDING' then 1 else 0 end),0) holdingCount,"
            + "coalesce(sum(case when a.status='BOOKED' then 1 else 0 end),0) bookedCount "
            + "from appointment a where a.schedule_id=? and a.deleted=0 and " + slotClause,
        args);
  }

  private List<Mismatch> mismatch(String scope, long inventoryId, long reservedQuota, long bookedQuota, Map<String, Object> counts) {
    long holding = number(counts, "holdingCount");
    long booked = number(counts, "bookedCount");
    return reservedQuota == holding && bookedQuota == booked
        ? List.of()
        : List.of(new Mismatch(scope, inventoryId, reservedQuota, holding, bookedQuota, booked));
  }

  private long number(Map<String, Object> row, String key) {
    return ((Number) row.get(key)).longValue();
  }

  public record Mismatch(String scope, long inventoryId, long reservedQuota, long holdingCount, long bookedQuota, long bookedCount) {}
}
