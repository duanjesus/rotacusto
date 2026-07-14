package com.rotacusto.domain;

/** Praça de pedágio encontrada ao vivo no OpenStreetMap (via Overpass API). */
public record OsmTollBooth(String nome, String rodovia, double lat, double lon) {
}
