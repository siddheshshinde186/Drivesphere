package com.drivesphere.service;

import com.drivesphere.exception.EmailDeliveryException;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.UUID;

@Slf4j
@Service
public class EmailService {

    @Autowired(required = false)
    private JavaMailSender mailSender;

    @Value("${drivesphere.mail.frontend-url:${FRONTEND_URL:${drivesphere.app.frontend-url:http://localhost:8080}}}")
    private String frontendUrl;

    @Value("${drivesphere.mail.from:${SMTP_FROM:${spring.mail.username:noreply@drivesphere.com}}}")
    private String fromEmail;

    @Value("${spring.mail.host:${SMTP_HOST:}}")
    private String smtpHost;

    @Value("${spring.mail.port:${SMTP_PORT:587}}")
    private int smtpPort;

    @Value("${spring.mail.username:${SMTP_USERNAME:}}")
    private String smtpUsername;

    @Value("${spring.mail.password:${SMTP_PASSWORD:}}")
    private String smtpPassword;

    @Value("${spring.mail.properties.mail.smtp.auth:${SMTP_AUTH:true}}")
    private String smtpAuth;

    @Value("${spring.mail.properties.mail.smtp.starttls.enable:${SMTP_STARTTLS:true}}")
    private String smtpStarttls;

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @jakarta.annotation.PostConstruct
    public void init() {
        if (mailSender == null && isSmtpCandidate()) {
            try {
                org.springframework.mail.javamail.JavaMailSenderImpl impl = new org.springframework.mail.javamail.JavaMailSenderImpl();
                impl.setHost(smtpHost.trim());
                impl.setPort(smtpPort > 0 ? smtpPort : 587);
                if (smtpUsername != null && !smtpUsername.isBlank()) {
                    impl.setUsername(smtpUsername.trim());
                }
                if (smtpPassword != null && !smtpPassword.isBlank()) {
                    impl.setPassword(smtpPassword.trim());
                }
                java.util.Properties props = impl.getJavaMailProperties();
                props.put("mail.transport.protocol", "smtp");
                props.put("mail.smtp.auth", smtpAuth != null ? smtpAuth : "true");
                props.put("mail.smtp.starttls.enable", smtpStarttls != null ? smtpStarttls : "true");
                props.put("mail.smtp.connectiontimeout", "5000");
                props.put("mail.smtp.timeout", "5000");
                props.put("mail.smtp.writetimeout", "5000");
                this.mailSender = impl;
            } catch (Exception e) {
                log.warn("Could not dynamically construct JavaMailSender: {}", e.getMessage());
            }
        }
        logSmtpDiagnostic();
    }

    public boolean isSmtpCandidate() {
        return smtpHost != null && !smtpHost.trim().isBlank() && !smtpHost.trim().equalsIgnoreCase("false");
    }

    /**
     * Determines whether real external SMTP has been configured.
     * Returns true if a mailSender exists, host is non-empty, and either
     * credentials exist or non-localhost host is targeted.
     */
    public boolean isSmtpConfigured() {
        if (mailSender == null) {
            return false;
        }
        if (!isSmtpCandidate()) {
            return false;
        }
        String cleanHost = smtpHost.trim().toLowerCase();
        boolean isLocal = cleanHost.equals("localhost") || cleanHost.equals("127.0.0.1");
        if (isLocal && (smtpUsername == null || smtpUsername.trim().isBlank())) {
            return false;
        }
        if (!isLocal && (smtpUsername == null || smtpUsername.trim().isBlank() || smtpPassword == null || smtpPassword.trim().isBlank())) {
            return false;
        }
        return true;
    }

    public void logSmtpDiagnostic() {
        boolean configured = isSmtpConfigured();
        String passStatus = (smtpPassword != null && !smtpPassword.isBlank())
                ? "[SET (" + smtpPassword.trim().length() + " chars)]"
                : "[NOT SET]";

        log.info("""

                --------------------------------------------------------------------------------
                [DRIVESPHERE SMTP SUBSYSTEM INITIALIZATION]
                Status       : {}
                SMTP Host    : {}
                SMTP Port    : {}
                Username     : {}
                Password     : {}
                Auth / TLS   : Auth={}, STARTTLS={}
                Sender From  : {}
                Frontend URL : {}
                --------------------------------------------------------------------------------""",
                configured ? "CONFIGURED (LIVE DISPATCH ENABLED)" : "INCOMPLETE (LOCAL CONSOLE FALLBACK ACTIVE)",
                (smtpHost != null && !smtpHost.isBlank()) ? smtpHost : "(not configured)",
                smtpPort,
                maskEmail(smtpUsername),
                passStatus,
                smtpAuth, smtpStarttls,
                maskEmail(fromEmail),
                frontendUrl);

        if (smtpHost != null && smtpHost.toLowerCase().contains("gmail.com")) {
            if (smtpPassword != null && !smtpPassword.isBlank() && smtpPassword.trim().length() != 16) {
                log.warn("[SMTP CONFIG WARNING] Gmail host detected but password length is {} characters (expected 16). Google requires a 16-character App Password (not your normal Google account password) when 2-Step Verification is enabled.",
                        smtpPassword.trim().length());
            }
        }
    }

    // Setters for unit tests
    public void setMailSender(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void setSmtpHost(String smtpHost) {
        this.smtpHost = smtpHost;
    }

    public void setSmtpPort(int smtpPort) {
        this.smtpPort = smtpPort;
    }

    public void setSmtpUsername(String smtpUsername) {
        this.smtpUsername = smtpUsername;
    }

    public void setSmtpPassword(String smtpPassword) {
        this.smtpPassword = smtpPassword;
    }

    public void setFromEmail(String fromEmail) {
        this.fromEmail = fromEmail;
    }

    public void setFrontendUrl(String frontendUrl) {
        this.frontendUrl = frontendUrl;
    }

    public void setActiveProfile(String activeProfile) {
        this.activeProfile = activeProfile;
    }

    /**
     * Helper to mask email addresses in application logs to protect PII.
     * e.g. "john.doe@example.com" -> "j***e@example.com"
     */
    public static String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 1) {
            return "***" + email.substring(Math.max(0, atIndex));
        }
        String local = email.substring(0, atIndex);
        String domain = email.substring(atIndex);
        if (local.length() <= 2) {
            return local.charAt(0) + "***" + domain;
        }
        return local.charAt(0) + "***" + local.charAt(local.length() - 1) + domain;
    }

    public void sendVerificationEmail(String toEmail, String recipientName, String verificationToken) {
        String cleanBase = frontendUrl.replaceAll("/+$", "");
        String verificationUrl = cleanBase + "/login.html?verified=pending&token=" + verificationToken;
        String subject = "Verify your DriveSphere Account";
        String htmlContent = String.format(
                """
                        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 24px; background-color: #0b0f19; color: #ffffff; border-radius: 12px; border: 1px solid rgba(255,255,255,0.1);">
                            <div style="margin-bottom: 20px; border-bottom: 1px solid rgba(255,255,255,0.08); padding-bottom: 15px;">
                                <span style="font-size: 20px; font-weight: bold; color: #00f2fe; letter-spacing: -0.5px;">DriveSphere</span>
                                <span style="font-size: 13px; color: #94a3b8; margin-left: 8px;">Smart Mobility Platform</span>
                            </div>
                            <h2 style="color: #38bdf8; margin-bottom: 12px;">Welcome to DriveSphere, %s!</h2>
                            <p style="color: #cbd5e1; font-size: 15px; line-height: 1.6;">Thank you for registering. To activate your account and start booking verified smart mobility and chauffeur services, please verify your email address.</p>
                            <div style="text-align: center; margin: 30px 0;">
                                <a href="%s" style="background: linear-gradient(135deg, #00f2fe, #3b82f6); color: #040810; padding: 12px 28px; text-decoration: none; font-weight: bold; border-radius: 8px; display: inline-block;">Verify Email Address</a>
                            </div>
                            <p style="color: #94a3b8; font-size: 13px;">Or copy and paste this verification link into your browser:<br><span style="color: #38bdf8; word-break: break-all;">%s</span></p>
                            <p style="color: #64748b; font-size: 12px; margin-top: 30px; border-top: 1px solid rgba(255,255,255,0.08); padding-top: 15px;">This verification link will expire in 24 hours. If you did not create a DriveSphere account, you can safely ignore this email.</p>
                        </div>
                        """,
                recipientName != null ? recipientName : "Valued Member", verificationUrl, verificationUrl);

        sendEmail(toEmail, subject, htmlContent, verificationUrl, "EMAIL_VERIFICATION");
    }

    public void sendPasswordResetEmail(String toEmail, String recipientName, String resetToken) {
        String cleanBase = frontendUrl.replaceAll("/+$", "");
        String resetUrl = cleanBase + "/reset-password.html?token=" + resetToken;
        String subject = "Reset your DriveSphere Password";
        String htmlContent = String.format(
                """
                        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 24px; background-color: #0b0f19; color: #ffffff; border-radius: 12px; border: 1px solid rgba(255,255,255,0.1);">
                            <div style="margin-bottom: 20px; border-bottom: 1px solid rgba(255,255,255,0.08); padding-bottom: 15px;">
                                <span style="font-size: 20px; font-weight: bold; color: #38bdf8; letter-spacing: -0.5px;">DriveSphere</span>
                                <span style="font-size: 13px; color: #94a3b8; margin-left: 8px;">Security Notification</span>
                            </div>
                            <h2 style="color: #38bdf8; margin-bottom: 12px;">Password Reset Request</h2>
                            <p style="color: #cbd5e1; font-size: 15px; line-height: 1.6;">Hello %s, we received a request to reset the password for your DriveSphere account. Click the button below to choose a new password.</p>
                            <div style="text-align: center; margin: 30px 0;">
                                <a href="%s" style="background: linear-gradient(135deg, #38bdf8, #6366f1); color: #ffffff; padding: 12px 28px; text-decoration: none; font-weight: bold; border-radius: 8px; display: inline-block;">Reset Password</a>
                            </div>
                            <p style="color: #94a3b8; font-size: 13px;">Or copy and paste this link into your browser:<br><span style="color: #38bdf8; word-break: break-all;">%s</span></p>
                            <div style="margin-top: 25px; padding: 12px 16px; background: rgba(56, 189, 248, 0.08); border-left: 3px solid #38bdf8; border-radius: 6px;">
                                <p style="color: #cbd5e1; font-size: 13px; margin: 0;"><strong>Notice:</strong> This single-use password reset link will expire in <strong>15 minutes</strong>.</p>
                            </div>
                            <p style="color: #64748b; font-size: 12px; margin-top: 25px; border-top: 1px solid rgba(255,255,255,0.08); padding-top: 15px;">If you did not request a password reset, please ignore this email or review your account security immediately. Your account remains protected.</p>
                        </div>
                        """,
                recipientName != null ? recipientName : "Valued Member", resetUrl, resetUrl);

        sendEmail(toEmail, subject, htmlContent, resetUrl, "PASSWORD_RESET");
    }

    public void sendBookingConfirmationEmail(String toEmail, String bookingReference, String vehicleName,
            BigDecimal amount) {
        String subject = "Booking Confirmed: " + bookingReference;
        String htmlContent = String.format(
                """
                        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 24px; background-color: #0b0f19; color: #ffffff; border-radius: 12px; border: 1px solid rgba(255,255,255,0.1);">
                            <h2 style="color: #10b981; margin-bottom: 12px;">Booking Confirmed!</h2>
                            <p style="color: #cbd5e1; font-size: 15px;">Your reservation for <strong>%s</strong> (Ref: <code>%s</code>) has been confirmed.</p>
                            <p style="color: #cbd5e1; font-size: 15px;">Total Amount Paid: <strong>₹%s</strong></p>
                            <p style="color: #94a3b8; font-size: 13px;">Track your trip telemetry live from your DriveSphere Customer Workspace.</p>
                        </div>
                        """,
                vehicleName, bookingReference, amount);

        sendEmail(toEmail, subject, htmlContent, null, "BOOKING_CONFIRMATION");
    }

    public void sendRefundReceiptEmail(String toEmail, String bookingReference, BigDecimal amount, String refundId) {
        String subject = "Refund Processed: " + bookingReference;
        String htmlContent = String.format(
                """
                        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 24px; background-color: #0b0f19; color: #ffffff; border-radius: 12px; border: 1px solid rgba(255,255,255,0.1);">
                            <h2 style="color: #f59e0b; margin-bottom: 12px;">Refund Initiated</h2>
                            <p style="color: #cbd5e1; font-size: 15px;">Your refund of <strong>₹%s</strong> for booking <code>%s</code> has been initiated.</p>
                            <p style="color: #94a3b8; font-size: 13px;">Gateway Refund ID: <code>%s</code></p>
                            <p style="color: #64748b; font-size: 12px; margin-top: 15px;">Funds will reflect in your original payment method within 3-5 business days.</p>
                        </div>
                        """,
                amount, bookingReference, refundId);

        sendEmail(toEmail, subject, htmlContent, null, "REFUND_RECEIPT");
    }

    public void sendNotificationEmail(String toEmail, String title, String body) {
        String subject = "DriveSphere Alert: " + title;
        String htmlContent = String.format(
                """
                        <div style="font-family: Arial, sans-serif; max-width: 600px; margin: auto; padding: 24px; background-color: #0b0f19; color: #ffffff; border-radius: 12px; border: 1px solid rgba(255,255,255,0.1);">
                            <h3 style="color: #38bdf8; margin-bottom: 12px;">%s</h3>
                            <p style="color: #cbd5e1; font-size: 14px; line-height: 1.6;">%s</p>
                            <p style="color: #64748b; font-size: 12px; margin-top: 25px; border-top: 1px solid rgba(255,255,255,0.08); padding-top: 12px;">DriveSphere Automated Notification System</p>
                        </div>
                        """,
                title, body);

        sendEmail(toEmail, subject, htmlContent, null, "EVENT_NOTIFICATION");
    }

    private void sendEmail(String toEmail, String subject, String htmlContent, String actionUrl, String emailType) {
        String effectiveSender = (fromEmail != null && !fromEmail.trim().isBlank()) ? fromEmail.trim()
                : "noreply@drivesphere.com";

        if (isSmtpConfigured()) {
            try {
                MimeMessage message = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
                helper.setFrom(new jakarta.mail.internet.InternetAddress(effectiveSender));
                helper.setTo(toEmail);
                helper.setSubject(subject);
                helper.setText(htmlContent, true);
                mailSender.send(message);
                log.info("Successfully dispatched transactional email [{}] to [{}] via SMTP ({}:{})",
                        emailType, maskEmail(toEmail), smtpHost, smtpPort);
                return;
            } catch (Exception e) {
                String correlationId = UUID.randomUUID().toString().substring(0, 8);
                log.error(
                        "[MAIL_DELIVERY_FAILURE] Category: SMTP_TRANSPORT_ERROR | CorrelationId: {} | Type: {} | Recipient: {} | Host: {}:{} | Cause: {}",
                        correlationId, emailType, maskEmail(toEmail), smtpHost, smtpPort, e.getMessage());
                throw new EmailDeliveryException(
                        "Failed to dispatch email via SMTP provider (Ref: " + correlationId + "): " + e.getMessage(), e);
            }
        }

        if ("prod".equalsIgnoreCase(activeProfile) || "production".equalsIgnoreCase(activeProfile)) {
            String correlationId = UUID.randomUUID().toString().substring(0, 8);
            log.error(
                    "[MAIL_DELIVERY_FAILURE] Category: SMTP_NOT_CONFIGURED | CorrelationId: {} | Type: {} | Recipient: {} | Cause: SMTP is required in production environment",
                    correlationId, emailType, maskEmail(toEmail));
            throw new EmailDeliveryException("Outbound transactional email dispatch failed (Ref: " + correlationId + "): SMTP is not configured in production environment.");
        }

        // Local Development Console Structured Log
        log.info("""

                ================================================================================
                [DRIVESPHERE EMAIL SERVICE - LOCAL DEV FALLBACK (NO REAL EMAIL SENT)]
                Event Type       : {}
                Recipient        : {}
                Subject          : {}
                Action / Link    : {}
                Token Expiration : 15 minutes
                --------------------------------------------------------------------------------
                NOTICE: SMTP delivery was NOT attempted because live SMTP configuration is incomplete.
                The email content above was recorded locally only.
                To enable live outbound email dispatch, configure in .env:
                  SMTP_HOST=smtp.gmail.com
                  SMTP_PORT=587
                  SMTP_USERNAME=your-email@gmail.com
                  SMTP_PASSWORD=your-16-char-app-password
                  SMTP_AUTH=true
                  SMTP_STARTTLS=true
                ================================================================================""",
                emailType, maskEmail(toEmail), subject, actionUrl != null ? actionUrl : "(No Action Link)");
    }
}
