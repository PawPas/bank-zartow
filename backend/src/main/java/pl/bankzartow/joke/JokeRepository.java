package pl.bankzartow.joke;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JokeRepository extends JpaRepository<Joke, Long> {

    /**
     * All jokes, newest first. {@code id} breaks ties so jokes created within the
     * same instant keep a stable, deterministic order (matching the supporting
     * index in {@code V2__joke.sql}).
     */
    List<Joke> findAllByOrderByCreatedAtDescIdDesc();
}
