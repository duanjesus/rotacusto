package com.rotacusto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rotacusto.entity.FuelPrice;
import com.rotacusto.entity.enums.TipoCombustivel;
import com.rotacusto.repository.FuelPriceRepository;

@ExtendWith(MockitoExtension.class)
class FuelPriceServiceTest {

    @Mock
    private FuelPriceRepository repository;

    private FuelPrice preco(String uf, String municipio, TipoCombustivel tipo, double valor) {
        FuelPrice p = new FuelPrice();
        p.setUf(uf);
        p.setMunicipio(municipio);
        p.setTipoCombustivel(tipo);
        p.setPrecoMedio(valor);
        p.setSemanaReferencia("2026-07-26");
        return p;
    }

    @Test
    void findsSpecificMunicipioPriceWhenAvailable() {
        FuelPriceService service = new FuelPriceService(repository);
        when(repository.findAll()).thenReturn(List.of(
                preco("RJ", null, TipoCombustivel.GASOLINA, 6.71),
                preco("RJ", "Rio de Janeiro", TipoCombustivel.GASOLINA, 6.52)));

        Optional<Double> resultado = service.buscarPrecoPorMunicipio("Rio de Janeiro", "RJ", TipoCombustivel.GASOLINA);

        assertTrue(resultado.isPresent());
        assertEquals(6.52, resultado.get());
    }

    @Test
    void matchesMunicipioIgnoringAccentAndCase() {
        // Fonte ANP salva sem acento ("Sao Paulo") — o município buscado
        // (vindo de reverse geocoding, com acento) precisa bater mesmo assim.
        FuelPriceService service = new FuelPriceService(repository);
        when(repository.findAll()).thenReturn(List.of(
                preco("SP", "Sao Paulo", TipoCombustivel.GASOLINA, 6.39)));

        Optional<Double> resultado = service.buscarPrecoPorMunicipio("São Paulo", "SP", TipoCombustivel.GASOLINA);

        assertTrue(resultado.isPresent());
        assertEquals(6.39, resultado.get());
    }

    @Test
    void fallsBackToUfAverageWhenNoSpecificMunicipioRow() {
        FuelPriceService service = new FuelPriceService(repository);
        when(repository.findAll()).thenReturn(List.of(
                preco("RJ", null, TipoCombustivel.GASOLINA, 6.71),
                preco("RJ", "Rio de Janeiro", TipoCombustivel.GASOLINA, 6.52)));

        Optional<Double> resultado = service.buscarPrecoPorMunicipio("Cidade Pequena Sem Dado", "RJ", TipoCombustivel.GASOLINA);

        assertTrue(resultado.isPresent());
        assertEquals(6.71, resultado.get());
    }

    @Test
    void returnsEmptyWhenUfHasNoDataAtAll() {
        FuelPriceService service = new FuelPriceService(repository);
        when(repository.findAll()).thenReturn(List.of(
                preco("RJ", null, TipoCombustivel.GASOLINA, 6.71)));

        Optional<Double> resultado = service.buscarPrecoPorMunicipio("Qualquer", "SP", TipoCombustivel.GASOLINA);

        assertTrue(resultado.isEmpty());
    }

    @Test
    void returnsEmptyWhenUfIsNull() {
        FuelPriceService service = new FuelPriceService(repository);

        Optional<Double> resultado = service.buscarPrecoPorMunicipio("Qualquer", null, TipoCombustivel.GASOLINA);

        assertTrue(resultado.isEmpty());
    }
}
