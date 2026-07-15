package com.rotacusto.service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rotacusto.dto.request.VehicleReportRequestDTO;

/**
 * Recebe pedidos de "não achei meu veículo no catálogo" direto do app (sem
 * depender do usuário saber usar GitHub — pedido explícito do usuário,
 * poucas pessoas usando o app têm familiaridade com isso).
 *
 * Grava num arquivo local (uma linha JSON por pedido) em vez de gravar no
 * H2: o banco é só em memória ({@code jdbc:h2:mem}, ver application.yml) e
 * reseta a cada restart do back-end — um pedido salvo lá desapareceria na
 * próxima vez que o servidor subisse. O arquivo (`.log`, já no .gitignore)
 * é simples de eu conferir manualmente depois, proporcional ao tamanho de
 * um projeto de um desenvolvedor só.
 */
@Service
public class VehicleReportService {

    private final ObjectMapper objectMapper;
    private final Path arquivo;

    public VehicleReportService(
            ObjectMapper objectMapper,
            @Value("${rotacusto.vehicle-reports.file:vehicle-reports.log}") String caminhoArquivo) {
        this.objectMapper = objectMapper;
        this.arquivo = Path.of(caminhoArquivo);
    }

    public synchronized void report(VehicleReportRequestDTO request) {
        Map<String, Object> linha = new LinkedHashMap<>();
        linha.put("timestamp", Instant.now().toString());
        linha.put("tipo", request.tipo());
        linha.put("descricao", request.descricao());

        try {
            String json = objectMapper.writeValueAsString(linha);
            Files.writeString(arquivo, json + System.lineSeparator(),
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gravar relato de veículo faltando", e);
        }
    }
}
