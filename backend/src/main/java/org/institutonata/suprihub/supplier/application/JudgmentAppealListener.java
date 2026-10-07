package org.institutonata.suprihub.supplier.application;

import org.institutonata.suprihub.supplier.domain.Appeal;

/**
 * Recurso contra o julgamento provido (pela Comissão ou pela Diretoria): o módulo de
 * julgamento reabre o julgamento. Pode recusar (exceção de negócio) se a ordem já está em
 * execução, e aí o provimento não é registrado.
 */
public interface JudgmentAppealListener {

    void onGranted(Appeal appeal, long deciderId, String reason);
}
