package com.toppaload.allocation.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

interface AllocationRepository extends JpaRepository<AllocationEntity, UUID>, JpaSpecificationExecutor<AllocationEntity> {

    Optional<AllocationEntity> findByIdAndDeletedAtIsNull(UUID id);
}
