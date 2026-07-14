package com.rotacusto.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.rotacusto.client.NominatimClient;
import com.rotacusto.client.PhotonClient;
import com.rotacusto.domain.AddressSuggestion;

@ExtendWith(MockitoExtension.class)
class GeocodingServiceTest {

    @Mock
    private NominatimClient nominatimClient;

    @Mock
    private PhotonClient photonClient;

    @Test
    void returnsEmptyListForQueriesShorterThanThreeChars() {
        GeocodingService service = new GeocodingService(nominatimClient, photonClient);

        assertTrue(service.suggest("Ri").isEmpty());
        assertTrue(service.suggest("").isEmpty());
        assertTrue(service.suggest("  ").isEmpty());
        verify(photonClient, never()).search(anyString(), anyInt());
    }

    @Test
    void delegatesToPhotonWithLimitFiveForValidQueries() {
        GeocodingService service = new GeocodingService(nominatimClient, photonClient);
        when(photonClient.search("Rio das Ostr", 5)).thenReturn(
                List.of(new AddressSuggestion("Rio das Ostras, RJ, Brasil", -22.53, -41.94)));

        List<AddressSuggestion> result = service.suggest("Rio das Ostr");

        assertEquals(1, result.size());
        assertEquals("Rio das Ostras, RJ, Brasil", result.get(0).displayName());
    }

    @Test
    void resolveStillParsesRawCoordinatesWithoutCallingAnyClient() {
        GeocodingService service = new GeocodingService(nominatimClient, photonClient);

        var coords = service.resolve("-22.9068, -43.1729");

        assertEquals(-22.9068, coords.lat(), 0.0001);
        assertEquals(-43.1729, coords.lon(), 0.0001);
        verify(nominatimClient, never()).geocode(anyString());
    }

    @Test
    void resolveDelegatesToNominatimForFreeTextAddresses() {
        GeocodingService service = new GeocodingService(nominatimClient, photonClient);
        when(nominatimClient.geocode("Copacabana, Rio de Janeiro, RJ"))
                .thenReturn(new com.rotacusto.domain.Coordinates(-22.9711, -43.1822));

        var coords = service.resolve("Copacabana, Rio de Janeiro, RJ");

        assertEquals(-22.9711, coords.lat(), 0.0001);
    }
}
