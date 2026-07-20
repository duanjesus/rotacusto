package com.rotacusto.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.rotacusto.entity.FuelPrice;
import com.rotacusto.repository.FuelPriceRepository;

/**
 * Tabela pequena (27 UFs x poucos combustíveis) — o cliente carrega ela
 * inteira uma vez e faz o lookup por UF/combustível localmente, em vez de
 * expor um endpoint com filtro por query param.
 */
@Service
public class FuelPriceService {

    private final FuelPriceRepository repository;

    public FuelPriceService(FuelPriceRepository repository) {
        this.repository = repository;
    }

    public List<FuelPrice> findAll() {
        return repository.findAll();
    }
}
