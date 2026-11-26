package pl.bankzartow.joke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * Unit coverage for content validation and normalization. No Spring context and
 * no database: the repository is mocked so the rules are tested in isolation.
 */
@ExtendWith(MockitoExtension.class)
class JokeServiceTest {

    @Mock
    private JokeRepository repository;

    @Test
    void rejectsJokeWithoutContent() {
        JokeService service = new JokeService(repository);

        assertThatThrownBy(() -> service.create("   "))
                .isInstanceOf(BlankJokeContentException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void rejectsNullContent() {
        JokeService service = new JokeService(repository);

        assertThatThrownBy(() -> service.create(null))
                .isInstanceOf(BlankJokeContentException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void rejectsContentThatNormalizesToEmpty() {
        JokeService service = new JokeService(repository);

        // Non-breaking spaces are NOT blank to String#isBlank (so a plain @NotBlank
        // would let them through), but the normalizer collapses them to nothing, so
        // the joke must still be rejected.
        String nonBreakingSpaces = "  ";
        assertThat(nonBreakingSpaces.isBlank())
                .as("precondition: non-breaking spaces are not blank to the JDK")
                .isFalse();
        assertThatThrownBy(() -> service.create(nonBreakingSpaces))
                .isInstanceOf(BlankJokeContentException.class);

        verify(repository, never()).save(any());
    }

    @Test
    void normalizesContentBeforeSaving() {
        JokeService service = new JokeService(repository);
        when(repository.save(any(Joke.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create("  Czemu  kura   przeszla?  \n\n  Bo mogla.  ");

        ArgumentCaptor<Joke> saved = ArgumentCaptor.forClass(Joke.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue().getContent())
                .as("surrounding and repeated whitespace is collapsed, line breaks are kept")
                .isEqualTo("Czemu kura przeszla?\n\nBo mogla.");
    }

    /** A joke as the repository would return it: persisted, with a known version. */
    private static Joke persistedJoke(String content, long version) {
        Joke joke = new Joke(content, Instant.parse("2026-11-20T10:00:00Z"));
        ReflectionTestUtils.setField(joke, "version", version);
        return joke;
    }

    @Test
    void getThrowsNotFoundForMissingJoke() {
        JokeService service = new JokeService(repository);
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(42L))
                .isInstanceOf(JokeNotFoundException.class);
    }

    @Test
    void editThrowsNotFoundForMissingJoke() {
        JokeService service = new JokeService(repository);
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.updateContent(42L, "Nowa tresc", 0L))
                .isInstanceOf(JokeNotFoundException.class);
    }

    @Test
    void editRejectsStaleVersionWithoutChangingContent() {
        JokeService service = new JokeService(repository);
        Joke joke = persistedJoke("Oryginal", 2L);
        when(repository.findById(1L)).thenReturn(Optional.of(joke));

        assertThatThrownBy(() -> service.updateContent(1L, "Spozniona edycja", 1L))
                .isInstanceOf(JokeVersionConflictException.class);

        assertThat(joke.getContent())
                .as("a conflicting edit must leave the entity untouched")
                .isEqualTo("Oryginal");
    }

    @Test
    void editRejectsContentThatNormalizesToEmptyWithoutChangingContent() {
        JokeService service = new JokeService(repository);
        Joke joke = persistedJoke("Oryginal", 0L);
        when(repository.findById(1L)).thenReturn(Optional.of(joke));

        assertThatThrownBy(() -> service.updateContent(1L, "  ", 0L))
                .isInstanceOf(BlankJokeContentException.class);

        assertThat(joke.getContent()).isEqualTo("Oryginal");
    }

    @Test
    void editNormalizesContentAndChangesOnlyContentAndUpdatedAt() {
        JokeService service = new JokeService(repository);
        Joke joke = persistedJoke("Oryginal", 0L);
        Instant createdAt = joke.getCreatedAt();
        when(repository.findById(1L)).thenReturn(Optional.of(joke));

        Joke updated = service.updateContent(1L, "  Nowa   tresc  ", 0L);

        assertThat(updated).isSameAs(joke);
        assertThat(updated.getContent()).isEqualTo("Nowa tresc");
        assertThat(updated.getCreatedAt())
                .as("editing must not touch the creation timestamp")
                .isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt())
                .as("editing refreshes the modification timestamp")
                .isAfter(createdAt);
    }

    @Test
    void deleteThrowsNotFoundForMissingJoke() {
        JokeService service = new JokeService(repository);
        when(repository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.delete(42L, 0L))
                .isInstanceOf(JokeNotFoundException.class);

        verify(repository, never()).delete(any());
    }

    @Test
    void deleteRejectsStaleVersionAndDeletesNothing() {
        JokeService service = new JokeService(repository);
        Joke joke = persistedJoke("Trwaly", 3L);
        when(repository.findById(1L)).thenReturn(Optional.of(joke));

        assertThatThrownBy(() -> service.delete(1L, 2L))
                .isInstanceOf(JokeVersionConflictException.class);

        verify(repository, never()).delete(any());
    }

    @Test
    void deleteRemovesJokeWithCurrentVersion() {
        JokeService service = new JokeService(repository);
        Joke joke = persistedJoke("Do usuniecia", 1L);
        when(repository.findById(1L)).thenReturn(Optional.of(joke));

        service.delete(1L, 1L);

        verify(repository).delete(joke);
    }
}
