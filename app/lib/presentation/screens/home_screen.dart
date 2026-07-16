import 'package:dio/dio.dart';
import 'package:flutter/material.dart';
import 'package:geolocator/geolocator.dart';

import '../../data/api_client.dart';
import '../../data/last_trip_cache.dart';
import '../../domain/models/address_suggestion.dart';
import '../../domain/models/tipo_combustivel.dart';
import '../../domain/models/trip_cost_breakdown.dart';
import '../../domain/models/vehicle_model.dart';
import '../../domain/models/vehicle_model_summary.dart';
import '../../domain/models/vehicle_type.dart';
import '../widgets/address_field.dart';
import '../widgets/cost_breakdown_bar.dart';
import '../widgets/section_card.dart';
import '../widgets/trip_map.dart';
import '../widgets/vehicle_search_field.dart';
import '../../theme/auth_controller.dart';
import '../../theme/theme_controller.dart';
import 'login_screen.dart';
import 'navigation_screen.dart';
import 'trip_history_screen.dart';

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

/// Uma parada intermediária no roteiro — mesmo par controller+seleção já
/// usado pra origem/destino, só que numa lista crescível.
class _ParadaField {
  final TextEditingController controller = TextEditingController();
  AddressSuggestion? selecionada;
}

class _HomeScreenState extends State<HomeScreen> {
  final ApiClient _apiClient = ApiClient();
  final _origemController = TextEditingController(text: 'Copacabana, Rio de Janeiro, RJ');
  final _destinoController = TextEditingController(text: 'Guarapari, ES');
  final _precoController = TextEditingController(text: _precoPadraoPorCombustivel[TipoCombustivel.gasolina]);

  VehicleType _selectedTipo = VehicleType.carro;
  VehicleModelSummary? _selectedModelSummary;
  List<VehicleModel> _availableVersions = [];
  bool _loadingVersions = false;
  int? _selectedAno;
  VehicleModel? _selectedVehicle;
  AddressSuggestion? _origemSelecionada;
  AddressSuggestion? _destinoSelecionado;
  final List<_ParadaField> _paradas = [];
  bool _idaEVolta = false;
  bool _loadingEstimate = false;
  TripCostBreakdown? _breakdown;
  // Guarda se o _breakdown atual é de ida e volta separado do checkbox ao
  // vivo — se o usuário desmarcar depois de calcular sem recalcular, o
  // resumo exibido continua rotulado corretamente.
  bool _breakdownIdaEVolta = false;
  String? _errorMessage;
  bool _salvandoNoHistorico = false;
  LastTrip? _ultimaViagem;

  // _availableVersions já vem ordenado por ano desc (do back-end) — o Set
  // preserva essa ordem de inserção, então os anos saem do mais recente
  // pro mais antigo sem precisar ordenar de novo.
  List<int> get _anosDisponiveis => {for (final v in _availableVersions) v.ano}.toList();

  List<VehicleModel> get _combustiveisDoAno =>
      _availableVersions.where((v) => v.ano == _selectedAno).toList();

  @override
  void initState() {
    super.initState();
    // Modo offline (Fase 6.5): se tinha uma viagem calculada antes de
    // fechar o app, oferece continuar de onde parou sem precisar do
    // back-end — útil se reabrir numa área sem sinal.
    loadLastTrip().then((viagem) {
      if (mounted && viagem != null) setState(() => _ultimaViagem = viagem);
    });
  }

  @override
  void dispose() {
    _origemController.dispose();
    _destinoController.dispose();
    _precoController.dispose();
    for (final parada in _paradas) {
      parada.controller.dispose();
    }
    super.dispose();
  }

  void _adicionarParada() {
    setState(() {
      _paradas.add(_ParadaField());
      // Paradas e ida-e-volta não se combinam nesta rodada (mesma filosofia
      // do recálculo de rota) — adicionar uma parada desliga o checkbox.
      _idaEVolta = false;
    });
  }

  void _removerParada(int index) {
    setState(() => _paradas.removeAt(index).controller.dispose());
  }

  IconData _iconeTipo(VehicleType tipo) {
    switch (tipo) {
      case VehicleType.carro:
        return Icons.directions_car_rounded;
      case VehicleType.moto:
        return Icons.two_wheeler_rounded;
      case VehicleType.van:
        return Icons.airport_shuttle_rounded;
      case VehicleType.caminhao:
        return Icons.local_shipping_rounded;
      case VehicleType.onibus:
        return Icons.directions_bus_rounded;
    }
  }

  Future<void> _usarLocalizacaoAtual() async {
    if (!await Geolocator.isLocationServiceEnabled()) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Localização desligada no dispositivo.')));
      return;
    }

    var permissao = await Geolocator.checkPermission();
    if (permissao == LocationPermission.denied) {
      permissao = await Geolocator.requestPermission();
    }
    if (permissao == LocationPermission.denied || permissao == LocationPermission.deniedForever) {
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Sem permissão de localização.')));
      return;
    }

    try {
      final posicao = await Geolocator.getCurrentPosition();
      setState(() {
        _origemController.text = 'Localização atual';
        _origemSelecionada = AddressSuggestion(
          displayName: 'Localização atual',
          lat: posicao.latitude,
          lon: posicao.longitude,
        );
      });
    } catch (_) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Não foi possível obter sua localização.')));
    }
  }

  void _onTipoSelected(VehicleType tipo) {
    setState(() {
      _selectedTipo = tipo;
      _selectedModelSummary = null;
      _availableVersions = [];
      _selectedAno = null;
      _selectedVehicle = null;
    });
  }

  Future<void> _onModelSelected(VehicleModelSummary? summary) async {
    setState(() {
      _selectedModelSummary = summary;
      _availableVersions = [];
      _selectedAno = null;
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
        _onAnoSelected(versions.first.ano);
      }
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _loadingVersions = false;
        _errorMessage = 'Não foi possível carregar os anos desse modelo.';
      });
    }
  }

  void _onAnoSelected(int? ano) {
    setState(() => _selectedAno = ano);
    // Pré-seleciona o primeiro combustível disponível nesse ano (a ordem já
    // vem fixa do back-end: Gasolina, Etanol, Diesel, Elétrico).
    _onVehicleSelected(_combustiveisDoAno.isNotEmpty ? _combustiveisDoAno.first : null);
  }

  void _onVehicleSelected(VehicleModel? v) {
    // _selectedVehicle == null cobre tanto o estado inicial do app quanto
    // toda troca de modelo/tipo de veículo (_onModelSelected reseta pra null
    // antes de auto-selecionar o primeiro ano/combustível) — sem esse caso,
    // o preço nunca atualizava na primeira escolha de um veículo, ficando
    // preso no default de gasolina mesmo escolhendo direto um caminhão a
    // diesel.
    final combustivelMudou =
        v != null && (_selectedVehicle == null || _selectedVehicle!.tipoCombustivel != v.tipoCombustivel);
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
      // Mesma resolução coordenada-ou-texto já usada pra origem/destino —
      // vazio quando não há paradas (ida-e-volta e paradas não coexistem,
      // ver _adicionarParada).
      final paradas = _paradas
          .map((p) => p.selecionada != null ? '${p.selecionada!.lat},${p.selecionada!.lon}' : p.controller.text)
          .toList();

      Future<TripCostBreakdown> estimar(String de, String para) => _apiClient.estimateTrip(
            origem: de,
            destino: para,
            vehicleModelId: _selectedVehicle!.id,
            precoPorLitro: isEletrico ? null : preco,
            precoPorKWh: isEletrico ? preco : null,
            paradas: paradas,
          );

      TripCostBreakdown result;
      if (_idaEVolta) {
        // Ida e volta calculadas separadamente (não é só dobrar o valor da
        // ida): a volta pode cruzar pedágios diferentes (praças de sentido
        // único) ou uma rota diferente. As duas em paralelo pra não dobrar o
        // tempo de espera.
        final resultados = await Future.wait([estimar(origem, destino), estimar(destino, origem)]);
        result = TripCostBreakdown.combine(resultados[0], resultados[1]);
      } else {
        result = await estimar(origem, destino);
      }
      setState(() {
        _breakdown = result;
        _breakdownIdaEVolta = _idaEVolta;
        _loadingEstimate = false;
        _ultimaViagem = null; // já era a viagem que acabou de ser salva de novo — evita o card piscar
      });
      // Guarda localmente pra dar pra retomar a navegação sem rede depois
      // (Fase 6.5) — mesma regra de recálculo de sempre: desabilitado com
      // ida-e-volta ou paradas.
      final recalculoDesabilitado = _idaEVolta || paradas.isNotEmpty;
      saveLastTrip(LastTrip(
        breakdown: result,
        origemLabel: _origemSelecionada?.displayName ?? _origemController.text,
        destinoLabel: _destinoSelecionado?.displayName ?? _destinoController.text,
        destino: recalculoDesabilitado ? null : destino,
        vehicleModelId: recalculoDesabilitado ? null : _selectedVehicle!.id,
        precoPorLitro: recalculoDesabilitado || isEletrico ? null : preco,
        precoPorKWh: recalculoDesabilitado || !isEletrico ? null : preco,
      ));
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

  /// Não automático — só quando o usuário toca em "Salvar no histórico" com
  /// uma sessão ativa (Fase 6.4b). Usa o texto exibido nos campos (não as
  /// coordenadas) pra ficar legível na lista do histórico.
  Future<void> _salvarNoHistorico() async {
    final breakdown = _breakdown;
    if (breakdown == null) return;

    setState(() => _salvandoNoHistorico = true);
    try {
      await _apiClient.saveTripToHistory(
        origem: _origemSelecionada?.displayName ?? _origemController.text,
        destino: _destinoSelecionado?.displayName ?? _destinoController.text,
        breakdown: breakdown,
      );
      if (!mounted) return;
      ScaffoldMessenger.of(context).showSnackBar(const SnackBar(content: Text('Viagem salva no histórico.')));
    } catch (e) {
      if (!mounted) return;
      ScaffoldMessenger.of(context)
          .showSnackBar(const SnackBar(content: Text('Não foi possível salvar no histórico.')));
    } finally {
      if (mounted) setState(() => _salvandoNoHistorico = false);
    }
  }

  /// Vai direto pra navegação com a última viagem calculada, sem passar
  /// pelo formulário nem falar com o back-end — o caso de uso é justamente
  /// reabrir o app numa área sem sinal (Fase 6.5).
  void _retomarNavegacao(LastTrip viagem) {
    Navigator.of(context).push(
      MaterialPageRoute(
        builder: (_) => NavigationScreen(
          breakdown: viagem.breakdown,
          destino: viagem.destino,
          vehicleModelId: viagem.vehicleModelId,
          precoPorLitro: viagem.precoPorLitro,
          precoPorKWh: viagem.precoPorKWh,
        ),
      ),
    );
  }

  Widget _buildRetomarViagemCard(LastTrip viagem) {
    final scheme = Theme.of(context).colorScheme;
    return Card(
      color: scheme.secondaryContainer,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Row(
          children: [
            Icon(Icons.history_rounded, color: scheme.onSecondaryContainer),
            const SizedBox(width: 12),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text('Última viagem calculada', style: TextStyle(color: scheme.onSecondaryContainer)),
                  Text(
                    '${viagem.origemLabel} → ${viagem.destinoLabel}',
                    style: TextStyle(color: scheme.onSecondaryContainer, fontWeight: FontWeight.w700),
                  ),
                ],
              ),
            ),
            TextButton(
              onPressed: () => _retomarNavegacao(viagem),
              child: const Text('Retomar'),
            ),
            IconButton(
              tooltip: 'Dispensar',
              icon: Icon(Icons.close_rounded, color: scheme.onSecondaryContainer),
              onPressed: () {
                clearLastTrip();
                setState(() => _ultimaViagem = null);
              },
            ),
          ],
        ),
      ),
    );
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
        actions: [
          ValueListenableBuilder<AuthSession?>(
            valueListenable: authSessionNotifier,
            builder: (context, sessao, _) {
              if (sessao == null) {
                return IconButton(
                  tooltip: 'Entrar (opcional — desbloqueia o histórico de viagens)',
                  icon: const Icon(Icons.person_outline_rounded),
                  onPressed: () => Navigator.of(context).push(
                    MaterialPageRoute(builder: (_) => const LoginScreen()),
                  ),
                );
              }
              return PopupMenuButton<String>(
                tooltip: sessao.email,
                icon: const Icon(Icons.person_rounded),
                onSelected: (opcao) {
                  if (opcao == 'historico') {
                    Navigator.of(context).push(MaterialPageRoute(builder: (_) => const TripHistoryScreen()));
                  } else if (opcao == 'sair') {
                    clearAuthSession();
                  }
                },
                itemBuilder: (context) => [
                  const PopupMenuItem(value: 'historico', child: Text('Histórico de viagens')),
                  const PopupMenuItem(value: 'sair', child: Text('Sair')),
                ],
              );
            },
          ),
          ValueListenableBuilder<ThemeMode>(
            valueListenable: themeModeNotifier,
            builder: (context, themeMode, _) {
              final escuro = themeMode == ThemeMode.dark ||
                  (themeMode == ThemeMode.system && MediaQuery.platformBrightnessOf(context) == Brightness.dark);
              return IconButton(
                tooltip: escuro ? 'Mudar pro modo claro' : 'Mudar pro modo escuro',
                icon: Icon(escuro ? Icons.light_mode_rounded : Icons.dark_mode_rounded),
                onPressed: () => toggleThemeMode(context),
              );
            },
          ),
        ],
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
        if (_ultimaViagem != null) _buildRetomarViagemCard(_ultimaViagem!),
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
                onUseCurrentLocation: _usarLocalizacaoAtual,
              ),
              // Paradas ficam entre Origem e Destino (ordem real do
              // roteiro) — uma parada nova entra logo acima do Destino,
              // empurrando-o pra baixo, em vez de aparecer depois dele.
              for (var i = 0; i < _paradas.length; i++)
                Padding(
                  padding: const EdgeInsets.only(top: 12),
                  child: Row(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Expanded(
                        child: AddressField(
                          controller: _paradas[i].controller,
                          label: 'Parada ${i + 1}',
                          fetchSuggestions: _apiClient.suggestAddress,
                          onSelected: (s) => _paradas[i].selecionada = s,
                        ),
                      ),
                      IconButton(
                        icon: const Icon(Icons.close_rounded, size: 20),
                        tooltip: 'Remover parada',
                        onPressed: () => _removerParada(i),
                      ),
                    ],
                  ),
                ),
              const SizedBox(height: 12),
              AddressField(
                controller: _destinoController,
                label: 'Destino',
                fetchSuggestions: _apiClient.suggestAddress,
                onSelected: (s) => _destinoSelecionado = s,
              ),
              Align(
                alignment: Alignment.centerLeft,
                child: TextButton.icon(
                  onPressed: _adicionarParada,
                  icon: const Icon(Icons.add_location_alt_outlined, size: 18),
                  label: const Text('Adicionar parada'),
                ),
              ),
              InkWell(
                borderRadius: BorderRadius.circular(10),
                onTap: _paradas.isEmpty ? () => setState(() => _idaEVolta = !_idaEVolta) : null,
                child: Padding(
                  padding: const EdgeInsets.symmetric(vertical: 8),
                  child: Row(
                    children: [
                      Checkbox(
                        value: _idaEVolta,
                        onChanged: _paradas.isEmpty ? (v) => setState(() => _idaEVolta = v ?? false) : null,
                      ),
                      const SizedBox(width: 4),
                      Text(
                        _paradas.isEmpty ? 'Ida e volta' : 'Ida e volta (indisponível com paradas)',
                        style: _paradas.isEmpty
                            ? null
                            : TextStyle(color: Theme.of(context).disabledColor),
                      ),
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
        SectionCard(
          icon: Icons.directions_car_filled_rounded,
          title: 'Veículo',
          child: Column(
            children: [
              // 5 opções com ícone+texto não cabem numa linha só, nem no
              // celular nem nos 400px do painel do desktop. Um scroll
              // horizontal (tentativa anterior) esconde as opções sem
              // nenhuma pista visual disso no Windows/desktop (usuário só
              // via 3 cortadas, sem saber que dava pra rolar) — Wrap deixa
              // tudo visível, só quebrando pra segunda linha quando precisa.
              Wrap(
                spacing: 8,
                runSpacing: 8,
                children: [
                  for (final tipo in VehicleType.values)
                    ChoiceChip(
                      avatar: Icon(_iconeTipo(tipo), size: 18),
                      label: Text(tipo.label),
                      selected: _selectedTipo == tipo,
                      showCheckmark: false,
                      onSelected: (_) => _onTipoSelected(tipo),
                    ),
                ],
              ),
              const SizedBox(height: 12),
              VehicleSearchField(
                // Força recriar o campo (e limpar o texto digitado) ao trocar
                // de tipo — initialValue só vale na criação do widget.
                key: ValueKey(_selectedTipo),
                initialValue: _selectedModelSummary,
                fetchSuggestions: (q) => _apiClient.searchVehicleModels(q, tipo: _selectedTipo),
                onSelected: _onModelSelected,
                tipo: _selectedTipo,
                onReportMissingVehicle: _apiClient.reportMissingVehicle,
              ),
              if (_loadingVersions) ...[
                const SizedBox(height: 8),
                const LinearProgressIndicator(),
              ],
              if (_anosDisponiveis.isNotEmpty) ...[
                const SizedBox(height: 12),
                Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Expanded(
                      child: DropdownButtonFormField<int>(
                        // Sem isExpanded, o botão tenta usar a largura
                        // intrínseca do texto selecionado — em telas
                        // estreitas (celular) isso estoura o espaço que o
                        // Expanded reservou (overflow só visível testando
                        // num device real, não no preview largo do Windows).
                        isExpanded: true,
                        initialValue: _selectedAno,
                        decoration: const InputDecoration(labelText: 'Ano'),
                        items: _anosDisponiveis
                            .map((ano) => DropdownMenuItem(value: ano, child: Text(ano.toString())))
                            .toList(),
                        onChanged: _onAnoSelected,
                      ),
                    ),
                    const SizedBox(width: 12),
                    Expanded(
                      child: DropdownButtonFormField<VehicleModel>(
                        // Precisa de key: a lista de itens muda toda vez que o ano
                        // muda (ids diferentes) — sem isso o Flutter tenta manter
                        // o valor antigo, que não existe mais nessa nova lista.
                        key: ValueKey(_selectedAno),
                        isExpanded: true,
                        initialValue: _selectedVehicle,
                        decoration: const InputDecoration(labelText: 'Combustível'),
                        items: _combustiveisDoAno
                            .map((v) => DropdownMenuItem(
                                  value: v,
                                  child: Text(v.tipoCombustivel.label, overflow: TextOverflow.ellipsis),
                                ))
                            .toList(),
                        onChanged: _onVehicleSelected,
                      ),
                    ),
                  ],
                ),
              ],
              if (_selectedVehicle?.cilindradaCC != null) ...[
                const SizedBox(height: 8),
                Text(
                  '${_selectedVehicle!.cilindradaCC} cc · consumo estimado pela cilindrada (sem tabela oficial pra moto)',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
                ),
              ],
              if (_selectedVehicle?.pbtKg != null) ...[
                const SizedBox(height: 8),
                Text(
                  '${_selectedVehicle!.pbtKg} kg (PBT) · consumo estimado pelo peso bruto total (sem tabela oficial pra esse porte)',
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                        color: Theme.of(context).colorScheme.onSurfaceVariant,
                      ),
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
      title: _breakdownIdaEVolta ? 'Resumo da viagem (ida e volta)' : 'Resumo da viagem',
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
          if (b.passosRota.isNotEmpty) ...[
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: () {
                // Recálculo de rota em desvio só é oferecido pra trecho único
                // sem paradas — o breakdown de ida-e-volta mistura a
                // geometria das duas pernas concatenadas, e recalcular com
                // paradas exigiria saber em qual trecho do roteiro o usuário
                // está (fora de escopo por enquanto, mesma filosofia).
                final recalculoDesabilitado = _breakdownIdaEVolta || b.paradasNaRota.isNotEmpty;
                Navigator.of(context).push(
                  MaterialPageRoute(
                    builder: (_) => NavigationScreen(
                      breakdown: b,
                      destino: recalculoDesabilitado
                          ? null
                          : (_destinoSelecionado != null
                              ? '${_destinoSelecionado!.lat},${_destinoSelecionado!.lon}'
                              : _destinoController.text),
                      vehicleModelId: recalculoDesabilitado ? null : _selectedVehicle!.id,
                      precoPorLitro: recalculoDesabilitado || _selectedVehicle!.isEletrico
                          ? null
                          : double.tryParse(_precoController.text.replaceAll(',', '.')),
                      precoPorKWh: recalculoDesabilitado || !_selectedVehicle!.isEletrico
                          ? null
                          : double.tryParse(_precoController.text.replaceAll(',', '.')),
                    ),
                  ),
                );
              },
              icon: const Icon(Icons.navigation_rounded),
              label: const Text('Navegar'),
            ),
          ],
          ValueListenableBuilder<AuthSession?>(
            valueListenable: authSessionNotifier,
            builder: (context, sessao, _) {
              if (sessao == null) return const SizedBox.shrink();
              return Padding(
                padding: const EdgeInsets.only(top: 8),
                child: OutlinedButton.icon(
                  onPressed: _salvandoNoHistorico ? null : _salvarNoHistorico,
                  icon: _salvandoNoHistorico
                      ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                      : const Icon(Icons.bookmark_add_outlined),
                  label: const Text('Salvar no histórico'),
                ),
              );
            },
          ),
        ],
      ),
    );
  }
}
