package dev.vk.oceanweather.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.lang.Nullable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;

/** SMTP adapter used by the AI tool; keeping it as a separate bean makes Spring Retry proxying effective. */
@Service
public class EmailDeliveryService {

  private static final Logger log = LoggerFactory.getLogger(EmailDeliveryService.class);

  private final JavaMailSender mailSender;
  private final String from;

  public EmailDeliveryService(
      @Nullable JavaMailSender mailSender,
      @Value("${spring.mail.username:}") String from) {
    this.mailSender = mailSender;
    this.from = from;
  }

  @Retryable(
      retryFor = Exception.class,
      maxAttemptsExpression = "${agent.retry.max-attempts:3}",
      backoff =
          @Backoff(
              delayExpression = "${agent.retry.delay-ms:1000}",
              multiplierExpression = "${agent.retry.multiplier:2.0}"))
  public void send(String location, String report, String recipient) {
    if (mailSender == null) {
      throw new WeatherAgentService.DeliveryException("Mail sender is not configured.", null);
    }
    SimpleMailMessage message = new SimpleMailMessage();
    if (from != null && !from.isBlank()) {
      message.setFrom(from);
    }
    message.setTo(recipient);
    message.setSubject("Coastal weather report: " + location);
    message.setText(report);
    mailSender.send(message);
    log.info("Report email sent to {} for {}", recipient, location);
  }

  @Recover
  public void recover(Exception exception, String location, String report, String recipient) {
    log.error(
        "Email delivery failed after configured retries for recipient={}", recipient, exception);
    throw new WeatherAgentService.DeliveryException(
        "Email delivery failed after retries.", exception);
  }
}
