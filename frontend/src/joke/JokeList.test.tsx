import { fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import JokeList from "./JokeList.tsx";
import { createJoke, fetchJokes, type Joke } from "../api/client.ts";

vi.mock("../api/client.ts", () => ({
  fetchJokes: vi.fn(),
  createJoke: vi.fn(),
}));

const fetchJokesMock = vi.mocked(fetchJokes);
const createJokeMock = vi.mocked(createJoke);

function joke(id: number, content: string): Joke {
  return { id, content, createdAt: "2026-11-02T10:00:00Z", version: 0 };
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

    render(<JokeList />);

    expect(await screen.findByText("Najnowszy żart")).toBeInTheDocument();
    expect(screen.getByText("Starszy żart")).toBeInTheDocument();
  });

  it("shows an empty-state message when there are no jokes", async () => {
    fetchJokesMock.mockResolvedValue([]);

    render(<JokeList />);

    expect(
      await screen.findByText("Nie masz jeszcze żadnych żartów."),
    ).toBeInTheDocument();
  });

  it("adds a joke with at most two taps beyond typing the content", async () => {
    createJokeMock.mockResolvedValue(joke(5, "Nowy żart"));
    render(<JokeList />);
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

  it("shows an error and keeps the typed content when adding fails", async () => {
    createJokeMock.mockRejectedValue(new Error("boom"));
    render(<JokeList />);
    await screen.findByText("Nie masz jeszcze żadnych żartów.");

    const field = screen.getByLabelText("Treść żartu");
    fireEvent.change(field, { target: { value: "Nieudany żart" } });
    fireEvent.click(screen.getByRole("button", { name: "Dodaj" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Nie udało się dodać żartu. Spróbuj ponownie.");
    // The content is preserved so the user does not lose their text on a failure.
    expect(field).toHaveValue("Nieudany żart");
  });

  it("shows an error when the jokes cannot be loaded", async () => {
    fetchJokesMock.mockRejectedValue(new Error("boom"));

    render(<JokeList />);

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Nie udało się wczytać żartów.",
    );
  });
});
