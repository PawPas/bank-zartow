package pl.bankzartow.joke;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

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
}
