import { useEffect, useRef, useState, type FormEvent } from "react";
import {
  createJoke,
  fetchJokes,
  UnauthorizedError,
  type Joke,
} from "../api/client.ts";

interface JokeListProps {
  /** The quick-add draft, lifted to App so it survives re-authentication. */
  draft: string;
  onDraftChange: (value: string) => void;
  /** Called when a data request returns 401 so App can show the login screen. */
  onSessionExpired: () => void;
}

/**
 * The joke bank's core screen: a list of jokes newest-first and an always-visible
 * quick-add form. Quick add is at most two taps beyond typing — focus the field,
 * then tap "Dodaj" — so there is no step that reveals the form first. Mobile-first
 * (360 px) and all text is in Polish.
 */
export default function JokeList({ draft, onDraftChange, onSessionExpired }: JokeListProps) {
  const [jokes, setJokes] = useState<Joke[] | null>(null);
  const [loadFailed, setLoadFailed] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [addError, setAddError] = useState<string | null>(null);

  // Guards the async add below: once this instance unmounts (e.g. App showed the
  // login screen on an expired session, or the user logged out and back in), a
  // late POST response must not touch App state. onSessionExpired and onDraftChange
  // are stable across sessions, so without this a stale response from a previous
  // session would re-show login or wipe the new draft.
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  useEffect(() => {
    let active = true;
    fetchJokes()
      .then((loaded) => {
        if (!active) {
          return;
        }
        // Merge instead of replace: a slow initial GET can resolve AFTER a joke was
        // already created locally. Keep any locally-added jokes the snapshot predates
        // (they are newer, so they stay on top), then the server list. Dedup by id so
        // a joke already in the snapshot is not shown twice.
        setJokes((current) => {
          if (current === null) {
            return loaded;
          }
          const loadedIds = new Set(loaded.map((joke) => joke.id));
          const localOnly = current.filter((joke) => !loadedIds.has(joke.id));
          return [...localOnly, ...loaded];
        });
      })
      .catch((cause) => {
        if (!active) {
          return;
        }
        if (cause instanceof UnauthorizedError) {
          onSessionExpired();
          return;
        }
        setLoadFailed(true);
      });
    return () => {
      active = false;
    };
  }, [onSessionExpired]);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (draft.trim() === "") {
      return;
    }
    setAddError(null);
    setSubmitting(true);
    try {
      const created = await createJoke(draft);
      if (!mounted.current) {
        return;
      }
      // Prepend so the newest joke stays on top, matching the backend order. Dedup
      // by id: if a concurrent GET already brought this joke in, don't add a second
      // row with the same id.
      setJokes((current) => {
        const existing = current ?? [];
        if (existing.some((joke) => joke.id === created.id)) {
          return existing;
        }
        return [created, ...existing];
      });
      onDraftChange("");
    } catch (cause) {
      if (!mounted.current) {
        return;
      }
      if (cause instanceof UnauthorizedError) {
        // Session expired: hand off to App to show the login screen. Keep the typed
        // draft (App preserves it across re-auth) and show no inline error here.
        onSessionExpired();
        return;
      }
      setAddError("Nie udało się dodać żartu. Spróbuj ponownie.");
    } finally {
      if (mounted.current) {
        setSubmitting(false);
      }
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
          value={draft}
          onChange={(event) => onDraftChange(event.target.value)}
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
