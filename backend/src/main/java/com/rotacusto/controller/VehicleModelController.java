package com.rotacusto.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rotacusto.dto.response.VehicleModelResponseDTO;
import com.rotacusto.entity.VehicleModel;
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
            @RequestParam(required = false) VehicleType tipo,
            @RequestParam(required = false) String q) {
        List<VehicleModel> resultado = (q != null) ? service.search(q) : service.list(marca, tipo);
        return resultado.stream()
                .map(mapper::toResponseDTO)
                .toList();
    }

    @GetMapping("/{id}")
    public VehicleModelResponseDTO getById(@PathVariable Long id) {
        return mapper.toResponseDTO(service.findById(id));
    }
}
