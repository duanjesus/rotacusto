package com.rotacusto.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rotacusto.dto.request.VehicleReportRequestDTO;
import com.rotacusto.service.VehicleReportService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/vehicle-reports")
public class VehicleReportController {

    private final VehicleReportService service;

    public VehicleReportController(VehicleReportService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<Void> report(@Valid @RequestBody VehicleReportRequestDTO request) {
        service.report(request);
        return ResponseEntity.status(HttpStatus.CREATED).build();
    }
}
