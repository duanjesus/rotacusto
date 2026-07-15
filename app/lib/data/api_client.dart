import 'package:dio/dio.dart';

import '../domain/models/address_suggestion.dart';
import '../domain/models/trip_cost_breakdown.dart';
import '../domain/models/vehicle_model.dart';
import '../domain/models/vehicle_model_summary.dart';
import '../domain/models/vehicle_type.dart';

/// Client HTTP para o back-end do RotaCusto.
/// Em desktop/mobile nativo, `localhost` aponta para a própria máquina.
/// Rodando via `flutter run -d chrome/edge`, o back-end precisa liberar CORS
/// (já configurado em WebConfig no back-end para desenvolvimento local).
class ApiClient {
  final Dio _dio;

  ApiClient({String baseUrl = 'http://localhost:8080/api'})
      : _dio = Dio(BaseOptions(
          baseUrl: baseUrl,
          connectTimeout: const Duration(seconds: 10),
          // /trips/estimate encadeia geocode x2 + rota + pedágios/postos (OSM
          // Overpass, com fallback se lento) — pode legitimamente passar de
          // 20s em condições normais.
          receiveTimeout: const Duration(seconds: 45),
        ));

  /// Passo 1 da escolha de veículo: marca+modelo distintos (sem ano ainda).
  /// [tipo] filtra por tipo de veículo (carro/moto/...) — sem isso, buscar
  /// "honda" misturaria carro (Civic) com moto (CG 160) no mesmo resultado.
  Future<List<VehicleModelSummary>> searchVehicleModels(String query, {VehicleType? tipo}) async {
    final response = await _dio.get('/vehicle-models/search', queryParameters: {
      'q': query,
      if (tipo != null) 'tipo': tipo.apiValue,
    });
    return (response.data as List<dynamic>)
        .map((json) => VehicleModelSummary.fromJson(json as Map<String, dynamic>))
        .toList();
  }

  /// Passo 2: anos/versões disponíveis do marca+modelo escolhido no passo 1.
  Future<List<VehicleModel>> fetchVehicleVersions(String marca, String modelo) async {
    final response = await _dio.get('/vehicle-models/versions', queryParameters: {
      'marca': marca,
      'modelo': modelo,
    });
    return (response.data as List<dynamic>)
        .map((json) => VehicleModel.fromJson(json as Map<String, dynamic>))
        .toList();
  }

  Future<List<AddressSuggestion>> suggestAddress(String query) async {
    final response = await _dio.get('/geocoding/suggest', queryParameters: {'q': query});
    return (response.data as List<dynamic>)
        .map((json) => AddressSuggestion.fromJson(json as Map<String, dynamic>))
        .toList();
  }

  Future<TripCostBreakdown> estimateTrip({
    required String origem,
    required String destino,
    required int vehicleModelId,
    double? precoPorLitro,
    double? precoPorKWh,
  }) async {
    final response = await _dio.post('/trips/estimate', data: {
      'origem': origem,
      'destino': destino,
      'vehicleModelId': vehicleModelId,
      if (precoPorLitro != null) 'precoPorLitro': precoPorLitro,
      if (precoPorKWh != null) 'precoPorKWh': precoPorKWh,
    });
    return TripCostBreakdown.fromJson(response.data as Map<String, dynamic>);
  }
}
