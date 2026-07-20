package com.rotacusto.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.rotacusto.dto.response.FuelPriceResponseDTO;
import com.rotacusto.service.FuelPriceService;

/**
 * Público, sem autenticação — mesma filosofia do resto do app. Retorna a
 * tabela inteira (pequena o bastante) pro cliente cachear e fazer lookup
 * local por UF/combustível, sugerindo um preço melhor que a média nacional
 * fixa de antes, sem travar o usuário nesse valor (ele sempre pode editar).
 */
@RestController
@RequestMapping("/api/fuel-prices")
public class FuelPriceController {

    private final FuelPriceService service;

    public FuelPriceController(FuelPriceService service) {
        this.service = service;
    }

    @GetMapping
    public List<FuelPriceResponseDTO> listAll() {
        return service.findAll().stream()
                .map(p -> new FuelPriceResponseDTO(p.getUf(), p.getTipoCombustivel(), p.getPrecoMedio(),
                        p.getSemanaReferencia()))
                .toList();
    }
}
