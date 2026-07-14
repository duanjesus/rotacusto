package com.rotacusto.domain;

/** Posto de combustível encontrado ao vivo no OpenStreetMap (via Overpass API). */
public record OsmFuelStation(String nome, double lat, double lon) {
}
