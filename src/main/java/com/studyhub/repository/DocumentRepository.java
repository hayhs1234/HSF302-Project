package com.studyhub.repository;

import com.studyhub.model.Document;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    boolean existsByTitle(String title);

    boolean existsByUserIdAndTitle(Long userId, String title);

    Optional<Document> findFirstByTitle(String title);

    Optional<Document> findFirstByUserIdAndTitle(Long userId, String title);

    List<Document> findByTitle(String title);

    List<Document> findByIsFavoriteTrue(Sort sort);

    List<Document> findBySubjectId(Long subjectId, Sort sort);

    List<Document> findByCategory(String category, Sort sort);

    List<Document> findByUserId(Long userId, Sort sort);

    @Query("SELECT d FROM Document d LEFT JOIN d.subject s WHERE " +
           "(:keyword IS NULL OR :keyword = '' " +
           " OR LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(d.author) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(d.tags) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(d.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(s.code) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:subjectId IS NULL OR s.id = :subjectId) " +
           "AND (:category IS NULL OR :category = '' OR d.category = :category) " +
           "AND (:favoriteOnly IS NULL OR :favoriteOnly = false OR d.isFavorite = true)")
    List<Document> searchDocuments(
            @Param("keyword") String keyword,
            @Param("subjectId") Long subjectId,
            @Param("category") String category,
            @Param("favoriteOnly") Boolean favoriteOnly,
            Sort sort);

    @Query("SELECT d FROM Document d LEFT JOIN d.subject s WHERE " +
           "(d.user.id = :userId OR (d.user IS NULL AND :userId = 1)) " +
           "AND (:keyword IS NULL OR :keyword = '' " +
           " OR LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(d.author) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(d.tags) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(d.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(s.code) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:subjectId IS NULL OR s.id = :subjectId) " +
           "AND (:category IS NULL OR :category = '' OR d.category = :category) " +
           "AND (:favoriteOnly IS NULL OR :favoriteOnly = false OR d.isFavorite = true)")
    List<Document> searchPersonalDocuments(
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("subjectId") Long subjectId,
            @Param("category") String category,
            @Param("favoriteOnly") Boolean favoriteOnly,
            Sort sort);

    @Query("SELECT d FROM Document d LEFT JOIN d.subject s WHERE " +
           "d.isShared = true " +
           "AND (:keyword IS NULL OR :keyword = '' " +
           " OR LOWER(d.title) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(d.author) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(d.tags) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(d.description) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(d.sharedByName) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(d.sharedNote) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(s.name) LIKE LOWER(CONCAT('%', :keyword, '%')) " +
           " OR LOWER(s.code) LIKE LOWER(CONCAT('%', :keyword, '%'))) " +
           "AND (:subjectId IS NULL OR s.id = :subjectId) " +
           "AND (:category IS NULL OR :category = '' OR d.category = :category)")
    List<Document> searchSharedDocuments(
            @Param("keyword") String keyword,
            @Param("subjectId") Long subjectId,
            @Param("category") String category,
            Sort sort);

    long countByIsFavoriteTrue();

    @Query("SELECT COUNT(d) FROM Document d WHERE (d.user.id = :userId OR (d.user IS NULL AND :userId = 1))")
    long countByUserIdOrNull(@Param("userId") Long userId);

    @Query("SELECT COUNT(d) FROM Document d WHERE (d.user.id = :userId OR (d.user IS NULL AND :userId = 1)) AND d.isFavorite = true")
    long countFavoritesByUserId(@Param("userId") Long userId);

    long countByIsSharedTrue();

    @Query("SELECT COUNT(DISTINCT d.subject.id) FROM Document d WHERE d.subject IS NOT NULL AND (d.user.id = :userId OR (d.user IS NULL AND :userId = 1))")
    long countDistinctSubjectsByUserId(@Param("userId") Long userId);

    @Query("SELECT COUNT(DISTINCT d.subject.id) FROM Document d WHERE d.subject IS NOT NULL")
    long countDistinctSubjectsWithDocuments();
}
