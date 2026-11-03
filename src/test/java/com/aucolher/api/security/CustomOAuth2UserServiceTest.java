package com.aucolher.api.security;

import com.aucolher.api.user.UserRepository;
import com.aucolher.api.user.entity.AuthProvider;
import com.aucolher.api.user.entity.User;
import com.aucolher.api.user.entity.UserType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** O login Google cria a conta e guarda uma cópia da foto, não o link do Google. */
@ExtendWith(MockitoExtension.class)
class CustomOAuth2UserServiceTest {

    static final String EMAIL = "person@gmail.com";
    static final String PICTURE = "https://lh3.googleusercontent.com/a/ABC=s96-c";
    static final String COPY = "data:image/jpeg;base64,AAAA";

    @Mock
    private UserRepository userRepository;
    @Mock
    private GooglePhotoDownloader googlePhoto;

    @InjectMocks
    private CustomOAuth2UserService service;

    @Test
    void firstLoginCreatesAPersonWithACopyOfThePhoto() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(googlePhoto.download(PICTURE)).thenReturn(COPY);

        service.syncAccount(EMAIL, "Maria", PICTURE);

        User saved = savedUser();
        assertThat(saved.getUserType()).isEqualTo(UserType.PERSON);
        assertThat(saved.getProvider()).isEqualTo(AuthProvider.GOOGLE);
        assertThat(saved.getPhotoUrl()).isEqualTo(COPY);
    }

    @Test
    void firstLoginKeepsTheLinkWhenTheDownloadFails() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());
        when(googlePhoto.download(PICTURE)).thenReturn(null);

        service.syncAccount(EMAIL, "Maria", PICTURE);

        assertThat(savedUser().getPhotoUrl()).isEqualTo(PICTURE);
    }

    @Test
    void oldGoogleLinkIsReplacedByTheCopy() {
        User user = person("https://lh3.googleusercontent.com/a/OLD=s96-c");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(googlePhoto.download(PICTURE)).thenReturn(COPY);

        service.syncAccount(EMAIL, "Maria", PICTURE);

        assertThat(savedUser().getPhotoUrl()).isEqualTo(COPY);
    }

    @Test
    void oldGoogleLinkStaysWhenTheDownloadFails() {
        User user = person("https://lh3.googleusercontent.com/a/OLD=s96-c");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(googlePhoto.download(PICTURE)).thenReturn(null);

        service.syncAccount(EMAIL, "Maria", PICTURE);

        verify(userRepository, never()).save(any());
    }

    @Test
    void accountWithItsOwnPhotoKeepsIt() {
        User user = person("data:image/png;base64,MINHA");
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));

        service.syncAccount(EMAIL, "Maria", PICTURE);

        verifyNoInteractions(googlePhoto);
        verify(userRepository, never()).save(any());
        assertThat(user.getPhotoUrl()).isEqualTo("data:image/png;base64,MINHA");
    }

    @Test
    void personWithoutPhotoGetsTheGoogleOne() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(person(null)));
        when(googlePhoto.download(PICTURE)).thenReturn(COPY);

        service.syncAccount(EMAIL, "Maria", PICTURE);

        assertThat(savedUser().getPhotoUrl()).isEqualTo(COPY);
    }

    @Test
    void ngoWithoutPhotoDoesNotGetAPersonalPhoto() {
        User ngo = User.builder().id(2L).email(EMAIL).userType(UserType.NGO).active(true).build();
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(ngo));

        service.syncAccount(EMAIL, "Patinhas", PICTURE);

        verifyNoInteractions(googlePhoto);
        verify(userRepository, never()).save(any());
    }

    private User savedUser() {
        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        return captor.getValue();
    }

    private static User person(String photoUrl) {
        return User.builder().id(5L).email(EMAIL).name("Maria").userType(UserType.PERSON)
                .provider(AuthProvider.GOOGLE).active(true).photoUrl(photoUrl).build();
    }
}
