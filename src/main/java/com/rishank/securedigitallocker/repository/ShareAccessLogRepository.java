package com.rishank.securedigitallocker.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rishank.securedigitallocker.model.ShareAccessLog;

public interface ShareAccessLogRepository extends JpaRepository<ShareAccessLog, Long> {

    boolean existsBySharedLinkIdAndIpAddress(Long sharedLinkId, String ipAddress);

    long countBySharedLinkId(Long sharedLinkId);

    List<ShareAccessLog> findTop100BySharedLinkDocumentOwnerEmailOrderByAccessedAtDesc(String email);

    void deleteBySharedLinkDocumentId(Long documentId);
}
