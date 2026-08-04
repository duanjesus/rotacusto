import 'tipo_combustivel.dart';

/// Preço médio de combustível por UF (ANP) — usado só como valor sugerido
/// no formulário, nunca trava o usuário nesse número.
///
/// [municipio] é nulo nas linhas de fallback por UF (média do estado
/// inteiro); preenchido nas linhas específicas de município (Fase 14). Vem
/// da fonte ANP SEM acento (ex. "Sao Paulo") — a comparação com o município
/// vindo do autocomplete (que TEM acento) precisa normalizar os dois lados
/// (ver `_chaveMunicipio` em home_screen.dart), nunca comparar direto.
class FuelPrice {
  final String uf;
  final String? municipio;
  final TipoCombustivel tipoCombustivel;
  final double precoMedio;

  FuelPrice({
    required this.uf,
    this.municipio,
    required this.tipoCombustivel,
    required this.precoMedio,
  });

  factory FuelPrice.fromJson(Map<String, dynamic> json) {
    return FuelPrice(
      uf: json['uf'] as String,
      municipio: json['municipio'] as String?,
      tipoCombustivel: TipoCombustivel.fromApiValue(json['tipoCombustivel'] as String?),
      precoMedio: (json['precoMedio'] as num).toDouble(),
    );
  }
}
