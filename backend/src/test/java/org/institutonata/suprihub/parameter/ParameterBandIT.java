package org.institutonata.suprihub.parameter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.institutonata.suprihub.support.Auth;
import org.institutonata.suprihub.support.IntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Critério de aceite: "mudar o limite em parameter muda o comportamento sem alterar código".
 */
class ParameterBandIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @AfterEach
    void restore() {
        jdbc.update("UPDATE parameter SET value = '65492.11' WHERE key = 'band2_upper_limit'");
    }

    @Test
    void alterarLimiteDaFaixa2MudaOEnquadramento() throws Exception {
        mvc.perform(get("/api/public/bands").param("estimatedValue", "20000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.band").value("FAIXA_2"))
                .andExpect(jsonPath("$.band2UpperLimit").value(65492.11));

        String admin = Auth.admin(mvc);
        mvc.perform(put("/api/admin/parameters/band2_upper_limit").header("Authorization", admin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"15000,00\",\"reason\":\"Teste de reajuste\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.value").value("15000.00"));

        mvc.perform(get("/api/public/bands").param("estimatedValue", "20000"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.band").value("FAIXA_3"));

        mvc.perform(get("/api/admin/parameters/band2_upper_limit/history").header("Authorization", admin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].oldValue").value("65492.11"))
                .andExpect(jsonPath("$[0].newValue").value("15000.00"))
                .andExpect(jsonPath("$[0].reason").value("Teste de reajuste"));

        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM audit_log WHERE entity_type = 'parameter' AND entity_id = 'band2_upper_limit'""",
                Long.class)).isPositive();
    }

    @Test
    void limiteDaFaixa1NaoPodeSuperarODaFaixa2() throws Exception {
        mvc.perform(put("/api/admin/parameters/band1_upper_limit").header("Authorization", Auth.admin(mvc))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"70000\",\"reason\":\"erro\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.code").value("INVALID_BAND_LIMITS"));
    }

    @Test
    void auditoriaEhImutavel() {
        assertThatThrownBy(() -> jdbc.update("UPDATE audit_log SET action = 'X'"))
                .hasMessageContaining("imutável");
    }

    @Test
    void somenteAdminAlteraParametros() throws Exception {
        mvc.perform(put("/api/admin/parameters/band2_upper_limit")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"value\":\"1\",\"reason\":\"x\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }
}
