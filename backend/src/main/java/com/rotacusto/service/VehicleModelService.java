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

    private final VehicleModelRepository repository;

    public VehicleModelService(VehicleModelRepository repository) {
        this.repository = repository;
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
