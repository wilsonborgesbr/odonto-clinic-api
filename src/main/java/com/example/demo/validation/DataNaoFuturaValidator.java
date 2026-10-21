package com.example.demo.validation;

import com.example.demo.util.FusoBrasil;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

import java.time.LocalDate;

public class DataNaoFuturaValidator implements ConstraintValidator<DataNaoFutura, LocalDate> {

    @Override
    public boolean isValid(LocalDate data, ConstraintValidatorContext context) {
        return data == null || !data.isAfter(FusoBrasil.hoje());
    }
}
