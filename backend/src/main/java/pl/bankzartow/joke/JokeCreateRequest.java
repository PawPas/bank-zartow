package pl.bankzartow.joke;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Body of {@code POST /api/jokes}. Bean Validation rejects an obviously empty or
 * over-long request up front; the authoritative "blank after normalization" check
 * (which also catches e.g. non-breaking spaces) lives in {@link JokeService}.
 * Since normalization never grows the text, a raw length within {@code 2000} keeps
 * the normalized content within the same bound (docs/EPIC-1.md §6).
 */
public record JokeCreateRequest(
        @NotBlank(message = "Treść żartu jest wymagana.")
        @Size(max = 2000, message = "Treść żartu może mieć najwyżej 2000 znaków.")
        String content) {
}
