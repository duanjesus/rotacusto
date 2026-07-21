import 'package:latlong2/latlong.dart';

import '../models/radar_point.dart';

/// Decide quando avisar por voz sobre uma câmera de radar à frente (Fase 12)
/// — lógica pura, mesmo espírito de `road_alert_proximity.dart`. Diferente de
/// [RoadAlert], [RadarPoint] não tem id próprio vindo do back-end (é só
/// coordenada, infraestrutura fixa do OpenStreetMap) — a própria posição
/// serve de chave de dedup pra "já anunciado uma vez".
class RadarProximityChecker {
  final double avisoDistanciaM;
  final _distance = const Distance();
  final Set<String> _jaAnunciados = {};

  RadarProximityChecker({this.avisoDistanciaM = 500});

  String _chave(RadarPoint radar) => '${radar.lat},${radar.lon}';

  /// Entre os radares ainda não anunciados, retorna o mais próximo dentro de
  /// [avisoDistanciaM] da posição atual (e marca como anunciado) — ou `null`
  /// se nenhum estiver perto o bastante.
  RadarPoint? checkProximity(LatLng posicaoAtual, List<RadarPoint> radares) {
    RadarPoint? maisProximo;
    var menorDistancia = double.infinity;

    for (final radar in radares) {
      if (_jaAnunciados.contains(_chave(radar))) continue;
      final distanciaM = _distance(posicaoAtual, LatLng(radar.lat, radar.lon));
      if (distanciaM <= avisoDistanciaM && distanciaM < menorDistancia) {
        menorDistancia = distanciaM;
        maisProximo = radar;
      }
    }

    if (maisProximo != null) {
      _jaAnunciados.add(_chave(maisProximo));
    }
    return maisProximo;
  }

  void reset() => _jaAnunciados.clear();
}
