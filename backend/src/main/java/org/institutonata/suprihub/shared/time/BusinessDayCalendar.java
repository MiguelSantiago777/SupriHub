package org.institutonata.suprihub.shared.time;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Calendário de dias úteis do Contrata + NATA (Edital item 12.2):
 * <em>"Os prazos contam-se excluindo o dia do início e incluindo o do vencimento,
 * e só se iniciam e vencem em dias úteis."</em>
 *
 * <p>Componente puro: recebe um predicado que diz se uma data é feriado/ponto
 * facultativo. Sábados e domingos nunca são úteis.
 */
public final class BusinessDayCalendar {

    private final Predicate<LocalDate> holiday;

    public BusinessDayCalendar(Predicate<LocalDate> holiday) {
        this.holiday = Objects.requireNonNull(holiday);
    }

    public static BusinessDayCalendar of(Set<LocalDate> holidays) {
        Set<LocalDate> copy = Set.copyOf(holidays);
        return new BusinessDayCalendar(copy::contains);
    }

    public boolean isBusinessDay(LocalDate date) {
        DayOfWeek dow = date.getDayOfWeek();
        return dow != DayOfWeek.SATURDAY && dow != DayOfWeek.SUNDAY && !holiday.test(date);
    }

    /**
     * Data de vencimento de um prazo de {@code days} dias úteis iniciado em {@code start}.
     * O dia do início é excluído; contam-se apenas dias úteis; o vencimento é sempre dia útil.
     * Com {@code days = 0}, devolve o próprio início (ou o próximo dia útil, se não for útil).
     */
    public LocalDate addBusinessDays(LocalDate start, int days) {
        if (days < 0) {
            throw new IllegalArgumentException("Prazo não pode ser negativo: " + days);
        }
        if (days == 0) {
            return nextBusinessDayOnOrAfter(start);
        }
        LocalDate date = start;
        int counted = 0;
        while (counted < days) {
            date = date.plusDays(1);
            if (isBusinessDay(date)) {
                counted++;
            }
        }
        return date;
    }

    /**
     * Quantidade de dias úteis no intervalo {@code (from, to]} — exclui o início e inclui o fim.
     * Retorna valor negativo se {@code to} for anterior a {@code from}.
     */
    public int businessDaysBetween(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            return -businessDaysBetween(to, from);
        }
        int count = 0;
        for (LocalDate d = from.plusDays(1); !d.isAfter(to); d = d.plusDays(1)) {
            if (isBusinessDay(d)) {
                count++;
            }
        }
        return count;
    }

    public LocalDate nextBusinessDayOnOrAfter(LocalDate date) {
        LocalDate d = date;
        while (!isBusinessDay(d)) {
            d = d.plusDays(1);
        }
        return d;
    }
}
