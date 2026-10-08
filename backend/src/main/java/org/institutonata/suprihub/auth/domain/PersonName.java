package org.institutonata.suprihub.auth.domain;

import java.util.Locale;
import java.util.Set;

/**
 * Padroniza nomes de pessoa digitados todo em minúsculas ou todo em maiúsculas
 * ("maria da silva" → "Maria da Silva"). Nome com caixa mista fica como a pessoa escreveu,
 * para respeitar grafias como "McDonald" ou "D'Ávila".
 */
public final class PersonName {

    private static final Locale PT_BR = Locale.of("pt", "BR");
    private static final Set<String> PARTICLES = Set.of("da", "das", "de", "di", "do", "dos", "du", "e");

    private PersonName() {
    }

    public static String normalize(String raw) {
        String name = raw.trim().replaceAll("\\s+", " ");
        if (!name.equals(name.toLowerCase(PT_BR)) && !name.equals(name.toUpperCase(PT_BR))) {
            return name;
        }
        String[] words = name.toLowerCase(PT_BR).split(" ");
        StringBuilder out = new StringBuilder(name.length());
        for (int i = 0; i < words.length; i++) {
            if (i > 0) {
                out.append(' ');
            }
            out.append(i > 0 && PARTICLES.contains(words[i]) ? words[i] : capitalize(words[i]));
        }
        return out.toString();
    }

    /** Maiúscula no início e depois de hífen ou apóstrofo: "ana-luíza" → "Ana-Luíza". */
    private static String capitalize(String word) {
        StringBuilder sb = new StringBuilder(word.length());
        boolean upper = true;
        for (int i = 0; i < word.length(); i++) {
            char c = word.charAt(i);
            sb.append(upper ? Character.toUpperCase(c) : c);
            upper = c == '-' || c == '\'' || c == '’';
        }
        return sb.toString();
    }
}
