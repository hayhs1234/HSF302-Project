package com.studyhub.repository;

import com.studyhub.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {

    List<DocumentChunk> findByDocumentIdOrderByChunkIndexAsc(Long documentId);

    @Query("SELECT c FROM DocumentChunk c JOIN FETCH c.document d LEFT JOIN FETCH d.subject WHERE d.id IN :documentIds ORDER BY d.id ASC, c.chunkIndex ASC")
    List<DocumentChunk> findByDocumentIdIn(@Param("documentIds") Collection<Long> documentIds);

    long countByDocumentId(Long documentId);

    @Modifying
    @Transactional
    @Query("DELETE FROM DocumentChunk c WHERE c.document.id = :documentId")
    void deleteByDocumentId(@Param("documentId") Long documentId);
}
