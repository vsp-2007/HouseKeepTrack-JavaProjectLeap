package com.hotel.housekeeptrack.dto;

import com.hotel.housekeeptrack.model.Housekeeper;
import com.hotel.housekeeptrack.model.HousekeeperStatus;

public class HousekeeperResponse {
    private Long id;
    private String name;
    private String email;
    private String phone;
    private HousekeeperStatus status;
    private Integer activeTaskCount;

    public HousekeeperResponse() {
    }

    public static HousekeeperResponse fromEntity(Housekeeper housekeeper) {
        HousekeeperResponse response = new HousekeeperResponse();
        response.setId(housekeeper.getId());
        response.setName(housekeeper.getName());
        response.setEmail(housekeeper.getEmail());
        response.setPhone(housekeeper.getPhone());
        response.setStatus(housekeeper.getStatus());
        response.setActiveTaskCount(housekeeper.getActiveTaskCount());
        return response;
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
