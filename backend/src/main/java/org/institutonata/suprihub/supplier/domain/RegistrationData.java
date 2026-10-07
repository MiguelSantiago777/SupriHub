package org.institutonata.suprihub.supplier.domain;

import org.institutonata.suprihub.supplier.domain.SupplierEnums.BankAccountType;

/** Dados que o credenciado atualiza sem novo requerimento (Anexo V; Política art. 17, § 2º). */
public record RegistrationData(
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
        String bankName,
        String bankBranch,
        String bankAccount,
        BankAccountType bankAccountType,
        String pixKey,
        String accountHolder) {

    @Override
    public String toString() {
        return "RegistrationData[***]";
    }
}
