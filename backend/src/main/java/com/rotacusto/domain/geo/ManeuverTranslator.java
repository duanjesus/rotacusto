package com.rotacusto.domain.geo;

/**
 * Traduz o código numérico de manobra do ORS (campo {@code type} de cada
 * step) pra uma instrução em português. O parâmetro {@code language=pt} do
 * ORS é IGNORADO nesta implantação (testado direto contra a API — instrução
 * continua em inglês independente do valor de language) — em vez de
 * depender disso, monta a instrução a partir de {@code type} (numérico,
 * documentado pelo ORS, independente de idioma) + {@code name} (nome da rua,
 * já vem no idioma real do OSM). Códigos conforme a documentação pública do
 * ORS Directions API.
 */
public final class ManeuverTranslator {

    private ManeuverTranslator() {
    }

    public static String translate(int type, String name) {
        String verbo = switch (type) {
            case 0 -> "Vire à esquerda";
            case 1 -> "Vire à direita";
            case 2 -> "Vire bem à esquerda";
            case 3 -> "Vire bem à direita";
            case 4 -> "Vire levemente à esquerda";
            case 5 -> "Vire levemente à direita";
            case 6 -> "Siga em frente";
            case 7 -> "Entre na rotatória";
            case 8 -> "Saia da rotatória";
            case 9 -> "Faça o retorno";
            case 10 -> "Chegue ao destino";
            case 11 -> "Siga";
            case 12 -> "Mantenha-se à esquerda";
            case 13 -> "Mantenha-se à direita";
            default -> "Continue";
        };
        boolean temNome = name != null && !name.isBlank() && !name.equals("-");
        return temNome ? verbo + " em " + name : verbo;
    }
}
