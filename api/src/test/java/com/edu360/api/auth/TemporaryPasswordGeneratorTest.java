package com.edu360.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

class TemporaryPasswordGeneratorTest {

    @Test
    void everyPasswordHasEachCharacterClassAndTheFullLength() {
        for (int i = 0; i < 1_000; i++) {
            String password = TemporaryPasswordGenerator.generate();
            assertThat(password).hasSize(TemporaryPasswordGenerator.LENGTH);
            assertThat(password.chars().anyMatch(c -> TemporaryPasswordGenerator.UPPER.indexOf(c) >= 0)).isTrue();
            assertThat(password.chars().anyMatch(c -> TemporaryPasswordGenerator.LOWER.indexOf(c) >= 0)).isTrue();
            assertThat(password.chars().anyMatch(c -> TemporaryPasswordGenerator.DIGITS.indexOf(c) >= 0)).isTrue();
            assertThat(password.chars().anyMatch(c -> TemporaryPasswordGenerator.SYMBOLS.indexOf(c) >= 0)).isTrue();
        }
    }

    @Test
    void passwordsHaveNoFixedPrefixAndDoNotRepeat() {
        Set<String> seen = new HashSet<>();
        Set<String> firstFour = new HashSet<>();
        for (int i = 0; i < 1_000; i++) {
            String password = TemporaryPasswordGenerator.generate();
            assertThat(password).doesNotStartWith("Edu360@");
            seen.add(password);
            firstFour.add(password.substring(0, 4));
        }
        assertThat(seen).hasSize(1_000);
        assertThat(firstFour.size()).isGreaterThan(900);
    }
}
