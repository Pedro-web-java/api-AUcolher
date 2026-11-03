package com.aucolher.api.security;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.URI;
import java.time.Duration;
import java.util.Base64;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Copia a foto da conta Google para o photo_url. O link que o Google manda
 * (lh3.googleusercontent.com) não serve para ficar no perfil: o Google recusa
 * parte dos carregamentos feitos de outros sites (429 Too Many Requests), e o
 * link muda quando a pessoa troca a foto. A API baixa a imagem uma vez e
 * guarda o data URL, no mesmo formato das fotos enviadas pelo cadastro.
 */
@Slf4j
@Component
public class GooglePhotoDownloader {

    /** O mesmo tamanho dos avatares que o frontend comprime (~400px). */
    private static final String SIZE = "=s400-c";
    private static final Pattern SIZE_SUFFIX = Pattern.compile("=s\\d+[-a-z]*$");
    private static final Set<String> TYPES = Set.of("image/jpeg", "image/png", "image/webp");

    /** Em base64 a imagem cresce 4/3: assim cabe com folga no PhotoUrl.MAX_LENGTH. */
    static final int MAX_BYTES = 200_000;

    private final RestClient http;

    public GooglePhotoDownloader() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofSeconds(3));
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.http = RestClient.builder().requestFactory(factory).build();
    }

    /** Link de foto hospedada pelo Google (o `picture` do login). */
    public static boolean isGoogleHosted(String url) {
        if (url == null || !url.startsWith("https://")) {
            return false;
        }
        try {
            String host = URI.create(url).getHost();
            return host != null && host.endsWith(".googleusercontent.com");
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** O Google entrega 96px por padrão; o sufixo `=s<tamanho>` pede outro. */
    static String resized(String url) {
        return SIZE_SUFFIX.matcher(url).find() ? SIZE_SUFFIX.matcher(url).replaceFirst(SIZE) : url;
    }

    /**
     * O data URL da foto, ou null quando não deu para baixar (link que não é
     * do Google, erro de rede, imagem grande demais ou de outro tipo). Quem
     * chama decide o que fazer sem a cópia.
     */
    public String download(String pictureUrl) {
        if (!isGoogleHosted(pictureUrl)) {
            return null;
        }
        try {
            ResponseEntity<byte[]> response = http.get().uri(URI.create(resized(pictureUrl)))
                    .retrieve().toEntity(byte[].class);
            MediaType type = response.getHeaders().getContentType();
            byte[] body = response.getBody();
            if (type == null || body == null || body.length == 0 || body.length > MAX_BYTES) {
                return null;
            }
            String mime = type.getType() + "/" + type.getSubtype();
            if (!TYPES.contains(mime)) {
                return null;
            }
            return "data:" + mime + ";base64," + Base64.getEncoder().encodeToString(body);
        } catch (RestClientException | IllegalArgumentException e) {
            log.warn("Não consegui baixar a foto do Google: {}", e.getMessage());
            return null;
        }
    }
}
