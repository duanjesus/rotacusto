package com.rotacusto.dto.response;

public record RouteStepDTO(String instrucao, double distanciaM, double duracaoS, int wayPointInicio, int wayPointFim) {
}
