import 'tipo_energia.dart';
import 'vehicle_type.dart';

class VehicleModel {
  final int id;
  final String marca;
  final String modelo;
  final int ano;
  final VehicleType tipo;
  final TipoEnergia tipoEnergia;
  final double? consumoCidadeKmL;
  final double? consumoEstradaKmL;
  final double? consumoKmPorKWh;
  final int numeroEixos;
  final double custoDesgastePorKm;

  bool get isEletrico => tipoEnergia == TipoEnergia.eletrico;

  VehicleModel({
    required this.id,
    required this.marca,
    required this.modelo,
    required this.ano,
    required this.tipo,
    this.tipoEnergia = TipoEnergia.combustao,
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
      tipoEnergia: TipoEnergia.fromApiValue(json['tipoEnergia'] as String?),
      consumoCidadeKmL: (json['consumoCidadeKmL'] as num?)?.toDouble(),
      consumoEstradaKmL: (json['consumoEstradaKmL'] as num?)?.toDouble(),
      consumoKmPorKWh: (json['consumoKmPorKWh'] as num?)?.toDouble(),
      numeroEixos: json['numeroEixos'] as int,
      custoDesgastePorKm: (json['custoDesgastePorKm'] as num).toDouble(),
    );
  }
}
