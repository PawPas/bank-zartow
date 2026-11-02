import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { useState } from "react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import JokeList from "./JokeList.tsx";
import {
  createJoke,
  fetchJokes,
  UnauthorizedError,
  type Joke,
} from "../api/client.ts";

vi.mock("../api/client.ts", () => ({
  fetchJokes: vi.fn(),
  createJoke: vi.fn(),
  UnauthorizedError: class UnauthorizedError extends Error {},
}));

const fetchJokesMock = vi.mocked(fetchJokes);
const createJokeMock = vi.mocked(createJoke);

function joke(id: number, content: string): Joke {
  return { id, content, createdAt: "2026-11-02T10:00:00Z", version: 0 };
}

/**
 * Renders JokeList inside a tiny stateful parent so the lifted draft behaves as it
 * does under App (controlled value that updates as the user types).
 */
function renderJokeList(onSessionExpired: () => void = vi.fn()) {
  function Harness() {
    const [draft, setDraft] = useState("");
    return (
      <JokeList draft={draft} onDraftChange={setDraft} onSessionExpired={onSessionExpired} />
    );
  }
  render(<Harness />);
  return { onSessionExpired };
}

describe("JokeList", () => {
  beforeEach(() => {
    fetchJokesMock.mockResolvedValue([]);
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("renders the jokes returned by the API client", async () => {
    fetchJokesMock.mockResolvedValue([
      joke(2, "Najnowszy żart"),
      joke(1, "Starszy żart"),
    ]);

    renderJokeList();

    expect(await screen.findByText("Najnowszy żart")).toBeInTheDocument();
    expect(screen.getByText("Starszy żart")).toBeInTheDocument();
  });

  it("shows an empty-state message when there are no jokes", async () => {
    fetchJokesMock.mockResolvedValue([]);

    renderJokeList();

    expect(
      await screen.findByText("Nie masz jeszcze żadnych żartów."),
    ).toBeInTheDocument();
  });

  it("adds a joke with at most two taps beyond typing the content", async () => {
    createJokeMock.mockResolvedValue(joke(5, "Nowy żart"));
    renderJokeList();
    await screen.findByText("Nie masz jeszcze żadnych żartów.");

    // The two — and only two — tap targets are present from the very first render,
    // with no prior interaction: no "reveal the form" step exists. Tap 1 is focusing
    // the content field; tap 2 is pressing "Dodaj". Typing is not a tap.
    const field = screen.getByLabelText("Treść żartu");
    const addButton = screen.getByRole("button", { name: "Dodaj" });

    // Tap 1: focus the field (what a phone user does by tapping it).
    fireEvent.focus(field);
    // Typing — not counted as a tap.
    fireEvent.change(field, { target: { value: "Nowy żart" } });
    // Tap 2: submit.
    fireEvent.click(addButton);

    await waitFor(() => expect(createJokeMock).toHaveBeenCalledWith("Nowy żart"));
    // The created joke appears in the list and the field is cleared for the next one.
    expect(await screen.findByText("Nowy żart")).toBeInTheDocument();
    expect(field).toHaveValue("");
  });

  it("keeps a newly added joke when a slow initial fetch resolves afterwards", async () => {
    // The initial GET is deliberately left pending so it can resolve AFTER the POST,
    // reproducing the race where a stale snapshot would otherwise erase the new joke.
    let resolveFetch!: (jokes: Joke[]) => void;
    fetchJokesMock.mockReturnValue(
      new Promise<Joke[]>((resolve) => {
        resolveFetch = resolve;
      }),
    );
    createJokeMock.mockResolvedValue(joke(5, "Nowy żart"));

    renderJokeList();
    fireEvent.change(screen.getByLabelText("Treść żartu"), {
      target: { value: "Nowy żart" },
    });
    fireEvent.click(screen.getByRole("button", { name: "Dodaj" }));

    // The created joke shows even though the initial GET has not resolved yet.
    expect(await screen.findByText("Nowy żart")).toBeInTheDocument();

    // Now the stale snapshot (taken before the POST, so missing the new joke) arrives.
    resolveFetch([joke(1, "Stary żart")]);

    // The snapshot is merged in, but the newly created joke is NOT overwritten...
    expect(await screen.findByText("Stary żart")).toBeInTheDocument();
    expect(screen.getByText("Nowy żart")).toBeInTheDocument();
    // ...and it stays on top, matching the server's newest-first order.
    const items = screen.getAllByRole("listitem").map((item) => item.textContent);
    expect(items).toEqual(["Nowy żart", "Stary żart"]);
  });

  it("shows an error and keeps the typed content when adding fails (non-auth error)", async () => {
    createJokeMock.mockRejectedValue(new Error("boom"));
    renderJokeList();
    await screen.findByText("Nie masz jeszcze żadnych żartów.");

    const field = screen.getByLabelText("Treść żartu");
    fireEvent.change(field, { target: { value: "Nieudany żart" } });
    fireEvent.click(screen.getByRole("button", { name: "Dodaj" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Nie udało się dodać żartu. Spróbuj ponownie.");
    // The content is preserved so the user does not lose their text on a failure.
    expect(field).toHaveValue("Nieudany żart");
  });

  it("shows an error when the jokes cannot be loaded (non-auth error)", async () => {
    fetchJokesMock.mockRejectedValue(new Error("boom"));

    renderJokeList();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Nie udało się wczytać żartów.",
    );
  });

  it("asks App to handle an expired session when loading the list returns 401", async () => {
    fetchJokesMock.mockRejectedValue(new UnauthorizedError());
    const onSessionExpired = vi.fn();

    renderJokeList(onSessionExpired);

    await waitFor(() => expect(onSessionExpired).toHaveBeenCalledOnce());
    // No generic load error: App switches to the login screen instead.
    expect(
      screen.queryByText("Nie udało się wczytać żartów."),
    ).not.toBeInTheDocument();
  });

  it("asks App to handle an expired session on a 401 while adding, keeping the draft", async () => {
    createJokeMock.mockRejectedValue(new UnauthorizedError());
    const onSessionExpired = vi.fn();

    renderJokeList(onSessionExpired);
    await screen.findByText("Nie masz jeszcze żadnych żartów.");

    const field = screen.getByLabelText("Treść żartu");
    fireEvent.change(field, { target: { value: "Niewysłany żart" } });
    fireEvent.click(screen.getByRole("button", { name: "Dodaj" }));

    await waitFor(() => expect(onSessionExpired).toHaveBeenCalledOnce());
    // The draft is kept (not cleared) and no "try again" error is shown; re-auth is
    // handled by App, after which the user can resend the same text.
    expect(field).toHaveValue("Niewysłany żart");
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });
});
