/// Uma instrução de navegação turn-by-turn (ex.: "Vire à direita na Rua X").
/// [wayPointInicio]/[wayPointFim] são índices dentro de
/// [TripCostBreakdown.geometriaRota] — marcam o trecho da rota ao qual essa
/// instrução se refere, usados por `route_progress.dart` pra casar a posição
/// GPS ao vivo com a instrução certa durante a navegação.
class RouteStep {
  final String instrucao;
  final double distanciaM;
  final double duracaoS;
  final int wayPointInicio;
  final int wayPointFim;

  RouteStep({
    required this.instrucao,
    required this.distanciaM,
    required this.duracaoS,
    required this.wayPointInicio,
    required this.wayPointFim,
  });

  factory RouteStep.fromJson(Map<String, dynamic> json) {
    return RouteStep(
      instrucao: json['instrucao'] as String,
      distanciaM: (json['distanciaM'] as num).toDouble(),
      duracaoS: (json['duracaoS'] as num).toDouble(),
      wayPointInicio: json['wayPointInicio'] as int,
      wayPointFim: json['wayPointFim'] as int,
    );
  }

  /// Usado ao concatenar ida+volta em [TripCostBreakdown.combine] — os
  /// índices da volta precisam ser deslocados pelo tamanho da geometria da
  /// ida pra continuarem válidos contra a lista combinada.
  RouteStep offsetWayPoints(int offset) {
    return RouteStep(
      instrucao: instrucao,
      distanciaM: distanciaM,
      duracaoS: duracaoS,
      wayPointInicio: wayPointInicio + offset,
      wayPointFim: wayPointFim + offset,
    );
  }
}
