package com.drivesphere.service;

import com.drivesphere.exception.EmailDeliveryException;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.mail.MailSendException;
import org.springframework.mail.javamail.JavaMailSender;

import java.math.BigDecimal;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Automated tests for EmailService correctness (Priority 5):
 * 1. Missing SMTP configuration gracefully falls back to local dev console without throwing.
 * 2. Valid SMTP configuration dispatches email via JavaMailSender.
 * 3. SMTP transport errors throw EmailDeliveryException with correlation ID preserved.
 * 4. PII masking prevents email leakage in application logs.
 */
class EmailServiceTests {

    private JavaMailSender mockMailSender;
    private EmailService emailService;

    @BeforeEach
    void setUp() {
        mockMailSender = Mockito.mock(JavaMailSender.class);
        when(mockMailSender.createMimeMessage()).thenReturn(new MimeMessage(Session.getInstance(new Properties())));

        emailService = new EmailService();
        emailService.setFrontendUrl("http://localhost:5500");
        emailService.setFromEmail("noreply@drivesphere.com");
    }

    @Test
    @DisplayName("P5.1: When SMTP is unconfigured, isSmtpConfigured() is false and fallback executes safely")
    void testUnconfiguredSmtp_fallsBackToConsoleSafely() {
        emailService.setMailSender(null);
        emailService.setSmtpHost("");
        emailService.setSmtpUsername("");

        assertFalse(emailService.isSmtpConfigured());

        // Must not throw an exception when sending in fallback mode
        assertDoesNotThrow(() -> {
            emailService.sendVerificationEmail("newuser@example.com", "Test User", "dummy-token-123");
            emailService.sendPasswordResetEmail("customer@example.com", "Customer", "reset-token-456");
            emailService.sendBookingConfirmationEmail("rider@example.com", "BK-9999", "Tata Nexon EV", BigDecimal.valueOf(2800));
            emailService.sendRefundReceiptEmail("rider@example.com", "BK-9999", BigDecimal.valueOf(2800), "rfnd_test_123");
            emailService.sendNotificationEmail("fleet@example.com", "Maintenance Alert", "Vehicle scheduled for checkup.");
        });
    }

    @Test
    @DisplayName("P5.2: When valid external SMTP is configured, emails are dispatched via JavaMailSender")
    void testConfiguredSmtp_dispatchesViaMailSender() {
        emailService.setMailSender(mockMailSender);
        emailService.setSmtpHost("smtp.gmail.com");
        emailService.setSmtpPort(587);
        emailService.setSmtpUsername("operator@gmail.com");
        emailService.setSmtpPassword("abcdefghijklmnop");

        assertTrue(emailService.isSmtpConfigured());

        emailService.sendVerificationEmail("verified@example.com", "Verified User", "token-abc");

        verify(mockMailSender, times(1)).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("P5.3: When SMTP transport throws an error, EmailDeliveryException is thrown with cause preserved")
    void testSmtpTransportFailure_throwsEmailDeliveryException() {
        emailService.setMailSender(mockMailSender);
        emailService.setSmtpHost("smtp.gmail.com");
        emailService.setSmtpPort(587);
        emailService.setSmtpUsername("operator@gmail.com");
        emailService.setSmtpPassword("abcdefghijklmnop");

        doThrow(new MailSendException("535 5.7.8 Authentication credentials invalid"))
                .when(mockMailSender).send(any(MimeMessage.class));

        EmailDeliveryException ex = assertThrows(EmailDeliveryException.class, () -> {
            emailService.sendPasswordResetEmail("user@example.com", "Target User", "reset-token-xyz");
        });

        assertNotNull(ex.getMessage());
        assertTrue(ex.getMessage().contains("SMTP provider"));
        assertNotNull(ex.getCause());
        assertTrue(ex.getCause().getMessage().contains("Authentication credentials invalid"));
    }

    @Test
    @DisplayName("P5.4: Email masking shields user PII in logs")
    void testEmailMasking_shieldsPII() {
        assertEquals("j***e@example.com", EmailService.maskEmail("john.doe@example.com"));
        assertEquals("s***6@gmail.com", EmailService.maskEmail("shindesiddhesh186@gmail.com"));
        assertEquals("a***@c.com", EmailService.maskEmail("ab@c.com"));
        assertEquals("a***c@c.com", EmailService.maskEmail("abc@c.com"));
        assertEquals("***", EmailService.maskEmail(null));
        assertEquals("***", EmailService.maskEmail(""));
    }
}
