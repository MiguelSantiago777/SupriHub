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

    /** Resposta a um convite do rodízio, para contar as recusas. */
    public record Response(Instant invitedAt, TenderEnums.InvitationStatus status, String declineReason) {

        /**
         * Recusa injustificada: não respondeu no prazo ou recusou sem motivo. Recusar
         * informando a indisponibilidade afasta a infração (Anexo XII).
         */
        public boolean unjustifiedRefusal() {
            return status == TenderEnums.InvitationStatus.EXPIRED
                    || (status == TenderEnums.InvitationStatus.DECLINED
                            && (declineReason == null || declineReason.isBlank()));
        }
    }

    /**
     * Política art. 13, § 4º: as {@code refusals} respostas mais recentes do credenciado no
     * rodízio são todas recusas injustificadas, e nenhuma já foi contada num deslocamento
     * anterior ({@code lastDisplacedAt}).
     */
    public static boolean shouldDisplace(List<Response> responses, int refusals, Instant lastDisplacedAt) {
        List<Response> latest = responses.stream()
                .sorted(Comparator.comparing(Response::invitedAt).reversed())
                .limit(refusals)
                .toList();
        return latest.size() == refusals
                && latest.stream().allMatch(Response::unjustifiedRefusal)
                && (lastDisplacedAt == null || latest.getLast().invitedAt().isAfter(lastDisplacedAt));
    }

    /** Ordem completa da fila (para exibir à Comissão). */
    public static List<Candidate> queue(Collection<Candidate> eligible, Set<Long> alreadyInvited) {
        return eligible.stream()
                .filter(c -> !alreadyInvited.contains(c.applicationId()))
                .sorted(ORDER)
                .toList();
    }
}
