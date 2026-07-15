import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../data/api_client.dart';
import '../../domain/models/address_suggestion.dart';
import '../../domain/models/tipo_combustivel.dart';
import '../../domain/models/trip_cost_breakdown.dart';
import '../../domain/models/vehicle_model.dart';
import '../../domain/models/vehicle_model_summary.dart';
import '../widgets/address_field.dart';
import '../widgets/cost_breakdown_bar.dart';
import '../widgets/section_card.dart';
import '../widgets/trip_map.dart';
import '../widgets/vehicle_search_field.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

// Médias aproximadas de mercado — só o valor inicial sugerido; o usuário
// sempre pode digitar o preço real de onde vai abastecer/carregar.
const _precoPadraoPorCombustivel = {
  TipoCombustivel.gasolina: '6.09',
  TipoCombustivel.etanol: '4.09',
  TipoCombustivel.diesel: '6.29',
  TipoCombustivel.eletrico: '0.90',
};

class _HomeScreenState extends State<HomeScreen> {
  final ApiClient _apiClient = ApiClient();
  final _origemController = TextEditingController(text: 'Copacabana, Rio de Janeiro, RJ');
  final _destinoController = TextEditingController(text: 'Guarapari, ES');
  final _precoController = TextEditingController(text: _precoPadraoPorCombustivel[TipoCombustivel.gasolina]);

  VehicleModelSummary? _selectedModelSummary;
  List<VehicleModel> _availableVersions = [];
  bool _loadingVersions = false;
  VehicleModel? _selectedVehicle;
  AddressSuggestion? _origemSelecionada;
  AddressSuggestion? _destinoSelecionado;
  bool _loadingEstimate = false;
  TripCostBreakdown? _breakdown;
  String? _errorMessage;

  @override
  void dispose() {
    _origemController.dispose();
    _destinoController.dispose();
    _precoController.dispose();
    super.dispose();
  }

  Future<void> _onModelSelected(VehicleModelSummary? summary) async {
    setState(() {
      _selectedModelSummary = summary;
      _availableVersions = [];
      _selectedVehicle = null;
    });
    if (summary == null) return;

    setState(() => _loadingVersions = true);
    try {
      final versions = await _apiClient.fetchVehicleVersions(summary.marca, summary.modelo);
      if (!mounted) return;
      setState(() {
        _availableVersions = versions;
        _loadingVersions = false;
      });
      // Anos vêm do mais recente pro mais antigo — pré-seleciona o mais recente.
      if (versions.isNotEmpty) {
        _onVehicleSelected(versions.first);
      }
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _loadingVersions = false;
        _errorMessage = 'Não foi possível carregar os anos desse modelo.';
      });
    }
  }

  void _onVehicleSelected(VehicleModel? v) {
    final combustivelMudou =
        v != null && _selectedVehicle != null && _selectedVehicle!.tipoCombustivel != v.tipoCombustivel;
    setState(() {
      _selectedVehicle = v;
      if (combustivelMudou) {
        _precoController.text = _precoPadraoPorCombustivel[v.tipoCombustivel]!;
      }
    });
  }

  Future<void> _calcular() async {
    if (_selectedVehicle == null) return;
    final preco = double.tryParse(_precoController.text.replaceAll(',', '.'));
    final isEletrico = _selectedVehicle!.isEletrico;
    if (preco == null || preco <= 0) {
      setState(() => _errorMessage = isEletrico
          ? 'Informe um preço de energia (R\$/kWh) válido.'
          : 'Informe um preço de combustível válido.');
      return;
    }

    setState(() {
      _loadingEstimate = true;
      _errorMessage = null;
      _breakdown = null;
    });

    try {
      // Se o usuário escolheu uma sugestão do dropdown, manda a coordenada
      // exata direto (o back-end já aceita "lat,lon"), evitando um novo
      // geocode (mais rápido e sem risco de resolver para outro lugar).
      final origem = _origemSelecionada != null
          ? '${_origemSelecionada!.lat},${_origemSelecionada!.lon}'
          : _origemController.text;
      final destino = _destinoSelecionado != null
          ? '${_destinoSelecionado!.lat},${_destinoSelecionado!.lon}'
          : _destinoController.text;

      final result = await _apiClient.estimateTrip(
        origem: origem,
        destino: destino,
        vehicleModelId: _selectedVehicle!.id,
        precoPorLitro: isEletrico ? null : preco,
        precoPorKWh: isEletrico ? preco : null,
      );
      setState(() {
        _breakdown = result;
        _loadingEstimate = false;
      });
    } on DioException catch (e) {
      final data = e.response?.data;
      String message;
      if (data is Map && data['message'] != null) {
        message = data['message'].toString();
      } else if (e.type == DioExceptionType.receiveTimeout || e.type == DioExceptionType.connectionTimeout) {
        message = 'A busca de pedágios/postos está lenta (fonte pública sobrecarregada). Tente de novo em instantes.';
      } else if (e.type == DioExceptionType.connectionError) {
        message = 'Não foi possível falar com o back-end. Ele está rodando em localhost:8080?';
      } else {
        message = 'Erro ao calcular a viagem.';
      }
      setState(() {
        _loadingEstimate = false;
        _errorMessage = message;
      });
    } catch (e) {
      setState(() {
        _loadingEstimate = false;
        _errorMessage = 'Erro inesperado: $e';
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(
        title: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.route_rounded, color: Theme.of(context).colorScheme.primary),
            const SizedBox(width: 10),
            const Text('RotaCusto'),
          ],
        ),
      ),
      body: SafeArea(
        child: Padding(
          padding: const EdgeInsets.all(16),
          child: LayoutBuilder(
            builder: (context, constraints) {
              final form = _buildForm();
              final map = ClipRRect(
                borderRadius: BorderRadius.circular(20),
                child: TripMap(breakdown: _breakdown),
              );
              final isWide = constraints.maxWidth > 900;

              if (isWide) {
                return Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    SizedBox(width: 400, child: SingleChildScrollView(child: form)),
                    const SizedBox(width: 20),
                    Expanded(child: map),
                  ],
                );
              }
              return SingleChildScrollView(
                child: Column(
                  children: [
                    form,
                    const SizedBox(height: 16),
                    SizedBox(height: 320, child: map),
                  ],
                ),
              );
            },
          ),
        ),
      ),
    );
  }

  Widget _buildForm() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      spacing: 16,
      children: [
        SectionCard(
          icon: Icons.alt_route_rounded,
          title: 'Rota',
          child: Column(
            children: [
              AddressField(
                controller: _origemController,
                label: 'Origem',
                fetchSuggestions: _apiClient.suggestAddress,
                onSelected: (s) => _origemSelecionada = s,
              ),
              const SizedBox(height: 12),
              AddressField(
                controller: _destinoController,
                label: 'Destino',
                fetchSuggestions: _apiClient.suggestAddress,
                onSelected: (s) => _destinoSelecionado = s,
              ),
            ],
          ),
        ),
        SectionCard(
          icon: Icons.directions_car_filled_rounded,
          title: 'Veículo',
          child: Column(
            children: [
              VehicleSearchField(
                initialValue: _selectedModelSummary,
                fetchSuggestions: _apiClient.searchVehicleModels,
                onSelected: _onModelSelected,
              ),
              if (_loadingVersions) ...[
                const SizedBox(height: 8),
                const LinearProgressIndicator(),
              ],
              if (_availableVersions.isNotEmpty) ...[
                const SizedBox(height: 12),
                DropdownButtonFormField<VehicleModel>(
                  initialValue: _selectedVehicle,
                  decoration: const InputDecoration(labelText: 'Ano / combustível'),
                  items: _availableVersions
                      .map((v) => DropdownMenuItem(value: v, child: Text(v.versaoLabel)))
                      .toList(),
                  onChanged: _onVehicleSelected,
                ),
              ],
              const SizedBox(height: 12),
              TextField(
                controller: _precoController,
                decoration: InputDecoration(
                  labelText: (_selectedVehicle?.isEletrico ?? false)
                      ? 'Preço da energia (R\$/kWh)'
                      : 'Preço do ${(_selectedVehicle?.tipoCombustivel ?? TipoCombustivel.gasolina).label.toLowerCase()} (R\$/L)',
                  prefixIcon: Icon((_selectedVehicle?.isEletrico ?? false) ? Icons.bolt_rounded : Icons.local_gas_station_rounded),
                ),
                keyboardType: const TextInputType.numberWithOptions(decimal: true),
              ),
            ],
          ),
        ),
        FilledButton.icon(
          onPressed: (_loadingEstimate || _selectedVehicle == null) ? null : _calcular,
          icon: _loadingEstimate
              ? const SizedBox(
                  width: 18,
                  height: 18,
                  child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white),
                )
              : const Icon(Icons.calculate_rounded),
          label: Text(_loadingEstimate ? 'Calculando...' : 'Calcular'),
        ),
        if (_errorMessage != null)
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: Theme.of(context).colorScheme.errorContainer,
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              children: [
                Icon(Icons.error_outline_rounded, color: Theme.of(context).colorScheme.onErrorContainer, size: 20),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(
                    _errorMessage!,
                    style: TextStyle(color: Theme.of(context).colorScheme.onErrorContainer),
                  ),
                ),
              ],
            ),
          ),
        if (_breakdown != null) _buildBreakdown(_breakdown!),
      ],
    );
  }

  Widget _buildBreakdown(TripCostBreakdown b) {
    final scheme = Theme.of(context).colorScheme;
    String currency(double v) => 'R\$ ${v.toStringAsFixed(2)}';

    return SectionCard(
      icon: Icons.receipt_long_rounded,
      title: 'Resumo da viagem',
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Icon(Icons.route_rounded, size: 16, color: scheme.onSurfaceVariant),
              const SizedBox(width: 6),
              Text(
                '${b.distanciaKm.toStringAsFixed(1)} km',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
              ),
              const SizedBox(width: 16),
              Icon(Icons.schedule_rounded, size: 16, color: scheme.onSurfaceVariant),
              const SizedBox(width: 6),
              Text(
                '${(b.duracaoMin / 60).toStringAsFixed(1)} h',
                style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700),
              ),
            ],
          ),
          const SizedBox(height: 20),
          CostBreakdownBar(
            segments: [
              CostSegment(
                label: (_selectedVehicle?.tipoCombustivel ?? TipoCombustivel.gasolina).label,
                value: b.custoCombustivel,
                light: const Color(0xFF2a78d6),
                dark: const Color(0xFF3987e5),
              ),
              CostSegment(
                label: 'Desgaste',
                value: b.custoDesgaste,
                light: const Color(0xFF1baf7a),
                dark: const Color(0xFF199e70),
              ),
              CostSegment(
                label: 'Pedágios',
                value: b.custoPedagio,
                light: const Color(0xFFeda100),
                dark: const Color(0xFFc98500),
              ),
              CostSegment(
                label: 'Lanche',
                value: b.custoLanche,
                light: const Color(0xFF008300),
                dark: const Color(0xFF008300),
              ),
            ],
          ),
          const SizedBox(height: 20),
          Container(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 14),
            decoration: BoxDecoration(
              color: scheme.primaryContainer,
              borderRadius: BorderRadius.circular(14),
            ),
            child: Row(
              mainAxisAlignment: MainAxisAlignment.spaceBetween,
              children: [
                Text('Total estimado', style: TextStyle(color: scheme.onPrimaryContainer, fontWeight: FontWeight.w600)),
                Text(
                  currency(b.total),
                  style: TextStyle(color: scheme.onPrimaryContainer, fontWeight: FontWeight.w800, fontSize: 20),
                ),
              ],
            ),
          ),
          if (b.postoSugerido != null) ...[
            const SizedBox(height: 12),
            Row(
              children: [
                Icon(Icons.local_gas_station_rounded, color: scheme.tertiary, size: 18),
                const SizedBox(width: 6),
                Expanded(
                  child: Text(
                    'Sugestão de parada: ${b.postoSugerido!.nome}',
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}
