package com.rotacusto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rotacusto.domain.Coordinates;
import com.rotacusto.entity.TollPlaza;
import com.rotacusto.repository.TollPlazaRepository;

@ExtendWith(MockitoExtension.class)
class TollServiceTest {

    @Mock
    private TollPlazaRepository repository;

    @Test
    void returnsOnlyPlazasWithinDetectionRadiusOfSomeRoutePoint() {
        TollService tollService = new TollService(repository, 5.0);

        TollPlaza pertoDaRota = new TollPlaza();
        pertoDaRota.setNome("Perto");
        pertoDaRota.setLat(-22.72);
        pertoDaRota.setLng(-42.62);

        TollPlaza longeDaRota = new TollPlaza();
        longeDaRota.setNome("Longe");
        longeDaRota.setLat(-10.0); // muito distante da rota de teste
        longeDaRota.setLng(-50.0);

        when(repository.findAll()).thenReturn(List.of(pertoDaRota, longeDaRota));

        List<Coordinates> rota = List.of(
                new Coordinates(-22.9711, -43.1822),
                new Coordinates(-22.72, -42.62), // exatamente sobre pertoDaRota
                new Coordinates(-20.6633, -40.4967));

        List<TollPlaza> cruzadas = tollService.findCrossedPlazas(rota);

        assertEquals(1, cruzadas.size());
        assertEquals("Perto", cruzadas.get(0).getNome());
    }

    @Test
    void returnsEmptyListWhenNoPlazaIsWithinRadius() {
        TollService tollService = new TollService(repository, 0.1);

        TollPlaza longeDaRota = new TollPlaza();
        longeDaRota.setLat(-10.0);
        longeDaRota.setLng(-50.0);
        when(repository.findAll()).thenReturn(List.of(longeDaRota));

        List<Coordinates> rota = List.of(new Coordinates(-22.9711, -43.1822));

        assertTrue(tollService.findCrossedPlazas(rota).isEmpty());
    }
}
