package com.rotacusto.controller;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotacusto.dto.request.SaveTripHistoryRequestDTO;
import com.rotacusto.dto.response.TripCostBreakdownDTO;
import com.rotacusto.dto.response.TripHistoryDetailDTO;
import com.rotacusto.dto.response.TripHistorySummaryDTO;
import com.rotacusto.entity.TripHistoryEntry;
import com.rotacusto.entity.User;
import com.rotacusto.exception.ResourceNotFoundException;
import com.rotacusto.repository.TripHistoryRepository;
import com.rotacusto.repository.UserRepository;

import jakarta.validation.Valid;

/** Autenticado (ver SecurityConfig) — salvar/ver histórico exige login. */
@RestController
@RequestMapping("/api/trip-history")
public class TripHistoryController {

    private final TripHistoryRepository tripHistoryRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;

    public TripHistoryController(TripHistoryRepository tripHistoryRepository, UserRepository userRepository,
            ObjectMapper objectMapper) {
        this.tripHistoryRepository = tripHistoryRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TripHistorySummaryDTO save(Authentication authentication, @Valid @RequestBody SaveTripHistoryRequestDTO request) {
        User usuario = resolveUsuario(authentication);
        TripHistoryEntry entry = new TripHistoryEntry();
        entry.setUsuario(usuario);
        entry.setOrigem(request.origem());
        entry.setDestino(request.destino());
        entry.setDistanciaKm(request.breakdown().distanciaKm());
        entry.setTotal(request.breakdown().total());
        entry.setCalculadoEm(Instant.now());
        entry.setBreakdownJson(writeJson(request.breakdown()));
        tripHistoryRepository.save(entry);
        return toSummary(entry);
    }

    @GetMapping
    public List<TripHistorySummaryDTO> list(Authentication authentication) {
        User usuario = resolveUsuario(authentication);
        return tripHistoryRepository.findByUsuarioOrderByCalculadoEmDesc(usuario).stream()
                .map(this::toSummary)
                .toList();
    }

    @GetMapping("/{id}")
    public TripHistoryDetailDTO detail(Authentication authentication, @PathVariable Long id) {
        User usuario = resolveUsuario(authentication);
        // findByIdAndUsuario (não só findById) garante que ninguém veja o
        // histórico de outra pessoa só adivinhando o id na URL.
        TripHistoryEntry entry = tripHistoryRepository.findByIdAndUsuario(id, usuario)
                .orElseThrow(() -> new ResourceNotFoundException("Viagem não encontrada no histórico."));
        return new TripHistoryDetailDTO(entry.getOrigem(), entry.getDestino(), entry.getCalculadoEm(),
                readJson(entry.getBreakdownJson()));
    }

    private User resolveUsuario(Authentication authentication) {
        return userRepository.findByEmailIgnoreCase(authentication.getName())
                .orElseThrow(() -> new IllegalStateException("Usuário do token não existe mais."));
    }

    private TripHistorySummaryDTO toSummary(TripHistoryEntry entry) {
        return new TripHistorySummaryDTO(entry.getId(), entry.getOrigem(), entry.getDestino(),
                entry.getDistanciaKm(), entry.getTotal(), entry.getCalculadoEm());
    }

    private String writeJson(TripCostBreakdownDTO breakdown) {
        try {
            return objectMapper.writeValueAsString(breakdown);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao serializar o breakdown da viagem.", e);
        }
    }

    private TripCostBreakdownDTO readJson(String json) {
        try {
            return objectMapper.readValue(json, TripCostBreakdownDTO.class);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Falha ao ler o breakdown salvo.", e);
        }
    }
}
