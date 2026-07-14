package com.rotacusto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rotacusto.client.OverpassClient;
import com.rotacusto.domain.Coordinates;
import com.rotacusto.domain.OsmTollBooth;
import com.rotacusto.entity.TollPlaza;
import com.rotacusto.repository.TollPlazaRepository;

@ExtendWith(MockitoExtension.class)
class TollServiceTest {

    @Mock
    private TollPlazaRepository repository;

    @Mock
    private OverpassClient overpassClient;

    private TollService newTollService(double curatedRadius, double osmRadius) {
        return new TollService(repository, overpassClient, curatedRadius, osmRadius, 5.50, 4.00);
    }

    @Test
    void returnsOsmTollBoothsNearTheRouteWithDefaultTariffWhenNoCuratedMatch() {
        TollService tollService = newTollService(5.0, 0.5);

        when(repository.findAll()).thenReturn(List.of());
        OsmTollBooth osmBooth = new OsmTollBooth("Pedágio OSM", "RJ-106", -22.90, -42.30);
        when(overpassClient.findTollBoothsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(osmBooth));

        List<Coordinates> rota = List.of(
                new Coordinates(-22.90, -43.30),
                new Coordinates(-22.90, -42.30), // exatamente sobre o pedágio OSM
                new Coordinates(-22.90, -41.90));

        List<TollPlaza> cruzadas = tollService.findCrossedPlazas(rota);

        assertEquals(1, cruzadas.size());
        assertEquals("Pedágio OSM", cruzadas.get(0).getNome());
        assertEquals(5.50, cruzadas.get(0).getTarifaPorEixo(), 0.001);
        assertEquals(4.00, cruzadas.get(0).getTarifaMoto(), 0.001);
    }

    @Test
    void ignoresOsmTollBoothsFarFromTheRoute() {
        TollService tollService = newTollService(5.0, 0.5);

        when(repository.findAll()).thenReturn(List.of());
        OsmTollBooth longe = new OsmTollBooth("Longe", "N/D", -10.0, -50.0);
        when(overpassClient.findTollBoothsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(longe));

        List<Coordinates> rota = List.of(new Coordinates(-22.9711, -43.1822));

        assertTrue(tollService.findCrossedPlazas(rota).isEmpty());
    }

    @Test
    void clustersMultipleOsmNodesOfTheSamePhysicalPlazaIntoOne() {
        TollService tollService = newTollService(5.0, 0.5);

        when(repository.findAll()).thenReturn(List.of());
        // Mesma praça mapeada como 3 nós (uma por faixa), a poucos metros uma da outra
        List<OsmTollBooth> faixas = List.of(
                new OsmTollBooth("Praça X - faixa 1", "BR-101", -22.8780341, -43.1158327),
                new OsmTollBooth("Praça X - faixa 2", "BR-101", -22.8778092, -43.1156406),
                new OsmTollBooth("Praça X - faixa 3", "BR-101", -22.8779000, -43.1157000));
        when(overpassClient.findTollBoothsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(faixas);

        List<Coordinates> rota = List.of(new Coordinates(-22.878, -43.116));

        List<TollPlaza> cruzadas = tollService.findCrossedPlazas(rota);

        assertEquals(1, cruzadas.size());
    }

    @Test
    void enrichesOsmMatchWithCuratedTariffInsteadOfAddingASeparateEntry() {
        TollService tollService = newTollService(5.0, 0.5);

        TollPlaza curada = new TollPlaza();
        curada.setNome("Pedágio BR-101 - Rio Bonito/RJ");
        curada.setRodovia("BR-101");
        curada.setConcessionaria("Arteris Fluminense");
        curada.setLat(-22.72);
        curada.setLng(-42.62);
        curada.setTarifaPorEixo(5.20);
        curada.setTarifaMoto(3.90);
        when(repository.findAll()).thenReturn(List.of(curada));

        // Mesma praça física, coordenada real do OSM a ~3km da coordenada
        // aproximada (centro do município) do dataset curado.
        OsmTollBooth mesmaPraca = new OsmTollBooth("Pedágio (OpenStreetMap)", "BR-101", -22.74, -42.60);
        when(overpassClient.findTollBoothsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of(mesmaPraca));

        List<Coordinates> rota = List.of(new Coordinates(-22.74, -42.60));

        List<TollPlaza> cruzadas = tollService.findCrossedPlazas(rota);

        // Uma única entrada (não duas), usando a tarifa curada e a coordenada precisa do OSM
        assertEquals(1, cruzadas.size());
        assertEquals("Pedágio BR-101 - Rio Bonito/RJ", cruzadas.get(0).getNome());
        assertEquals(5.20, cruzadas.get(0).getTarifaPorEixo(), 0.001);
        assertEquals(-22.74, cruzadas.get(0).getLat(), 0.001);
    }

    @Test
    void fallsBackToCuratedOnlyWhenOverpassFails() {
        TollService tollService = newTollService(5.0, 0.5);

        TollPlaza curada = new TollPlaza();
        curada.setNome("Curada");
        curada.setLat(-22.72);
        curada.setLng(-42.62);
        when(repository.findAll()).thenReturn(List.of(curada));
        when(overpassClient.findTollBoothsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("Overpass indisponível"));

        List<Coordinates> rota = List.of(new Coordinates(-22.72, -42.62));

        List<TollPlaza> cruzadas = tollService.findCrossedPlazas(rota);

        assertEquals(1, cruzadas.size());
        assertEquals("Curada", cruzadas.get(0).getNome());
    }

    @Test
    void oneWayTollChargingOnlyTowardReferenceIsIncludedWhenHeadingThatWay() {
        TollService tollService = newTollService(5.0, 0.5);

        TollPlaza pedagio = umaPracaDeSentidoUnico(true);
        when(repository.findAll()).thenReturn(List.of(pedagio));
        when(overpassClient.findTollBoothsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("Overpass indisponível"));

        // Referência fica a leste (lon 10) do pedágio (lon 0); rota indo de oeste pra leste = rumo à referência.
        List<Coordinates> rotaIndoParaLeste = List.of(new Coordinates(0.0, -1.0), new Coordinates(0.0, 0.0), new Coordinates(0.0, 1.0));

        assertEquals(1, tollService.findCrossedPlazas(rotaIndoParaLeste).size());
    }

    @Test
    void oneWayTollChargingOnlyTowardReferenceIsExcludedWhenHeadingAway() {
        TollService tollService = newTollService(5.0, 0.5);

        TollPlaza pedagio = umaPracaDeSentidoUnico(true);
        when(repository.findAll()).thenReturn(List.of(pedagio));
        when(overpassClient.findTollBoothsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("Overpass indisponível"));

        // Rota indo de leste pra oeste = se afastando da referência (que fica a leste).
        List<Coordinates> rotaIndoParaOeste = List.of(new Coordinates(0.0, 1.0), new Coordinates(0.0, 0.0), new Coordinates(0.0, -1.0));

        assertTrue(tollService.findCrossedPlazas(rotaIndoParaOeste).isEmpty());
    }

    @Test
    void oneWayTollChargingOnlyWhenLeavingIsIncludedWhenHeadingAway() {
        TollService tollService = newTollService(5.0, 0.5);

        TollPlaza pedagio = umaPracaDeSentidoUnico(false);
        when(repository.findAll()).thenReturn(List.of(pedagio));
        when(overpassClient.findTollBoothsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("Overpass indisponível"));

        List<Coordinates> rotaIndoParaOeste = List.of(new Coordinates(0.0, 1.0), new Coordinates(0.0, 0.0), new Coordinates(0.0, -1.0));

        assertEquals(1, tollService.findCrossedPlazas(rotaIndoParaOeste).size());
    }

    @Test
    void tollWithoutDirectionConstraintAlwaysCounts() {
        TollService tollService = newTollService(5.0, 0.5);

        TollPlaza pedagio = new TollPlaza();
        pedagio.setNome("Pedágio comum");
        pedagio.setLat(0.0);
        pedagio.setLng(0.0);
        pedagio.setTarifaPorEixo(5.0);
        // refLat/refLng/cobraApenasIndo ficam nulos -> sem restrição de sentido
        when(repository.findAll()).thenReturn(List.of(pedagio));
        when(overpassClient.findTollBoothsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new RuntimeException("Overpass indisponível"));

        List<Coordinates> qualquerRota = List.of(new Coordinates(0.0, 1.0), new Coordinates(0.0, 0.0), new Coordinates(0.0, -1.0));

        assertEquals(1, tollService.findCrossedPlazas(qualquerRota).size());
    }

    private static TollPlaza umaPracaDeSentidoUnico(boolean cobraApenasIndo) {
        TollPlaza pedagio = new TollPlaza();
        pedagio.setNome("Pedágio sentido único");
        pedagio.setLat(0.0);
        pedagio.setLng(0.0);
        pedagio.setTarifaPorEixo(5.0);
        pedagio.setRefLat(0.0);
        pedagio.setRefLng(10.0); // referência a leste
        pedagio.setCobraApenasIndo(cobraApenasIndo);
        return pedagio;
    }

    @Test
    void returnsEmptyListWhenNothingIsFound() {
        TollService tollService = newTollService(0.1, 0.1);

        when(repository.findAll()).thenReturn(List.of());
        when(overpassClient.findTollBoothsInBoundingBox(anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(List.of());

        List<Coordinates> rota = List.of(new Coordinates(-22.9711, -43.1822));

        assertTrue(tollService.findCrossedPlazas(rota).isEmpty());
    }
}
