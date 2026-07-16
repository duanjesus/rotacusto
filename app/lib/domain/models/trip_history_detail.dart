import 'trip_cost_breakdown.dart';

class TripHistoryDetail {
  final String origem;
  final String destino;
  final DateTime calculadoEm;
  final TripCostBreakdown breakdown;

  TripHistoryDetail({
    required this.origem,
    required this.destino,
    required this.calculadoEm,
    required this.breakdown,
  });

  factory TripHistoryDetail.fromJson(Map<String, dynamic> json) {
    return TripHistoryDetail(
      origem: json['origem'] as String,
      destino: json['destino'] as String,
      calculadoEm: DateTime.parse(json['calculadoEm'] as String),
      breakdown: TripCostBreakdown.fromJson(json['breakdown'] as Map<String, dynamic>),
    );
  }
}
