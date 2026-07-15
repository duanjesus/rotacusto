import 'package:flutter_test/flutter_test.dart';
import 'package:latlong2/latlong.dart';

import 'package:rotacusto_app/domain/models/route_step.dart';
import 'package:rotacusto_app/domain/navigation/route_progress.dart';

// Rota sintética reta ao longo do equador, ~111km por grau de longitude —
// fácil de calcular distância esperada de cabeça pra validar os testes.
final _geometria = [
  const LatLng(0, 0),
  const LatLng(0, 0.01), // ~1.1km do ponto 0
  const LatLng(0, 0.02), // ~2.2km do ponto 0
  const LatLng(0, 0.03), // ~3.3km do ponto 0
  const LatLng(0, 0.04), // ~4.4km do ponto 0
];

final _passos = [
  RouteStep(instrucao: 'Siga em frente na Rua A', distanciaM: 2200, duracaoS: 120, wayPointInicio: 0, wayPointFim: 2),
  RouteStep(instrucao: 'Vire à direita na Rua B', distanciaM: 2200, duracaoS: 120, wayPointInicio: 2, wayPointFim: 4),
];

void main() {
  test('posição no início da rota fica no primeiro passo, com toda a distância restante dele', () {
    final progresso = RouteProgressCalculator.calculate(
      posicaoAtual: const LatLng(0, 0),
      geometriaRota: _geometria,
      passosRota: _passos,
    );

    expect(progresso.currentStepIndex, 0);
    expect(progresso.distanciaAteProximaViradaM, closeTo(2223, 5));
    expect(progresso.distanciaAteRotaM, closeTo(0, 1));
  });

  test('posição no meio do primeiro passo mostra distância restante menor, não a distância total do passo', () {
    final progresso = RouteProgressCalculator.calculate(
      posicaoAtual: const LatLng(0, 0.01), // ponto de índice 1, no meio do passo 0
      geometriaRota: _geometria,
      passosRota: _passos,
    );

    expect(progresso.currentStepIndex, 0);
    expect(progresso.distanciaAteProximaViradaM, closeTo(1112, 5));
  });

  test('posição bem em cima da virada (fim do passo 0 / início do passo 1) prefere o passo seguinte', () {
    final progresso = RouteProgressCalculator.calculate(
      posicaoAtual: const LatLng(0, 0.02), // ponto de índice 2 — fronteira entre os dois passos
      geometriaRota: _geometria,
      passosRota: _passos,
    );

    // indexWhere acha o primeiro passo cujo intervalo contém o índice — o
    // passo 0 (wayPointFim: 2) bate primeiro que o passo 1 (wayPointInicio: 2).
    expect(progresso.currentStepIndex, 0);
    expect(progresso.distanciaAteProximaViradaM, closeTo(0, 1));
  });

  test('posição perto do fim da rota fica no último passo, com distância restante pequena', () {
    final progresso = RouteProgressCalculator.calculate(
      posicaoAtual: const LatLng(0, 0.038),
      geometriaRota: _geometria,
      passosRota: _passos,
    );

    expect(progresso.currentStepIndex, 1);
    expect(progresso.distanciaAteProximaViradaM, lessThan(300));
  });

  test('posição fora da rota ainda casa com o ponto mais próximo, e reporta a distância até ele', () {
    final progresso = RouteProgressCalculator.calculate(
      posicaoAtual: const LatLng(0.05, 0.01), // ~5.5km ao norte do ponto de índice 1
      geometriaRota: _geometria,
      passosRota: _passos,
    );

    expect(progresso.currentStepIndex, 0);
    expect(progresso.distanciaAteRotaM, greaterThan(5000));
  });

  test('rota sem geometria ou sem passos não quebra, retorna step nulo', () {
    final semGeometria = RouteProgressCalculator.calculate(
      posicaoAtual: const LatLng(0, 0),
      geometriaRota: [],
      passosRota: _passos,
    );
    final semPassos = RouteProgressCalculator.calculate(
      posicaoAtual: const LatLng(0, 0),
      geometriaRota: _geometria,
      passosRota: [],
    );

    expect(semGeometria.currentStepIndex, isNull);
    expect(semPassos.currentStepIndex, isNull);
  });
}
