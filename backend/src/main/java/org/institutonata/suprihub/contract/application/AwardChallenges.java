package org.institutonata.suprihub.contract.application;

/** Recurso contra o resultado em análise: a ordem da oportunidade não é emitida (D-049). */
public interface AwardChallenges {

    boolean pending(long tenderId);
}
