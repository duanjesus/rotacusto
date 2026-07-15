import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_map/flutter_map.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:geolocator/geolocator.dart';
import 'package:latlong2/latlong.dart';

import '../../domain/models/trip_cost_breakdown.dart';
import '../../domain/navigation/route_progress.dart';
import '../widgets/trip_map.dart';

enum _NavStatus { carregando, semPermissao, semServico, ativo }

/// Navegação turn-by-turn ao vivo pra uma viagem já calculada — mapa segue
/// a posição GPS, banner mostra a instrução atual, voz fala a instrução
/// quando ela muda. Sem recálculo de rota se o usuário sair do caminho, e
/// só funciona com o app aberto e a tela ligada (fora de escopo por ora,
/// documentado no plano da Fase 6).
class NavigationScreen extends StatefulWidget {
  final TripCostBreakdown breakdown;

  const NavigationScreen({super.key, required this.breakdown});

  @override
  State<NavigationScreen> createState() => _NavigationScreenState();
}

class _NavigationScreenState extends State<NavigationScreen> {
  final MapController _mapController = MapController();
  final FlutterTts _tts = FlutterTts();

  _NavStatus _status = _NavStatus.carregando;
  StreamSubscription<Position>? _positionSub;
  LatLng? _posicaoAtual;
  RouteProgress? _progresso;
  int? _ultimoStepFalado;

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
      geometriaRota: widget.breakdown.geometriaRota,
      passosRota: widget.breakdown.passosRota,
    );

    if (progresso.currentStepIndex != null && progresso.currentStepIndex != _ultimoStepFalado) {
      _ultimoStepFalado = progresso.currentStepIndex;
      _tts.speak(widget.breakdown.passosRota[progresso.currentStepIndex!].instrucao);
    }

    setState(() {
      _posicaoAtual = atual;
      _progresso = progresso;
    });

    final zoomAtual = _mapController.camera.zoom;
    _mapController.move(atual, zoomAtual < 15 ? 17 : zoomAtual);
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
    final stepAtual = stepIndex != null ? widget.breakdown.passosRota[stepIndex] : null;

    return Stack(
      children: [
        TripMap(breakdown: widget.breakdown, mapController: _mapController, posicaoAtual: _posicaoAtual),
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
