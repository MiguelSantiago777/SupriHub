package org.institutonata.suprihub.tender.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.institutonata.suprihub.tender.domain.RotationPolicy.Response;
import org.institutonata.suprihub.tender.domain.TenderEnums.InvitationStatus;
import org.junit.jupiter.api.Test;

/** Política art. 13, § 4º: duas recusas injustificadas seguidas deslocam ao final do rodízio. */
class RotationDisplacementTest {

    static final Instant T0 = Instant.parse("2026-10-01T12:00:00Z");

    static Response at(int day, InvitationStatus status, String reason) {
        return new Response(T0.plusSeconds(day * 86_400L), status, reason);
    }

    @Test
    void duasRecusasSemMotivoOuSemRespostaDeslocam() {
        assertThat(RotationPolicy.shouldDisplace(List.of(
                at(1, InvitationStatus.CONFIRMED, null),
                at(2, InvitationStatus.EXPIRED, null),
                at(3, InvitationStatus.DECLINED, " ")), 2, null)).isTrue();
    }

    @Test
    void recusaComMotivoAfastaAInfracao() {
        // "Comunicação prévia da indisponibilidade afasta a infração" (Anexo XII)
        assertThat(RotationPolicy.shouldDisplace(List.of(
                at(2, InvitationStatus.EXPIRED, null),
                at(3, InvitationStatus.DECLINED, "Estarei viajando nessa data.")), 2, null)).isFalse();
    }

    @Test
    void selecaoNoMeioQuebraASequencia() {
        assertThat(RotationPolicy.shouldDisplace(List.of(
                at(1, InvitationStatus.EXPIRED, null),
                at(2, InvitationStatus.CONFIRMED, null),
                at(3, InvitationStatus.EXPIRED, null)), 2, null)).isFalse();
        assertThat(RotationPolicy.shouldDisplace(List.of(at(1, InvitationStatus.EXPIRED, null)), 2, null))
                .isFalse();
    }

    @Test
    void recusasJaContadasNaoDeslocamDeNovo() {
        List<Response> responses = List.of(at(1, InvitationStatus.EXPIRED, null),
                at(2, InvitationStatus.EXPIRED, null));
        Instant displaced = T0.plusSeconds(2 * 86_400L + 60);
        assertThat(RotationPolicy.shouldDisplace(responses, 2, displaced)).isFalse();
        List<Response> more = List.of(at(1, InvitationStatus.EXPIRED, null), at(2, InvitationStatus.EXPIRED, null),
                at(5, InvitationStatus.EXPIRED, null));
        // A 3ª recusa sozinha não basta: a 2ª já entrou no deslocamento anterior
        assertThat(RotationPolicy.shouldDisplace(more, 2, displaced)).isFalse();
        List<Response> again = List.of(at(5, InvitationStatus.EXPIRED, null), at(6, InvitationStatus.DECLINED, null));
        assertThat(RotationPolicy.shouldDisplace(again, 2, displaced)).isTrue();
    }
}
