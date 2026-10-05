package org.institutonata.suprihub.supplier.domain;

import java.time.LocalDate;
import java.util.Set;

import org.institutonata.suprihub.catalog.domain.Territory;
import org.institutonata.suprihub.supplier.domain.SupplierEnums.BankAccountType;
import org.institutonata.suprihub.supplier.domain.SupplierEnums.Race;

/**
 * Dados do Anexo II preenchidos pelo fornecedor. Todos os campos podem ficar vazios no
 * rascunho; a completude só é exigida no envio ({@link ApplicationCompleteness}).
 */
public record ApplicationData(
        SupplierType supplierType,
        String legalName,
        String tradeName,
        String documentNumber,
        LocalDate birthDate,
        String street,
        String addressNumber,
        String addressComplement,
        String district,
        Integer municipalityId,
        String cep,
        String phone,
        String contactEmail,
        String website,
        String representativeName,
        String representativeCpf,
        String representativeRole,
        Set<String> lineCodes,
        String linesDescription,
        Set<Territory> territories,
        String territoriesNote,
        String bankName,
        String bankBranch,
        String bankAccount,
        BankAccountType bankAccountType,
        String pixKey,
        String accountHolder,
        boolean womanLed,
        boolean publishWomanLed,
        Race race,
        boolean cadUnico,
        String nis,
        boolean declarationsAccepted) {

    public ApplicationData {
        lineCodes = lineCodes == null ? Set.of() : Set.copyOf(lineCodes);
        territories = territories == null ? Set.of() : Set.copyOf(territories);
    }

    @Override
    public String toString() {
        return "ApplicationData[***]"; // dados pessoais e bancários nunca em log
    }
}
