package org.institutonata.suprihub;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;

import org.institutonata.suprihub.parameter.domain.ParameterKey;
import org.institutonata.suprihub.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

/** Seeds do Flyway conferidos contra os documentos (Anexo I, Parâmetros, IBGE). */
class SeedDataIT extends IntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Test
    void catalogoDoAnexoIComVinteLinhas() throws Exception {
        mvc.perform(get("/api/public/supply-lines"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(20)))
                .andExpect(jsonPath("$[0].code").value("L01"))
                .andExpect(jsonPath("$[0].cnaeReference").value("5620-1/04; 5620-1/02; 1091-1/02; 4729-6/99"))
                .andExpect(jsonPath("$[0].allowsIndividual").value(false))
                .andExpect(jsonPath("$[19].code").value("L20"));

        List<String> pfLines = jdbc.queryForList(
                "SELECT code FROM supply_line WHERE allows_individual ORDER BY code", String.class);
        assertThat(pfLines).doesNotContain("L01", "L04", "L05", "L10", "L13", "L14", "L16").hasSize(13);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM supply_line WHERE allows_medium_large", Long.class))
                .isZero();
    }

    @Test
    void municipiosDoRjComRegiaoMetropolitana() throws Exception {
        mvc.perform(get("/api/public/municipalities")).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(92)));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM municipality WHERE metropolitan_region", Long.class))
                .isEqualTo(22);
        assertThat(jdbc.queryForObject(
                "SELECT metropolitan_region FROM municipality WHERE ibge_code = '3302858'", Boolean.class)).isTrue();
    }

    @Test
    void todosOsParametrosDoCodigoExistemComValoresDe2026() {
        List<String> keys = jdbc.queryForList("SELECT key FROM parameter", String.class);
        assertThat(keys).containsAll(Arrays.stream(ParameterKey.values()).map(ParameterKey::dbKey).toList());
        assertThat(jdbc.queryForObject("SELECT value FROM parameter WHERE key='band1_upper_limit'", String.class))
                .isEqualTo("13098.41");
        assertThat(jdbc.queryForObject("SELECT value FROM parameter WHERE key='band2_upper_limit'", String.class))
                .isEqualTo("65492.11");
    }

    @Test
    void feriadosDeMesquitaVinculadosAoMunicipio() {
        assertThat(jdbc.queryForObject("""
                SELECT count(*) FROM business_holiday h JOIN municipality m ON m.id = h.municipality_id
                WHERE m.ibge_code = '3302858'""", Long.class)).isEqualTo(2);
    }

    @Test
    void prazoEmDiasUteisUsaOCalendarioDoBanco() throws Exception {
        // sexta 09/10/2026 + 1 d.u. → segunda 12/10 é feriado nacional → terça 13/10
        mvc.perform(get("/api/public/business-days").param("start", "2026-10-09").param("businessDays", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").value("2026-10-13"));
    }
}
