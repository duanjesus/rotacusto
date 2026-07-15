package com.rotacusto.domain;

import java.util.List;

public record RouteResult(double distanciaKm, double duracaoMin, List<Coordinates> geometria, List<RouteStep> passos) {
}
