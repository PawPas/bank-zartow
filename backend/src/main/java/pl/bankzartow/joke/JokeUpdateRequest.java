package pl.bankzartow.joke;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code PUT /api/jokes/{id}}. Content rules match creation (see
 * {@link JokeCreateRequest}); the authoritative "blank after normalization"
 * check lives in {@link JokeService}. {@code version} is the optimistic-lock
 * version the client last saw — a mismatch makes the edit fail with 409 instead
 * of silently overwriting a concurrent change.
 */
public record JokeUpdateRequest(
        @NotBlank(message = "Treść żartu jest wymagana.")
        @Size(max = 2000, message = "Treść żartu może mieć najwyżej 2000 znaków.")
        String content,
        @NotNull(message = "Wersja żartu jest wymagana.")
        Long version) {
}
