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

import com.rotacusto.entity.VehicleModel;
import com.rotacusto.repository.VehicleModelRepository;

@ExtendWith(MockitoExtension.class)
class VehicleModelServiceTest {

    @Mock
    private VehicleModelRepository repository;

    private static VehicleModel vehicle(String marca, String modelo) {
        VehicleModel v = new VehicleModel();
        v.setMarca(marca);
        v.setModelo(modelo);
        return v;
    }

    @Test
    void returnsEmptyListForQueriesShorterThanTwoChars() {
        VehicleModelService service = new VehicleModelService(repository);

        assertTrue(service.search("C").isEmpty());
        assertTrue(service.search("").isEmpty());
        verify(repository, never()).findAll();
    }

    @Test
    void searchesByMarcaOrModeloUsingTheSameQuery() {
        VehicleModelService service = new VehicleModelService(repository);
        when(repository.findAll()).thenReturn(List.of(
                vehicle("TOYOTA", "COROLLA"),
                vehicle("HONDA", "CIVIC")));

        List<VehicleModel> result = service.search("Corolla");

        assertEquals(1, result.size());
        assertEquals("COROLLA", result.get(0).getModelo());
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

        List<VehicleModel> result = service.search("honda hr-v");

        assertEquals(1, result.size());
        assertEquals("HR-V", result.get(0).getModelo());
    }

    @Test
    void wordOrderInQueryDoesNotMatter() {
        VehicleModelService service = new VehicleModelService(repository);
        when(repository.findAll()).thenReturn(List.of(vehicle("HONDA", "HR-V")));

        List<VehicleModel> result = service.search("hr-v honda");

        assertEquals(1, result.size());
    }

    @Test
    void capsResultsAtTwentyForDropdownUsability() {
        VehicleModelService service = new VehicleModelService(repository);
        List<VehicleModel> manyMatches = IntStream.range(0, 50)
                .mapToObj(i -> vehicle("Marca" + i, "Modelo" + i))
                .toList();
        when(repository.findAll()).thenReturn(manyMatches);

        List<VehicleModel> result = service.search("ma");

        assertEquals(20, result.size());
    }
}
