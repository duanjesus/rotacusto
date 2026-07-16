import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart';

import '../../data/api_client.dart';
import '../../domain/models/trip_cost_breakdown.dart';
import '../../domain/navigation/deviation_detector.dart';
import '../../domain/navigation/route_progress.dart';
import '../widgets/trip_map.dart';

enum _NavStatus { carregando, semPermissao, semServico, ativo }

/// Navegação turn-by-turn ao vivo pra uma viagem já calculada — mapa segue
/// a posição GPS, banner mostra a instrução atual, voz fala a instrução
/// quando ela muda. Recalcula a rota se o usuário sair do caminho por 3
/// leituras de GPS seguidas (só quando [destino]/[vehicleModelId] são
/// passados — viagem de ida-e-volta não suporta recálculo, ver comentário
/// no construtor). Só funciona com o app aberto e a tela ligada (operação
/// em segundo plano é fora de escopo, item separado do punch list).
class NavigationScreen extends StatefulWidget {
  final TripCostBreakdown breakdown;

  /// Estes 4 parâmetros habilitam o recálculo de rota em desvio — vêm
  /// preenchidos só quando a viagem foi calculada como trecho único
  /// (não ida-e-volta). O `breakdown` combinado de ida+volta mistura a
  /// geometria/passos das duas pernas concatenados, e não dá pra saber de
  /// forma simples qual perna o usuário desviou — nesse caso os 4 ficam
  /// null e a tela se comporta como antes (só mostra a rota original).
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
  final FlutterTts _tts = FlutterTts();
  final ApiClient _apiClient = ApiClient();
  final DeviationDetector _detector = DeviationDetector();

  late TripCostBreakdown _breakdown = widget.breakdown;
  _NavStatus _status = _NavStatus.carregando;
  StreamSubscription<Position>? _positionSub;
  LatLng? _posicaoAtual;
  RouteProgress? _progresso;
  int? _ultimoStepFalado;
  bool _recalculando = false;

  bool get _recalculoHabilitado => widget.destino != null && widget.vehicleModelId != null;

  @override
  void initState() {
    super.initState();
    _tts.setLanguage('pt-BR');
    _iniciar();
  }

  @override
  void dispose() {
    _positionSub?.cancel();
    _tts.stop();
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

    setState(() => _status = _NavStatus.ativo);
    _positionSub = Geolocator.getPositionStream(
      locationSettings: const LocationSettings(accuracy: LocationAccuracy.high, distanceFilter: 5),
    ).listen(_onPosicao);
  }

  void _onPosicao(Position posicao) {
    final atual = LatLng(posicao.latitude, posicao.longitude);
    final progresso = RouteProgressCalculator.calculate(
      posicaoAtual: atual,
      geometriaRota: _breakdown.geometriaRota,
      passosRota: _breakdown.passosRota,
    );

    if (progresso.currentStepIndex != null && progresso.currentStepIndex != _ultimoStepFalado) {
      _ultimoStepFalado = progresso.currentStepIndex;
      _tts.speak(_breakdown.passosRota[progresso.currentStepIndex!].instrucao);
    }

    setState(() {
      _posicaoAtual = atual;
      _progresso = progresso;
    });

    final zoomAtual = _mapController.camera.zoom;
    _mapController.move(atual, zoomAtual < 15 ? 17 : zoomAtual);

    if (_recalculoHabilitado && !_recalculando && _detector.registrarLeitura(progresso.distanciaAteRotaM)) {
      _recalcularRota(atual);
    }
  }

  Future<void> _recalcularRota(LatLng origemAtual) async {
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
        // Os índices de step são relativos à NOVA lista de passos —
        // reaproveitar o índice antigo falaria a instrução errada.
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
        TripMap(breakdown: _breakdown, mapController: _mapController, posicaoAtual: _posicaoAtual),
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
