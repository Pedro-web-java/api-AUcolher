package com.aucolher.api.event;

import com.aucolher.api.event.entity.EventAttendance;
import com.aucolher.api.event.entity.EventAttendanceId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface EventAttendanceRepository extends JpaRepository<EventAttendance, EventAttendanceId> {

    boolean existsByEventIdAndUserId(Long eventId, Long userId);

    long countByEventId(Long eventId);

    List<EventAttendance> findByEventIdOrderByConfirmedAtAsc(Long eventId);

    /** Retorna linhas [eventId, total] para evitar N+1 nas listas. */
    @Query("select a.eventId, count(a) from EventAttendance a where a.eventId in :ids group by a.eventId")
    List<Object[]> countByEventIds(@Param("ids") Collection<Long> ids);

    @Modifying
    @Query("delete from EventAttendance a where a.eventId = :eventId and a.userId = :userId")
    int deleteAttendance(@Param("eventId") Long eventId, @Param("userId") Long userId);

    /** Exclusão da conta: as presenças que ela confirmou (sempre em eventos de outras ONGs). */
    @Modifying
    @Query("delete from EventAttendance a where a.userId = :userId")
    int deleteAllOf(@Param("userId") Long userId);
}
