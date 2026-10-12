package com.aucolher.api.shared.validation;

import java.util.Locale;

/**
 * Normalização dos textos recebidos nos cadastros, aplicada nos
 * construtores compactos dos records.
 *
 * Deixa o objeto já cleaned antes da validação e antes de chegar na Service:
 * as regras de formato só precisam conhecer o formato canônico (CNPJ e CEP
 * só com dígitos, rede social sem @, link com protocolo, texto vazio como null).
 */
public final class Sanitizer {

    private Sanitizer() {}

    /** Remove espaços nas pontas; texto vazio vira null. */
    public static String text(String value) {
        if (value == null) return null;
        String cleaned = value.trim();
        return cleaned.isEmpty() ? null : cleaned;
    }

    /** Nome de usuário de rede social sem o @ inicial. */
    public static String withoutAt(String value) {
        String cleaned = text(value);
        return cleaned == null ? null : text(cleaned.replaceFirst("^@+", ""));
    }

    /** Link colado sem protocolo (ex: "facebook.com/ong") vira https://. */
    public static String withProtocol(String url) {
        String cleaned = text(url);
        if (cleaned == null) return null;
        return cleaned.matches("(?i)^https?://.*") ? cleaned : "https://" + cleaned;
    }

    /** Mantém só os dígitos — usado para CNPJ e CEP, que chegam com máscara. */
    public static String digitsOnly(String value) {
        String cleaned = text(value);
        return cleaned == null ? null : text(cleaned.replaceAll("\\D", ""));
    }

    /** Sigla de UF em maiúsculas. */
    public static String uppercase(String value) {
        String cleaned = text(value);
        return cleaned == null ? null : cleaned.toUpperCase(Locale.ROOT);
    }
}
