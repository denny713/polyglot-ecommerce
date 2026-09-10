package com.ecommerce.auth.security;

import com.ecommerce.auth.model.RawPassword;
import jakarta.enterprise.context.ApplicationScoped;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/**
 * Produces the eight-character password a newly registered account is given.
 *
 * <p>
 * <strong>Why one character of each class.</strong> The {@code ecommerce} realm
 * runs {@code length(8) and upperCase(1) and lowerCase(1) and specialChars(1)},
 * and Keycloak applies that policy to admin-set passwords too — a password that
 * happened to come out all lower-case would be refused with HTTP 400 and the
 * registration would fail for no reason the caller could act on. Guaranteeing
 * one of each class up front makes that impossible rather than unlikely. A digit
 * is included on the same principle, so the value stays acceptable if the policy
 * ever gains {@code digits(1)}.
 *
 * <p>
 * <strong>Why these alphabets.</strong> The pairs that are indistinguishable in
 * most fonts are left out — {@code O}/{@code 0}, {@code l}/{@code 1}/{@code I} —
 * because this password is read out of an email and typed by hand, and a
 * character the reader cannot identify turns into a support request. The special
 * characters are limited to ones that survive a copy-paste out of a mail client
 * without being swallowed by quoting or auto-formatting.
 *
 * <p>
 * {@link SecureRandom} rather than {@link java.util.Random}: this value is a
 * credential, and a predictable one is no credential at all.
 */
@ApplicationScoped
public class TemporaryPasswordGenerator {

    /**
     * Eight characters, the minimum the realm policy accepts. It is short on
     * purpose — the password is transcribed by hand from an email, and its job
     * is to survive only until the holder changes it.
     */
    static final int LENGTH = 8;

    static final String UPPER = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final String LOWER = "abcdefghijkmnopqrstuvwxyz";
    static final String DIGITS = "23456789";
    static final String SPECIAL = "!@#$%&*+-=?";

    private static final String ALL = UPPER + LOWER + DIGITS + SPECIAL;

    private final SecureRandom random = new SecureRandom();

    public RawPassword generate() {
        List<Character> characters = new ArrayList<>(LENGTH);

        // One of each class first, so the realm policy is satisfied by
        // construction and not by luck.
        characters.add(pick(UPPER));
        characters.add(pick(LOWER));
        characters.add(pick(DIGITS));
        characters.add(pick(SPECIAL));

        while (characters.size() < LENGTH) {
            characters.add(pick(ALL));
        }

        // Without the shuffle the classes would always appear in the same
        // positions, which hands an attacker four characters of the search space.
        shuffle(characters);

        StringBuilder password = new StringBuilder(LENGTH);
        characters.forEach(password::append);

        return new RawPassword(password.toString());
    }

    private char pick(String alphabet) {
        return alphabet.charAt(random.nextInt(alphabet.length()));
    }

    /**
     * Fisher-Yates, drawing from {@link SecureRandom} — the shuffle has to be as
     * unpredictable as the characters it is rearranging.
     */
    private void shuffle(List<Character> characters) {
        for (int i = characters.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            Character swap = characters.get(i);
            characters.set(i, characters.get(j));
            characters.set(j, swap);
        }
    }
}
