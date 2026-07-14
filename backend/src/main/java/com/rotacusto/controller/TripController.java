package com.rotacusto.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rotacusto.dto.request.TripEstimateRequestDTO;
import com.rotacusto.dto.response.TripCostBreakdownDTO;
import com.rotacusto.service.TripEstimationService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/trips")
public class TripController {

    private final TripEstimationService service;

    public TripController(TripEstimationService service) {
        this.service = service;
    }

    @PostMapping("/estimate")
    public TripCostBreakdownDTO estimate(@Valid @RequestBody TripEstimateRequestDTO request) {
        return service.estimate(request);
    }
}
