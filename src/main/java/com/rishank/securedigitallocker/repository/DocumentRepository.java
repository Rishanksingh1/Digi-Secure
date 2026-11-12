package com.rishank.securedigitallocker.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.rishank.securedigitallocker.model.Document;

public interface DocumentRepository extends JpaRepository<Document, Long> {

    List<Document> findByOwnerEmailOrderByUploadDateDesc(String email);

        List<Document> findByOwnerEmailAndFileNameContainingIgnoreCaseOrderByUploadDateDesc(
            String email, String fileName);

    Optional<Document> findByIdAndOwnerEmail(Long id, String email);

    long countByOwnerEmail(String email);
}
