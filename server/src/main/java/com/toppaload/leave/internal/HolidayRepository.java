package com.toppaload.leave.internal;

import com.toppaload.leave.HolidayScope;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface HolidayRepository extends JpaRepository<HolidayEntity, UUID> {

    Optional<HolidayEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<HolidayEntity> findByHolidayDateAndScopeAndDeletedAtIsNull(LocalDate holidayDate, HolidayScope scope);

    Optional<HolidayEntity> findFirstByHolidayDateAndScopeOrderByCreatedAtDesc(LocalDate holidayDate, HolidayScope scope);

    List<HolidayEntity> findByDeletedAtIsNullOrderByHolidayDateAsc();

    List<HolidayEntity> findByHolidayDateBetweenAndDeletedAtIsNullOrderByHolidayDateAsc(LocalDate startDate, LocalDate endDate);
}
