package com.aucolher.api.animal.entity;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

class AgeGroupTest {

    @ParameterizedTest(name = "{0} {1} = {2}")
    @CsvSource({
            "0,  MONTHS, PUPPY",
            "11, MONTHS, PUPPY",
            "1,  YEARS,  ADULT",
            "7,  YEARS,  ADULT",
            "8,  YEARS,  SENIOR",
            "15, YEARS,  SENIOR",
    })
    void computesAgeGroupFromAge(int value, AgeUnit unit, AgeGroup expected) {
        assertThat(AgeGroup.of(value, unit)).isEqualTo(expected);
    }
}
