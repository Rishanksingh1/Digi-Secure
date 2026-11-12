package com.rishank.securedigitallocker.repository;

import java.util.Optional;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.rishank.securedigitallocker.model.SharedLink;
import jakarta.persistence.LockModeType;

public interface SharedLinkRepository extends JpaRepository<SharedLink, Long> {

    Optional<SharedLink> findByToken(String token);

    long countByDocumentOwnerEmailAndRevokedFalseAndExpiryDateAfter(
            String email, java.time.LocalDateTime now);

    List<SharedLink> findByDocumentOwnerEmailOrderByCreatedDateDesc(String email);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select link from SharedLink link where link.token = :token")
    Optional<SharedLink> findByTokenForUpdate(@Param("token") String token);

    void deleteByDocumentId(Long documentId);
}
