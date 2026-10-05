package org.institutonata.suprihub.tender.domain;

import java.time.Instant;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * Próximo do rodízio da Faixa 1 (D-034, critério da Planilha/Manual): primeiro quem nunca foi
 * selecionado, depois quem está há mais tempo sem seleção; empate → credenciamento mais
 * antigo; por fim, o número do requerimento. Quem já foi convidado nesta oportunidade sai.
 */
public final class RotationPolicy {

    private RotationPolicy() {
    }

    /**
     * @param lastSelectedAt última vez que foi selecionado no rodízio ({@code null} = nunca)
     * @param accreditedAt   data da habilitação
     */
    public record Candidate(Long applicationId, Instant lastSelectedAt, Instant accreditedAt) {
    }

    private static final Comparator<Candidate> ORDER = Comparator
            .comparing(Candidate::lastSelectedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
            .thenComparing(Candidate::accreditedAt, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Candidate::applicationId);

    public static Optional<Candidate> next(Collection<Candidate> eligible, Set<Long> alreadyInvited) {
        return eligible.stream()
                .filter(c -> !alreadyInvited.contains(c.applicationId()))
                .min(ORDER);
    }

    /** Ordem completa da fila (para exibir à Comissão). */
    public static List<Candidate> queue(Collection<Candidate> eligible, Set<Long> alreadyInvited) {
        return eligible.stream()
                .filter(c -> !alreadyInvited.contains(c.applicationId()))
                .sorted(ORDER)
                .toList();
    }
}
