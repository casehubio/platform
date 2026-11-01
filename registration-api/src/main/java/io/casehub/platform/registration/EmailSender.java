package io.casehub.platform.registration;

public interface EmailSender {
    void send(String to, String subject, String body);
}
