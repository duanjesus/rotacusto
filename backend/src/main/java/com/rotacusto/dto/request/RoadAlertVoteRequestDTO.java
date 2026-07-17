package com.rotacusto.dto.request;

import jakarta.validation.constraints.NotNull;

public record RoadAlertVoteRequestDTO(
        @NotNull String deviceId,
        @NotNull Boolean confirma) {
}
