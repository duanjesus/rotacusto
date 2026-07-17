package com.rotacusto.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.rotacusto.domain.Coordinates;
import com.rotacusto.dto.request.NearbyRoadAlertsRequestDTO;
import com.rotacusto.dto.request.RoadAlertRequestDTO;
import com.rotacusto.dto.request.RoadAlertVoteRequestDTO;
import com.rotacusto.dto.response.RoadAlertResponseDTO;
import com.rotacusto.entity.RoadAlert;
import com.rotacusto.service.RoadAlertService;

import jakarta.validation.Valid;

/**
 * Público, sem autenticação (Fase 6.6) — qualquer um reporta ou consulta
 * alertas de trânsito, mesma decisão de escopo do resto do app (login nunca
 * trava funcionalidade principal). Ver SecurityConfig: só /api/trip-history
 * exige token.
 */
@RestController
@RequestMapping("/api/road-alerts")
public class RoadAlertController {

    private final RoadAlertService service;

    public RoadAlertController(RoadAlertService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public RoadAlertResponseDTO report(@Valid @RequestBody RoadAlertRequestDTO request) {
        RoadAlert saved = service.report(request.tipo(), request.lat(), request.lng());
        return toDTO(saved);
    }

    /**
     * Usado pelo polling ao vivo durante a navegação (front-end) — pega
     * alertas reportados por OUTRAS pessoas depois que a viagem já tinha
     * sido calculada, diferente de {@code alertasNaRota} em
     * TripCostBreakdownDTO (que só reflete o momento do cálculo inicial).
     */
    @PostMapping("/nearby")
    public List<RoadAlertResponseDTO> nearby(@Valid @RequestBody NearbyRoadAlertsRequestDTO request) {
        Coordinates ponto = new Coordinates(request.lat(), request.lng());
        List<RoadAlert> alerts = request.raioKm() != null
                ? service.findNearPoint(ponto, request.raioKm())
                : service.findNearPoint(ponto);
        return alerts.stream().map(this::toDTO).toList();
    }

    /**
     * Confirmação/reputação (Fase 6.8) — "ainda está lá?"/"já foi resolvido", sem
     * login, um voto por dispositivo (identificado por um UUID anônimo gerado e salvo
     * localmente pelo app, ver `device_id.dart`).
     */
    @PostMapping("/{id}/vote")
    public RoadAlertResponseDTO vote(@PathVariable Long id, @Valid @RequestBody RoadAlertVoteRequestDTO request) {
        RoadAlert atualizado = service.vote(id, request.deviceId(), request.confirma());
        return toDTO(atualizado);
    }

    private RoadAlertResponseDTO toDTO(RoadAlert alert) {
        return new RoadAlertResponseDTO(alert.getId(), alert.getTipo(), alert.getLat(), alert.getLng(),
                alert.getCriadoEm(), alert.getExpiraEm());
    }
}
