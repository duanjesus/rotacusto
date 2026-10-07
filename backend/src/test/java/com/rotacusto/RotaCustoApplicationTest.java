package com.rotacusto;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class RotaCustoApplicationTest {

    @Test
    void startsEmbeddedPostgresWhenNoExternalDatasourceIsConfigured() {
        assertThat(RotaCustoApplication.shouldStartEmbeddedPostgres(null)).isTrue();
        assertThat(RotaCustoApplication.shouldStartEmbeddedPostgres("  ")).isTrue();
    }

    @Test
    void skipsEmbeddedPostgresWhenAnExternalDatasourceIsConfigured() {
        assertThat(RotaCustoApplication.shouldStartEmbeddedPostgres("jdbc:postgresql://db.example.com/rotacusto"))
                .isFalse();
    }
}
