import { useEffect, useState, type FormEvent } from "react";
import { createJoke, fetchJokes, type Joke } from "../api/client.ts";

/**
 * The joke bank's core screen: a list of jokes newest-first and an always-visible
 * quick-add form. Quick add is at most two taps beyond typing — focus the field,
 * then tap "Dodaj" — so there is no step that reveals the form first. Mobile-first
 * (360 px) and all text is in Polish.
 */
export default function JokeList() {
  const [jokes, setJokes] = useState<Joke[] | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [content, setContent] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [addError, setAddError] = useState<string | null>(null);

  useEffect(() => {
    let active = true;
    fetchJokes()
      .then((loaded) => {
        if (active) {
          setJokes(loaded);
        }
      })
      .catch(() => {
        if (active) {
          setLoadFailed(true);
        }
      });
    return () => {
      active = false;
    };
  }, []);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (content.trim() === "") {
      return;
    }
    setAddError(null);
    setSubmitting(true);
    try {
      const created = await createJoke(content);
      // Prepend so the newest joke stays on top, matching the backend order.
      setJokes((current) => [created, ...(current ?? [])]);
      setContent("");
    } catch {
      setAddError("Nie udało się dodać żartu. Spróbuj ponownie.");
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <section className="jokes">
      <form className="joke-add" onSubmit={handleSubmit} aria-label="Dodaj żart">
        <label htmlFor="joke-content">Treść żartu</label>
        <textarea
          id="joke-content"
          name="content"
          rows={3}
          value={content}
          onChange={(event) => setContent(event.target.value)}
        />
        {addError && (
          <p className="joke-error" role="alert">
            {addError}
          </p>
        )}
        <button type="submit" disabled={submitting}>
          {submitting ? "Dodawanie…" : "Dodaj"}
        </button>
      </form>

      {loadFailed ? (
        <p className="joke-error" role="alert">
          Nie udało się wczytać żartów.
        </p>
      ) : jokes === null ? (
        <p>Ładowanie żartów…</p>
      ) : jokes.length === 0 ? (
        <p>Nie masz jeszcze żadnych żartów.</p>
      ) : (
        <ul className="joke-items">
          {jokes.map((joke) => (
            <li key={joke.id} className="joke-item">
              {joke.content}
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
