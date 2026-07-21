import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_map_cache/flutter_map_cache.dart';
import 'package:latlong2/latlong.dart';

import '../../data/tile_cache.dart';
import '../../domain/models/road_alert.dart';
import '../../domain/models/traffic_report.dart';
import '../../domain/models/trip_cost_breakdown.dart';
import '../../domain/navigation/traffic_overlay.dart';

class TripMap extends StatefulWidget {
  final TripCostBreakdown? breakdown;
  /// Dono é de quem precisa mover a câmera de fora (ex.: seguir o GPS ao
  /// vivo na tela de navegação) — sem isso, `TripMap` continua stateless.
  final MapController? mapController;
  /// Posição ao vivo do usuário (Fase 6, navegação) — null fora da tela de
  /// navegação, sem nenhum efeito no uso normal (resultado da viagem).
  final LatLng? posicaoAtual;
  /// Alertas de trânsito descobertos pelo polling ao vivo durante a
  /// navegação (Fase 6.6) — além dos que já vieram embutidos em
  /// [breakdown.alertasNaRota] no cálculo inicial. Mesclado por id (sem
  /// duplicar) na hora de desenhar os marcadores; null fora da navegação.
  final List<RoadAlert>? alertasAoVivo;
  /// Relatos de trânsito lento (Fase 6.7) descobertos pelo polling ao vivo
  /// durante a navegação, além dos que já vieram embutidos em
  /// [breakdown.trafegoNaRota] no cálculo inicial — mesmo padrão de
  /// [alertasAoVivo]. null fora da navegação.
  final List<TrafficReport>? trafegoAoVivo;

  const TripMap({
    super.key,
    this.breakdown,
    this.mapController,
    this.posicaoAtual,
    this.alertasAoVivo,
    this.trafegoAoVivo,
  });

  @override
  State<TripMap> createState() => _TripMapState();
}

class _TripMapState extends State<TripMap> {
  // Construído uma vez (não a cada rebuild) — abrir o cache em disco é
  // assíncrono, e recriar o provider toda hora perderia o sentido do cache.
  late final Future<CachedTileProvider> _tileProviderFuture = buildTileProvider();

  @override
  Widget build(BuildContext context) {
    final breakdown = widget.breakdown;
    final mapController = widget.mapController;
    final posicaoAtual = widget.posicaoAtual;
    final route = breakdown?.geometriaRota ?? const <LatLng>[];
    // Mescla por id — alertasAoVivo (polling durante a navegação) pode
    // repetir um que já veio em breakdown.alertasNaRota (cálculo inicial).
    final alertasPorId = <int, RoadAlert>{
      for (final a in breakdown?.alertasNaRota ?? const <RoadAlert>[]) a.id: a,
      for (final a in widget.alertasAoVivo ?? const <RoadAlert>[]) a.id: a,
    };
    final alertas = alertasPorId.values.toList();
    final trafegoPorId = <int, TrafficReport>{
      for (final t in breakdown?.trafegoNaRota ?? const <TrafficReport>[]) t.id: t,
      for (final t in widget.trafegoAoVivo ?? const <TrafficReport>[]) t.id: t,
    };
    final segmentosTrafego = buildTrafficSegments(route, trafegoPorId.values.toList());
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
          // FutureBuilder só troca o tileProvider (cache em disco) assim que
          // o cache termina de abrir — enquanto isso, o TileLayer já
          // funciona normalmente (sem cache) pra não travar o mapa esperando
          // I/O de arquivo. Depois que resolve uma vez, fica resolvido.
          FutureBuilder<CachedTileProvider>(
            future: _tileProviderFuture,
            builder: (context, snapshot) {
              return TileLayer(
                urlTemplate: tileUrl,
                userAgentPackageName: 'com.rotacusto.app',
                subdomains: const ['a', 'b', 'c', 'd'],
                tileProvider: snapshot.data,
              );
            },
          ),
          if (route.isNotEmpty)
            PolylineLayer(polylines: [
              Polyline(points: route, strokeWidth: 4, color: Theme.of(context).colorScheme.primary),
            ]),
          // Trechos de trânsito lento (Fase 6.7) por cima da rota base, mais
          // grossos, coloridos conforme a severidade — aproximação visual em
          // torno do ponto de cada relato (ver traffic_overlay.dart).
          if (segmentosTrafego.isNotEmpty)
            PolylineLayer(polylines: [
              for (final s in segmentosTrafego)
                Polyline(points: s.pontos, strokeWidth: 7, color: s.severidade.color),
            ]),
          if (breakdown != null)
            MarkerLayer(
              markers: [
                ...breakdown.paradasNaRota.map(
                  (p) => Marker(
                    point: p,
                    width: 32,
                    height: 32,
                    child: const Tooltip(
                      message: 'Parada do roteiro',
                      child: Icon(Icons.flag_rounded, color: Colors.deepOrange, size: 26),
                    ),
                  ),
                ),
                ...breakdown.pedagiosNaRota.map(
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
                if (breakdown.postoSugerido != null)
                  Marker(
                    point: LatLng(breakdown.postoSugerido!.lat, breakdown.postoSugerido!.lon),
                    width: 36,
                    height: 36,
                    child: Tooltip(
                      message: 'Parada sugerida\n${breakdown.postoSugerido!.nome}',
                      child: const Icon(Icons.local_gas_station, color: Colors.green, size: 28),
                    ),
                  ),
              ],
            ),
          if (alertas.isNotEmpty)
            MarkerLayer(
              markers: [
                for (final a in alertas)
                  Marker(
                    point: LatLng(a.lat, a.lng),
                    width: 34,
                    height: 34,
                    child: Tooltip(
                      message: a.tipo.label,
                      child: Icon(a.tipo.icon, color: Colors.deepOrange, size: 26),
                    ),
                  ),
              ],
            ),
          // Radares fixos (Fase 12) — infraestrutura permanente, vêm só do
          // cálculo inicial (breakdown.radaresNaRota), sem polling ao vivo
          // (ao contrário de alertas/trânsito, câmera de radar não some/muda
          // durante a viagem).
          if (breakdown != null && breakdown.radaresNaRota.isNotEmpty)
            MarkerLayer(
              markers: [
                for (final r in breakdown.radaresNaRota)
                  Marker(
                    point: LatLng(r.lat, r.lon),
                    width: 30,
                    height: 30,
                    child: Tooltip(
                      message: r.tipo.label,
                      child: Icon(r.tipo.icon, color: Colors.indigo, size: 24),
                    ),
                  ),
              ],
            ),
          if (posicaoAtual != null)
            MarkerLayer(markers: [
              Marker(
                point: posicaoAtual,
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
