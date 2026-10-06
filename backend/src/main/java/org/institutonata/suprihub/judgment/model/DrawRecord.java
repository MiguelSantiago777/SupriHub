package org.institutonata.suprihub.judgment.model;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.Immutable;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Registro do sorteio (Edital 7.6): semente e grupos na ordem sorteada. Imutável. */
@Entity
@Immutable
@Table(name = "draw_record")
public class DrawRecord {

    public static final String ALGORITHM = "SplittableRandom(semente) + Fisher-Yates; empatadas em ordem de id";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "judgment_id", nullable = false)
    private Long judgmentId;

    @Column(nullable = false)
    private long seed;

    @Column(nullable = false)
    private String algorithm;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "groups_json", nullable = false)
    private String groupsJson;

    @Column(name = "drawn_at", nullable = false)
    private Instant drawnAt;

    protected DrawRecord() {
    }

    public DrawRecord(Long judgmentId, long seed, String groupsJson, Instant now) {
        this.judgmentId = judgmentId;
        this.seed = seed;
        this.algorithm = ALGORITHM;
        this.groupsJson = groupsJson;
        this.drawnAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getJudgmentId() {
        return judgmentId;
    }

    public long getSeed() {
        return seed;
    }

    public String getAlgorithm() {
        return algorithm;
    }

    public String getGroupsJson() {
        return groupsJson;
    }

    public Instant getDrawnAt() {
        return drawnAt;
    }
}
