-- =====================================================================
-- Lacunas do fluxo: recurso contra o julgamento (Anexo X), convocação do próximo
-- classificado (Edital 9.1), sinal (Edital 10.4), contratação de urgência (Política
-- art. 13, § 3º) e deslocamento ao final do rodízio (Política art. 13, § 4º; Anexo XII).
-- =====================================================================

-- 1. Recurso contra o julgamento: mesmo formulário e mesmas etapas do recurso de habilitação.
ALTER TABLE application_appeal ADD COLUMN kind VARCHAR(20) NOT NULL DEFAULT 'HABILITATION'; -- HABILITATION | JUDGMENT
ALTER TABLE application_appeal ADD COLUMN judgment_id BIGINT REFERENCES judgment (id);
ALTER TABLE application_appeal ADD COLUMN tender_id BIGINT REFERENCES tender (id);
-- Assunto mostrado nas filas: "OP-2026-0003 — título" (fixado no protocolo do recurso).
ALTER TABLE application_appeal ADD COLUMN subject VARCHAR(300);
DROP INDEX ux_application_appeal_open;
CREATE UNIQUE INDEX ux_application_appeal_open
    ON application_appeal (application_id, kind, COALESCE(judgment_id, 0)) WHERE status IN ('FILED', 'FORWARDED');
CREATE INDEX ix_application_appeal_judgment ON application_appeal (judgment_id) WHERE judgment_id IS NOT NULL;

-- Prazo para recorrer do resultado (3 d.u. da homologação, que é a comunicação aos proponentes).
ALTER TABLE judgment ADD COLUMN appeal_deadline DATE;
-- Recurso provido depois da homologação reabre o julgamento.
ALTER TABLE judgment ADD COLUMN reopened_at TIMESTAMPTZ;

-- 2. Convocação do próximo classificado: várias ordens por oportunidade, uma só viva.
ALTER TABLE service_order DROP CONSTRAINT service_order_tender_id_key;
CREATE UNIQUE INDEX ux_service_order_tender_live ON service_order (tender_id) WHERE status <> 'CANCELLED';
ALTER TABLE service_order ADD COLUMN replaces_order_id BIGINT REFERENCES service_order (id);

-- 3. Sinal pago como parcela separada (antes do recebimento).
ALTER TABLE service_order ADD COLUMN upfront_paid_on        DATE;
ALTER TABLE service_order ADD COLUMN upfront_paid_value     NUMERIC(14, 2) CHECK (upfront_paid_value > 0);
ALTER TABLE service_order ADD COLUMN upfront_payment_method VARCHAR(20);
ALTER TABLE service_order ADD COLUMN upfront_recorded_by    BIGINT REFERENCES app_user (id);

-- 4. Contratação de urgência: o gestor contrata direto um credenciado da linha, com
-- justificativa no DFD, e a Comissão toma ciência em 2 d.u.
ALTER TABLE tender ADD COLUMN urgent                  BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE tender ADD COLUMN urgency_justification   VARCHAR(3000);
ALTER TABLE tender ADD COLUMN urgency_ack_due         DATE;
ALTER TABLE tender ADD COLUMN urgency_acknowledged_at TIMESTAMPTZ;
ALTER TABLE tender ADD COLUMN urgency_acknowledged_by BIGINT REFERENCES app_user (id);
ALTER TABLE tender ADD COLUMN urgency_ack_note        VARCHAR(3000);

INSERT INTO parameter (key, value, value_type, category, description, source) VALUES
('urgency_acknowledgement_business_days', '2', 'INTEGER', 'CONTRATACAO',
 'Prazo para a Comissão tomar ciência da contratação de urgência (dias úteis)', 'Política art. 13, § 3º; Manual 4.3'),
('rotation_unjustified_refusals', '2', 'INTEGER', 'PROPOSTAS',
 'Recusas injustificadas consecutivas no rodízio que deslocam o credenciado ao final da fila', 'Política art. 13, § 4º; Anexo XII');

-- 5. Deslocamento ao final do rodízio por recusas injustificadas consecutivas.
CREATE TABLE rotation_displacement (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    application_id BIGINT       NOT NULL REFERENCES supplier_application (id),
    tender_id      BIGINT       NOT NULL REFERENCES tender (id),   -- oportunidade da última recusa
    displaced_at   TIMESTAMPTZ  NOT NULL,
    reason         VARCHAR(500) NOT NULL,
    recidivism     BOOLEAN      NOT NULL DEFAULT FALSE             -- já deslocado antes: cabe advertência
);
CREATE INDEX ix_rotation_displacement_application ON rotation_displacement (application_id, displaced_at DESC);
