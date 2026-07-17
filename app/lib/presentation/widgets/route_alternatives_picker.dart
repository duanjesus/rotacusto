import 'package:flutter/material.dart';

import '../../domain/models/trip_cost_breakdown.dart';

/// Lista de escolha de rota quando o back-end acha mais de um caminho
/// possível (Fase 10, só origem→destino simples — ver
/// `TripEstimationService.estimateAlternatives`). [alternativas] já vem
/// ordenada por [TripCostBreakdown.total] crescente (o ORS não garante
/// nenhuma ordem por custo — só descobrimos o custo depois de calcular cada
/// uma). Renderizado inline (dentro de um `SectionCard`, logo abaixo do
/// botão "Calcular" em `home_screen.dart`) em vez de bottom sheet — assim o
/// mapa continua visível e dá pra trocar de opção sem recalcular. [selecionada]
/// destaca a opção atualmente mostrada no resumo abaixo. Sem `ApiClient`
/// embutido, só um callback, pra dar pra testar sem rede.
class RouteAlternativesPicker extends StatelessWidget {
  final List<TripCostBreakdown> alternativas;
  final TripCostBreakdown? selecionada;
  final void Function(TripCostBreakdown escolhida) onSelected;

  const RouteAlternativesPicker({
    super.key,
    required this.alternativas,
    required this.onSelected,
    this.selecionada,
  });

  @override
  Widget build(BuildContext context) {
    final maisBarata = alternativas.first.total;

    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        for (var i = 0; i < alternativas.length; i++) _buildOpcao(context, i, alternativas[i], maisBarata),
      ],
    );
  }

  Widget _buildOpcao(BuildContext context, int index, TripCostBreakdown opcao, double maisBarata) {
    final diferenca = opcao.total - maisBarata;
    final horas = opcao.duracaoMin ~/ 60;
    final minutos = (opcao.duracaoMin % 60).round();
    final duracaoTexto = horas > 0 ? '${horas}h${minutos}min' : '${minutos}min';
    final estaSelecionada = identical(selecionada, opcao);
    final scheme = Theme.of(context).colorScheme;

    return Container(
      margin: const EdgeInsets.only(bottom: 4),
      decoration: BoxDecoration(
        color: estaSelecionada ? scheme.primaryContainer.withValues(alpha: 0.4) : null,
        borderRadius: BorderRadius.circular(12),
      ),
      child: ListTile(
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        leading: CircleAvatar(child: Text('${index + 1}')),
        title: Text('${opcao.distanciaKm.toStringAsFixed(0)} km · $duracaoTexto'),
        subtitle: Text(
          diferenca <= 0.01 ? 'Opção mais barata' : '+R\$ ${diferenca.toStringAsFixed(2)} que a mais barata',
        ),
        trailing: Text(
          'R\$ ${opcao.total.toStringAsFixed(2)}',
          style: const TextStyle(fontWeight: FontWeight.bold),
        ),
        onTap: () => onSelected(opcao),
      ),
    );
  }
}
