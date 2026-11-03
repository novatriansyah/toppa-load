package com.toppaload.identity.internal;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface UserRepository extends JpaRepository<UserEntity, UUID> {

    Optional<UserEntity> findByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    Optional<UserEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<UserEntity> findByIdAndDeletedAtIsNullAndActiveTrue(UUID id);

    List<UserEntity> findByDeletedAtIsNullAndActiveTrueOrderByFullNameAsc();

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNull(String email);

    boolean existsByEmailIgnoreCaseAndDeletedAtIsNullAndIdNot(String email, UUID id);
}
