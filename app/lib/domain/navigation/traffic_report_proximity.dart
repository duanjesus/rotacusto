import 'package:latlong2/latlong.dart';

import '../models/traffic_report.dart';

/// Decide quando avisar por voz sobre um relato de trânsito lento de OUTRO
/// usuário à frente (Fase 12) — lógica pura, mesmo espírito exato de
/// `road_alert_proximity.dart`. Raio menor que o de alertas de trânsito: um
/// relato de trânsito é sobre uma via específica (mesmo raio apertado de
/// detecção já usado em `traffic-reports` no back-end), não uma área ampla.
class TrafficReportProximityChecker {
  final double avisoDistanciaM;
  final _distance = const Distance();
  final Set<int> _jaAnunciados = {};

  TrafficReportProximityChecker({this.avisoDistanciaM = 300});

  /// Entre os relatos ainda não anunciados, retorna o mais próximo dentro de
  /// [avisoDistanciaM] da posição atual (e marca como anunciado) — ou `null`
  /// se nenhum estiver perto o bastante.
  TrafficReport? checkProximity(LatLng posicaoAtual, List<TrafficReport> trafego) {
    TrafficReport? maisProximo;
    var menorDistancia = double.infinity;

    for (final relato in trafego) {
      if (_jaAnunciados.contains(relato.id)) continue;
      final distanciaM = _distance(posicaoAtual, LatLng(relato.lat, relato.lng));
      if (distanciaM <= avisoDistanciaM && distanciaM < menorDistancia) {
        menorDistancia = distanciaM;
        maisProximo = relato;
      }
    }

    if (maisProximo != null) {
      _jaAnunciados.add(maisProximo.id);
    }
    return maisProximo;
  }

  void reset() => _jaAnunciados.clear();
}
