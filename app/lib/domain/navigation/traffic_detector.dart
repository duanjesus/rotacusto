import '../models/traffic_severity.dart';

/// Decide quando a velocidade GPS ao vivo está baixa o bastante, por tempo
/// suficiente, pra classificar como trânsito lento — lógica pura, sem
/// Flutter, sem rede, mesmo espírito de [DeviationDetector]. [agora] é
/// passado como parâmetro (não `DateTime.now()` interno) pra dar pra testar
/// com sequências sintéticas sem `Timer` real.
class TrafficDetector {
  /// Razão velocidadeAtual/velocidadeEsperada abaixo da qual já conta como
  /// "abaixo do normal" (severidade LEVE) — acima disso, trânsito normal.
  final double limiarLeve;
  final double limiarMedio;
  final double limiarIntenso;

  /// Quantas leituras consecutivas abaixo de [limiarLeve] são necessárias
  /// antes de classificar — evita disparo por 1 leitura isolada (ex. parado
  /// alguns instantes num semáforo).
  final int leiturasConsecutivasNecessarias;

  /// Intervalo mínimo entre dois relatos — sem isso, um engarrafamento
  /// sustentado geraria um POST a cada leitura de GPS.
  final Duration cooldown;

  int _contador = 0;
  DateTime? _ultimoRelato;

  TrafficDetector({
    this.limiarLeve = 0.7,
    this.limiarMedio = 0.5,
    this.limiarIntenso = 0.3,
    this.leiturasConsecutivasNecessarias = 3,
    this.cooldown = const Duration(seconds: 90),
  });

  /// Registra uma nova leitura de velocidade. Retorna a severidade só
  /// quando o padrão se sustenta por [leiturasConsecutivasNecessarias]
  /// leituras seguidas E o [cooldown] desde o último relato já passou —
  /// `null` caso contrário (inclui o caso de velocidade normal, que zera o
  /// contador de leituras consecutivas).
  TrafficSeverity? registrarLeitura({
    required double velocidadeAtualMps,
    required double velocidadeEsperadaMps,
    required DateTime agora,
  }) {
    // Passo de duração zero (ou dado inconsistente) — sem base de
    // comparação válida, não classifica.
    if (velocidadeEsperadaMps <= 0) {
      _contador = 0;
      return null;
    }

    final razao = velocidadeAtualMps / velocidadeEsperadaMps;
    if (razao >= limiarLeve) {
      _contador = 0;
      return null;
    }

    _contador++;
    if (_contador < leiturasConsecutivasNecessarias) return null;

    if (_ultimoRelato != null && agora.difference(_ultimoRelato!) < cooldown) {
      return null;
    }

    _ultimoRelato = agora;
    if (razao < limiarIntenso) return TrafficSeverity.intenso;
    if (razao < limiarMedio) return TrafficSeverity.medio;
    return TrafficSeverity.leve;
  }

  void reset() {
    _contador = 0;
    _ultimoRelato = null;
  }
}
