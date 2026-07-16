import 'dart:async';
import 'dart:convert';

import 'package:flutter/widgets.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart';

import '../../data/api_client.dart';
import '../models/trip_cost_breakdown.dart';
import 'deviation_detector.dart';
import 'route_progress.dart';

const kNavigationBreakdownKey = 'navigation_breakdown';
const kNavigationDestinoKey = 'navigation_destino';
const kNavigationVehicleModelIdKey = 'navigation_vehicleModelId';
const kNavigationPrecoPorLitroKey = 'navigation_precoPorLitro';
const kNavigationPrecoPorKWhKey = 'navigation_precoPorKWh';

/// Callback de entrada do isolate de segundo plano (Android, Fase 6.3) — tem
/// que ser top-level e marcada `vm:entry-point` pro sistema conseguir
/// invocar mesmo depois que a Activity principal foi destruída.
@pragma('vm:entry-point')
void navigationTaskStartCallback() {
  // Este isolate é uma entrada nova do Dart, sem o binding que o main()
  // normal já teria configurado — sem isso, qualquer plugin baseado em
  // MethodChannel (flutter_tts, geolocator) quebra com "Cannot set the
  // method call handler before the binary messenger has been initialized"
  // assim que o construtor de NavigationTaskHandler tenta usá-los.
  WidgetsFlutterBinding.ensureInitialized();
  FlutterForegroundTask.setTaskHandler(NavigationTaskHandler());
}

/// Roda a navegação (posição, instrução, voz, recálculo em desvio) dentro do
/// serviço em primeiro plano do Android — sobrevive à tela apagada e ao
/// usuário trocando de app. Reimplementa deliberadamente a mesma lógica que
/// `NavigationScreen` usa em plataformas sem esse conceito de isolate
/// separado (Windows): não dá pra compartilhar métodos de instância de um
/// `State` através da fronteira TaskHandler↔UI, então a duplicação aqui é
/// intencional, não descuido.
class NavigationTaskHandler extends TaskHandler {
  final FlutterTts _tts = FlutterTts();
  final ApiClient _apiClient = ApiClient();
  final DeviationDetector _detector = DeviationDetector();

  TripCostBreakdown? _breakdown;
  String? _destino;
  int? _vehicleModelId;
  double? _precoPorLitro;
  double? _precoPorKWh;

  bool get _recalculoHabilitado => _destino != null && _vehicleModelId != null;

  StreamSubscription<Position>? _positionSub;
  int? _ultimoStepFalado;
  bool _recalculando = false;

  @override
  Future<void> onStart(DateTime timestamp, TaskStarter starter) async {
    await _tts.setLanguage('pt-BR');

    final breakdownJson = await FlutterForegroundTask.getData<String>(key: kNavigationBreakdownKey);
    if (breakdownJson != null) {
      _breakdown = TripCostBreakdown.fromJson(jsonDecode(breakdownJson) as Map<String, dynamic>);
    }
    _destino = await FlutterForegroundTask.getData<String>(key: kNavigationDestinoKey);
    _vehicleModelId = await FlutterForegroundTask.getData<int>(key: kNavigationVehicleModelIdKey);
    _precoPorLitro = await FlutterForegroundTask.getData<double>(key: kNavigationPrecoPorLitroKey);
    _precoPorKWh = await FlutterForegroundTask.getData<double>(key: kNavigationPrecoPorKWhKey);

    _positionSub = Geolocator.getPositionStream(
      locationSettings: const LocationSettings(accuracy: LocationAccuracy.high, distanceFilter: 5),
    ).listen(_onPosicao);
  }

  void _onPosicao(Position posicao) {
    final breakdown = _breakdown;
    if (breakdown == null || breakdown.passosRota.isEmpty) return;

    final atual = LatLng(posicao.latitude, posicao.longitude);
    final progresso = RouteProgressCalculator.calculate(
      posicaoAtual: atual,
      geometriaRota: breakdown.geometriaRota,
      passosRota: breakdown.passosRota,
    );

    if (progresso.currentStepIndex != null && progresso.currentStepIndex != _ultimoStepFalado) {
      _ultimoStepFalado = progresso.currentStepIndex;
      final instrucao = breakdown.passosRota[progresso.currentStepIndex!].instrucao;
      _tts.speak(instrucao);
      FlutterForegroundTask.updateService(notificationTitle: 'Navegando', notificationText: instrucao);
    }

    FlutterForegroundTask.sendDataToMain({
      'tipo': 'posicao',
      'lat': atual.latitude,
      'lon': atual.longitude,
      'currentStepIndex': progresso.currentStepIndex,
      'distanciaAteProximaViradaM': progresso.distanciaAteProximaViradaM,
    });

    if (_recalculoHabilitado && !_recalculando && _detector.registrarLeitura(progresso.distanciaAteRotaM)) {
      _recalcularRota(atual);
    }
  }

  Future<void> _recalcularRota(LatLng origemAtual) async {
    _recalculando = true;
    FlutterForegroundTask.sendDataToMain({'tipo': 'recalculando', 'valor': true});
    try {
      final novoResultado = await _apiClient.estimateTrip(
        origem: '${origemAtual.latitude},${origemAtual.longitude}',
        destino: _destino!,
        vehicleModelId: _vehicleModelId!,
        precoPorLitro: _precoPorLitro,
        precoPorKWh: _precoPorKWh,
      );
      _breakdown = novoResultado;
      _ultimoStepFalado = null;
      _recalculando = false;
      _tts.speak('Rota recalculada');
      FlutterForegroundTask.sendDataToMain({
        'tipo': 'rota_recalculada',
        'breakdownJson': jsonEncode(novoResultado.toJson()),
      });
    } catch (_) {
      _recalculando = false;
      FlutterForegroundTask.sendDataToMain({'tipo': 'recalculando', 'valor': false, 'erro': true});
    }
  }

  @override
  void onRepeatEvent(DateTime timestamp) {
    // Não usado — a atualização é orientada por posição do GPS (a cada leitura
    // do stream), não por um intervalo fixo. `ForegroundTaskEventAction.nothing()`
    // é passado no `init` pra nem chamar isso.
  }

  @override
  Future<void> onDestroy(DateTime timestamp, bool isTimeout) async {
    await _positionSub?.cancel();
    _positionSub = null;
    await _tts.stop();
  }
}
