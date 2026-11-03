package com.toppaload.project.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface ProjectRepository extends JpaRepository<ProjectEntity, UUID> {

    Optional<ProjectEntity> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    boolean existsByCodeIgnoreCaseAndIdNotAndDeletedAtIsNull(String code, UUID id);

    Optional<ProjectEntity> findByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    List<ProjectEntity> findByDeletedAtIsNullOrderByCreatedAtDesc();

    List<ProjectEntity> findByDeletedAtIsNullAndStatusOrderByCreatedAtDesc(String status);

    @Query("""
        SELECT p FROM ProjectEntity p
        WHERE p.deletedAt IS NULL
          AND (
              LOWER(p.code) LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
          )
        ORDER BY p.createdAt DESC
    """)
    List<ProjectEntity> searchByQuery(@Param("q") String q);

    @Query("""
        SELECT p FROM ProjectEntity p
        WHERE p.deletedAt IS NULL
          AND p.status = :status
          AND (
              LOWER(p.code) LIKE LOWER(CONCAT('%', :q, '%'))
              OR LOWER(p.name) LIKE LOWER(CONCAT('%', :q, '%'))
          )
        ORDER BY p.createdAt DESC
    """)
    List<ProjectEntity> searchByQueryAndStatus(@Param("q") String q, @Param("status") String status);
}
