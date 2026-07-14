package com.rotacusto.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.rotacusto.dto.response.VehicleModelSummaryDTO;
import com.rotacusto.entity.VehicleModel;
import com.rotacusto.entity.enums.VehicleType;
import com.rotacusto.exception.ResourceNotFoundException;
import com.rotacusto.repository.VehicleModelRepository;

@Service
public class VehicleModelService {

    private static final int SEARCH_LIMIT = 20;
    private static final int MIN_QUERY_LENGTH = 2;

    private final VehicleModelRepository repository;

    public VehicleModelService(VehicleModelRepository repository) {
        this.repository = repository;
    }

    /**
     * Busca por texto livre (autocomplete, passo 1 — escolher marca+modelo,
     * sem ano ainda): usuário digita "marca modelo" junto (ex.: "honda hrv"),
     * então cada palavra da consulta precisa achar em algum lugar de
     * marca+modelo (AND entre palavras, não uma frase literal) — senão uma
     * busca de duas palavras nunca bate com nada. Pontuação (hífen em
     * "HR-V", etc.) é ignorada dos dois lados. O catálogo tem uma linha por
     * ano/versão do mesmo modelo — aqui devolve só pares marca+modelo
     * distintos (o passo 2, {@link #findVersions}, lista os anos).
     */
    public List<VehicleModelSummaryDTO> searchModels(String q) {
        if (!StringUtils.hasText(q) || q.trim().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        String[] termos = q.trim().toLowerCase().split("\\s+");
        for (int i = 0; i < termos.length; i++) {
            termos[i] = normalizeForSearch(termos[i]);
        }

        Map<String, VehicleModelSummaryDTO> distintos = new LinkedHashMap<>();
        for (VehicleModel v : repository.findAll()) {
            if (distintos.size() >= SEARCH_LIMIT) {
                break;
            }
            if (!matchesAllTerms(v, termos)) {
                continue;
            }
            String chave = v.getMarca().toLowerCase() + "|" + v.getModelo().toLowerCase();
            distintos.putIfAbsent(chave, new VehicleModelSummaryDTO(v.getMarca(), v.getModelo()));
        }
        return List.copyOf(distintos.values());
    }

    /**
     * Passo 2 — lista as versões/anos disponíveis de um marca+modelo já
     * escolhido no passo 1, mais recente primeiro. Quando o mesmo ano tem
     * mais de uma versão/trim com consumo diferente (o catálogo não guarda o
     * texto da versão), fica só a primeira encontrada — evitaria duas
     * entradas de "2023" indistinguíveis no dropdown de ano.
     */
    public List<VehicleModel> findVersions(String marca, String modelo) {
        Map<Integer, VehicleModel> porAno = new LinkedHashMap<>();
        for (VehicleModel v : repository.findByMarcaIgnoreCaseAndModeloIgnoreCaseOrderByAnoDesc(marca, modelo)) {
            porAno.putIfAbsent(v.getAno(), v);
        }
        return List.copyOf(porAno.values());
    }

    private boolean matchesAllTerms(VehicleModel v, String[] termos) {
        String haystack = normalizeForSearch(v.getMarca() + " " + v.getModelo());
        for (String termo : termos) {
            if (!haystack.contains(termo)) {
                return false;
            }
        }
        return true;
    }

    private static String normalizeForSearch(String s) {
        return s.toLowerCase().replaceAll("[^a-z0-9]+", "");
    }

    public List<VehicleModel> list(String marca, VehicleType tipo) {
        boolean hasMarca = StringUtils.hasText(marca);
        if (hasMarca && tipo != null) {
            return repository.findByMarcaContainingIgnoreCaseAndTipo(marca, tipo);
        }
        if (hasMarca) {
            return repository.findByMarcaContainingIgnoreCase(marca);
        }
        if (tipo != null) {
            return repository.findByTipo(tipo);
        }
        return repository.findAll();
    }

    public VehicleModel findById(Long id) {
        return repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Modelo de veículo não encontrado: " + id));
    }
}
