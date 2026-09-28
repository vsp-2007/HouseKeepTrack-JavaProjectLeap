package com.hotel.housekeeptrack.model;

import jakarta.persistence.*;

/**
 * Entity representing a hotel housekeeping staff member.
 */
@Entity
@Table(name = "housekeepers")
public class Housekeeper {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "phone")
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private HousekeeperStatus status = HousekeeperStatus.AVAILABLE;

    @Column(name = "active_task_count", nullable = false)
    private Integer activeTaskCount = 0;

    public Housekeeper() {
    }

    public Housekeeper(String name, String email, String phone) {
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.status = HousekeeperStatus.AVAILABLE;
        this.activeTaskCount = 0;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public HousekeeperStatus getStatus() {
        return status;
    }

    public void setStatus(HousekeeperStatus status) {
        this.status = status;
    }

    public Integer getActiveTaskCount() {
        return activeTaskCount;
    }

    public void setActiveTaskCount(Integer activeTaskCount) {
        this.activeTaskCount = activeTaskCount;
    }
}
