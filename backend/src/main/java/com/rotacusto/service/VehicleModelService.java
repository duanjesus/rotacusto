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
     * Busca por texto livre (autocomplete): acha em marca OU modelo,
     * limitada pra caber num dropdown de sugestões.
     */
    public List<VehicleModel> search(String q) {
        if (!StringUtils.hasText(q) || q.trim().length() < MIN_QUERY_LENGTH) {
            return List.of();
        }
        return repository.findByMarcaContainingIgnoreCaseOrModeloContainingIgnoreCase(q, q).stream()
                .limit(SEARCH_LIMIT)
                .toList();
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
