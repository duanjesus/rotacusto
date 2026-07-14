import 'package:dio/dio.dart';

import '../domain/models/address_suggestion.dart';
import '../domain/models/trip_cost_breakdown.dart';
import '../domain/models/vehicle_model.dart';

/// Client HTTP para o back-end do RotaCusto.
/// Em desktop/mobile nativo, `localhost` aponta para a própria máquina.
/// Rodando via `flutter run -d chrome/edge`, o back-end precisa liberar CORS
/// (já configurado em WebConfig no back-end para desenvolvimento local).
class ApiClient {
  final Dio _dio;

  ApiClient({String baseUrl = 'http://localhost:8080/api'})
      : _dio = Dio(BaseOptions(baseUrl: baseUrl, connectTimeout: const Duration(seconds: 10)));

  Future<List<VehicleModel>> fetchVehicleModels({String? marca}) async {
    final response = await _dio.get('/vehicle-models', queryParameters: {
      if (marca != null && marca.isNotEmpty) 'marca': marca,
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
    required double precoCombustivelPorLitro,
  }) async {
    final response = await _dio.post('/trips/estimate', data: {
      'origem': origem,
      'destino': destino,
      'vehicleModelId': vehicleModelId,
      'precoCombustivelPorLitro': precoCombustivelPorLitro,
    });
    return TripCostBreakdown.fromJson(response.data as Map<String, dynamic>);
  }
}
