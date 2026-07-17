package com.rotacusto.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.rotacusto.client.OpenRouteServiceClient;
import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.RouteResult;

@Service
public class RoutingService {

    private final OpenRouteServiceClient client;

    public RoutingService(OpenRouteServiceClient client) {
        this.client = client;
    }

    /**
     * @param waypoints origem, zero ou mais paradas intermediárias, destino — nesta ordem.
     */
    public RouteResult route(List<Coordinates> waypoints) {
        return client.getRoute(waypoints);
    }

    /**
     * Rotas alternativas (Fase 10) — só origem+destino, ver
     * {@link com.rotacusto.client.OpenRouteServiceClient#getRoutes}.
     */
    public List<RouteResult> routes(List<Coordinates> waypoints) {
        return client.getRoutes(waypoints);
    }
}
