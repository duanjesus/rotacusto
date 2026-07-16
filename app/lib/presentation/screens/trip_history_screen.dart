import 'package:flutter/material.dart';

import '../../data/api_client.dart';
import '../../domain/models/trip_history_detail.dart';
import '../../domain/models/trip_history_summary.dart';
import '../widgets/cost_breakdown_bar.dart';
import '../widgets/section_card.dart';
import '../widgets/trip_map.dart';

/// Lista as viagens que o usuário logado salvou (botão "Salvar no histórico"
/// no resumo da viagem, ver home_screen.dart) — Fase 6.4b.
class TripHistoryScreen extends StatefulWidget {
  const TripHistoryScreen({super.key});

  @override
  State<TripHistoryScreen> createState() => _TripHistoryScreenState();
}

class _TripHistoryScreenState extends State<TripHistoryScreen> {
  final ApiClient _apiClient = ApiClient();
  late final Future<List<TripHistorySummary>> _future = _apiClient.fetchTripHistory();

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Histórico de viagens')),
      body: FutureBuilder<List<TripHistorySummary>>(
        future: _future,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text('Não foi possível carregar o histórico.\n${snapshot.error}', textAlign: TextAlign.center),
              ),
            );
          }
          final viagens = snapshot.data ?? [];
          if (viagens.isEmpty) {
            return const Center(
              child: Padding(
                padding: EdgeInsets.all(24),
                child: Text('Nenhuma viagem salva ainda. Calcule uma viagem e toque em "Salvar no histórico".',
                    textAlign: TextAlign.center),
              ),
            );
          }
          return ListView.separated(
            padding: const EdgeInsets.all(12),
            itemCount: viagens.length,
            separatorBuilder: (_, _) => const SizedBox(height: 8),
            itemBuilder: (context, index) {
              final v = viagens[index];
              return Card(
                child: ListTile(
                  leading: const Icon(Icons.route_outlined),
                  title: Text('${v.origem} → ${v.destino}'),
                  subtitle: Text('${v.distanciaKm.toStringAsFixed(1)} km · ${_formatarData(v.calculadoEm)}'),
                  trailing: Text('R\$ ${v.total.toStringAsFixed(2)}',
                      style: Theme.of(context).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700)),
                  onTap: () => Navigator.of(context).push(
                    MaterialPageRoute(builder: (_) => _TripHistoryDetailScreen(id: v.id)),
                  ),
                ),
              );
            },
          );
        },
      ),
    );
  }

  String _formatarData(DateTime data) {
    final local = data.toLocal();
    return '${local.day.toString().padLeft(2, '0')}/${local.month.toString().padLeft(2, '0')}/${local.year}';
  }
}

class _TripHistoryDetailScreen extends StatefulWidget {
  final int id;

  const _TripHistoryDetailScreen({required this.id});

  @override
  State<_TripHistoryDetailScreen> createState() => _TripHistoryDetailScreenState();
}

class _TripHistoryDetailScreenState extends State<_TripHistoryDetailScreen> {
  final ApiClient _apiClient = ApiClient();
  late final Future<TripHistoryDetail> _future = _apiClient.fetchTripHistoryDetail(widget.id);

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Viagem salva')),
      body: FutureBuilder<TripHistoryDetail>(
        future: _future,
        builder: (context, snapshot) {
          if (snapshot.connectionState != ConnectionState.done) {
            return const Center(child: CircularProgressIndicator());
          }
          if (snapshot.hasError) {
            return Center(child: Text('Não foi possível carregar essa viagem.\n${snapshot.error}'));
          }
          final detalhe = snapshot.data!;
          final b = detalhe.breakdown;
          return SingleChildScrollView(
            padding: const EdgeInsets.all(12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              spacing: 12,
              children: [
                SectionCard(
                  icon: Icons.receipt_long_outlined,
                  title: '${detalhe.origem} → ${detalhe.destino}',
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Row(
                        children: [
                          Icon(Icons.route_rounded, size: 18, color: Theme.of(context).colorScheme.primary),
                          const SizedBox(width: 6),
                          Text('${b.distanciaKm.toStringAsFixed(1)} km'),
                          const SizedBox(width: 16),
                          Icon(Icons.schedule_rounded, size: 18, color: Theme.of(context).colorScheme.primary),
                          const SizedBox(width: 6),
                          Text('${(b.duracaoMin / 60).toStringAsFixed(1)} h'),
                        ],
                      ),
                      const SizedBox(height: 16),
                      CostBreakdownBar(segments: [
                        CostSegment(
                          label: 'Combustível',
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
                      ]),
                      const SizedBox(height: 12),
                      Text('Total: R\$ ${b.total.toStringAsFixed(2)}',
                          style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
                    ],
                  ),
                ),
                SizedBox(height: 260, child: TripMap(breakdown: b)),
              ],
            ),
          );
        },
      ),
    );
  }
}
