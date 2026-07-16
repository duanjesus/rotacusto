import 'package:flutter_test/flutter_test.dart';

import 'package:rotacusto_app/domain/navigation/deviation_detector.dart';

void main() {
  test('leituras dentro do limiar nunca disparam', () {
    final detector = DeviationDetector();

    for (var i = 0; i < 10; i++) {
      expect(detector.registrarLeitura(30), isFalse);
    }
  });

  test('uma única leitura acima do limiar não dispara', () {
    final detector = DeviationDetector();

    expect(detector.registrarLeitura(100), isFalse);
  });

  test('duas leituras seguidas acima do limiar ainda não disparam (precisa de 3)', () {
    final detector = DeviationDetector();

    expect(detector.registrarLeitura(100), isFalse);
    expect(detector.registrarLeitura(100), isFalse);
  });

  test('três leituras consecutivas acima do limiar disparam', () {
    final detector = DeviationDetector();

    expect(detector.registrarLeitura(100), isFalse);
    expect(detector.registrarLeitura(100), isFalse);
    expect(detector.registrarLeitura(100), isTrue);
  });

  test('uma leitura dentro do limiar no meio da sequência reseta o contador', () {
    final detector = DeviationDetector();

    expect(detector.registrarLeitura(100), isFalse);
    expect(detector.registrarLeitura(100), isFalse);
    expect(detector.registrarLeitura(10), isFalse); // volta pra rota — reseta
    expect(detector.registrarLeitura(100), isFalse); // precisa recomeçar a contagem
    expect(detector.registrarLeitura(100), isFalse);
    expect(detector.registrarLeitura(100), isTrue);
  });

  test('depois de disparar, o contador reseta e precisa de mais 3 leituras pra disparar de novo', () {
    final detector = DeviationDetector();

    expect(detector.registrarLeitura(100), isFalse);
    expect(detector.registrarLeitura(100), isFalse);
    expect(detector.registrarLeitura(100), isTrue);

    // Logo em seguida, ainda fora da rota — não deveria disparar de novo
    // imediatamente, só depois de mais 3 leituras consecutivas.
    expect(detector.registrarLeitura(100), isFalse);
    expect(detector.registrarLeitura(100), isFalse);
    expect(detector.registrarLeitura(100), isTrue);
  });

  test('limiar e leituras consecutivas necessárias são configuráveis', () {
    final detector = DeviationDetector(limiarMetros: 20, leiturasConsecutivasNecessarias: 1);

    expect(detector.registrarLeitura(15), isFalse);
    expect(detector.registrarLeitura(25), isTrue);
  });

  test('reset() manual zera o contador', () {
    final detector = DeviationDetector();

    expect(detector.registrarLeitura(100), isFalse);
    expect(detector.registrarLeitura(100), isFalse);
    detector.reset();
    expect(detector.registrarLeitura(100), isFalse);
    expect(detector.registrarLeitura(100), isFalse);
    expect(detector.registrarLeitura(100), isTrue);
  });
}
