package io.casehub.platform.registration.core;

import de.mkammerer.argon2.Argon2;
import de.mkammerer.argon2.Argon2Factory;
import io.casehub.platform.registration.PasswordHasher;

public class Argon2PasswordHasher implements PasswordHasher {

    private final Argon2 argon2;
    private final int iterations;
    private final int memory;
    private final int parallelism;

    public Argon2PasswordHasher() {
        this(65536, 3, 1);
    }

    public Argon2PasswordHasher(int memory, int iterations, int parallelism) {
        this.argon2 = Argon2Factory.create(Argon2Factory.Argon2Types.ARGON2id);
        this.memory = memory;
        this.iterations = iterations;
        this.parallelism = parallelism;
    }

    @Override
    public String hash(String plaintext) {
        return argon2.hash(iterations, memory, parallelism, plaintext.toCharArray());
    }

    @Override
    public boolean verify(String plaintext, String hashed) {
        return argon2.verify(hashed, plaintext.toCharArray());
    }
}
