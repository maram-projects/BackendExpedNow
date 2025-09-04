package com.example.ExpedNow.services.core.impl;

import com.example.ExpedNow.models.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;

import jakarta.mail.internet.MimeMessage;
@Service
public class EmailService {

    @Autowired
    private JavaMailSender emailSender;

    @Autowired
    private TemplateEngine templateEngine;

    @Value("${app.frontend.url:http://localhost:4200}")
    private String frontendUrl;

    @Value("${spring.mail.username}")
    private String fromEmail;

    public void sendWelcomeEmail(User user) {
        try {
            Context context = new Context();
            context.setVariable("firstName", user.getFirstName());
            context.setVariable("loginUrl", frontendUrl + "/login");
            context.setVariable("supportUrl", frontendUrl + "/support");
            context.setVariable("privacyUrl", frontendUrl + "/privacy");
            context.setVariable("termsUrl", frontendUrl + "/terms");

            String htmlContent = templateEngine.process("email/welcome-email", context);

            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("welcome@expedenow.com", "ExpedeNow Team");
            helper.setTo(user.getEmail());
            helper.setSubject("Welcome to ExpedeNow - Your Reliable Delivery Partner!");
            helper.setText(htmlContent, true);

            emailSender.send(message);

            System.out.println("Welcome email sent successfully to: " + user.getEmail());
        } catch (Exception e) {
            System.err.println("Failed to send welcome email to " + user.getEmail() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendPasswordResetEmail(User user, String resetToken) {
        try {
            String resetUrl = frontendUrl + "/reset-password?token=" + resetToken;

            Context context = new Context();
            context.setVariable("firstName", user.getFirstName());
            context.setVariable("resetUrl", resetUrl);
            context.setVariable("supportUrl", frontendUrl + "/support");
            context.setVariable("securityUrl", frontendUrl + "/security");
            context.setVariable("userEmail", user.getEmail());

            String htmlContent = templateEngine.process("email/password-reset-email", context);

            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("security@expedenow.com", "ExpedeNow Security");
            helper.setTo(user.getEmail());
            helper.setSubject("Reset Your ExpedeNow Password");
            helper.setText(htmlContent, true);

            emailSender.send(message);

            System.out.println("Password reset email sent successfully to: " + user.getEmail());
        } catch (Exception e) {
            System.err.println("Failed to send password reset email to " + user.getEmail() + ": " + e.getMessage());
            e.printStackTrace();
            throw new RuntimeException("Failed to send password reset email. Please try again later.");
        }
    }

    public void sendPasswordSuccessEmail(User user) {
        try {
            Context context = new Context();
            context.setVariable("firstName", user.getFirstName());
            context.setVariable("loginUrl", frontendUrl + "/login");
            context.setVariable("supportUrl", frontendUrl + "/support");
            context.setVariable("securityUrl", frontendUrl + "/security");

            String htmlContent = templateEngine.process("email/password-success-email", context);

            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("security@expedenow.com", "ExpedeNow Security");
            helper.setTo(user.getEmail());
            helper.setSubject("Password Successfully Updated - ExpedeNow");
            helper.setText(htmlContent, true);

            emailSender.send(message);

            System.out.println("Password success email sent successfully to: " + user.getEmail());
        } catch (Exception e) {
            System.err.println("Failed to send password success email to " + user.getEmail() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendAccountApprovalEmail(User user) {
        try {
            Context context = new Context();
            context.setVariable("firstName", user.getFirstName());
            context.setVariable("loginUrl", frontendUrl + "/login");
            context.setVariable("dashboardUrl", frontendUrl + "/dashboard");

            // You'll need to create this template
            String htmlContent = templateEngine.process("email/account-approval-email", context);

            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("admin@expedenow.com", "ExpedeNow Admin");
            helper.setTo(user.getEmail());
            helper.setSubject("Your ExpedeNow Account Has Been Approved!");
            helper.setText(htmlContent, true);

            emailSender.send(message);

            System.out.println("Account approval email sent successfully to: " + user.getEmail());
        } catch (Exception e) {
            System.err.println("Failed to send account approval email to " + user.getEmail() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void sendAccountRejectionEmail(User user, String reason) {
        try {
            Context context = new Context();
            context.setVariable("firstName", user.getFirstName());
            context.setVariable("reason", reason != null ? reason : "Account did not meet our requirements");
            context.setVariable("supportUrl", frontendUrl + "/support");
            context.setVariable("registerUrl", frontendUrl + "/register");

            // You'll need to create this template
            String htmlContent = templateEngine.process("email/account-rejection-email", context);

            MimeMessage message = emailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

            helper.setFrom("admin@expedenow.com", "ExpedeNow Admin");
            helper.setTo(user.getEmail());
            helper.setSubject("ExpedeNow Account Registration Update");
            helper.setText(htmlContent, true);

            emailSender.send(message);

            System.out.println("Account rejection email sent successfully to: " + user.getEmail());
        } catch (Exception e) {
            System.err.println("Failed to send account rejection email to " + user.getEmail() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }
}