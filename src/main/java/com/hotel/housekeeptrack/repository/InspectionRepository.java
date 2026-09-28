package com.hotel.housekeeptrack.repository;

import com.hotel.housekeeptrack.model.Inspection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface InspectionRepository extends JpaRepository<Inspection, Long> {
    List<Inspection> findByRoomIdOrderByInspectedAtDesc(Long roomId);
    Optional<Inspection> findTopByRoomIdOrderByInspectedAtDesc(Long roomId);
}
