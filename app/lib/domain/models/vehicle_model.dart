import 'tipo_combustivel.dart';
import 'vehicle_type.dart';

class VehicleModel {
  final int id;
  final String marca;
  final String modelo;
  final int ano;
  final VehicleType tipo;
  final TipoCombustivel tipoCombustivel;
  final double? consumoCidadeKmL;
  final double? consumoEstradaKmL;
  final double? consumoKmPorKWh;
  final int numeroEixos;
  final double custoDesgastePorKm;

  bool get isEletrico => tipoCombustivel == TipoCombustivel.eletrico;

  /// Rótulo do passo 2 (escolha de ano/versão): "2023 · Gasolina" — um
  /// modelo flex tem 2 linhas no mesmo ano (gasolina e etanol), então o
  /// combustível precisa aparecer no rótulo pra distinguir.
  String get versaoLabel => '$ano · ${tipoCombustivel.label}';

  VehicleModel({
    required this.id,
    required this.marca,
    required this.modelo,
    required this.ano,
    required this.tipo,
    this.tipoCombustivel = TipoCombustivel.gasolina,
    this.consumoCidadeKmL,
    this.consumoEstradaKmL,
    this.consumoKmPorKWh,
    required this.numeroEixos,
    required this.custoDesgastePorKm,
  });

  String get displayName => '$marca $modelo ($ano)';

  factory VehicleModel.fromJson(Map<String, dynamic> json) {
    return VehicleModel(
      id: json['id'] as int,
      marca: json['marca'] as String,
      modelo: json['modelo'] as String,
      ano: json['ano'] as int,
      tipo: VehicleType.fromApiValue(json['tipo'] as String),
      tipoCombustivel: TipoCombustivel.fromApiValue(json['tipoCombustivel'] as String?),
      consumoCidadeKmL: (json['consumoCidadeKmL'] as num?)?.toDouble(),
      consumoEstradaKmL: (json['consumoEstradaKmL'] as num?)?.toDouble(),
      consumoKmPorKWh: (json['consumoKmPorKWh'] as num?)?.toDouble(),
      numeroEixos: json['numeroEixos'] as int,
      custoDesgastePorKm: (json['custoDesgastePorKm'] as num).toDouble(),
    );
  }
}
