package com.hotel.housekeeptrack.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Entity recording supervisor inspections on rooms.
 */
@Entity
@Table(name = "inspections")
public class Inspection {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER, optional = false)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @Column(name = "supervisor_name", nullable = false)
    private String supervisorName;

    @Enumerated(EnumType.STRING)
    @Column(name = "result", nullable = false)
    private InspectionResult result;

    @Column(name = "failure_reason", length = 1000)
    private String failureReason;

    @Column(name = "inspected_at", nullable = false)
    private LocalDateTime inspectedAt = LocalDateTime.now();

    public Inspection() {
    }

    public Inspection(Room room, String supervisorName, InspectionResult result, String failureReason) {
        this.room = room;
        this.supervisorName = supervisorName;
        this.result = result;
        this.failureReason = failureReason;
        this.inspectedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Room getRoom() {
        return room;
    }

    public void setRoom(Room room) {
        this.room = room;
    }

    public String getSupervisorName() {
        return supervisorName;
    }

    public void setSupervisorName(String supervisorName) {
        this.supervisorName = supervisorName;
    }

    public InspectionResult getResult() {
        return result;
    }

    public void setResult(InspectionResult result) {
        this.result = result;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public LocalDateTime getInspectedAt() {
        return inspectedAt;
    }

    public void setInspectedAt(LocalDateTime inspectedAt) {
        this.inspectedAt = inspectedAt;
    }
}
