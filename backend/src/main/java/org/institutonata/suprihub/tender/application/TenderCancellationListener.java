package org.institutonata.suprihub.tender.application;

import org.institutonata.suprihub.tender.domain.Tender;

/**
 * Avisado, na mesma transação, quando uma oportunidade é cancelada (ex.: o julgamento em
 * curso é anulado). Evita que {@code tender} dependa do módulo de julgamento.
 */
public interface TenderCancellationListener {

    void onCancelled(Tender tender, String reason);
}
