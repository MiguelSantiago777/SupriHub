package org.institutonata.suprihub.shared.validation;

/**
 * Validação e normalização de CPF, CNPJ, CEP e telefone brasileiros.
 * Funções puras, sem dependências.
 */
public final class DocumentNumbers {

    private DocumentNumbers() {
    }

    /** Remove tudo que não for dígito. */
    public static String digits(String value) {
        return value == null ? "" : value.replaceAll("\\D", "");
    }

    public static boolean isValidCpf(String value) {
        String cpf = digits(value);
        if (cpf.length() != 11 || allSameDigit(cpf)) {
            return false;
        }
        int d1 = cpfDigit(cpf, 9);
        int d2 = cpfDigit(cpf, 10);
        return d1 == cpf.charAt(9) - '0' && d2 == cpf.charAt(10) - '0';
    }

    private static int cpfDigit(String cpf, int length) {
        int sum = 0;
        for (int i = 0; i < length; i++) {
            sum += (cpf.charAt(i) - '0') * (length + 1 - i);
        }
        int rest = (sum * 10) % 11;
        return rest == 10 ? 0 : rest;
    }

    public static boolean isValidCnpj(String value) {
        String cnpj = digits(value);
        if (cnpj.length() != 14 || allSameDigit(cnpj)) {
            return false;
        }
        int[] w1 = {5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        int[] w2 = {6, 5, 4, 3, 2, 9, 8, 7, 6, 5, 4, 3, 2};
        return cnpjDigit(cnpj, w1) == cnpj.charAt(12) - '0'
                && cnpjDigit(cnpj, w2) == cnpj.charAt(13) - '0';
    }

    private static int cnpjDigit(String cnpj, int[] weights) {
        int sum = 0;
        for (int i = 0; i < weights.length; i++) {
            sum += (cnpj.charAt(i) - '0') * weights[i];
        }
        int rest = sum % 11;
        return rest < 2 ? 0 : 11 - rest;
    }

    public static boolean isValidCpfOrCnpj(String value) {
        String d = digits(value);
        return d.length() == 11 ? isValidCpf(d) : isValidCnpj(d);
    }

    public static boolean isValidCep(String value) {
        String d = digits(value);
        return d.length() == 8 && !d.equals("00000000");
    }

    /**
     * Telefone BR: DDD (11–99) + 8 dígitos (fixo, começando em 2–5) ou
     * 9 dígitos (celular, começando em 9). Aceita prefixo +55.
     */
    public static boolean isValidBrazilianPhone(String value) {
        String d = digits(value);
        if (d.length() >= 12 && d.startsWith("55")) {
            d = d.substring(2);
        }
        if (d.length() != 10 && d.length() != 11) {
            return false;
        }
        int ddd = Integer.parseInt(d.substring(0, 2));
        if (ddd < 11 || d.charAt(0) == '0' || d.charAt(1) == '0') {
            return false;
        }
        char first = d.charAt(2);
        return d.length() == 11 ? first == '9' : first >= '2' && first <= '5';
    }

    public static String formatCpf(String value) {
        String d = digits(value);
        return d.length() != 11 ? value
                : d.substring(0, 3) + "." + d.substring(3, 6) + "." + d.substring(6, 9) + "-" + d.substring(9);
    }

    public static String formatCnpj(String value) {
        String d = digits(value);
        return d.length() != 14 ? value
                : d.substring(0, 2) + "." + d.substring(2, 5) + "." + d.substring(5, 8) + "/"
                        + d.substring(8, 12) + "-" + d.substring(12);
    }

    private static boolean allSameDigit(String s) {
        return s.chars().allMatch(c -> c == s.charAt(0));
    }
}
