package com.rotacusto.domain;

/** Câmera de velocidade (radar fixo) encontrada ao vivo no OpenStreetMap (via Overpass API). */
public record OsmSpeedCamera(double lat, double lon) {
}
