package com.rotacusto.domain;

/** Restaurante encontrado ao vivo no OpenStreetMap (via Overpass API). */
public record OsmRestaurant(String nome, double lat, double lon) {
}
