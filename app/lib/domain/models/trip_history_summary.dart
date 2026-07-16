class TripHistorySummary {
  final int id;
  final String origem;
  final String destino;
  final double distanciaKm;
  final double total;
  final DateTime calculadoEm;

  TripHistorySummary({
    required this.id,
    required this.origem,
    required this.destino,
    required this.distanciaKm,
    required this.total,
    required this.calculadoEm,
  });

  factory TripHistorySummary.fromJson(Map<String, dynamic> json) {
    return TripHistorySummary(
      id: json['id'] as int,
      origem: json['origem'] as String,
      destino: json['destino'] as String,
      distanciaKm: (json['distanciaKm'] as num).toDouble(),
      total: (json['total'] as num).toDouble(),
      calculadoEm: DateTime.parse(json['calculadoEm'] as String),
    );
  }
}
