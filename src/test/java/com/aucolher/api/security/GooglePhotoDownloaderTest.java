package com.aucolher.api.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** As regras do link (sem rede: o download de verdade depende do Google). */
class GooglePhotoDownloaderTest {

    @Test
    void onlyGoogleHostedLinksCount() {
        assertThat(GooglePhotoDownloader.isGoogleHosted("https://lh3.googleusercontent.com/a/ABC=s96-c")).isTrue();
        assertThat(GooglePhotoDownloader.isGoogleHosted("http://lh3.googleusercontent.com/a/ABC")).isFalse();
        assertThat(GooglePhotoDownloader.isGoogleHosted("https://googleusercontent.com.evil.com/a.png")).isFalse();
        assertThat(GooglePhotoDownloader.isGoogleHosted("data:image/png;base64,AAAA")).isFalse();
        assertThat(GooglePhotoDownloader.isGoogleHosted(null)).isFalse();
    }

    @Test
    void asksForTheAvatarSize() {
        assertThat(GooglePhotoDownloader.resized("https://lh3.googleusercontent.com/a/ABC=s96-c"))
                .isEqualTo("https://lh3.googleusercontent.com/a/ABC=s400-c");
        assertThat(GooglePhotoDownloader.resized("https://lh3.googleusercontent.com/a/ABC"))
                .isEqualTo("https://lh3.googleusercontent.com/a/ABC");
    }

    @Test
    void linkFromAnotherSiteIsNotDownloaded() {
        assertThat(new GooglePhotoDownloader().download("https://example.com/foto.jpg")).isNull();
    }
}
