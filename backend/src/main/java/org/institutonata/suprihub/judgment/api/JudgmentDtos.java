package org.institutonata.suprihub.judgment.api;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.judgment.application.JudgmentService.Snapshot;
import org.institutonata.suprihub.judgment.application.JudgmentService.SupplierView;
import org.institutonata.suprihub.judgment.model.CoverOffer;
import org.institutonata.suprihub.judgment.model.DrawRecord;
import org.institutonata.suprihub.judgment.model.Judgment;
import org.institutonata.suprihub.judgment.model.JudgmentEntry;
import org.institutonata.suprihub.judgment.model.JudgmentEnums.CoverStatus;
import org.institutonata.suprihub.judgment.model.JudgmentEnums.Status;
import org.institutonata.suprihub.tender.domain.Band;
import org.institutonata.suprihub.tender.domain.Tender;

public final class JudgmentDtos {

    private JudgmentDtos() {
    }

    // -------------------------------------------------------------- requisições

    public record StartRequest(@Size(max = 2000) String justification) {
    }

    public record ReasonRequest(@NotBlank(message = "Escreva o motivo.") @Size(max = 1000) String reason) {
    }

    public record DecisionRequest(@Size(max = 2000) String note, long version) {
    }

    public record AnnulRequest(@NotBlank(message = "Escreva o motivo.") @Size(max = 2000) String reason,
            long version) {
    }

    public record CoverRequest(@NotNull(message = "Informe o valor.")
            @DecimalMin(value = "0.01", message = "Informe um valor positivo.") BigDecimal value) {
    }

    // --------------------------------------------------------- Comissão/Diretoria

    /** Linha do mapa. {@code blackWomanOrCadUnico} só vai à equipe interna, nunca ao fornecedor. */
    public record EntryDto(Long proposalId, Long applicationId, Integer position, String supplierName,
            String supplierType, Territory territory, BigDecimal totalValue, BigDecimal pctAbove,
            boolean womanLed, boolean blackWomanOrCadUnico, boolean mei, boolean fictitiousTie,
            boolean localPreference, boolean finalist, boolean drawn, String advantage, boolean mustCover,
            String disqualifiedReason, boolean winner) {

        static EntryDto of(JudgmentEntry e, Long winnerId) {
            return new EntryDto(e.getProposalId(), e.getApplicationId(), e.getPosition(), e.getSupplierName(),
                    e.getSupplierType() == null ? null : e.getSupplierType().label(), e.getTerritory(),
                    e.getTotalValue(), e.getPctAbove(), e.isWomanLed(), e.isBlackWomanOrCadUnico(), e.isMei(),
                    e.isFictitiousTie(), e.isLocalPreference(), e.isFinalist(), e.isDrawn(),
                    e.getAdvantage() == null ? null : e.getAdvantage().label(), e.mustCover(),
                    e.getDisqualifiedReason(), e.getProposalId().equals(winnerId));
        }
    }

    public record CoverDto(Long id, int position, String supplierName, CoverStatus status, String statusLabel,
            BigDecimal ceiling, Instant offeredAt, LocalDate dueDate, Instant respondedAt, BigDecimal offeredValue) {

        static CoverDto of(CoverOffer c, Map<Long, String> names) {
            return new CoverDto(c.getId(), c.getPosition(), names.get(c.getProposalId()), c.getStatus(),
                    c.getStatus().label(), c.getCeiling(), c.getOfferedAt(), c.getDueDate(), c.getRespondedAt(),
                    c.getOfferedValue());
        }
    }

    public record DrawDto(long seed, String algorithm, Instant drawnAt, List<List<String>> groups) {
    }

    public record HistoryDto(Long id, Status status, String statusLabel, Instant startedAt, Instant annulledAt,
            String annulReason) {
    }

    public record JudgmentDto(Long id, Long tenderId, Status status, String statusLabel, Band band,
            BigDecimal lowestValue, int validCount, int minValidRequired, String belowMinimumJustification,
            BigDecimal tieMargin, BigDecimal localMargin, boolean localPreferenceForAll, boolean coverStrictlyLower,
            Long winnerProposalId, String winnerName, BigDecimal awardedValue, String winnerReason,
            Instant startedAt, Instant decidedAt, Instant homologatedAt, String homologationNote,
            Instant annulledAt, String annulReason, String ataSha256, boolean approvalByBoard,
            List<EntryDto> entries, List<CoverDto> covers, DrawDto draw, List<HistoryDto> history, long version) {

        static JudgmentDto of(Tender t, Snapshot s, ObjectMapper json) {
            Judgment j = s.judgment();
            Map<Long, String> names = s.entries().stream()
                    .collect(Collectors.toMap(JudgmentEntry::getProposalId, JudgmentEntry::getSupplierName));
            Judgment.Rules r = j.rules();
            return new JudgmentDto(j.getId(), j.getTenderId(), j.getStatus(), j.getStatus().label(), t.getBand(),
                    j.getLowestValue(), j.getValidCount(), r.minValidRequired(), j.getBelowMinimumJustification(),
                    r.tieMargin(), r.localMargin(), r.localPreferenceForAll(), r.coverStrictlyLower(),
                    j.getWinnerProposalId(), j.getWinnerProposalId() == null ? null : names.get(j.getWinnerProposalId()),
                    j.getAwardedValue(), j.getWinnerReason(), j.getStartedAt(), j.getDecidedAt(), j.getHomologatedAt(),
                    j.getHomologationNote(), j.getAnnulledAt(), j.getAnnulReason(), j.getAtaSha256(),
                    t.getBand() == Band.FAIXA_3,
                    s.entries().stream().map(e -> EntryDto.of(e, j.getWinnerProposalId())).toList(),
                    s.covers().stream().map(c -> CoverDto.of(c, names)).toList(),
                    draw(s.draw(), names, json),
                    s.history().stream().map(h -> new HistoryDto(h.getId(), h.getStatus(), h.getStatus().label(),
                            h.getStartedAt(), h.getAnnulledAt(), h.getAnnulReason())).toList(),
                    j.getVersion());
        }

        private static DrawDto draw(DrawRecord d, Map<Long, String> names, ObjectMapper json) {
            if (d == null) {
                return null;
            }
            List<List<Long>> groups;
            try {
                groups = json.readValue(d.getGroupsJson(), new TypeReference<>() {
                });
            } catch (Exception e) {
                groups = List.of();
            }
            return new DrawDto(d.getSeed(), d.getAlgorithm(), d.getDrawnAt(),
                    groups.stream().map(g -> g.stream().map(names::get).toList()).toList());
        }
    }

    /** Painel do julgamento na tela da oportunidade. {@code judgment} nulo = ainda não julgada. */
    public record JudgmentPanel(boolean canStart, int minValidRequired, JudgmentDto judgment) {
    }

    /** Fila da Diretoria. */
    public record BoardItem(Long judgmentId, Long tenderId, String tenderNumber, String tenderTitle,
            String lineCode, String winnerName, BigDecimal awardedValue, BigDecimal estimatedValue,
            Instant decidedAt) {
    }

    public record BoardDetail(Long tenderId, String tenderNumber, String tenderTitle, String description,
            String lineCode, String lineName, String bandLabel, BigDecimal estimatedValue, String executionMunicipality,
            Instant proposalDeadline, JudgmentDto judgment) {
    }

    // ---------------------------------------------------------------- Fornecedor

    public record MyCover(CoverStatus status, String statusLabel, BigDecimal ceiling, LocalDate dueDate,
            boolean strictlyLower, boolean canRespond, BigDecimal offeredValue) {
    }

    public record MyResult(boolean winner, BigDecimal awardedValue, String reason, Integer myPosition,
            boolean disqualified, String disqualifiedReason, Instant homologatedAt) {
    }

    /** O que o fornecedor vê do julgamento: só a própria convocação e o resultado homologado. */
    public record MyJudgment(boolean judging, MyCover cover, MyResult result) {

        static MyJudgment of(SupplierView v) {
            MyCover cover = v.cover() == null ? null : new MyCover(v.cover().getStatus(), v.cover().getStatus().label(),
                    v.cover().getCeiling(), v.cover().getDueDate(), v.coverStrictlyLower(), v.canCover(),
                    v.cover().getOfferedValue());
            MyResult result = null;
            if (v.judgment() != null) {
                JudgmentEntry e = v.myEntry();
                result = new MyResult(e != null && e.getProposalId().equals(v.judgment().getWinnerProposalId()),
                        v.judgment().getAwardedValue(), v.publicReason(), e == null ? null : e.getPosition(),
                        e != null && e.isDisqualified(), e == null ? null : e.getDisqualifiedReason(),
                        v.judgment().getHomologatedAt());
            }
            return new MyJudgment(true, cover, result);
        }
    }
}
