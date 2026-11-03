package com.aucolher.api.event.entity;

import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class EventAttendanceId implements Serializable {
    private Long eventId;
    private Long userId;
}
