package com.rotacusto.domain;

/**
 * {@code uf} é nullable — só o Photon (autocomplete) tem essa informação
 * estruturada disponível hoje (ver {@link com.rotacusto.client.PhotonClient});
 * o Nominatim continua usando o construtor de 3 argumentos, sem UF.
 */
public record AddressSuggestion(String displayName, double lat, double lon, String uf) {

    public AddressSuggestion(String displayName, double lat, double lon) {
        this(displayName, lat, lon, null);
    }
}
