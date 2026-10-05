package org.institutonata.suprihub.supplier.domain;

import java.time.LocalDate;
import java.time.Period;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.shared.validation.DocumentNumbers;

/**
 * Confere se o requerimento está completo para envio (Edital itens 3 e 4.1). Regra pura:
 * devolve as pendências com o campo e a mensagem em pt-BR; lista vazia = pode enviar.
 * A Comissão ainda analisa o conteúdo — aqui só se garante o "requerimento completo"
 * a partir do qual corre o prazo de 10 dias úteis (Edital 4.3).
 */
public final class ApplicationCompleteness {

    public static final int MIN_AGE_INDIVIDUAL = 18;

    private ApplicationCompleteness() {
    }

    public record Issue(String field, String message) {
    }

    public record LineRef(String code, String shortName, boolean allowsIndividual, boolean active) {
    }

    /** Retrato dos dados necessários à conferência (independente de JPA). */
    public record Snapshot(
            SupplierType supplierType,
            String legalName,
            String documentNumber,
            LocalDate birthDate,
            String street,
            String addressNumber,
            String district,
            boolean hasMunicipality,
            String cep,
            String phone,
            String contactEmail,
            String representativeName,
            String representativeCpf,
            List<LineRef> lines,
            String linesDescription,
            Set<Territory> territories,
            String bankName,
            String bankBranch,
            String bankAccount,
            String accountHolder,
            boolean cadUnico,
            String nis,
            boolean declarationsAccepted) {

        public static Snapshot of(SupplierApplication a) {
            return new Snapshot(a.getSupplierType(), a.getLegalName(), a.getDocumentNumber(), a.getBirthDate(),
                    a.getStreet(), a.getAddressNumber(), a.getDistrict(), a.getMunicipality() != null, a.getCep(),
                    a.getPhone(), a.getContactEmail(), a.getRepresentativeName(), a.getRepresentativeCpf(),
                    a.sortedLines().stream()
                            .map(l -> new LineRef(l.getCode(), l.getShortName(), l.isAllowsIndividual(), l.isActive()))
                            .toList(),
                    a.getLinesDescription(), a.getTerritories(), a.getBankName(), a.getBankBranch(),
                    a.getBankAccount(), a.getAccountHolder(), a.isCadUnico(), a.getNis(), a.declarationsAccepted());
        }
    }

    public static List<Issue> check(Snapshot s, Set<DocumentType> uploaded, LocalDate today) {
        List<Issue> issues = new ArrayList<>();
        SupplierType type = s.supplierType();
        if (type == null) {
            issues.add(new Issue("supplierType", "Escolha o tipo do seu negócio."));
        }
        boolean pf = type == SupplierType.INDIVIDUAL;

        // 1. Identificação
        require(issues, s.legalName(), "legalName", pf ? "Informe seu nome completo." : "Informe a razão social.");
        if (blank(s.documentNumber())) {
            issues.add(new Issue("documentNumber", pf ? "Informe o CPF." : "Informe o CNPJ."));
        } else if (type != null && pf && !DocumentNumbers.isValidCpf(s.documentNumber())) {
            issues.add(new Issue("documentNumber", "CPF inválido. Confira os números."));
        } else if (type != null && !pf && !DocumentNumbers.isValidCnpj(s.documentNumber())) {
            issues.add(new Issue("documentNumber", "CNPJ inválido. Confira os números."));
        }
        if (pf) {
            if (s.birthDate() == null) {
                issues.add(new Issue("birthDate", "Informe a data de nascimento."));
            } else if (Period.between(s.birthDate(), today).getYears() < MIN_AGE_INDIVIDUAL) {
                issues.add(new Issue("birthDate",
                        "O credenciamento de pessoa física exige idade mínima de 18 anos (Edital 2.1.3)."));
            }
        }
        require(issues, s.street(), "street", "Informe o endereço (rua, avenida...).");
        require(issues, s.addressNumber(), "addressNumber", "Informe o número (ou \"s/n\").");
        require(issues, s.district(), "district", "Informe o bairro.");
        if (!s.hasMunicipality()) {
            issues.add(new Issue("municipalityId", "Escolha o município."));
        }
        if (blank(s.cep())) {
            issues.add(new Issue("cep", "Informe o CEP."));
        } else if (!DocumentNumbers.isValidCep(s.cep())) {
            issues.add(new Issue("cep", "CEP inválido."));
        }
        if (blank(s.phone())) {
            issues.add(new Issue("phone", "Informe um telefone com WhatsApp."));
        } else if (!DocumentNumbers.isValidBrazilianPhone(s.phone())) {
            issues.add(new Issue("phone", "Telefone inválido. Use DDD + número."));
        }
        require(issues, s.contactEmail(), "contactEmail", "Informe o e-mail de contato.");
        if (!pf && type != null) {
            require(issues, s.representativeName(), "representativeName",
                    "Informe o nome do titular ou representante legal.");
            if (blank(s.representativeCpf())) {
                issues.add(new Issue("representativeCpf", "Informe o CPF do representante."));
            } else if (!DocumentNumbers.isValidCpf(s.representativeCpf())) {
                issues.add(new Issue("representativeCpf", "CPF do representante inválido."));
            }
        }

        // 2 e 3. Linhas e territórios
        if (s.lines().isEmpty()) {
            issues.add(new Issue("lineCodes", "Escolha pelo menos uma linha de fornecimento."));
        }
        for (LineRef line : s.lines()) {
            if (!line.active()) {
                issues.add(new Issue("lineCodes", "A linha " + line.code() + " não está mais disponível."));
            } else if (pf && !line.allowsIndividual()) {
                issues.add(new Issue("lineCodes", "A linha " + line.code() + " (" + line.shortName()
                        + ") não aceita pessoa física. Retire-a ou escolha outro tipo de negócio."));
            }
        }
        require(issues, s.linesDescription(), "linesDescription",
                "Descreva o que você oferece nas linhas escolhidas.");
        if (s.territories().isEmpty()) {
            issues.add(new Issue("territories", "Escolha pelo menos um território de atendimento."));
        }

        // 4. Pagamento
        require(issues, s.bankName(), "bankName", "Informe o banco.");
        require(issues, s.bankBranch(), "bankBranch", "Informe a agência.");
        require(issues, s.bankAccount(), "bankAccount", "Informe a conta.");
        require(issues, s.accountHolder(), "accountHolder", "Informe o titular da conta.");

        // 5. Equidade (facultativo, mas o NIS é necessário se marcou CadÚnico)
        if (s.cadUnico() && blank(s.nis())) {
            issues.add(new Issue("nis", "Informe o NIS ou desmarque a inscrição no CadÚnico."));
        }

        // Documentos (Edital 4.1)
        if (type != null) {
            for (DocumentType doc : DocumentType.values()) {
                if (doc.requirementFor(type) == DocumentType.Requirement.REQUIRED && !uploaded.contains(doc)) {
                    issues.add(new Issue("documents." + doc.name(), "Envie: " + doc.label() + "."));
                }
            }
        }

        if (!s.declarationsAccepted()) {
            issues.add(new Issue("declarationsAccepted",
                    "Leia e aceite as declarações e o termo de adesão (Anexos III e V)."));
        }
        return issues;
    }

    private static void require(List<Issue> issues, String value, String field, String message) {
        if (blank(value)) {
            issues.add(new Issue(field, message));
        }
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
