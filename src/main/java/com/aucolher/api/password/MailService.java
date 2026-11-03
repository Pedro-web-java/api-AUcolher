package com.aucolher.api.password;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.util.HtmlUtils;

import jakarta.mail.internet.MimeMessage;

/**
 * Envia os e-mails (texto simples + HTML sem imagens externas). Assíncrono: o /forgot responde
 * rápido e no mesmo tempo com ou sem conta. Sem MAIL_USERNAME, só registra o conteúdo no log.
 */
@Service
public class MailService {

    private static final Logger log = LoggerFactory.getLogger(MailService.class);
    private static final DateTimeFormatter WHEN = DateTimeFormatter.ofPattern("dd/MM/yyyy 'às' HH:mm");

    private final ObjectProvider<JavaMailSender> senderProvider;
    private final String from;

    public MailService(ObjectProvider<JavaMailSender> senderProvider,
                       @Value("${spring.mail.username:}") String from) {
        this.senderProvider = senderProvider;
        this.from = from;
    }

    @Async
    public void sendPasswordReset(String name, String to, String link, boolean googleOnly) {
        String googleLine = googleOnly
                ? "Sua conta no AUcolher foi criada com o Google e ainda não tem senha. "
                  + "Com o link abaixo você cria uma e passa a entrar também com e-mail e senha.\n\n"
                : "";
        String text = "Olá, " + name + "!\n\n"
                + "Recebemos um pedido para redefinir a senha da sua conta no AUcolher.\n\n"
                + googleLine
                + "Para criar uma nova senha, acesse o link abaixo. Ele vale por 30 minutos e só pode ser usado uma vez:\n\n"
                + link + "\n\n"
                + "Se você não fez esse pedido, ignore este email: sua senha continua a mesma.\n";
        String html = "<p>Olá, " + HtmlUtils.htmlEscape(name) + "!</p>"
                + "<p>Recebemos um pedido para redefinir a senha da sua conta no AUcolher.</p>"
                + (googleOnly
                    ? "<p>Sua conta no AUcolher foi criada com o Google e ainda não tem senha. "
                      + "Com o link abaixo você cria uma e passa a entrar também com e-mail e senha.</p>"
                    : "")
                + "<p>Para criar uma nova senha, acesse o link abaixo. Ele vale por 30 minutos e só pode ser usado uma vez:</p>"
                + "<p><a href=\"" + HtmlUtils.htmlEscape(link) + "\">" + HtmlUtils.htmlEscape(link) + "</a></p>"
                + "<p>Se você não fez esse pedido, ignore este email: sua senha continua a mesma.</p>";
        send(to, "Redefinir sua senha do AUcolher", text, html);
    }

    @Async
    public void sendPasswordChanged(String name, String to, LocalDateTime when) {
        String line = "Sua senha do AUcolher foi alterada em " + WHEN.format(when)
                + ". Se não foi você, use 'Esqueci minha senha'.";
        String text = "Olá, " + name + "!\n\n" + line + "\n";
        String html = "<p>Olá, " + HtmlUtils.htmlEscape(name) + "!</p><p>" + HtmlUtils.htmlEscape(line) + "</p>";
        send(to, "Sua senha do AUcolher foi alterada", text, html);
    }

    private void send(String to, String subject, String text, String html) {
        JavaMailSender sender = senderProvider.getIfAvailable();
        if (sender == null || from == null || from.isBlank()) {
            log.warn("MAIL_USERNAME não configurado: e-mail '{}' não enviado. Conteúdo:\n{}", subject, text);
            return;
        }
        try {
            MimeMessage message = sender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setFrom(from);
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(text, html);
            sender.send(message);
        } catch (Exception e) {
            log.error("Falha ao enviar o e-mail '{}': {}", subject, e.getClass().getSimpleName());
        }
    }
}
