package com.ecommerce.auth.security;

import com.ecommerce.auth.model.RawPassword;
import jakarta.enterprise.context.ApplicationScoped;

import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.List;

/** Produces the eight-character password a newly registered account is given. */
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
