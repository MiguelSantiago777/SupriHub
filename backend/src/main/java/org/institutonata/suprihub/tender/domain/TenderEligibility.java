package org.institutonata.suprihub.tender.domain;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Set;

import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.tender.domain.TenderEnums.Audience;

/**
 * Quem pode ver e responder a uma oportunidade. Regra pura:
 * <ul>
 *   <li>linha da oportunidade entre as linhas do fornecedor;</li>
 *   <li>algum território de atendimento em comum;</li>
 *   <li>exclusiva a mulheres → negócio liderado por mulher (Edital 8.5);</li>
 *   <li>Faixa 2 e rodízio: credenciado com credenciamento vigente;</li>
 *   <li>Faixa 3: também quem tem requerimento enviado/em ajuste (Política: "a quem se
 *       credencie até a data-limite"; D-033).</li>
 * </ul>
 */
public final class TenderEligibility {

    private TenderEligibility() {
    }

    /** Situação do fornecedor no credenciamento (independente de JPA). */
    public enum Standing { ACCREDITED, PENDING, OTHER }

    public record SupplierProfile(Long applicationId, Standing standing, LocalDate validUntil,
            Set<String> lineCodes, Set<Territory> territories, boolean womanLed) {
    }

    public record Opportunity(Band band, String lineCode, Set<Territory> territories, Audience audience) {
    }

    public static boolean eligible(Opportunity o, SupplierProfile s, LocalDate today) {
        if (!s.lineCodes().contains(o.lineCode())) {
            return false;
        }
        if (Collections.disjoint(o.territories(), s.territories())) {
            return false;
        }
        if (o.audience() == Audience.WOMEN && !s.womanLed()) {
            return false;
        }
        boolean accredited = s.standing() == Standing.ACCREDITED && s.validUntil() != null
                && !s.validUntil().isBefore(today);
        if (o.band() == Band.FAIXA_3) {
            return accredited || s.standing() == Standing.PENDING;
        }
        return accredited;
    }
}
