package pl.bankzartow.joke;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints for the joke bank. The controller only maps between HTTP and the
 * service and converts entities to DTOs; it holds no logic (CONVENTIONS.md). All
 * {@code /api/**} paths require a session (T-006); modifying methods are
 * CSRF-protected. Modifying requests carry the optimistic-lock version — in the
 * body for {@code PUT}, in the {@code If-Match} header for {@code DELETE}.
 */
@RestController
@RequestMapping("/api/jokes")
public class JokeController {

    private final JokeService service;

    public JokeController(JokeService service) {
        this.service = service;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JokeResponse create(@Valid @RequestBody JokeCreateRequest request) {
        return JokeResponse.from(service.create(request.content()));
    }

    @GetMapping
    public List<JokeSummary> list() {
        return service.listNewestFirst().stream().map(JokeSummary::from).toList();
    }

    @GetMapping("/{id}")
    public JokeDetail get(@PathVariable long id) {
        return JokeDetail.from(service.get(id));
    }

    @PutMapping("/{id}")
    public JokeDetail update(@PathVariable long id, @Valid @RequestBody JokeUpdateRequest request) {
        return JokeDetail.from(service.updateContent(id, request.content(), request.version()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable long id, @RequestHeader("If-Match") String ifMatch) {
        service.delete(id, parseVersion(ifMatch));
    }

    /**
     * Reads the expected version from the ETag-shaped {@code If-Match} header; the
     * SPA sends the bare number, but a quoted value is equally valid.
     */
    private static long parseVersion(String ifMatch) {
        String value = ifMatch.trim();
        if (value.length() >= 2 && value.startsWith("\"") && value.endsWith("\"")) {
            value = value.substring(1, value.length() - 1);
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException cause) {
            throw new InvalidVersionHeaderException(ifMatch);
        }
    }
}
