package com.rotacusto.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.rotacusto.domain.Coordinates;
import com.rotacusto.dto.request.NearbyTrafficRequestDTO;
import com.rotacusto.dto.request.TrafficReportRequestDTO;
import com.rotacusto.dto.response.TrafficReportResponseDTO;
import com.rotacusto.entity.TrafficReport;
import com.rotacusto.service.TrafficReportService;

import jakarta.validation.Valid;

/**
 * Público, sem autenticação (Fase 6.7) — mesma decisão de escopo do resto do
 * app (login nunca trava funcionalidade principal). {@code report} é chamado
 * automaticamente pelo app ao detectar trânsito lento, não por uma ação
 * manual do usuário (diferente de RoadAlertController).
 */
@RestController
@RequestMapping("/api/traffic-reports")
public class TrafficReportController {

    private final TrafficReportService service;

    public TrafficReportController(TrafficReportService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TrafficReportResponseDTO report(@Valid @RequestBody TrafficReportRequestDTO request) {
        TrafficReport saved = service.report(request.severidade(), request.lat(), request.lng());
        return toDTO(saved);
    }

    /**
     * Usado pelo polling ao vivo durante a navegação (front-end) — pega
     * relatos de trânsito de OUTRAS pessoas depois que a viagem já tinha
     * sido calculada, diferente de {@code trafegoNaRota} em
     * TripCostBreakdownDTO (que só reflete o momento do cálculo inicial).
     */
    @PostMapping("/nearby")
    public List<TrafficReportResponseDTO> nearby(@Valid @RequestBody NearbyTrafficRequestDTO request) {
        Coordinates ponto = new Coordinates(request.lat(), request.lng());
        List<TrafficReport> relatos = request.raioKm() != null
                ? service.findNearPoint(ponto, request.raioKm())
                : service.findNearPoint(ponto);
        return relatos.stream().map(this::toDTO).toList();
    }

    private TrafficReportResponseDTO toDTO(TrafficReport relato) {
        return new TrafficReportResponseDTO(relato.getId(), relato.getSeveridade(), relato.getLat(), relato.getLng(),
                relato.getCriadoEm(), relato.getExpiraEm());
    }
}
