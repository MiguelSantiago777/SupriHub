package org.institutonata.suprihub.tender.application;

import java.math.BigDecimal;
import java.time.Instant;

import org.institutonata.suprihub.tender.domain.Tender;

/**
 * Avisado, na mesma transação, quando a oportunidade é adjudicada (Faixa 1: seleção
 * confirmada; Faixas 2 e 3: julgamento homologado). Cria a Ordem de Fornecimento sem que
 * {@code tender} dependa do módulo de contratação.
 */
public interface TenderAwardListener {

    /**
     * @param acceptedAt Faixa 1: quando o fornecedor aceitou o convite (o aceite eletrônico
     *                   substitui a assinatura da ordem — Edital 9.2); nulo nas demais faixas
     */
    void onAwarded(Tender tender, long applicationId, Long proposalId, BigDecimal value, Instant acceptedAt);
}
