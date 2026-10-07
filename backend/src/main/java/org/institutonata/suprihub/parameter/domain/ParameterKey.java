package org.institutonata.suprihub.parameter.domain;

/**
 * Chaves dos parâmetros de negócio (tabela {@code parameter}). O valor nunca fica no
 * código: aqui só o nome e o tipo. A aplicação falha ao subir se alguma chave não existir.
 */
public enum ParameterKey {

    // Faixas
    REFERENCE_YEAR(ValueType.INTEGER),
    UPDATE_DECREE(ValueType.TEXT),
    BAND1_UPPER_LIMIT(ValueType.DECIMAL),
    BAND2_UPPER_LIMIT(ValueType.DECIMAL),
    // Julgamento / tratamento favorecido
    FICTITIOUS_TIE_MARGIN(ValueType.DECIMAL),
    LOCAL_PREFERENCE_MARGIN(ValueType.DECIMAL),
    LOCAL_PREFERENCE_FOR_ALL(ValueType.BOOLEAN),
    COVER_REQUIRES_STRICTLY_LOWER(ValueType.BOOLEAN),
    COVER_DEADLINE_BUSINESS_DAYS(ValueType.INTEGER),
    BAND2_MIN_VALID_PROPOSALS(ValueType.INTEGER),
    EXCLUSIVITY_MIN_SMALL_BUSINESSES(ValueType.INTEGER),
    WOMEN_EXCLUSIVE_MIN_SUPPLIERS(ValueType.INTEGER),
    PRICE_BANK_CEILING_MARGIN(ValueType.DECIMAL),
    // Propostas
    BAND1_ACCEPTANCE_BUSINESS_DAYS(ValueType.INTEGER),
    BAND2_MIN_PROPOSAL_BUSINESS_DAYS(ValueType.INTEGER),
    BAND3_MIN_PROPOSAL_BUSINESS_DAYS(ValueType.INTEGER),
    PROPOSAL_VALIDITY_DAYS(ValueType.INTEGER),
    SERVICE_ORDER_SIGNATURE_BUSINESS_DAYS(ValueType.INTEGER),
    UPFRONT_PAYMENT_MAX_SHARE(ValueType.DECIMAL),
    // Contratação, recebimento e pagamento
    PAYMENT_DEADLINE_DAYS(ValueType.INTEGER),
    LOW_RATING_THRESHOLD(ValueType.DECIMAL),
    LOW_RATING_OCCURRENCES(ValueType.INTEGER),
    LOW_RATING_WINDOW_MONTHS(ValueType.INTEGER),
    RATING_WINDOW_MONTHS(ValueType.INTEGER),
    // Sanções e controle
    SANCTION_DEFENSE_BUSINESS_DAYS(ValueType.INTEGER),
    SANCTION_DECISION_BUSINESS_DAYS(ValueType.INTEGER),
    SANCTION_APPEAL_BUSINESS_DAYS(ValueType.INTEGER),
    SANCTION_BOARD_DECISION_BUSINESS_DAYS(ValueType.INTEGER),
    SANCTION_RECIDIVISM_YEARS(ValueType.INTEGER),
    DOSSIER_REVIEW_SAMPLE_SHARE(ValueType.DECIMAL),
    // Credenciamento
    ACCREDITATION_VALIDITY_MONTHS(ValueType.INTEGER),
    CERTIFICATE_ALERT_DAYS(ValueType.INTEGER),
    CERTIFICATE_RECHECK_MONTHS(ValueType.INTEGER),
    CERTIFICATE_REGULARIZATION_BUSINESS_DAYS(ValueType.INTEGER),
    DEREGISTRATION_AFTER_IRREGULAR_DAYS(ValueType.INTEGER),
    ANALYSIS_BUSINESS_DAYS(ValueType.INTEGER),
    DILIGENCE_BUSINESS_DAYS(ValueType.INTEGER),
    FISCAL_REGULARIZATION_BUSINESS_DAYS(ValueType.INTEGER),
    FISCAL_REGULARIZATION_EXTENSION_BUSINESS_DAYS(ValueType.INTEGER),
    APPEAL_FILING_BUSINESS_DAYS(ValueType.INTEGER),
    APPEAL_RECONSIDERATION_BUSINESS_DAYS(ValueType.INTEGER),
    BOARD_APPEAL_DECISION_BUSINESS_DAYS(ValueType.INTEGER),
    REGISTRATION_UPDATE_BUSINESS_DAYS(ValueType.INTEGER),
    RENEWAL_NOTICE_DAYS(ValueType.INTEGER),
    UPLOAD_MAX_SIZE_MB(ValueType.INTEGER),
    // Calendário
    OPTIONAL_POINTS_ARE_NON_BUSINESS(ValueType.BOOLEAN),
    // Metas
    GOAL_SMALL_BUSINESS_SHARE(ValueType.DECIMAL),
    GOAL_WOMEN_SHARE(ValueType.DECIMAL),
    GOAL_T1_SHARE(ValueType.DECIMAL),
    GOAL_MIN_AVG_RATING(ValueType.DECIMAL),
    GOAL_MAX_URGENCY_SHARE(ValueType.DECIMAL);

    private final ValueType type;

    ParameterKey(ValueType type) {
        this.type = type;
    }

    public ValueType type() {
        return type;
    }

    /** Nome da coluna {@code key} no banco (snake_case minúsculo). */
    public String dbKey() {
        return name().toLowerCase();
    }

    public static ParameterKey fromDbKey(String key) {
        return valueOf(key.toUpperCase());
    }
}
