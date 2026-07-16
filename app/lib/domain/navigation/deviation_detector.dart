/// Decide quando um desvio da rota é "de verdade" (não ruído de GPS) e
/// merece disparar um recálculo. Lógica pura — sem Flutter, sem rede — pra
/// dar pra testar com sequências sintéticas de posição.
class DeviationDetector {
  final double limiarMetros;
  final int leiturasConsecutivasNecessarias;

  int _contador = 0;

  DeviationDetector({this.limiarMetros = 60, this.leiturasConsecutivasNecessarias = 3});

  /// Registra uma nova leitura de "distância até a rota" (metros). Retorna
  /// `true` só quando o desvio se confirma por
  /// [leiturasConsecutivasNecessarias] leituras seguidas acima do limiar —
  /// uma leitura isolada não dispara (evita recálculo por ruído/multipath
  /// de GPS em área urbana). Uma leitura dentro do limiar zera o contador;
  /// um disparo também zera, pra não repetir imediatamente.
  bool registrarLeitura(double distanciaAteRotaM) {
    if (distanciaAteRotaM <= limiarMetros) {
      _contador = 0;
      return false;
    }
    _contador++;
    if (_contador >= leiturasConsecutivasNecessarias) {
      _contador = 0;
      return true;
    }
    return false;
  }

  void reset() => _contador = 0;
}
