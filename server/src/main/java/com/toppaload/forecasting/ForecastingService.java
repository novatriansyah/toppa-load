package com.toppaload.forecasting;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface ForecastingService {

    List<ScenarioDto> listScenarios();

    ScenarioDto createScenario(UUID creatorId, CreateScenarioRequest request);

    ScenarioDto getScenario(UUID id);

    List<ScenarioAllocationDto> getScenarioAllocations(UUID scenarioId);

    ScenarioAllocationDto addScenarioAllocation(UUID scenarioId, AddScenarioAllocationRequest request);

    void deleteScenarioAllocation(UUID scenarioId, UUID allocationId);

    SimulationResultDto simulateScenario(UUID scenarioId, LocalDate startDate, LocalDate endDate);

    int promoteScenario(UUID scenarioId, UUID promoterId);
}

