package com.rotacusto;

import java.io.IOException;
import java.nio.file.Path;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;

@SpringBootApplication
public class RotaCustoApplication {

    public static void main(String[] args) throws IOException {
        startEmbeddedPostgres();
        SpringApplication.run(RotaCustoApplication.class, args);
    }

    /**
     * Sobe um Postgres real, gerenciado pelo próprio processo do back-end — evita
     * depender de Docker (não funciona nesta máquina, limitação de virtualização
     * aninhada) ou de instalar um Postgres à parte. Roda ANTES do SpringApplication.run
     * de propósito: garante que o servidor já está aceitando conexões quando o
     * datasource do Spring (configurado em application.yml pra apontar pra essa mesma
     * porta) tenta conectar, sem precisar lutar com ordem de inicialização de beans.
     *
     * Pasta de dados fica fora do repo (não em backend/) pra nunca virar risco de
     * commit binário por engano. Porta 5433 (não a 5432 padrão) pra não colidir com
     * um Postgres "de verdade" que o usuário possa instalar nesta máquina no futuro.
     *
     * Não roda durante os testes: @SpringBootTest sobe o contexto do Spring direto,
     * sem passar por este main() — por isso os testes continuam em H2
     * (src/test/resources/application.yml), sem precisar de Postgres nenhum.
     */
    private static void startEmbeddedPostgres() throws IOException {
        Path dataDir = Path.of(System.getProperty("user.home"), ".rotacusto", "pgdata");
        EmbeddedPostgres postgres = EmbeddedPostgres.builder()
                .setDataDirectory(dataDir)
                // Por padrão a lib trata o data dir como descartável e tenta rodar
                // initdb de novo a cada start — o que falha (e falharia mesmo sem
                // erro, destruiria os dados) numa pasta já inicializada. Isso é o
                // que faz o dado sobreviver de verdade entre restarts.
                .setCleanDataDirectory(false)
                .setPort(5433)
                .start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                postgres.close();
            } catch (IOException ignored) {
                // Best-effort no desligamento — não impede o processo de encerrar.
            }
        }));
    }
}
