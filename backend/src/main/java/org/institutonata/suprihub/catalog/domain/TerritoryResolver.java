package org.institutonata.suprihub.catalog.domain;

/**
 * Calcula o território de um fornecedor <em>em relação a um edital</em> (Política art. 3º, XII):
 * <ol>
 *   <li>T1 — sede em Mesquita ou no município de execução do edital;</li>
 *   <li>T2 — sede em outro município da Região Metropolitana do RJ;</li>
 *   <li>T3 — sede em outro município do Estado do RJ;</li>
 *   <li>fora do estado — nível 4.</li>
 * </ol>
 * Município de execução desconhecido (ausente do cadastro): só Mesquita é T1, e o
 * resultado sinaliza o aviso à Comissão (docs/DECISOES.md).
 */
public final class TerritoryResolver {

    /** Código IBGE de Mesquita/RJ — sede do Instituto, sempre T1. */
    public static final String MESQUITA_IBGE = "3302858";
    public static final String RJ = "RJ";

    private TerritoryResolver() {
    }

    public record Resolution(Territory territory, boolean executionMunicipalityUnknown) {
    }

    public static Resolution resolve(MunicipalityRef headquarters, MunicipalityRef execution) {
        boolean unknownExecution = execution == null;
        if (headquarters == null || !RJ.equalsIgnoreCase(headquarters.uf())) {
            return new Resolution(Territory.OUT_OF_STATE, unknownExecution);
        }
        boolean isSeat = MESQUITA_IBGE.equals(headquarters.ibgeCode());
        boolean isExecution = !unknownExecution && headquarters.ibgeCode().equals(execution.ibgeCode());
        if (isSeat || isExecution) {
            return new Resolution(Territory.T1, unknownExecution);
        }
        if (headquarters.metropolitanRegion()) {
            return new Resolution(Territory.T2, unknownExecution);
        }
        return new Resolution(Territory.T3, unknownExecution);
    }
}
