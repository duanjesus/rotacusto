package com.rotacusto.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.rotacusto.dto.response.AddressSuggestionResponseDTO;
import com.rotacusto.service.GeocodingService;

@RestController
@RequestMapping("/api/geocoding")
public class GeocodingController {

    private final GeocodingService geocodingService;

    public GeocodingController(GeocodingService geocodingService) {
        this.geocodingService = geocodingService;
    }

    @GetMapping("/suggest")
    public List<AddressSuggestionResponseDTO> suggest(@RequestParam String q) {
        return geocodingService.suggest(q).stream()
                .map(s -> new AddressSuggestionResponseDTO(s.displayName(), s.lat(), s.lon()))
                .toList();
    }
}
