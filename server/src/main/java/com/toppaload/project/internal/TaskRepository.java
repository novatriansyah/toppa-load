package com.toppaload.project.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface TaskRepository extends JpaRepository<TaskEntity, UUID> {

    Optional<TaskEntity> findByIdAndDeletedAtIsNull(UUID id);

    @Query("SELECT t FROM TaskEntity t JOIN FETCH t.project WHERE t.id = :id AND t.deletedAt IS NULL")
    Optional<TaskEntity> findActiveWithProject(@Param("id") UUID id);

    int countByProjectIdAndDeletedAtIsNull(UUID projectId);

    @Query("""
        SELECT t FROM TaskEntity t
        JOIN FETCH t.project p
        WHERE t.project.id = :projectId
          AND t.deletedAt IS NULL
        ORDER BY t.createdAt DESC
    """)
    List<TaskEntity> findByProjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(@Param("projectId") UUID projectId);

    @Query("""
        SELECT t FROM TaskEntity t
        JOIN FETCH t.project p
        WHERE t.project.id = :projectId
          AND t.deletedAt IS NULL
          AND t.status = :status
        ORDER BY t.createdAt DESC
    """)
    List<TaskEntity> findByProjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(
            @Param("projectId") UUID projectId,
            @Param("status") String status
    );

    @Query("""
        SELECT t FROM TaskEntity t
        JOIN FETCH t.project p
        WHERE t.project.id = :projectId
          AND t.deletedAt IS NULL
          AND (
              LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%'))
              OR (t.description IS NOT NULL AND LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%')))
          )
        ORDER BY t.createdAt DESC
    """)
    List<TaskEntity> searchByQuery(
            @Param("projectId") UUID projectId,
            @Param("q") String q
    );

    @Query("""
        SELECT t FROM TaskEntity t
        JOIN FETCH t.project p
        WHERE t.project.id = :projectId
          AND t.deletedAt IS NULL
          AND t.status = :status
          AND (
              LOWER(t.title) LIKE LOWER(CONCAT('%', :q, '%'))
              OR (t.description IS NOT NULL AND LOWER(t.description) LIKE LOWER(CONCAT('%', :q, '%')))
          )
        ORDER BY t.createdAt DESC
    """)
    List<TaskEntity> searchByQueryAndStatus(
            @Param("projectId") UUID projectId,
            @Param("q") String q,
            @Param("status") String status
    );

    @Modifying
    @Query("UPDATE TaskEntity t SET t.deletedAt = :now WHERE t.project.id = :projectId AND t.deletedAt IS NULL")
    void softDeleteAllByProjectId(@Param("projectId") UUID projectId, @Param("now") Instant now);
}
