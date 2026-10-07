package com.rotacusto.service;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.rotacusto.dto.request.VehicleReportRequestDTO;
import com.rotacusto.entity.VehicleReport;
import com.rotacusto.repository.VehicleReportRepository;

/**
 * Recebe pedidos de "não achei meu veículo no catálogo" direto do app (sem
 * depender do usuário saber usar GitHub — pedido explícito do usuário,
 * poucas pessoas usando o app têm familiaridade com isso).
 *
 * Grava no banco: em produção o disco do servidor é efêmero (some a cada
 * deploy ou quando a instância dorme), então um arquivo local perderia os
 * relatos. Pra revisar, consultar a tabela {@code vehicle_reports}.
 */
@Service
public class VehicleReportService {

    private final VehicleReportRepository repository;

    public VehicleReportService(VehicleReportRepository repository) {
        this.repository = repository;
    }

    public void report(VehicleReportRequestDTO request) {
        VehicleReport relato = new VehicleReport();
        relato.setTipo(request.tipo());
        relato.setDescricao(request.descricao());
        relato.setCriadoEm(Instant.now());
        repository.save(relato);
    }
}
