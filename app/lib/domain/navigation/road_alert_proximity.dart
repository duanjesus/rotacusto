import 'package:latlong2/latlong.dart';

import '../models/road_alert.dart';

/// Decide quando avisar por voz sobre um alerta de trânsito reportado por
/// outro usuário (Fase 6.6) — lógica pura, sem Flutter/TTS/rede, pra dar pra
/// testar com sequências sintéticas de posição (mesmo espírito de
/// `deviation_detector.dart`).
class RoadAlertProximityChecker {
  final double avisoDistanciaM;
  final _distance = const Distance();
  final Set<int> _jaAnunciados = {};

  RoadAlertProximityChecker({this.avisoDistanciaM = 500});

  /// Entre os alertas ainda não anunciados, retorna o mais próximo dentro de
  /// [avisoDistanciaM] da posição atual (e marca como anunciado, pra não
  /// repetir) — ou `null` se nenhum estiver perto o bastante. Cada alerta é
  /// anunciado no máximo uma vez por instância (uma viagem de navegação).
  RoadAlert? checkProximity(LatLng posicaoAtual, List<RoadAlert> alertas) {
    RoadAlert? maisProximo;
    var menorDistancia = double.infinity;

    for (final alerta in alertas) {
      if (_jaAnunciados.contains(alerta.id)) continue;
      final distanciaM = _distance(posicaoAtual, LatLng(alerta.lat, alerta.lng));
      if (distanciaM <= avisoDistanciaM && distanciaM < menorDistancia) {
        menorDistancia = distanciaM;
        maisProximo = alerta;
      }
    }

    if (maisProximo != null) {
      _jaAnunciados.add(maisProximo.id);
    }
    return maisProximo;
  }

  void reset() => _jaAnunciados.clear();
}
