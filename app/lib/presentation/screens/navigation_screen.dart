import 'dart:async';
import 'dart:convert';

import 'package:dio/dio.dart';
import 'package:flutter/foundation.dart' show TargetPlatform, defaultTargetPlatform, kIsWeb;
import 'package:flutter/material.dart';
import 'package:flutter_foreground_task/flutter_foreground_task.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart';
import 'package:permission_handler/permission_handler.dart';

import '../../data/api_client.dart';
import '../../data/device_id.dart';
import '../../domain/models/road_alert.dart';
import '../../domain/models/road_alert_type.dart';
import '../../domain/models/traffic_report.dart';
import '../../domain/models/traffic_severity.dart';
import '../../domain/models/trip_cost_breakdown.dart';
import '../../domain/navigation/deviation_detector.dart';
import '../../domain/navigation/navigation_task_handler.dart';
import '../../domain/navigation/road_alert_proximity.dart';
import '../../domain/navigation/route_progress.dart';
import '../../domain/navigation/traffic_detector.dart';
import '../widgets/road_alert_picker.dart';
import '../widgets/trip_map.dart';

enum _NavStatus { carregando, semPermissao, semServico, ativo }

bool get _isAndroid => !kIsWeb && defaultTargetPlatform == TargetPlatform.android;

/// Navegação turn-by-turn ao vivo pra uma viagem já calculada — mapa segue
/// a posição GPS, banner mostra a instrução atual, voz fala a instrução
/// quando ela muda, recalcula a rota se o usuário sair do caminho por 3
/// leituras de GPS seguidas (só quando [destino]/[vehicleModelId] são
/// passados — viagem de ida-e-volta ou com paradas não suporta recálculo).
///
/// **No Android** (Fase 6.3), tudo isso roda dentro de um serviço em
/// primeiro plano (`NavigationTaskHandler`, isolate separado com
/// notificação persistente) — sobrevive à tela apagada e ao usuário
/// trocando de app. **Em outras plataformas** (Windows/web, sem esse
/// conceito de serviço em segundo plano) a tela assina o GPS direto, mesmo
/// jeito de sempre — a navegação só funciona com o app aberto/tela ligada
/// ali, e não tem como ser diferente (sem foreground service no Windows).
class NavigationScreen extends StatefulWidget {
  final TripCostBreakdown breakdown;

  /// Estes 4 parâmetros habilitam o recálculo de rota em desvio — vêm
  /// preenchidos só quando a viagem foi calculada como trecho único (não
  /// ida-e-volta, não com paradas). `null` = recálculo desabilitado, mesmo
  /// comportamento nos dois ramos (Android/outras plataformas).
  final String? destino;
  final int? vehicleModelId;
  final double? precoPorLitro;
  final double? precoPorKWh;

  const NavigationScreen({
    super.key,
    required this.breakdown,
    this.destino,
    this.vehicleModelId,
    this.precoPorLitro,
    this.precoPorKWh,
  });

  @override
  State<NavigationScreen> createState() => _NavigationScreenState();
}

class _NavigationScreenState extends State<NavigationScreen> {
  final MapController _mapController = MapController();
  final ApiClient _apiClient = ApiClient();

  late TripCostBreakdown _breakdown = widget.breakdown;
  _NavStatus _status = _NavStatus.carregando;
  LatLng? _posicaoAtual;
  RouteProgress? _progresso;
  bool _recalculando = false;
  // Alertas de trânsito conhecidos (Fase 6.6) — no Android vêm do polling
  // que já roda dentro do NavigationTaskHandler; no Windows, de um polling
  // próprio desta tela (ver _pollNearbyAlertsWindows).
  List<RoadAlert> _alertasConhecidos = [];
  // Relatos de trânsito lento conhecidos (Fase 6.7) — mesma origem dupla de
  // _alertasConhecidos.
  List<TrafficReport> _trafegoConhecido = [];

  bool get _recalculoHabilitado => widget.destino != null && widget.vehicleModelId != null;

  // --- Só usados no ramo Windows/outras plataformas (sem serviço em segundo
  // plano) — no Android essa lógica vive inteira em NavigationTaskHandler. ---
  final FlutterTts _tts = FlutterTts();
  final DeviationDetector _detector = DeviationDetector();
  final RoadAlertProximityChecker _alertProximity = RoadAlertProximityChecker();
  final TrafficDetector _trafficDetector = TrafficDetector();
  StreamSubscription<Position>? _positionSub;
  Timer? _roadAlertPollingTimer;
  Timer? _trafficPollingTimer;
  int? _ultimoStepFalado;

  @override
  void initState() {
    super.initState();
    _tts.setLanguage('pt-BR');
    _iniciar();
  }

  @override
  void dispose() {
    if (_isAndroid) {
      FlutterForegroundTask.removeTaskDataCallback(_onDadosDoServico);
      FlutterForegroundTask.stopService();
    } else {
      _positionSub?.cancel();
      _roadAlertPollingTimer?.cancel();
      _trafficPollingTimer?.cancel();
      _tts.stop();
    }
    super.dispose();
  }

  Future<void> _iniciar() async {
    setState(() => _status = _NavStatus.carregando);

    final servicoLigado = await Geolocator.isLocationServiceEnabled();
    if (!servicoLigado) {
      setState(() => _status = _NavStatus.semServico);
      return;
    }

    var permissao = await Geolocator.checkPermission();
    if (permissao == LocationPermission.denied) {
      permissao = await Geolocator.requestPermission();
    }
    if (permissao == LocationPermission.denied || permissao == LocationPermission.deniedForever) {
      setState(() => _status = _NavStatus.semPermissao);
      return;
    }

    if (_isAndroid) {
      await _iniciarServicoEmSegundoPlano();
    } else {
      _positionSub = Geolocator.getPositionStream(
        locationSettings: const LocationSettings(accuracy: LocationAccuracy.high, distanceFilter: 5),
      ).listen(_onPosicaoWindows);
      // O primeiro poll de verdade acontece dentro de _onPosicaoWindows, assim
      // que a primeira posição chega (chamar aqui seria inútil — _posicaoAtual
      // ainda está null nesse ponto). O timer só cobre as atualizações seguintes.
      _roadAlertPollingTimer = Timer.periodic(kRoadAlertPollingInterval, (_) => _pollNearbyAlertsWindows());
      _trafficPollingTimer = Timer.periodic(kTrafficPollingInterval, (_) => _pollNearbyTrafficWindows());
    }

    setState(() => _status = _NavStatus.ativo);
  }

  Future<void> _iniciarServicoEmSegundoPlano() async {
    if (!mounted) return;
    // "Permitir sempre" é um passo à parte do foreground que já foi
    // concedido acima — pedir sem explicar antes é mal visto (e o SO só
    // mostra essa opção quando pedida separada, não junto com FINE/COARSE).
    // Se o usuário recusar, a navegação segue normal em primeiro plano —
    // só não sobrevive tela apagada/troca de app.
    if (!await Permission.locationAlways.isGranted) {
      final aceitou = await _mostrarRationaleSegundoPlano();
      if (aceitou && mounted) {
        await Permission.locationAlways.request();
      }
    }

    FlutterForegroundTask.init(
      androidNotificationOptions: AndroidNotificationOptions(
        channelId: 'rotacusto_navigation',
        channelName: 'Navegação RotaCusto',
        channelDescription: 'Mostra a instrução atual enquanto a navegação está ativa.',
        onlyAlertOnce: true,
      ),
      iosNotificationOptions: const IOSNotificationOptions(showNotification: false, playSound: false),
      foregroundTaskOptions: ForegroundTaskOptions(
        eventAction: ForegroundTaskEventAction.nothing(),
        autoRunOnBoot: false,
        allowWakeLock: true,
      ),
    );

    final notificacaoPermitida = await FlutterForegroundTask.checkNotificationPermission();
    if (notificacaoPermitida != NotificationPermission.granted) {
      await FlutterForegroundTask.requestNotificationPermission();
    }

    await FlutterForegroundTask.clearAllData();
    await FlutterForegroundTask.saveData(key: kNavigationBreakdownKey, value: jsonEncode(_breakdown.toJson()));
    if (widget.destino != null) {
      await FlutterForegroundTask.saveData(key: kNavigationDestinoKey, value: widget.destino!);
    }
    if (widget.vehicleModelId != null) {
      await FlutterForegroundTask.saveData(key: kNavigationVehicleModelIdKey, value: widget.vehicleModelId!);
    }
    if (widget.precoPorLitro != null) {
      await FlutterForegroundTask.saveData(key: kNavigationPrecoPorLitroKey, value: widget.precoPorLitro!);
    }
    if (widget.precoPorKWh != null) {
      await FlutterForegroundTask.saveData(key: kNavigationPrecoPorKWhKey, value: widget.precoPorKWh!);
    }

    FlutterForegroundTask.addTaskDataCallback(_onDadosDoServico);
    await FlutterForegroundTask.startService(
      serviceId: 5000,
      serviceTypes: const [ForegroundServiceTypes.location],
      notificationTitle: 'RotaCusto',
      notificationText: 'Preparando navegação...',
      callback: navigationTaskStartCallback,
    );
  }

  Future<bool> _mostrarRationaleSegundoPlano() async {
    if (!mounted) return false;
    return await showDialog<bool>(
          context: context,
          builder: (context) => AlertDialog(
            title: const Text('Navegar com a tela apagada'),
            content: const Text(
              'Pra continuar te guiando por voz mesmo com a tela apagada ou usando outro '
              'app, o RotaCusto precisa da permissão de localização "Permitir sempre". '
              'Sem ela, a navegação continua funcionando normal, só não acompanha se você '
              'sair do app.',
            ),
            actions: [
              TextButton(onPressed: () => Navigator.of(context).pop(false), child: const Text('Agora não')),
              FilledButton(onPressed: () => Navigator.of(context).pop(true), child: const Text('Permitir')),
            ],
          ),
        ) ??
        false;
  }

  void _onDadosDoServico(Object data) {
    if (data is! Map) return;
    switch (data['tipo']) {
      case 'posicao':
        final atual = LatLng((data['lat'] as num).toDouble(), (data['lon'] as num).toDouble());
        final alertasJson = data['alertasConhecidosJson'] as String?;
        final trafegoJson = data['trafegoConhecidoJson'] as String?;
        setState(() {
          _posicaoAtual = atual;
          _progresso = RouteProgress(
            currentStepIndex: data['currentStepIndex'] as int?,
            distanciaAteProximaViradaM: (data['distanciaAteProximaViradaM'] as num).toDouble(),
            distanciaAteRotaM: 0,
          );
          if (alertasJson != null) {
            _alertasConhecidos = (jsonDecode(alertasJson) as List<dynamic>)
                .map((a) => RoadAlert.fromJson(a as Map<String, dynamic>))
                .toList();
          }
          if (trafegoJson != null) {
            _trafegoConhecido = (jsonDecode(trafegoJson) as List<dynamic>)
                .map((t) => TrafficReport.fromJson(t as Map<String, dynamic>))
                .toList();
          }
        });
        final zoomAtual = _mapController.camera.zoom;
        _mapController.move(atual, zoomAtual < 15 ? 17 : zoomAtual);
      case 'recalculando':
        setState(() => _recalculando = data['valor'] as bool);
        if (data['erro'] == true && mounted) {
          ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Não foi possível recalcular a rota. Tentando de novo se o desvio continuar.')),
          );
        }
      case 'rota_recalculada':
        final novoResultado =
            TripCostBreakdown.fromJson(jsonDecode(data['breakdownJson'] as String) as Map<String, dynamic>);
        setState(() {
          _breakdown = novoResultado;
          _recalculando = false;
        });
      case 'alerta_proximo':
        // A voz já foi falada dentro do NavigationTaskHandler (Android) —
        // aqui só mostra o aviso visual, pra não duplicar o TTS.
        final alerta = RoadAlert.fromJson(jsonDecode(data['alertaJson'] as String) as Map<String, dynamic>);
        _mostrarAvisoDeAlerta(alerta);
    }
  }

  void _mostrarAvisoDeAlerta(RoadAlert alerta) {
    if (!mounted) return;
    // Confirmação/reputação (Fase 6.8) — botões de "ainda está lá"/"já foi
    // resolvido" direto no aviso de proximidade, sem precisar de outra tela.
    // Duração maior que o SnackBar de instrução normal (7s em vez de 5s) pra
    // dar tempo de tocar num dos dois. Layout (ícones em vez de texto pros
    // botões, pra caber) precisa de ajuste ao testar ao vivo, mesmo processo
    // já usado pro RoadAlertPicker na Fase 6.6.
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(
      content: Row(
        children: [
          Icon(alerta.tipo.icon),
          const SizedBox(width: 12),
          Expanded(child: Text(alerta.tipo.label)),
          IconButton(
            icon: const Icon(Icons.thumb_up_outlined, size: 20),
            tooltip: 'Ainda está lá',
            onPressed: () => _votarAlerta(alerta, confirma: true),
          ),
          IconButton(
            icon: const Icon(Icons.thumb_down_outlined, size: 20),
            tooltip: 'Já foi resolvido',
            onPressed: () => _votarAlerta(alerta, confirma: false),
          ),
        ],
      ),
      duration: const Duration(seconds: 7),
    ));
  }

  Future<void> _votarAlerta(RoadAlert alerta, {required bool confirma}) async {
    ScaffoldMessenger.of(context).hideCurrentSnackBar();
    final deviceId = await getOrCreateDeviceId();
    try {
      await _apiClient.voteRoadAlert(alerta.id, deviceId, confirma);
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Valeu por avisar!'), duration: Duration(seconds: 2)));
    } on DioException catch (e) {
      if (!mounted) return;
      final jaVotou = e.response?.statusCode == 409;
      ScaffoldMessenger.of(context).showSnackBar(SnackBar(
        content: Text(jaVotou ? 'Você já votou nesse alerta.' : 'Não foi possível registrar seu voto.'),
        duration: const Duration(seconds: 2),
      ));
    } catch (_) {
      // Engolido silenciosamente — mesmo padrão tolerante do resto desta tela.
    }
  }

  // --- Ramo Windows/outras plataformas — mesma lógica do NavigationTaskHandler
  // (Android), reimplementada aqui porque não existe isolate de segundo plano
  // fora do Android (mesma duplicação deliberada de sempre, ver Fase 6.3). ---

  Future<void> _pollNearbyAlertsWindows() async {
    final posicao = _posicaoAtual;
    if (posicao == null) return;
    try {
      final alertas = await _apiClient.fetchNearbyRoadAlerts(
        posicao.latitude,
        posicao.longitude,
        raioKm: kRoadAlertFetchRadiusKm,
      );
      if (mounted) setState(() => _alertasConhecidos = alertas);
    } catch (_) {
      // Sem sinal ou back-end fora do ar — mantém a última lista conhecida.
    }
  }

  Future<void> _pollNearbyTrafficWindows() async {
    final posicao = _posicaoAtual;
    if (posicao == null) return;
    try {
      final trafego = await _apiClient.fetchNearbyTraffic(
        posicao.latitude,
        posicao.longitude,
        raioKm: kTrafficFetchRadiusKm,
      );
      if (mounted) setState(() => _trafegoConhecido = trafego);
    } catch (_) {
      // Mesma tolerância de _pollNearbyAlertsWindows.
    }
  }

  /// Relato automático (Fase 6.7) — fire-and-forget, mesma tolerância a
  /// falha dos outros pollings/relatos desta tela.
  Future<void> _reportarTrafegoAutoWindows(TrafficSeverity severidade, LatLng posicao) async {
    try {
      await _apiClient.reportTraffic(severidade, posicao.latitude, posicao.longitude);
    } catch (_) {
      // Sem sinal ou back-end fora do ar — só não reporta desta vez.
    }
  }

  void _onPosicaoWindows(Position posicao) {
    final atual = LatLng(posicao.latitude, posicao.longitude);
    final primeiraPosicao = _posicaoAtual == null;
    final progresso = RouteProgressCalculator.calculate(
      posicaoAtual: atual,
      geometriaRota: _breakdown.geometriaRota,
      passosRota: _breakdown.passosRota,
    );
    if (primeiraPosicao) {
      _pollNearbyAlertsWindows();
      _pollNearbyTrafficWindows();
    }

    if (progresso.currentStepIndex != null && progresso.currentStepIndex != _ultimoStepFalado) {
      _ultimoStepFalado = progresso.currentStepIndex;
      _tts.speak(_breakdown.passosRota[progresso.currentStepIndex!].instrucao);
    }

    final alertaProximo = _alertProximity.checkProximity(atual, _alertasConhecidos);
    if (alertaProximo != null) {
      _tts.speak(alertaProximo.tipo.vozTexto);
      _mostrarAvisoDeAlerta(alertaProximo);
    }

    // Detecção automática de trânsito lento (Fase 6.7) — mesma lógica do
    // NavigationTaskHandler (Android), reimplementada aqui (duplicação
    // deliberada, ver Fase 6.3).
    if (progresso.currentStepIndex != null) {
      final passoAtual = _breakdown.passosRota[progresso.currentStepIndex!];
      final velocidadeEsperadaMps = passoAtual.duracaoS > 0 ? passoAtual.distanciaM / passoAtual.duracaoS : 0.0;
      final severidade = _trafficDetector.registrarLeitura(
        velocidadeAtualMps: posicao.speed,
        velocidadeEsperadaMps: velocidadeEsperadaMps,
        agora: DateTime.now(),
      );
      if (severidade != null) {
        _reportarTrafegoAutoWindows(severidade, atual);
      }
    }

    setState(() {
      _posicaoAtual = atual;
      _progresso = progresso;
    });

    final zoomAtual = _mapController.camera.zoom;
    _mapController.move(atual, zoomAtual < 15 ? 17 : zoomAtual);

    if (_recalculoHabilitado && !_recalculando && _detector.registrarLeitura(progresso.distanciaAteRotaM)) {
      _recalcularRotaWindows(atual);
    }
  }

  Future<void> _recalcularRotaWindows(LatLng origemAtual) async {
    setState(() => _recalculando = true);
    try {
      final novoResultado = await _apiClient.estimateTrip(
        origem: '${origemAtual.latitude},${origemAtual.longitude}',
        destino: widget.destino!,
        vehicleModelId: widget.vehicleModelId!,
        precoPorLitro: widget.precoPorLitro,
        precoPorKWh: widget.precoPorKWh,
      );
      if (!mounted) return;
      setState(() {
        _breakdown = novoResultado;
        _ultimoStepFalado = null;
        _recalculando = false;
      });
      _tts.speak('Rota recalculada');
    } catch (_) {
      if (!mounted) return;
      setState(() => _recalculando = false);
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Não foi possível recalcular a rota. Tentando de novo se o desvio continuar.')),
      );
    }
  }

  // --- Reportar alerta de trânsito (Fase 6.6) — funciona igual nas duas
  // plataformas, é uma ação de UI (precisa de alguém tocando num botão), não
  // algo orientado por posição — por isso fica só aqui, não duplicado no
  // NavigationTaskHandler. ---

  void _abrirSeletorDeAlerta() {
    if (_posicaoAtual == null) return;
    showModalBottomSheet<void>(
      context: context,
      // Sem isso, a altura do sheet fica travada em ~metade da tela e os 6
      // tipos + título estouram em celulares menores (achado testando de
      // verdade — ver comentário em RoadAlertPicker).
      isScrollControlled: true,
      builder: (context) => RoadAlertPicker(
        onSelected: (tipo) {
          Navigator.of(context).pop();
          _reportarAlerta(tipo);
        },
      ),
    );
  }

  Future<void> _reportarAlerta(RoadAlertType tipo) async {
    final posicao = _posicaoAtual;
    if (posicao == null) return;
    try {
      await _apiClient.reportRoadAlert(tipo, posicao.latitude, posicao.longitude);
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(SnackBar(content: Text('${tipo.label} reportado — obrigado por avisar!')));
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Não foi possível enviar o relato.')));
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: const Text('Navegação'),
        leading: IconButton(
          icon: const Icon(Icons.close_rounded),
          onPressed: () => Navigator.of(context).pop(),
        ),
        bottom: _recalculando
            ? const PreferredSize(
                preferredSize: Size.fromHeight(3),
                child: LinearProgressIndicator(minHeight: 3),
              )
            : null,
      ),
      body: switch (_status) {
        _NavStatus.carregando => const Center(child: CircularProgressIndicator()),
        _NavStatus.semServico => _mensagemErro(
            icon: Icons.location_disabled_rounded,
            texto: 'A localização do dispositivo está desligada. Ative-a nas configurações e tente de novo.',
          ),
        _NavStatus.semPermissao => _mensagemErro(
            icon: Icons.location_off_rounded,
            texto: 'Sem permissão de localização, não dá pra navegar. Conceda a permissão e tente de novo.',
          ),
        _NavStatus.ativo => _buildNavegacaoAtiva(context),
      },
      floatingActionButton: _status == _NavStatus.ativo && _posicaoAtual != null
          ? FloatingActionButton.extended(
              onPressed: _abrirSeletorDeAlerta,
              icon: const Icon(Icons.report_gmailerrorred_rounded),
              label: const Text('Reportar'),
            )
          : null,
    );
  }

  Widget _mensagemErro({required IconData icon, required String texto}) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(24),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(icon, size: 48),
            const SizedBox(height: 16),
            Text(texto, textAlign: TextAlign.center),
            const SizedBox(height: 16),
            FilledButton(onPressed: _iniciar, child: const Text('Tentar de novo')),
          ],
        ),
      ),
    );
  }

  Widget _buildNavegacaoAtiva(BuildContext context) {
    final stepIndex = _progresso?.currentStepIndex;
    final stepAtual = stepIndex != null ? _breakdown.passosRota[stepIndex] : null;

    return Stack(
      children: [
        TripMap(
          breakdown: _breakdown,
          mapController: _mapController,
          posicaoAtual: _posicaoAtual,
          alertasAoVivo: _alertasConhecidos,
          trafegoAoVivo: _trafegoConhecido,
        ),
        if (stepAtual != null)
          Positioned(
            top: 12,
            left: 12,
            right: 12,
            child: Card(
              elevation: 4,
              child: Padding(
                padding: const EdgeInsets.all(16),
                child: Row(
                  children: [
                    const Icon(Icons.turn_right_rounded, size: 32),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text(
                            '${_progresso!.distanciaAteProximaViradaM.round()} m',
                            style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
                          ),
                          Text(stepAtual.instrucao),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
          ),
        if (_posicaoAtual == null)
          const Positioned(
            bottom: 24,
            left: 0,
            right: 0,
            child: Center(child: CircularProgressIndicator()),
          ),
      ],
    );
  }
}
