import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:latlong2/latlong.dart';

import '../../domain/models/trip_cost_breakdown.dart';

class TripMap extends StatelessWidget {
  final TripCostBreakdown? breakdown;
  /// Dono é de quem precisa mover a câmera de fora (ex.: seguir o GPS ao
  /// vivo na tela de navegação) — sem isso, `TripMap` continua stateless.
  final MapController? mapController;
  /// Posição ao vivo do usuário (Fase 6, navegação) — null fora da tela de
  /// navegação, sem nenhum efeito no uso normal (resultado da viagem).
  final LatLng? posicaoAtual;

  const TripMap({super.key, this.breakdown, this.mapController, this.posicaoAtual});

  @override
  Widget build(BuildContext context) {
    final route = breakdown?.geometriaRota ?? const <LatLng>[];
    final center = posicaoAtual ?? (route.isNotEmpty ? route[route.length ~/ 2] : const LatLng(-22.9068, -43.1729));
    final isDark = Theme.of(context).brightness == Brightness.dark;
    // Tiles CartoDB (estilo mais limpo, com variante clara/escura) em vez do
    // OSM padrão, mais carregado visualmente — requer atribuição própria
    // (RichAttributionWidget abaixo), além da do OSM (fonte dos dados).
    final tileUrl = isDark
        ? 'https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png'
        : 'https://{s}.basemaps.cartocdn.com/light_all/{z}/{x}/{y}{r}.png';

    return Container(
      color: Theme.of(context).colorScheme.surfaceContainerLow,
      child: FlutterMap(
        mapController: mapController,
        options: MapOptions(
          initialCenter: center,
          initialZoom: route.isNotEmpty ? 7 : 10,
        ),
        children: [
          TileLayer(
            urlTemplate: tileUrl,
            userAgentPackageName: 'com.rotacusto.app',
            subdomains: const ['a', 'b', 'c', 'd'],
          ),
          if (route.isNotEmpty)
            PolylineLayer(polylines: [
              Polyline(points: route, strokeWidth: 4, color: Theme.of(context).colorScheme.primary),
            ]),
          if (breakdown != null)
            MarkerLayer(
              markers: [
                ...breakdown!.pedagiosNaRota.map(
                  (p) => Marker(
                    point: LatLng(p.lat, p.lng),
                    width: 36,
                    height: 36,
                    child: Tooltip(
                      message: '${p.nome}\nR\$ ${p.valorCobrado.toStringAsFixed(2)}',
                      child: const Icon(Icons.toll, color: Colors.red, size: 28),
                    ),
                  ),
                ),
                // Só o posto sugerido aparece no mapa (os demais postos da
                // rota poluiriam demais — podem passar de 100 em áreas urbanas).
                if (breakdown!.postoSugerido != null)
                  Marker(
                    point: LatLng(breakdown!.postoSugerido!.lat, breakdown!.postoSugerido!.lon),
                    width: 36,
                    height: 36,
                    child: Tooltip(
                      message: 'Parada sugerida\n${breakdown!.postoSugerido!.nome}',
                      child: const Icon(Icons.local_gas_station, color: Colors.green, size: 28),
                    ),
                  ),
              ],
            ),
          if (posicaoAtual != null)
            MarkerLayer(markers: [
              Marker(
                point: posicaoAtual!,
                width: 24,
                height: 24,
                child: Container(
                  decoration: BoxDecoration(
                    color: Theme.of(context).colorScheme.primary,
                    shape: BoxShape.circle,
                    border: Border.all(color: Colors.white, width: 3),
                    boxShadow: const [BoxShadow(color: Colors.black38, blurRadius: 4)],
                  ),
                ),
              ),
            ]),
          RichAttributionWidget(
            attributions: [
              TextSourceAttribution('OpenStreetMap contributors'),
              TextSourceAttribution('CARTO'),
            ],
          ),
        ],
      ),
    );
  }
}
