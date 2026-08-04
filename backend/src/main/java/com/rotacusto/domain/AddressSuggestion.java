package com.rotacusto.domain;

/**
 * {@code uf}/{@code municipio} são nullable — só o Photon (autocomplete) tem
 * essa informação estruturada disponível hoje (ver
 * {@link com.rotacusto.client.PhotonClient}); o Nominatim continua usando o
 * construtor de 3 argumentos, sem UF/município.
 */
public record AddressSuggestion(String displayName, double lat, double lon, String uf, String municipio) {

    public AddressSuggestion(String displayName, double lat, double lon) {
        this(displayName, lat, lon, null, null);
    }
}
