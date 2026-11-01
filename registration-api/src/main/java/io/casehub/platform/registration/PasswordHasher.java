package io.casehub.platform.registration;

public interface PasswordHasher {
    String hash(String plaintext);
    boolean verify(String plaintext, String hashed);
}
