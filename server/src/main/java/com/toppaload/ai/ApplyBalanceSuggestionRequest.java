package com.toppaload.ai;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record ApplyBalanceSuggestionRequest(
        @NotNull(message = "ID alokasi wajib diisi")
        UUID allocationId,

        @NotNull(message = "ID kandidat pengguna wajib diisi")
        UUID candidateUserId,

        UUID taskId
) {}
