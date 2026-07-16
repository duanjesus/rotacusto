import 'dart:convert';

import 'package:shared_preferences/shared_preferences.dart';

import '../domain/models/trip_cost_breakdown.dart';

/// Última viagem calculada com sucesso, guardada localmente pra dar pra
/// retomar a navegação sem falar com o back-end de novo (Fase 6.5, modo
/// offline — útil se o app for fechado/reaberto numa área sem sinal).
/// Os 4 campos além do breakdown espelham exatamente os parâmetros que
/// `NavigationScreen` já usa pro recálculo de rota em desvio — `null`
/// quando a viagem era ida-e-volta ou tinha paradas (mesma regra de sempre).
class LastTrip {
  final TripCostBreakdown breakdown;
  final String origemLabel;
  final String destinoLabel;
  final String? destino;
  final int? vehicleModelId;
  final double? precoPorLitro;
  final double? precoPorKWh;

  LastTrip({
    required this.breakdown,
    required this.origemLabel,
    required this.destinoLabel,
    required this.destino,
    required this.vehicleModelId,
    required this.precoPorLitro,
    required this.precoPorKWh,
  });
}

const _kLastTripKey = 'last_trip_v1';

Future<void> saveLastTrip(LastTrip trip) async {
  final prefs = await SharedPreferences.getInstance();
  await prefs.setString(
    _kLastTripKey,
    jsonEncode({
      'breakdown': trip.breakdown.toJson(),
      'origemLabel': trip.origemLabel,
      'destinoLabel': trip.destinoLabel,
      'destino': trip.destino,
      'vehicleModelId': trip.vehicleModelId,
      'precoPorLitro': trip.precoPorLitro,
      'precoPorKWh': trip.precoPorKWh,
    }),
  );
}

Future<LastTrip?> loadLastTrip() async {
  final prefs = await SharedPreferences.getInstance();
  final raw = prefs.getString(_kLastTripKey);
  if (raw == null) return null;
  try {
    final json = jsonDecode(raw) as Map<String, dynamic>;
    return LastTrip(
      breakdown: TripCostBreakdown.fromJson(json['breakdown'] as Map<String, dynamic>),
      origemLabel: json['origemLabel'] as String,
      destinoLabel: json['destinoLabel'] as String,
      destino: json['destino'] as String?,
      vehicleModelId: json['vehicleModelId'] as int?,
      precoPorLitro: (json['precoPorLitro'] as num?)?.toDouble(),
      precoPorKWh: (json['precoPorKWh'] as num?)?.toDouble(),
    );
  } catch (_) {
    // Formato salvo por uma versão antiga do app, ou dado corrompido — não
    // trava o app por causa de uma viagem em cache, só ignora.
    return null;
  }
}

Future<void> clearLastTrip() async {
  final prefs = await SharedPreferences.getInstance();
  await prefs.remove(_kLastTripKey);
}
