package pl.bankzartow.joke;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * Maps joke domain errors to RFC 7807 {@code ProblemDetail} responses
 * (CONVENTIONS.md). Scoped to {@link JokeController} so it does not affect other
 * features. Bean Validation failures are turned into {@code ProblemDetail}
 * automatically by {@code spring.mvc.problemdetails.enabled=true}.
 */
@RestControllerAdvice(assignableTypes = JokeController.class)
class JokeExceptionHandler {

    @ExceptionHandler(BlankJokeContentException.class)
    ProblemDetail handleBlankContent(BlankJokeContentException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "Treść żartu nie może być pusta.");
        problem.setTitle("Nieprawidłowa treść żartu");
        return problem;
    }

    @ExceptionHandler(JokeNotFoundException.class)
    ProblemDetail handleNotFound(JokeNotFoundException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(
                HttpStatus.NOT_FOUND, "Żart nie istnieje.");
        problem.setTitle("Nie znaleziono żartu");
        return problem;
    }

    /**
     * A stale version caught by the service check and a genuine concurrent-write
     * race caught by the {@code @Version} check at flush are the same conflict to
     * the client: 409, nothing changed, refresh and retry.
     */
    @ExceptionHandler({JokeVersionConflictException.class, OptimisticLockingFailureException.class})
    ProblemDetail handleVersionConflict(RuntimeException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT,
                "Żart został w międzyczasie zmieniony. Odśwież dane i spróbuj ponownie.");
        problem.setTitle("Konflikt wersji");
        return problem;
    }

    @ExceptionHandler(InvalidVersionHeaderException.class)
    ProblemDetail handleInvalidVersionHeader(InvalidVersionHeaderException exception) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "Nagłówek If-Match musi zawierać wersję żartu.");
        problem.setTitle("Nieprawidłowa wersja");
        return problem;
    }
}
