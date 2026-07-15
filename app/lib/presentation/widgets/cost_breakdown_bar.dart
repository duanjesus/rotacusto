import 'package:flutter/material.dart';

class CostSegment {
  final String label;
  final double value;
  final Color light;
  final Color dark;

  const CostSegment({required this.label, required this.value, required this.light, required this.dark});
}

/// Barra horizontal proporcional (part-to-whole) mostrando a composição do
/// custo total. Paleta categórica de ordem fixa (validada — ver skill
/// dataviz), nunca ciclada nem escolhida por ranking. Como duas cores da
/// paleta ficam abaixo de 3:1 de contraste no claro, o rótulo/valor de cada
/// fatia usa cor de texto do tema (nunca a cor da série) — a legenda é a
/// mitigação exigida pela regra de "relief".
class CostBreakdownBar extends StatelessWidget {
  final List<CostSegment> segments;

  const CostBreakdownBar({super.key, required this.segments});

  @override
  Widget build(BuildContext context) {
    final isDark = Theme.of(context).brightness == Brightness.dark;
    final total = segments.fold<double>(0, (sum, s) => sum + s.value);
    final visible = segments.where((s) => s.value > 0).toList();

    if (total <= 0 || visible.isEmpty) return const SizedBox.shrink();

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(6),
          child: SizedBox(
            height: 12,
            child: Row(
              children: [
                for (var i = 0; i < visible.length; i++) ...[
                  if (i > 0) const SizedBox(width: 2),
                  Expanded(
                    flex: (visible[i].value * 1000 / total).round().clamp(1, 1000),
                    child: Container(color: isDark ? visible[i].dark : visible[i].light),
                  ),
                ],
              ],
            ),
          ),
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 16,
          runSpacing: 6,
          children: [
            for (final s in visible)
              Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Container(
                    width: 8,
                    height: 8,
                    decoration: BoxDecoration(
                      color: isDark ? s.dark : s.light,
                      shape: BoxShape.circle,
                    ),
                  ),
                  const SizedBox(width: 6),
                  Text(
                    '${s.label} · R\$ ${s.value.toStringAsFixed(2)}',
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                          color: Theme.of(context).colorScheme.onSurfaceVariant,
                        ),
                  ),
                ],
              ),
          ],
        ),
      ],
    );
  }
}
