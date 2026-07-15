package com.rotacusto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rotacusto.dto.response.VehicleModelSummaryDTO;
import com.rotacusto.entity.VehicleModel;
import com.rotacusto.entity.enums.TipoCombustivel;
import com.rotacusto.repository.VehicleModelRepository;

@ExtendWith(MockitoExtension.class)
class VehicleModelServiceTest {

    @Mock
    private VehicleModelRepository repository;

    private static VehicleModel vehicle(String marca, String modelo) {
        return vehicle(marca, modelo, null, TipoCombustivel.GASOLINA);
    }

    private static VehicleModel vehicle(String marca, String modelo, Integer ano) {
        return vehicle(marca, modelo, ano, TipoCombustivel.GASOLINA);
    }

    private static VehicleModel vehicle(String marca, String modelo, Integer ano, TipoCombustivel combustivel) {
        VehicleModel v = new VehicleModel();
        v.setMarca(marca);
        v.setModelo(modelo);
        v.setAno(ano);
        v.setTipoCombustivel(combustivel);
        return v;
    }

    @Test
    void returnsEmptyListForQueriesShorterThanTwoChars() {
        VehicleModelService service = new VehicleModelService(repository);

        assertTrue(service.searchModels("C").isEmpty());
        assertTrue(service.searchModels("").isEmpty());
        verify(repository, never()).findAll();
    }

    @Test
    void searchesByMarcaOrModeloUsingTheSameQuery() {
        VehicleModelService service = new VehicleModelService(repository);
        when(repository.findAll()).thenReturn(List.of(
                vehicle("TOYOTA", "COROLLA"),
                vehicle("HONDA", "CIVIC")));

        List<VehicleModelSummaryDTO> result = service.searchModels("Corolla");

        assertEquals(1, result.size());
        assertEquals("COROLLA", result.get(0).modelo());
    }

    @Test
    void matchesQueryWithBothMarcaAndModeloWordsTogether() {
        // Regressão: usuário digita "marca modelo" junto (ex.: "honda hrv") —
        // precisa achar mesmo sem a frase literal existir num único campo.
        VehicleModelService service = new VehicleModelService(repository);
        when(repository.findAll()).thenReturn(List.of(
                vehicle("HONDA", "HR-V"),
                vehicle("HONDA", "CIVIC"),
                vehicle("TOYOTA", "COROLLA")));

        List<VehicleModelSummaryDTO> result = service.searchModels("honda hr-v");

        assertEquals(1, result.size());
        assertEquals("HR-V", result.get(0).modelo());
    }

    @Test
    void wordOrderInQueryDoesNotMatter() {
        VehicleModelService service = new VehicleModelService(repository);
        when(repository.findAll()).thenReturn(List.of(vehicle("HONDA", "HR-V")));

        List<VehicleModelSummaryDTO> result = service.searchModels("hr-v honda");

        assertEquals(1, result.size());
    }

    @Test
    void collapsesMultipleYearsOfTheSameModelIntoOneSummary() {
        VehicleModelService service = new VehicleModelService(repository);
        when(repository.findAll()).thenReturn(List.of(
                vehicle("HONDA", "HR-V", 2023),
                vehicle("HONDA", "HR-V", 2022),
                vehicle("HONDA", "HR-V", 2021)));

        List<VehicleModelSummaryDTO> result = service.searchModels("hr-v");

        assertEquals(1, result.size(), "os 3 anos do mesmo modelo devem virar 1 resultado só no passo 1");
    }

    @Test
    void capsResultsAtTwentyForDropdownUsability() {
        VehicleModelService service = new VehicleModelService(repository);
        List<VehicleModel> manyMatches = IntStream.range(0, 50)
                .mapToObj(i -> vehicle("Marca" + i, "Modelo" + i))
                .toList();
        when(repository.findAll()).thenReturn(manyMatches);

        List<VehicleModelSummaryDTO> result = service.searchModels("ma");

        assertEquals(20, result.size());
    }

    @Test
    void findVersionsReturnsOneEntryPerYearMostRecentFirst() {
        VehicleModelService service = new VehicleModelService(repository);
        when(repository.findByMarcaIgnoreCaseAndModeloIgnoreCaseOrderByAnoDesc("HONDA", "HR-V"))
                .thenReturn(List.of(
                        vehicle("HONDA", "HR-V", 2023),
                        vehicle("HONDA", "HR-V", 2022)));

        List<VehicleModel> result = service.findVersions("HONDA", "HR-V");

        assertEquals(2, result.size());
        assertEquals(2023, result.get(0).getAno());
        assertEquals(2022, result.get(1).getAno());
    }

    @Test
    void findVersionsDropsDuplicateTrimsOfTheSameYear() {
        VehicleModelService service = new VehicleModelService(repository);
        when(repository.findByMarcaIgnoreCaseAndModeloIgnoreCaseOrderByAnoDesc("HONDA", "HR-V"))
                .thenReturn(List.of(
                        vehicle("HONDA", "HR-V", 2023),
                        vehicle("HONDA", "HR-V", 2023), // trim diferente, mesmo ano
                        vehicle("HONDA", "HR-V", 2022)));

        List<VehicleModel> result = service.findVersions("HONDA", "HR-V");

        assertEquals(2, result.size(), "não deveria haver dois '2023' indistinguíveis no dropdown");
    }

    @Test
    void findVersionsKeepsBothFuelsOfAFlexVehicleInTheSameYear() {
        VehicleModelService service = new VehicleModelService(repository);
        when(repository.findByMarcaIgnoreCaseAndModeloIgnoreCaseOrderByAnoDesc("FIAT", "MOBI"))
                .thenReturn(List.of(
                        vehicle("FIAT", "MOBI", 2023, TipoCombustivel.ETANOL),
                        vehicle("FIAT", "MOBI", 2023, TipoCombustivel.GASOLINA)));

        List<VehicleModel> result = service.findVersions("FIAT", "MOBI");

        assertEquals(2, result.size(), "flex tem 2 versões reais no mesmo ano — gasolina e etanol não são duplicatas");
        assertEquals(TipoCombustivel.GASOLINA, result.get(0).getTipoCombustivel(), "gasolina vem antes de etanol na ordem fixa");
        assertEquals(TipoCombustivel.ETANOL, result.get(1).getTipoCombustivel());
    }
}
