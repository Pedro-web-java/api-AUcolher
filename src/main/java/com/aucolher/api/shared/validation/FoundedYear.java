package com.aucolher.api.shared.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.time.Year;

/**
 * Ano de fundação da ONG, usado no cadastro e na edição do perfil.
 *
 * Opcional (nulo é válido), mas quando vem precisa estar entre {@value #MIN}
 * e o ano atual. O teto muda a cada ano, por isso não dá para usar @Min/@Max,
 * que só aceitam constantes.
 */
@Documented
@Constraint(validatedBy = FoundedYear.Validator.class)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.PARAMETER, ElementType.ANNOTATION_TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface FoundedYear {

    int MIN = 1800;

    String message() default "Informe um ano de fundação entre 1800 e o ano atual";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};

    class Validator implements ConstraintValidator<FoundedYear, Integer> {

        @Override
        public boolean isValid(Integer year, ConstraintValidatorContext context) {
            return year == null || (year >= MIN && year <= Year.now().getValue());
        }
    }
}
