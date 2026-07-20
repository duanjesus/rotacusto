import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart' show TargetPlatform, defaultTargetPlatform, kIsWeb;

import '../domain/models/address_suggestion.dart';
import '../domain/models/fuel_price.dart';
import '../domain/models/road_alert.dart';
import '../domain/models/road_alert_type.dart';
import '../domain/models/traffic_report.dart';
import '../domain/models/traffic_severity.dart';
import '../domain/models/trip_cost_breakdown.dart';
import '../domain/models/trip_history_detail.dart';
import '../domain/models/trip_history_summary.dart';
import '../domain/models/vehicle_model.dart';
import '../domain/models/vehicle_model_summary.dart';
import '../domain/models/vehicle_type.dart';
import '../theme/auth_controller.dart';

/// `localhost` do PONTO DE VISTA DE QUEM RODA O APP: em Windows/web
/// (Chrome/Edge) e num celular Android FÍSICO com `adb reverse tcp:8080
/// tcp:8080`, aponta certo pra própria máquina. O **emulador Android** é a
/// exceção: `localhost` dentro dele é o próprio emulador, não o PC —
/// precisa do alias especial `10.0.2.2`. Um Android físico na mesma
/// rede (sem `adb reverse`) precisaria do IP de LAN real do PC, não coberto
/// aqui (fora do escopo de teste local).
String _defaultBaseUrl() {
  if (!kIsWeb && defaultTargetPlatform == TargetPlatform.android) {
    return 'http://10.0.2.2:8080/api';
  }
  return 'http://localhost:8080/api';
}

/// Client HTTP para o back-end do RotaCusto.
/// Rodando via `flutter run -d chrome/edge`, o back-end precisa liberar CORS
/// (já configurado em WebConfig no back-end para desenvolvimento local).
class ApiClient {
  final Dio _dio;

  ApiClient({String? baseUrl})
      : _dio = Dio(BaseOptions(
          baseUrl: baseUrl ?? _defaultBaseUrl(),
          connectTimeout: const Duration(seconds: 10),
          // /trips/estimate encadeia geocode x2 + rota + pedágios/postos (OSM
          // Overpass, com fallback se lento) — pode legitimamente passar de
          // 20s em condições normais.
          receiveTimeout: const Duration(seconds: 45),
        )) {
    // Anexa o token em toda requisição quando há sessão logada — a maioria
    // dos endpoints ignora o header (são públicos), só /trip-history exige.
    _dio.interceptors.add(InterceptorsWrapper(onRequest: (options, handler) {
      final sessao = authSessionNotifier.value;
      if (sessao != null) {
        options.headers['Authorization'] = 'Bearer ${sessao.token}';
      }
      handler.next(options);
    }));
  }

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

  /// Tabela inteira de preço médio de combustível por UF (ANP) — pequena o
  /// bastante pra carregar uma vez e fazer lookup local, sem round-trip a
  /// cada troca de Origem/veículo.
  Future<List<FuelPrice>> fetchFuelPrices() async {
    final response = await _dio.get('/fuel-prices');
    return (response.data as List<dynamic>)
        .map((json) => FuelPrice.fromJson(json as Map<String, dynamic>))
        .toList();
  }

  Future<TripCostBreakdown> estimateTrip({
    required String origem,
    required String destino,
    required int vehicleModelId,
    double? precoPorLitro,
    double? precoPorKWh,
    List<String>? paradas,
  }) async {
    final response = await _dio.post('/trips/estimate', data: {
      'origem': origem,
      'destino': destino,
      'vehicleModelId': vehicleModelId,
      'precoPorLitro': ?precoPorLitro,
      'precoPorKWh': ?precoPorKWh,
      if (paradas != null && paradas.isNotEmpty) 'paradas': paradas,
    });
    return TripCostBreakdown.fromJson(response.data as Map<String, dynamic>);
  }

  /// Rotas alternativas (Fase 10) — só funciona sem paradas (mesma restrição do
  /// ORS, ver back-end). Pode devolver só 1 resultado se o ORS não achar uma
  /// alternativa genuinamente diferente da rota principal.
  Future<List<TripCostBreakdown>> estimateTripAlternatives({
    required String origem,
    required String destino,
    required int vehicleModelId,
    double? precoPorLitro,
    double? precoPorKWh,
  }) async {
    final response = await _dio.post('/trips/estimate/alternatives', data: {
      'origem': origem,
      'destino': destino,
      'vehicleModelId': vehicleModelId,
      'precoPorLitro': ?precoPorLitro,
      'precoPorKWh': ?precoPorKWh,
    });
    return (response.data as List<dynamic>)
        .map((json) => TripCostBreakdown.fromJson(json as Map<String, dynamic>))
        .toList();
  }

  /// "Não achou seu veículo?" — grava o pedido no back-end pra eu revisar
  /// depois (substituiu um link pro GitHub Issues: poucos usuários do app
  /// têm familiaridade com GitHub).
  Future<void> reportMissingVehicle(VehicleType tipo, String descricao) async {
    await _dio.post('/vehicle-reports', data: {
      'tipo': tipo.apiValue,
      'descricao': descricao,
    });
  }

  // --- Fase 6.4b: conta de usuário + histórico de viagens (login opcional). ---

  Future<AuthSession> register(String email, String senha) async {
    final response = await _dio.post('/auth/register', data: {'email': email, 'senha': senha});
    return _sessionFromResponse(response.data as Map<String, dynamic>);
  }

  Future<AuthSession> login(String email, String senha) async {
    final response = await _dio.post('/auth/login', data: {'email': email, 'senha': senha});
    return _sessionFromResponse(response.data as Map<String, dynamic>);
  }

  AuthSession _sessionFromResponse(Map<String, dynamic> json) {
    return AuthSession(token: json['token'] as String, email: json['email'] as String);
  }

  /// Salva a viagem já calculada no histórico — não automático, só quando o
  /// usuário toca em "Salvar no histórico" com uma sessão ativa.
  Future<void> saveTripToHistory({
    required String origem,
    required String destino,
    required TripCostBreakdown breakdown,
  }) async {
    await _dio.post('/trip-history', data: {
      'origem': origem,
      'destino': destino,
      'breakdown': breakdown.toJson(),
    });
  }

  Future<List<TripHistorySummary>> fetchTripHistory() async {
    final response = await _dio.get('/trip-history');
    return (response.data as List<dynamic>)
        .map((json) => TripHistorySummary.fromJson(json as Map<String, dynamic>))
        .toList();
  }

  Future<TripHistoryDetail> fetchTripHistoryDetail(int id) async {
    final response = await _dio.get('/trip-history/$id');
    return TripHistoryDetail.fromJson(response.data as Map<String, dynamic>);
  }

  // --- Fase 6.6: alertas de trânsito reportados por usuários, sem login. ---

  Future<RoadAlert> reportRoadAlert(RoadAlertType tipo, double lat, double lng) async {
    final response = await _dio.post('/road-alerts', data: {
      'tipo': tipo.apiValue,
      'lat': lat,
      'lng': lng,
    });
    return RoadAlert.fromJson(response.data as Map<String, dynamic>);
  }

  /// Usado pelo polling ao vivo durante a navegação — pega alertas
  /// reportados por outras pessoas depois que a viagem já tinha sido
  /// calculada. [raioKm] nulo usa o raio padrão configurado no back-end.
  Future<List<RoadAlert>> fetchNearbyRoadAlerts(double lat, double lng, {double? raioKm}) async {
    final response = await _dio.post('/road-alerts/nearby', data: {
      'lat': lat,
      'lng': lng,
      'raioKm': ?raioKm,
    });
    return (response.data as List<dynamic>)
        .map((json) => RoadAlert.fromJson(json as Map<String, dynamic>))
        .toList();
  }

  /// Confirmação/reputação (Fase 6.8) — "ainda está lá?"/"já foi resolvido". Deixa
  /// a exceção (DioException) subir pro chamador tratar: quem chama distingue 409
  /// (dispositivo já votou nesse alerta) do resto.
  Future<RoadAlert> voteRoadAlert(int alertId, String deviceId, bool confirma) async {
    final response = await _dio.post('/road-alerts/$alertId/vote', data: {
      'deviceId': deviceId,
      'confirma': confirma,
    });
    return RoadAlert.fromJson(response.data as Map<String, dynamic>);
  }

  // --- Fase 6.7: relatos automáticos de trânsito lento, sem login. ---

  /// Chamado automaticamente pelo app (TrafficDetector), não por uma ação
  /// manual do usuário como [reportRoadAlert].
  Future<TrafficReport> reportTraffic(TrafficSeverity severidade, double lat, double lng) async {
    final response = await _dio.post('/traffic-reports', data: {
      'severidade': severidade.apiValue,
      'lat': lat,
      'lng': lng,
    });
    return TrafficReport.fromJson(response.data as Map<String, dynamic>);
  }

  /// Usado pelo polling ao vivo durante a navegação — pega relatos de
  /// trânsito de outras pessoas depois que a viagem já tinha sido calculada.
  /// [raioKm] nulo usa o raio padrão configurado no back-end.
  Future<List<TrafficReport>> fetchNearbyTraffic(double lat, double lng, {double? raioKm}) async {
    final response = await _dio.post('/traffic-reports/nearby', data: {
      'lat': lat,
      'lng': lng,
      'raioKm': ?raioKm,
    });
    return (response.data as List<dynamic>)
        .map((json) => TrafficReport.fromJson(json as Map<String, dynamic>))
        .toList();
  }
}
