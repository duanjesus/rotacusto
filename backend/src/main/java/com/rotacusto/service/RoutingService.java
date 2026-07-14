package com.rotacusto.service;

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

    public RouteResult route(Coordinates origin, Coordinates destination) {
        return client.getRoute(origin, destination);
    }
}
