package com.aucolher.api.shared.validation;

/**
 * Regras compartilhadas do campo fotoUrl dos cadastros.
 *
 * Aceita uma URL http(s) ou, enquanto a API não tem upload próprio de
 * arquivos, o data URL da imagem que o frontend já comprime para ~400px.
 * O limite de tamanho barra fotos originais sem compressão.
 */
public final class PhotoUrl {

    public static final int MAX_LENGTH = 300_000;
    public static final String FORMAT = "^(https?://|data:image/(jpeg|png|webp);base64,).+$";

    private PhotoUrl() {}
}
