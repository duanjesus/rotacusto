package com.rotacusto.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rotacusto.dto.response.VehicleModelResponseDTO;
import com.rotacusto.dto.response.VehicleModelSummaryDTO;
import com.rotacusto.entity.enums.VehicleType;
import com.rotacusto.mapper.VehicleModelMapper;
import com.rotacusto.service.VehicleModelService;

@RestController
@RequestMapping("/api/vehicle-models")
public class VehicleModelController {

    private final VehicleModelService service;
    private final VehicleModelMapper mapper;

    public VehicleModelController(VehicleModelService service, VehicleModelMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @GetMapping
    public List<VehicleModelResponseDTO> list(
            @RequestParam(required = false) String marca,
            @RequestParam(required = false) VehicleType tipo) {
        return service.list(marca, tipo).stream()
                .map(mapper::toResponseDTO)
                .toList();
    }

    /** Passo 1 da escolha de veículo: marca+modelo distintos que batem com a busca. */
    @GetMapping("/search")
    public List<VehicleModelSummaryDTO> search(@RequestParam String q) {
        return service.searchModels(q);
    }

    /** Passo 2: anos/versões disponíveis do marca+modelo escolhido no passo 1. */
    @GetMapping("/versions")
    public List<VehicleModelResponseDTO> versions(@RequestParam String marca, @RequestParam String modelo) {
        return service.findVersions(marca, modelo).stream()
                .map(mapper::toResponseDTO)
                .toList();
    }

    @GetMapping("/{id}")
    public VehicleModelResponseDTO getById(@PathVariable Long id) {
        return mapper.toResponseDTO(service.findById(id));
    }
}
