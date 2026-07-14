package com.rotacusto.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

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
     * Busca por texto livre (autocomplete): usuário digita "marca modelo"
     * junto (ex.: "honda hrv"), então cada palavra da consulta precisa achar
     * em algum lugar de marca+modelo (AND entre palavras, não uma frase
     * literal) — senão uma busca de duas palavras nunca bate com nada.
     * Pontuação (hífen em "HR-V", etc.) é ignorada dos dois lados, já que o
     * usuário não tem como saber se o catálogo grafa com ou sem hífen/espaço.
     */
    public List<VehicleModel> search(String q) {
        if (!StringUtils.hasText(q) || q.trim().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        String[] termos = q.trim().toLowerCase().split("\\s+");
        for (int i = 0; i < termos.length; i++) {
            termos[i] = normalizeForSearch(termos[i]);
        }
        return repository.findAll().stream()
                .filter(v -> matchesAllTerms(v, termos))
                .limit(SEARCH_LIMIT)
                .toList();
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
