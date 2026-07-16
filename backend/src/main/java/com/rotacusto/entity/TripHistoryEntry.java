package com.rotacusto.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Uma viagem calculada, salva a pedido do usuário logado — não é automático,
 * ver botão "Salvar no histórico" no app. {@code breakdownJson} guarda o
 * {@code TripCostBreakdownDTO} inteiro serializado (mesma forma que a API já
 * devolve) — mais simples que modelar pedágios/postos/passos em tabelas
 * próprias, e dá pra tela de detalhe reconstruir a viagem inteira de graça.
 * origem/destino/distanciaKm/total ficam como colunas próprias só pra listar
 * sem precisar parsear o JSON inteiro.
 */
@Entity
@Table(name = "trip_history_entries")
public class TripHistoryEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "usuario_id", nullable = false)
    private User usuario;

    @Column(nullable = false)
    private String origem;

    @Column(nullable = false)
    private String destino;

    @Column(name = "distancia_km", nullable = false)
    private Double distanciaKm;

    @Column(nullable = false)
    private Double total;

    // columnDefinition = TEXT (não @Lob) de propósito: no Postgres, @Lob numa
    // String vira um "large object" (OID) — um mecanismo antigo que só funciona
    // dentro de uma transação explícita, e quebra em auto-commit com
    // "Objetos Grandes não podem ser usados no modo de efetivação automática".
    // TEXT é uma coluna Postgres normal, sem esse limite de tamanho real, sem
    // precisar de transação especial pra ler.
    @Column(name = "breakdown_json", nullable = false, columnDefinition = "TEXT")
    private String breakdownJson;

    @Column(name = "calculado_em", nullable = false)
    private Instant calculadoEm;

    public TripHistoryEntry() {
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public User getUsuario() {
        return usuario;
    }

    public void setUsuario(User usuario) {
        this.usuario = usuario;
    }

    public String getOrigem() {
        return origem;
    }

    public void setOrigem(String origem) {
        this.origem = origem;
    }

    public String getDestino() {
        return destino;
    }

    public void setDestino(String destino) {
        this.destino = destino;
    }

    public Double getDistanciaKm() {
        return distanciaKm;
    }

    public void setDistanciaKm(Double distanciaKm) {
        this.distanciaKm = distanciaKm;
    }

    public Double getTotal() {
        return total;
    }

    public void setTotal(Double total) {
        this.total = total;
    }

    public String getBreakdownJson() {
        return breakdownJson;
    }

    public void setBreakdownJson(String breakdownJson) {
        this.breakdownJson = breakdownJson;
    }

    public Instant getCalculadoEm() {
        return calculadoEm;
    }

    public void setCalculadoEm(Instant calculadoEm) {
        this.calculadoEm = calculadoEm;
    }
}
