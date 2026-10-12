package com.aucolher.api.event;

import com.aucolher.api.event.entity.Event;
import com.aucolher.api.event.entity.EventStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface EventRepository extends JpaRepository<Event, Long> {

    /** SELECT ... FOR UPDATE: usado nas operações que mexem em vagas/presenças/exclusão. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from Event e where e.id = :id")
    Optional<Event> findByIdForUpdate(@Param("id") Long id);

    List<Event> findByStatusAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(
            EventStatus status, LocalDate today);

    List<Event> findByNgoIdAndStatusAndDateGreaterThanEqualOrderByDateAscStartTimeAsc(
            Long ngoId, EventStatus status, LocalDate today);

    /** Todos os eventos da ONG (inclusive passados), sem os CANCELLED. */
    List<Event> findByNgoIdAndStatusOrderByDateDescStartTimeDesc(Long ngoId, EventStatus status);

    /** Eventos em que o usuário confirmou presença (inclusive passados e CANCELLED). */
    @Query("select e from Event e, EventAttendance a "
            + "where a.eventId = e.id and a.userId = :userId "
            + "order by e.date desc, e.startTime desc")
    List<Event> findAttendedBy(@Param("userId") Long userId);
}
