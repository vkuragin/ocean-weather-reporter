package dev.vk.oceanweather.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

class EmailToolTest {

  @Test
  void sendsToTheAddressFromToolContext() {
    AtomicReference<SimpleMailMessage> sent = new AtomicReference<>();
    EmailTool tool =
        new EmailTool(new EmailDeliveryService(new CapturingMailSender(sent), ""));
    EmailTool.DeliveryState state = new EmailTool.DeliveryState();

    String result =
        tool.sendEmail(
            "Lisbon, Portugal",
            "Sunny.",
            new ToolContext(
                Map.of("requestedEmail", "reader@example.com", "deliveryState", state)));

    assertEquals("Email sent successfully.", result);
    assertTrue(state.sentSuccessfully());
    assertEquals("reader@example.com", sent.get().getTo()[0]);
    assertEquals("Sunny.", sent.get().getText());
  }

  private static final class CapturingMailSender implements JavaMailSender {
    private final AtomicReference<SimpleMailMessage> sent;

    private CapturingMailSender(AtomicReference<SimpleMailMessage> sent) {
      this.sent = sent;
    }

    @Override
    public void send(SimpleMailMessage message) {
      sent.set(message);
    }

    @Override
    public void send(SimpleMailMessage... messages) {
      for (SimpleMailMessage message : messages) {
        send(message);
      }
    }

    @Override
    public jakarta.mail.internet.MimeMessage createMimeMessage() {
      throw new UnsupportedOperationException();
    }

    @Override
    public jakarta.mail.internet.MimeMessage createMimeMessage(java.io.InputStream content) {
      throw new UnsupportedOperationException();
    }

    @Override
    public void send(jakarta.mail.internet.MimeMessage... messages) {
      throw new UnsupportedOperationException();
    }
  }
}
