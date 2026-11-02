package pl.bankzartow.joke;

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
}
