package com.rotacusto.domain;

import com.rotacusto.entity.enums.RadarType;

/**
 * Radar fixo encontrado ao vivo no OpenStreetMap (via Overpass API) — velocidade
 * (tag {@code highway=speed_camera}) ou avanço de sinal (várias tags/relations
 * diferentes, ver {@code OverpassClient.findRadarsInBoundingBox}). Infraestrutura
 * permanente: sem relato de usuário, sem expiração, sem voto.
 */
public record OsmRadar(RadarType tipo, double lat, double lon) {
}
