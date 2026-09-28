package com.hotel.housekeeptrack.repository;

import com.hotel.housekeeptrack.model.Housekeeper;
import com.hotel.housekeeptrack.model.HousekeeperStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HousekeeperRepository extends JpaRepository<Housekeeper, Long> {
    Optional<Housekeeper> findByEmail(String email);
    boolean existsByEmail(String email);
    List<Housekeeper> findByStatus(HousekeeperStatus status);
    List<Housekeeper> findByStatusOrderByActiveTaskCountAsc(HousekeeperStatus status);
}
