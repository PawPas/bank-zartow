import { useCallback, useEffect, useRef, useState, type FormEvent } from "react";
import {
  deleteJoke,
  fetchJokeDetail,
  NotFoundError,
  UnauthorizedError,
  updateJoke,
  VersionConflictError,
  type JokeDetail,
} from "../api/client.ts";

interface JokeDetailsProps {
  jokeId: number;
  /** Back to the list — also after a successful delete. */
  onBack: () => void;
  /** Called when a request returns 401 so App can show the login screen. */
  onSessionExpired: () => void;
}

const CONFLICT_MESSAGE =
  "Żart został w międzyczasie zmieniony. Odśwież dane i spróbuj ponownie.";

/**
 * Details of a single joke with in-place content editing and deletion
 * (mobile-first, Polish texts). Every modifying call carries the version the
 * view last saw; a 409 shows a conflict message with an explicit refresh, so
 * concurrent edits are never overwritten silently.
 */
export default function JokeDetails({ jokeId, onBack, onSessionExpired }: JokeDetailsProps) {
  const [detail, setDetail] = useState<JokeDetail | null>(null);
  const [draft, setDraft] = useState("");
  const [notFound, setNotFound] = useState(false);
  const [loadFailed, setLoadFailed] = useState(false);
  const [conflict, setConflict] = useState(false);
  const [actionError, setActionError] = useState<string | null>(null);
  const [busy, setBusy] = useState<"saving" | "deleting" | null>(null);

  // Guards every async callback below: once this view unmounts (back to the
  // list, logout, expired session), a late response must neither touch state
  // nor navigate — onBack/onSessionExpired from a dead view would yank the
  // user out of whatever they are doing now.
  const mounted = useRef(true);
  useEffect(() => {
    mounted.current = true;
    return () => {
      mounted.current = false;
    };
  }, []);

  const load = useCallback(async () => {
    try {
      const loaded = await fetchJokeDetail(jokeId);
      if (!mounted.current) {
        return;
      }
      setDetail(loaded);
      // The textarea always shows the freshly loaded server state; on an
      // explicit refresh after a conflict this is exactly what the user asked for.
      setDraft(loaded.content);
      setConflict(false);
      setActionError(null);
    } catch (cause) {
      if (!mounted.current) {
        return;
      }
      if (cause instanceof UnauthorizedError) {
        onSessionExpired();
        return;
      }
      if (cause instanceof NotFoundError) {
        setNotFound(true);
        return;
      }
      setLoadFailed(true);
    }
  }, [jokeId, onSessionExpired]);

  useEffect(() => {
    void load();
  }, [load]);

  function handleModifyError(cause: unknown) {
    if (cause instanceof UnauthorizedError) {
      onSessionExpired();
      return;
    }
    if (cause instanceof NotFoundError) {
      setNotFound(true);
      return;
    }
    if (cause instanceof VersionConflictError) {
      setConflict(true);
      return;
    }
    setActionError("Nie udało się zapisać zmian. Spróbuj ponownie.");
  }

  async function handleSave(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (detail === null || draft.trim() === "") {
      return;
    }
    setActionError(null);
    setBusy("saving");
    // The field stays editable while the PUT is in flight, so by the time the
    // response lands the draft may already be newer than what was sent.
    const sentContent = draft;
    try {
      const updated = await updateJoke(jokeId, sentContent, detail.version);
      if (!mounted.current) {
        return;
      }
      // Track the bumped version so the next save does not falsely conflict.
      setDetail(updated);
      // Only a draft still equal to the sent content is replaced by the saved
      // (normalized) one; a newer draft typed meanwhile must never be erased.
      setDraft((current) => (current === sentContent ? updated.content : current));
    } catch (cause) {
      if (!mounted.current) {
        return;
      }
      handleModifyError(cause);
    } finally {
      if (mounted.current) {
        setBusy(null);
      }
    }
  }

  async function handleDelete() {
    if (detail === null) {
      return;
    }
    setActionError(null);
    setBusy("deleting");
    try {
      await deleteJoke(jokeId, detail.version);
      if (!mounted.current) {
        return;
      }
      onBack();
    } catch (cause) {
      if (!mounted.current) {
        return;
      }
      handleModifyError(cause);
    } finally {
      if (mounted.current) {
        setBusy(null);
      }
    }
  }

  if (notFound) {
    return (
      <section className="joke-details">
        <p className="joke-error" role="alert">
          Ten żart już nie istnieje.
        </p>
        <button type="button" onClick={onBack}>
          Wróć
        </button>
      </section>
    );
  }

  if (loadFailed) {
    return (
      <section className="joke-details">
        <p className="joke-error" role="alert">
          Nie udało się wczytać żartu.
        </p>
        <button type="button" onClick={onBack}>
          Wróć
        </button>
      </section>
    );
  }

  if (detail === null) {
    return (
      <section className="joke-details">
        <p>Ładowanie żartu…</p>
      </section>
    );
  }

  return (
    <section className="joke-details">
      {conflict && (
        <div className="joke-conflict" role="alert">
          <p className="joke-error">{CONFLICT_MESSAGE}</p>
          <button type="button" onClick={() => void load()}>
            Odśwież
          </button>
        </div>
      )}
      {actionError && (
        <p className="joke-error" role="alert">
          {actionError}
        </p>
      )}
      <form className="joke-edit" onSubmit={handleSave} aria-label="Edycja żartu">
        <label htmlFor="joke-detail-content">Treść żartu</label>
        <textarea
          id="joke-detail-content"
          name="content"
          rows={6}
          value={draft}
          onChange={(event) => setDraft(event.target.value)}
        />
        <p className="joke-dates">
          Utworzono: {new Date(detail.createdAt).toLocaleString("pl-PL")}
          <br />
          Zaktualizowano: {new Date(detail.updatedAt).toLocaleString("pl-PL")}
        </p>
        <div className="joke-actions">
          <button type="submit" disabled={busy !== null}>
            {busy === "saving" ? "Zapisywanie…" : "Zapisz"}
          </button>
          <button
            type="button"
            className="joke-delete"
            onClick={() => void handleDelete()}
            disabled={busy !== null}
          >
            {busy === "deleting" ? "Usuwanie…" : "Usuń"}
          </button>
          <button type="button" onClick={onBack} disabled={busy !== null}>
            Wróć
          </button>
        </div>
      </form>
    </section>
  );
}
