import 'package:latlong2/latlong.dart';

import '../models/route_step.dart';

/// Resultado de casar uma posição GPS ao vivo com a rota calculada.
class RouteProgress {
  /// Índice em `passosRota` da instrução "atual" — null se a rota não tem
  /// geometria/passos (não deveria acontecer com uma viagem calculada de
  /// verdade, mas evita índice inválido se acontecer).
  final int? currentStepIndex;

  /// Distância restante (metros) do ponto mais próximo da rota até o FIM do
  /// passo atual — quanto falta pra próxima virada, não a distância total
  /// do passo (que ignoraria o quanto já foi percorrido dele).
  final double distanciaAteProximaViradaM;

  /// Distância (metros) da posição atual até o ponto mais próximo da rota —
  /// não usado pra nada nesta fase (sem recálculo/desvio), só exposto porque
  /// já é calculado de graça e pode servir de sinal futuro.
  final double distanciaAteRotaM;

  const RouteProgress({
    required this.currentStepIndex,
    required this.distanciaAteProximaViradaM,
    required this.distanciaAteRotaM,
  });
}

/// Lógica pura de progresso na rota — sem rede, sem GPS de verdade, sem
/// Flutter. Roda a cada posição nova recebida do `Geolocator` durante a
/// navegação (não dá pra ida-e-volta ao back-end a cada tick de GPS).
class RouteProgressCalculator {
  static const _distance = Distance();

  static RouteProgress calculate({
    required LatLng posicaoAtual,
    required List<LatLng> geometriaRota,
    required List<RouteStep> passosRota,
  }) {
    if (geometriaRota.isEmpty || passosRota.isEmpty) {
      return const RouteProgress(currentStepIndex: null, distanciaAteProximaViradaM: 0, distanciaAteRotaM: 0);
    }

    var nearestIndex = 0;
    var nearestDist = double.infinity;
    for (var i = 0; i < geometriaRota.length; i++) {
      final d = _distance(posicaoAtual, geometriaRota[i]);
      if (d < nearestDist) {
        nearestDist = d;
        nearestIndex = i;
      }
    }

    var stepIndex = passosRota.indexWhere(
      (s) => nearestIndex >= s.wayPointInicio && nearestIndex <= s.wayPointFim,
    );
    if (stepIndex == -1) {
      // Ponto mais próximo caiu fora de todo intervalo coberto (não deveria
      // acontecer se os passos cobrem a rota inteira, mas defensivo contra
      // gaps) — usa o último passo cujo início já foi ultrapassado.
      stepIndex = passosRota.lastIndexWhere((s) => s.wayPointInicio <= nearestIndex);
      if (stepIndex == -1) stepIndex = 0;
    }

    final wayPointFim = passosRota[stepIndex].wayPointFim.clamp(0, geometriaRota.length - 1);
    var distanciaRestante = 0.0;
    for (var i = nearestIndex; i < wayPointFim; i++) {
      distanciaRestante += _distance(geometriaRota[i], geometriaRota[i + 1]);
    }

    return RouteProgress(
      currentStepIndex: stepIndex,
      distanciaAteProximaViradaM: distanciaRestante,
      distanciaAteRotaM: nearestDist,
    );
  }
}
