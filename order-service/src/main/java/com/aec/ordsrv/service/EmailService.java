package com.aec.ordsrv.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;

    @Value("${spring.mail.username}")
    private String senderEmail;


    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    /** Devuelve fullName si no es null/blank, si no el fallback */
    private String safeName(String fullName, String fallback) {
        if (fullName != null && !fullName.isBlank()) {
            return fullName;
        }
        return fallback;
    }

    private void sendHtmlEmail(String toEmail, String subject, String htmlContent) {
        if (toEmail == null || toEmail.isBlank()) {
            log.warn("No hay destinatario para el email: {}", subject);
            return;
        }
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, "utf-8");
            helper.setFrom(senderEmail);
            helper.setTo(toEmail);
            helper.setSubject(subject);
            helper.setText(htmlContent, true);
            mailSender.send(msg);
            log.info("Email enviado a {}: {}", toEmail, subject);
        } catch (MessagingException e) {
            log.error("Error enviando email a {}: {}", toEmail, e.getMessage(), e);
        }
    }

    public void sendPaymentApprovedEmail(String customerEmail, String customerFullName, Long orderId) {
        String name = safeName(customerFullName, "cliente");
        String subject = "¡Tu pago ha sido aprobado para la Orden #" + orderId + "!";
        String html = String.format("""
            <html>
              <body>
                <p>Estimado/a %s,</p>
                <p>Nos complace informarte que tu pago para la Orden <strong>#%d</strong> ha sido <strong>aprobado</strong>.</p>
                <p>Ahora puedes acceder a la descarga de tus productos desde tu cuenta.</p>
                <p>Gracias por tu compra.</p>
                <p>Saludos cordiales,<br/>El equipo de AECBlock</p>
              </body>
            </html>
            """, name, orderId);
        sendHtmlEmail(customerEmail, subject, html);
    }

    public void sendPaymentRejectedEmail(String customerEmail, String customerFullName, Long orderId, String adminComment) {
        String name    = safeName(customerFullName, "cliente");
        String subject = "Estado de tu pago para la Orden #" + orderId;
        String comment = (adminComment != null && !adminComment.isBlank())
                         ? adminComment
                         : "Sin comentario.";
        String html = String.format("""
            <html>
              <body>
                <p>Estimado/a %s,</p>
                <p>Queremos informarte que el comprobante de pago que subiste para la Orden <strong>#%d</strong> ha sido <strong>revisado</strong>.</p>
                <p><strong>Estado: Pago Rechazado.</strong></p>
                <p>Comentario del administrador: %s</p>
                <p>Por favor, revisa la información de tu transferencia e intenta subir un nuevo comprobante si es necesario, o contacta a soporte para más asistencia.</p>
                <p>Lamentamos cualquier inconveniente.</p>
                <p>Saludos cordiales,<br/>El equipo de AECBlock</p>
              </body>
            </html>
            """, name, orderId, comment);
        sendHtmlEmail(customerEmail, subject, html);
    }


    @Value("${admin.email:support@aecblock.com}") // Valor por defecto si no se configura
    private String adminEmailRecipient;


       public void sendReceiptUploadedNotification(Long orderId, String customerUsername, String customerEmail, double totalAmount) {
        if (adminEmailRecipient == null || adminEmailRecipient.isBlank()) {
            log.error("No se puede enviar notificación de comprobante subido: el correo del administrador no está configurado.");
            return;
        }

        String subject = "🔔 Nuevo Comprobante Subido para Orden #" + orderId;
        String html = String.format("""
            <html>
              <body>
                <p>Estimado Administrador,</p>
                <p>Se ha subido un nuevo comprobante de pago para la Orden <strong>#%d</strong>.</p>
                <ul>
                  <li><strong>Cliente:</strong> %s</li>
                  <li><strong>Email del Cliente:</strong> %s</li>
                  <li><strong>Monto Total:</strong> $%.2f</li>
                </ul>
                <p>Por favor, revisa el comprobante y aprueba o rechaza el pago según corresponda.</p>
                <p>Saludos cordiales,<br/>El sistema de AECBlock</p>
              </body>
            </html>
            """, orderId, customerUsername, customerEmail, totalAmount);

        sendHtmlEmail(adminEmailRecipient, subject, html);
    }
}
