import 'package:dio/dio.dart';
import 'package:flutter/material.dart';

import '../../data/api_client.dart';
import '../../domain/models/address_suggestion.dart';
import '../../domain/models/trip_cost_breakdown.dart';
import '../../domain/models/vehicle_model.dart';
import '../widgets/address_field.dart';
import '../widgets/trip_map.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final ApiClient _apiClient = ApiClient();
  final _origemController = TextEditingController(text: 'Copacabana, Rio de Janeiro, RJ');
  final _destinoController = TextEditingController(text: 'Guarapari, ES');
  final _precoController = TextEditingController(text: '6.09');

  List<VehicleModel> _vehicleModels = [];
  VehicleModel? _selectedVehicle;
  AddressSuggestion? _origemSelecionada;
  AddressSuggestion? _destinoSelecionado;
  bool _loadingModels = true;
  bool _loadingEstimate = false;
  TripCostBreakdown? _breakdown;
  String? _errorMessage;

  @override
  void initState() {
    super.initState();
    _loadVehicleModels();
  }

  @override
  void dispose() {
    _origemController.dispose();
    _destinoController.dispose();
    _precoController.dispose();
    super.dispose();
  }

  Future<void> _loadVehicleModels() async {
    try {
      final models = await _apiClient.fetchVehicleModels();
      setState(() {
        _vehicleModels = models;
        _selectedVehicle = models.isNotEmpty ? models.first : null;
        _loadingModels = false;
      });
    } catch (_) {
      setState(() {
        _loadingModels = false;
        _errorMessage = 'Não foi possível carregar o catálogo de veículos. '
            'O back-end está rodando em localhost:8080?';
      });
    }
  }

  Future<void> _calcular() async {
    if (_selectedVehicle == null) return;
    final preco = double.tryParse(_precoController.text.replaceAll(',', '.'));
    if (preco == null || preco <= 0) {
      setState(() => _errorMessage = 'Informe um preço de combustível válido.');
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
        precoCombustivelPorLitro: preco,
      );
      setState(() {
        _breakdown = result;
        _loadingEstimate = false;
      });
    } on DioException catch (e) {
      final data = e.response?.data;
      final message = (data is Map && data['message'] != null) ? data['message'].toString() : 'Erro ao calcular a viagem.';
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
      appBar: AppBar(title: const Text('RotaCusto')),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: LayoutBuilder(
          builder: (context, constraints) {
            final form = _buildForm();
            final map = TripMap(breakdown: _breakdown);
            final isWide = constraints.maxWidth > 900;

            if (isWide) {
              return Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  SizedBox(width: 380, child: SingleChildScrollView(child: form)),
                  const SizedBox(width: 16),
                  Expanded(child: map),
                ],
              );
            }
            return SingleChildScrollView(
              child: Column(
                children: [
                  form,
                  const SizedBox(height: 16),
                  SizedBox(height: 400, child: map),
                ],
              ),
            );
          },
        ),
      ),
    );
  }

  Widget _buildForm() {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
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
        const SizedBox(height: 12),
        _loadingModels
            ? const Padding(
                padding: EdgeInsets.symmetric(vertical: 8),
                child: LinearProgressIndicator(),
              )
            : DropdownButtonFormField<VehicleModel>(
                initialValue: _selectedVehicle,
                decoration: const InputDecoration(labelText: 'Veículo'),
                isExpanded: true,
                items: _vehicleModels
                    .map((v) => DropdownMenuItem(
                          value: v,
                          child: Text(v.displayName, overflow: TextOverflow.ellipsis),
                        ))
                    .toList(),
                onChanged: (v) => setState(() => _selectedVehicle = v),
              ),
        const SizedBox(height: 12),
        TextField(
          controller: _precoController,
          decoration: const InputDecoration(labelText: 'Preço do combustível (R\$/L)'),
          keyboardType: const TextInputType.numberWithOptions(decimal: true),
        ),
        const SizedBox(height: 16),
        FilledButton(
          onPressed: _loadingEstimate ? null : _calcular,
          child: _loadingEstimate
              ? const SizedBox(
                  width: 20,
                  height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2),
                )
              : const Text('Calcular'),
        ),
        if (_errorMessage != null) ...[
          const SizedBox(height: 12),
          Text(_errorMessage!, style: TextStyle(color: Theme.of(context).colorScheme.error)),
        ],
        if (_breakdown != null) ...[
          const SizedBox(height: 20),
          _buildBreakdown(_breakdown!),
        ],
      ],
    );
  }

  Widget _buildBreakdown(TripCostBreakdown b) {
    String currency(double v) => 'R\$ ${v.toStringAsFixed(2)}';

    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              '${b.distanciaKm.toStringAsFixed(1)} km · ${(b.duracaoMin / 60).toStringAsFixed(1)} h',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const Divider(),
            _buildRow('Combustível', currency(b.custoCombustivel)),
            _buildRow('Desgaste do veículo', currency(b.custoDesgaste)),
            _buildRow('Pedágios (${b.pedagiosNaRota.length})', currency(b.custoPedagio)),
            _buildRow('Lanche', currency(b.custoLanche)),
            const Divider(),
            _buildRow('Total', currency(b.total), bold: true),
            if (b.postoSugerido != null) ...[
              const SizedBox(height: 12),
              Row(
                children: [
                  const Icon(Icons.local_gas_station, color: Colors.green, size: 18),
                  const SizedBox(width: 6),
                  Expanded(
                    child: Text(
                      'Sugestão de parada: ${b.postoSugerido!.nome}',
                      style: const TextStyle(fontSize: 13),
                    ),
                  ),
                ],
              ),
            ],
          ],
        ),
      ),
    );
  }

  Widget _buildRow(String label, String value, {bool bold = false}) {
    final style = bold ? const TextStyle(fontWeight: FontWeight.bold) : null;
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(
        mainAxisAlignment: MainAxisAlignment.spaceBetween,
        children: [Text(label, style: style), Text(value, style: style)],
      ),
    );
  }
}
