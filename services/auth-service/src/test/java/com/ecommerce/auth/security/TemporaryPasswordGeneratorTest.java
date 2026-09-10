package com.ecommerce.auth.security;

import com.ecommerce.auth.model.RawPassword;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The generated password has to clear the realm password policy
 * ({@code length(8) and upperCase(1) and lowerCase(1) and specialChars(1)})
 * <em>every</em> time. Keycloak applies that policy to admin-set passwords, so
 * a run that happened to come out all lower-case would fail a registration for
 * a reason no caller could act on — which is why the class-invariant tests
 * below are {@link RepeatedTest}s rather than single samples.
 */
class TemporaryPasswordGeneratorTest {

    private static final int SAMPLES = 200;

    private final TemporaryPasswordGenerator generator = new TemporaryPasswordGenerator();

    @RepeatedTest(50)
    void shouldProduceExactlyEightCharacters() {
        assertEquals(TemporaryPasswordGenerator.LENGTH, generator.generate().value().length());
    }

    @RepeatedTest(50)
    void shouldAlwaysSatisfyTheRealmPasswordPolicy() {
        String password = generator.generate().value();

        assertTrue(containsAnyOf(password, TemporaryPasswordGenerator.UPPER),
                "no upper-case letter in " + password);
        assertTrue(containsAnyOf(password, TemporaryPasswordGenerator.LOWER),
                "no lower-case letter in " + password);
        assertTrue(containsAnyOf(password, TemporaryPasswordGenerator.SPECIAL),
                "no special character in " + password);
    }

    /**
     * Not demanded by the current policy, but generated anyway so the value
     * stays acceptable if the realm ever gains {@code digits(1)}.
     */
    @RepeatedTest(50)
    void shouldAlwaysContainADigit() {
        String password = generator.generate().value();

        assertTrue(containsAnyOf(password, TemporaryPasswordGenerator.DIGITS), "no digit in " + password);
    }

    @RepeatedTest(50)
    void shouldDrawOnlyFromTheDeclaredAlphabets() {
        String allowed = TemporaryPasswordGenerator.UPPER
                + TemporaryPasswordGenerator.LOWER
                + TemporaryPasswordGenerator.DIGITS
                + TemporaryPasswordGenerator.SPECIAL;
        String password = generator.generate().value();

        password.chars().forEach(c -> assertTrue(allowed.indexOf(c) >= 0,
                "unexpected character '" + (char) c + "' in " + password));
    }

    /**
     * The pairs nobody can tell apart in a monospace email are excluded on
     * purpose: this value is read off a screen and typed by hand.
     */
    @Test
    void shouldNeverUseCharactersThatAreEasilyMisread() {
        String ambiguous = "O0lI1";

        IntStream.range(0, SAMPLES).forEach(i -> {
            String password = generator.generate().value();
            password.chars().forEach(c -> assertTrue(ambiguous.indexOf(c) < 0,
                    "ambiguous character '" + (char) c + "' in " + password));
        });
    }

    /**
     * Without the shuffle the four guaranteed classes would always land in the
     * same four positions, handing an attacker half the search space.
     */
    @Test
    void shouldNotAlwaysPutTheSameCharacterClassFirst() {
        Set<Boolean> firstCharacterWasUpperCase = new HashSet<>();

        IntStream.range(0, SAMPLES).forEach(i -> firstCharacterWasUpperCase.add(
                TemporaryPasswordGenerator.UPPER.indexOf(generator.generate().value().charAt(0)) >= 0));

        assertEquals(2, firstCharacterWasUpperCase.size(),
                "the first character was always from the same class — is the shuffle running?");
    }

    @Test
    void shouldNotRepeatItself() {
        Set<String> generated = new HashSet<>();

        IntStream.range(0, SAMPLES).forEach(i -> generated.add(generator.generate().value()));

        assertEquals(SAMPLES, generated.size(), "the generator produced a duplicate");
    }

    @Test
    void shouldReturnAValueThatMasksItselfInLogs() {
        RawPassword password = generator.generate();

        assertEquals("RawPassword[value=***]", password.toString());
    }

    private boolean containsAnyOf(String password, String alphabet) {
        return password.chars().anyMatch(c -> alphabet.indexOf(c) >= 0);
    }
}
