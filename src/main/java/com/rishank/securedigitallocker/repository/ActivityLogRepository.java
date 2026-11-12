package com.rishank.securedigitallocker.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rishank.securedigitallocker.model.ActivityLog;

public interface ActivityLogRepository extends JpaRepository<ActivityLog, Long> {

    List<ActivityLog> findTop20ByUserEmailOrderByTimestampDesc(String email);

    Optional<ActivityLog> findTopByOrderByIdDesc();

    List<ActivityLog> findAllByOrderByIdAsc();

    void deleteByDocumentId(Long documentId);
}