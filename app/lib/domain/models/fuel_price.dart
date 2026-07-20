import 'tipo_combustivel.dart';

/// Preço médio de combustível por UF (ANP) — usado só como valor sugerido
/// no formulário, nunca trava o usuário nesse número.
class FuelPrice {
  final String uf;
  final TipoCombustivel tipoCombustivel;
  final double precoMedio;

  FuelPrice({required this.uf, required this.tipoCombustivel, required this.precoMedio});

  factory FuelPrice.fromJson(Map<String, dynamic> json) {
    return FuelPrice(
      uf: json['uf'] as String,
      tipoCombustivel: TipoCombustivel.fromApiValue(json['tipoCombustivel'] as String?),
      precoMedio: (json['precoMedio'] as num).toDouble(),
    );
  }
}
