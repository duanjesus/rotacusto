import 'dart:async';

import 'package:flutter/material.dart';

import '../../domain/models/vehicle_model_summary.dart';

/// Campo de texto com dropdown de sugestões de veículo (passo 1: marca+modelo,
/// sem ano ainda), buscando no back-end conforme o usuário digita, com
/// debounce. Necessário porque o catálogo tem centenas de modelos — um
/// dropdown simples com todos eles listados não é usável.
class VehicleSearchField extends StatefulWidget {
  final VehicleModelSummary? initialValue;
  final Future<List<VehicleModelSummary>> Function(String query) fetchSuggestions;
  final void Function(VehicleModelSummary?) onSelected;

  const VehicleSearchField({
    super.key,
    this.initialValue,
    required this.fetchSuggestions,
    required this.onSelected,
  });

  @override
  State<VehicleSearchField> createState() => _VehicleSearchFieldState();
}

class _VehicleSearchFieldState extends State<VehicleSearchField> {
  static const _debounceDuration = Duration(milliseconds: 400);
  static const _minQueryLength = 2;

  late final TextEditingController _controller;
  Timer? _debounce;
  List<VehicleModelSummary> _suggestions = [];
  bool _loading = false;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialValue?.displayName ?? '');
  }

  @override
  void dispose() {
    _debounce?.cancel();
    _controller.dispose();
    super.dispose();
  }

  void _onChanged(String value) {
    widget.onSelected(null); // edição manual invalida a seleção anterior
    _debounce?.cancel();

    if (value.trim().length < _minQueryLength) {
      setState(() => _suggestions = []);
      return;
    }

    _debounce = Timer(_debounceDuration, () => _fetchSuggestions(value));
  }

  Future<void> _fetchSuggestions(String query) async {
    setState(() => _loading = true);
    try {
      final results = await widget.fetchSuggestions(query);
      if (!mounted) return;
      setState(() {
        _suggestions = results;
        _loading = false;
      });
    } catch (_) {
      if (!mounted) return;
      setState(() {
        _suggestions = [];
        _loading = false;
      });
    }
  }

  void _select(VehicleModelSummary vehicle) {
    _controller.text = vehicle.displayName;
    widget.onSelected(vehicle);
    setState(() => _suggestions = []);
    FocusScope.of(context).unfocus();
  }

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        TextField(
          controller: _controller,
          decoration: InputDecoration(
            labelText: 'Veículo (digite marca ou modelo)',
            suffixIcon: _loading
                ? const Padding(
                    padding: EdgeInsets.all(12),
                    child: SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2)),
                  )
                : null,
          ),
          onChanged: _onChanged,
        ),
        if (_suggestions.isNotEmpty)
          Container(
            margin: const EdgeInsets.only(top: 2),
            constraints: const BoxConstraints(maxHeight: 220),
            decoration: BoxDecoration(
              border: Border.all(color: Theme.of(context).dividerColor),
              borderRadius: BorderRadius.circular(4),
            ),
            child: ListView.builder(
              padding: EdgeInsets.zero,
              shrinkWrap: true,
              itemCount: _suggestions.length,
              itemBuilder: (context, index) {
                final vehicle = _suggestions[index];
                return ListTile(
                  dense: true,
                  leading: const Icon(Icons.directions_car_outlined, size: 18),
                  title: Text(vehicle.displayName, style: const TextStyle(fontSize: 13)),
                  onTap: () => _select(vehicle),
                );
              },
            ),
          ),
      ],
    );
  }
}
