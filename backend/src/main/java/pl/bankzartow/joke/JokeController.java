package pl.bankzartow.joke;

import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * HTTP endpoints for the joke bank. The controller only maps between HTTP and the
 * service and converts entities to DTOs; it holds no logic (CONVENTIONS.md). All
 * {@code /api/**} paths require a session (T-006); {@code POST} is CSRF-protected.
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
}
