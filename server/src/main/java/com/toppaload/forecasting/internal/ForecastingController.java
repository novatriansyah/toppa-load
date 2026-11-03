package com.toppaload.forecasting.internal;

import com.toppaload.forecasting.AddScenarioAllocationRequest;
import com.toppaload.forecasting.CreateScenarioRequest;
import com.toppaload.forecasting.ForecastingService;
import com.toppaload.forecasting.ScenarioAllocationDto;
import com.toppaload.forecasting.ScenarioDto;
import com.toppaload.forecasting.SimulationResultDto;
import com.toppaload.identity.AuthenticatedActor;
import com.toppaload.identity.UserDirectory;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/scenarios")
class ForecastingController {

    private final ForecastingService forecastingService;
    private final UserDirectory userDirectory;

    ForecastingController(ForecastingService forecastingService, UserDirectory userDirectory) {
        this.forecastingService = forecastingService;
        this.userDirectory = userDirectory;
    }

    @GetMapping
    public ResponseEntity<List<ScenarioDto>> listScenarios() {
        return ResponseEntity.ok(forecastingService.listScenarios());
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ScenarioDto> createScenario(@Valid @RequestBody CreateScenarioRequest request) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        ScenarioDto created = forecastingService.createScenario(actor.userId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<ScenarioDto> getScenario(@PathVariable UUID id) {
        return ResponseEntity.ok(forecastingService.getScenario(id));
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<Void> deleteScenario(@PathVariable UUID id) {
        forecastingService.deleteScenario(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/allocations")
    public ResponseEntity<List<ScenarioAllocationDto>> getScenarioAllocations(@PathVariable UUID id) {
        return ResponseEntity.ok(forecastingService.getScenarioAllocations(id));
    }

    @PostMapping("/{id}/allocations")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<ScenarioAllocationDto> addScenarioAllocation(
            @PathVariable UUID id,
            @Valid @RequestBody AddScenarioAllocationRequest request
    ) {
        ScenarioAllocationDto created = forecastingService.addScenarioAllocation(id, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @DeleteMapping("/{id}/allocations/{allocationId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<Void> deleteScenarioAllocation(
            @PathVariable UUID id,
            @PathVariable UUID allocationId
    ) {
        forecastingService.deleteScenarioAllocation(id, allocationId);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/simulate")
    public ResponseEntity<SimulationResultDto> simulateScenario(
            @PathVariable UUID id,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        return ResponseEntity.ok(forecastingService.simulateScenario(id, startDate, endDate));
    }

    @PostMapping("/{id}/promote")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR')")
    public ResponseEntity<Map<String, Object>> promoteScenario(@PathVariable UUID id) {
        AuthenticatedActor actor = userDirectory.requireCurrentActor();
        int promotedCount = forecastingService.promoteScenario(id, actor.userId());
        return ResponseEntity.ok(Map.of(
                "message", "Skenario berhasil dipromosikan ke alokasi produksi.",
                "promotedAllocationsCount", promotedCount
        ));
    }
}

