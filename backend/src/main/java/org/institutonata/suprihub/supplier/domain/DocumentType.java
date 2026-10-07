package org.institutonata.suprihub.supplier.domain;

import static org.institutonata.suprihub.supplier.domain.DocumentType.Requirement.NOT_APPLICABLE;
import static org.institutonata.suprihub.supplier.domain.DocumentType.Requirement.OPTIONAL;
import static org.institutonata.suprihub.supplier.domain.DocumentType.Requirement.REQUIRED;

import java.util.Map;

/**
 * Documentos de habilitação do Edital, item 4.1, com a exigência por tipo de interessado.
 * "Se aplicável", "quando exigível" e "recomendado" são opcionais no envio: a Comissão
 * pede por ajuste quando a atividade exigir.
 */
public enum DocumentType {

    CCMEI("Certificado da Condição de Microempreendedor Individual (CCMEI)",
            Map.of(SupplierType.MEI, REQUIRED)),
    SOCIAL_CONTRACT("Contrato social ou estatuto e última alteração (cooperativas: ata de eleição da diretoria)",
            Map.of(SupplierType.ME, REQUIRED, SupplierType.EPP, REQUIRED, SupplierType.COOPERATIVE, REQUIRED)),
    CNPJ_CARD("Comprovante de inscrição e situação cadastral no CNPJ",
            Map.of(SupplierType.MEI, REQUIRED, SupplierType.ME, REQUIRED, SupplierType.EPP, REQUIRED,
                    SupplierType.COOPERATIVE, REQUIRED)),
    SIZE_DECLARATION("Declaração de enquadramento como ME/EPP ou certidão simplificada da Junta Comercial",
            Map.of(SupplierType.ME, REQUIRED, SupplierType.EPP, REQUIRED, SupplierType.COOPERATIVE, OPTIONAL)),
    ID_DOCUMENT("Documento de identidade e CPF do titular, administrador ou pessoa física",
            all(REQUIRED)),
    ADDRESS_PROOF("Comprovante de residência ou de endereço da sede (até 90 dias)",
            all(REQUIRED)),
    FEDERAL_CERTIFICATE("Certidão de débitos federais e dívida ativa da União (RFB/PGFN)",
            all(REQUIRED), true),
    FGTS_CERTIFICATE("Certificado de Regularidade do FGTS (CRF)",
            Map.of(SupplierType.MEI, REQUIRED, SupplierType.ME, REQUIRED, SupplierType.EPP, REQUIRED,
                    SupplierType.COOPERATIVE, REQUIRED), true),
    LABOR_CERTIFICATE("Certidão Negativa de Débitos Trabalhistas (CNDT)",
            all(REQUIRED), true),
    MUNICIPAL_CERTIFICATE("Certidão de regularidade com a Fazenda Municipal (quando exigível)",
            all(OPTIONAL), true),
    LICENSE("Alvará, licença sanitária ou autorização específica (se a atividade exigir)",
            all(OPTIONAL)),
    INSS_REGISTRATION("NIT/PIS e comprovante de inscrição como contribuinte individual do INSS",
            Map.of(SupplierType.INDIVIDUAL, REQUIRED)),
    PORTFOLIO("Portfólio, currículo, atestados ou declarações de serviços prestados (recomendado)",
            all(OPTIONAL)),
    BANK_PROOF("Comprovante de conta bancária ou chave Pix de titularidade do interessado",
            all(REQUIRED));

    public enum Requirement { REQUIRED, OPTIONAL, NOT_APPLICABLE }

    private final String label;
    private final Map<SupplierType, Requirement> requirements;
    private final boolean certificate;

    DocumentType(String label, Map<SupplierType, Requirement> requirements) {
        this(label, requirements, false);
    }

    DocumentType(String label, Map<SupplierType, Requirement> requirements, boolean certificate) {
        this.label = label;
        this.requirements = requirements;
        this.certificate = certificate;
    }

    /**
     * Certidão com prazo de validade (federal, FGTS, CNDT e municipal): a data é informada no
     * envio e acompanhada durante o credenciamento (Política art. 21, § 2º).
     */
    public boolean isCertificate() {
        return certificate;
    }

    public String label() {
        return label;
    }

    public Requirement requirementFor(SupplierType type) {
        return requirements.getOrDefault(type, NOT_APPLICABLE);
    }

    public boolean appliesTo(SupplierType type) {
        return requirementFor(type) != NOT_APPLICABLE;
    }

    private static Map<SupplierType, Requirement> all(Requirement r) {
        return Map.of(SupplierType.MEI, r, SupplierType.ME, r, SupplierType.EPP, r,
                SupplierType.COOPERATIVE, r, SupplierType.INDIVIDUAL, r);
    }
}
