package org.institutonata.suprihub.tender.application;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Referência do Banco de Preços para um item (Manual 4.1: na Faixa 1 o preço do rodízio é
 * conferido contra a média + margem). Implementada pelo módulo de contratação.
 */
public interface PriceReferenceSource {

    record Reference(int count, BigDecimal average, BigDecimal ceiling) {
    }

    Optional<Reference> referenceFor(short supplyLineId, String itemDescription, String unit);
}
