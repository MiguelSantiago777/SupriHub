package org.institutonata.suprihub.shared.validation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.function.Predicate;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

/**
 * Anotações Bean Validation para documentos brasileiros. Valores nulos/vazios são
 * considerados válidos (combine com {@code @NotBlank} quando obrigatório).
 */
public final class BrValidators {

    private BrValidators() {
    }

    @Documented
    @Constraint(validatedBy = CpfValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Cpf {
        String message() default "CPF inválido.";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @Documented
    @Constraint(validatedBy = CnpjValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Cnpj {
        String message() default "CNPJ inválido.";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @Documented
    @Constraint(validatedBy = CpfOrCnpjValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface CpfOrCnpj {
        String message() default "CPF ou CNPJ inválido.";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @Documented
    @Constraint(validatedBy = CepValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Cep {
        String message() default "CEP inválido.";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    @Documented
    @Constraint(validatedBy = PhoneValidator.class)
    @Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
    @Retention(RetentionPolicy.RUNTIME)
    public @interface PhoneBr {
        String message() default "Telefone inválido. Use DDD + número, ex.: (21) 99999-0000.";
        Class<?>[] groups() default {};
        Class<? extends Payload>[] payload() default {};
    }

    abstract static class StringPredicateValidator<A extends java.lang.annotation.Annotation>
            implements ConstraintValidator<A, String> {
        private final Predicate<String> predicate;

        StringPredicateValidator(Predicate<String> predicate) {
            this.predicate = predicate;
        }

        @Override
        public boolean isValid(String value, ConstraintValidatorContext context) {
            return value == null || value.isBlank() || predicate.test(value);
        }
    }

    public static class CpfValidator extends StringPredicateValidator<Cpf> {
        public CpfValidator() {
            super(DocumentNumbers::isValidCpf);
        }
    }

    public static class CnpjValidator extends StringPredicateValidator<Cnpj> {
        public CnpjValidator() {
            super(DocumentNumbers::isValidCnpj);
        }
    }

    public static class CpfOrCnpjValidator extends StringPredicateValidator<CpfOrCnpj> {
        public CpfOrCnpjValidator() {
            super(DocumentNumbers::isValidCpfOrCnpj);
        }
    }

    public static class CepValidator extends StringPredicateValidator<Cep> {
        public CepValidator() {
            super(DocumentNumbers::isValidCep);
        }
    }

    public static class PhoneValidator extends StringPredicateValidator<PhoneBr> {
        public PhoneValidator() {
            super(DocumentNumbers::isValidBrazilianPhone);
        }
    }
}
