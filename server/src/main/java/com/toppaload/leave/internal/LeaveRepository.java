package com.toppaload.leave.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import java.util.Optional;
import java.util.UUID;

interface LeaveRepository extends JpaRepository<LeaveEntity, UUID>, JpaSpecificationExecutor<LeaveEntity> {

    Optional<LeaveEntity> findByIdAndDeletedAtIsNull(UUID id);
}
