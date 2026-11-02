import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App.tsx";
import {
  createJoke,
  fetchJokes,
  fetchSession,
  login,
  logout,
  UnauthorizedError,
} from "./api/client.ts";

vi.mock("./api/client.ts", () => ({
  fetchSession: vi.fn(),
  logout: vi.fn(),
  login: vi.fn(),
  // The authenticated screen renders JokeList, which loads jokes on mount.
  // Default to an empty list so these App-level tests stay focused on auth.
  fetchJokes: vi.fn().mockResolvedValue([]),
  createJoke: vi.fn(),
  InvalidCredentialsError: class extends Error {},
  UnauthorizedError: class UnauthorizedError extends Error {},
}));

const fetchSessionMock = vi.mocked(fetchSession);
const logoutMock = vi.mocked(logout);
const loginMock = vi.mocked(login);
const fetchJokesMock = vi.mocked(fetchJokes);
const createJokeMock = vi.mocked(createJoke);

describe("App", () => {
  beforeEach(() => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    vi.spyOn(console, "warn").mockImplementation(() => {});
  });

  afterEach(() => {
    vi.restoreAllMocks();
    vi.clearAllMocks();
  });

  it("shows the main screen with a logout button when a session exists", async () => {
    fetchSessionMock.mockResolvedValue({ authenticated: true, username: "ja" });

    render(<App />);

    expect(
      await screen.findByRole("heading", { name: "Bank żartów" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Wyloguj się" }),
    ).toBeInTheDocument();
  });

  it("shows the login screen when there is no session", async () => {
    fetchSessionMock.mockResolvedValue({ authenticated: false, username: null });

    render(<App />);

    expect(
      await screen.findByRole("form", { name: "Logowanie" }),
    ).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Zaloguj się" }),
    ).toBeInTheDocument();
  });

  it("redirects to the login screen when the session check is unauthorized", async () => {
    // A 401 surfaces as a rejected session check; the app must fall back to login.
    fetchSessionMock.mockRejectedValue(new Error("Session check failed with status 401"));

    render(<App />);

    expect(
      await screen.findByRole("form", { name: "Logowanie" }),
    ).toBeInTheDocument();
  });

  it("returns to the login screen after logging out", async () => {
    fetchSessionMock
      .mockResolvedValueOnce({ authenticated: true, username: "ja" })
      .mockResolvedValue({ authenticated: false, username: null });
    logoutMock.mockResolvedValue();

    render(<App />);
    const logoutButton = await screen.findByRole("button", {
      name: "Wyloguj się",
    });

    fireEvent.click(logoutButton);

    expect(
      await screen.findByRole("form", { name: "Logowanie" }),
    ).toBeInTheDocument();
    expect(logoutMock).toHaveBeenCalledOnce();
  });

  it("re-fetches the session after logout so logging in again works without reload", async () => {
    // mount -> authenticated, after logout -> anonymous, after re-login -> authenticated.
    fetchSessionMock
      .mockResolvedValueOnce({ authenticated: true, username: "ja" })
      .mockResolvedValueOnce({ authenticated: false, username: null })
      .mockResolvedValueOnce({ authenticated: true, username: "ja" });
    logoutMock.mockResolvedValue();
    loginMock.mockResolvedValue();

    render(<App />);
    fireEvent.click(await screen.findByRole("button", { name: "Wyloguj się" }));

    // Login form is shown again...
    await screen.findByRole("form", { name: "Logowanie" });
    // ...and the session was re-fetched after logout (this is what re-arms the
    // CSRF cookie the next login needs).
    expect(fetchSessionMock).toHaveBeenCalledTimes(2);

    // Logging in again (no page reload) succeeds and returns to the main screen.
    fireEvent.change(screen.getByLabelText("Login"), { target: { value: "ja" } });
    fireEvent.change(screen.getByLabelText("Hasło"), { target: { value: "x" } });
    fireEvent.click(screen.getByRole("button", { name: "Zaloguj się" }));

    expect(
      await screen.findByRole("button", { name: "Wyloguj się" }),
    ).toBeInTheDocument();
    expect(loginMock).toHaveBeenCalledWith("ja", "x");
  });

  it("stays logged in and shows an error when logout fails", async () => {
    fetchSessionMock.mockResolvedValue({ authenticated: true, username: "ja" });
    logoutMock.mockRejectedValue(new Error("Logout failed with status 403"));

    render(<App />);
    fireEvent.click(await screen.findByRole("button", { name: "Wyloguj się" }));

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Nie udało się wylogować. Spróbuj ponownie.");
    // Still on the authenticated screen; the session was NOT re-fetched (no false
    // logged-out state), so only the initial mount call happened.
    expect(
      screen.getByRole("button", { name: "Wyloguj się" }),
    ).toBeInTheDocument();
    expect(fetchSessionMock).toHaveBeenCalledOnce();
  });

  it("shows login on an expired session, preserves the draft, then lets the user retry", async () => {
    fetchSessionMock
      .mockResolvedValueOnce({ authenticated: true, username: "ja" }) // initial mount
      .mockResolvedValue({ authenticated: true, username: "ja" }); // after re-login
    // A private joke from the first session must not reappear after re-auth; it is
    // dropped and re-fetched. The second fetch returns an empty list.
    fetchJokesMock
      .mockResolvedValueOnce([
        { id: 1, content: "Prywatny żart", createdAt: "2026-11-02T09:00:00Z", version: 0 },
      ])
      .mockResolvedValue([]);
    // First add attempt hits an expired session; the retry after re-auth succeeds.
    createJokeMock
      .mockRejectedValueOnce(new UnauthorizedError())
      .mockResolvedValue({ id: 9, content: "Mój żart", createdAt: "2026-11-02T10:00:00Z", version: 0 });
    loginMock.mockResolvedValue();

    render(<App />);
    const field = await screen.findByLabelText("Treść żartu");
    expect(await screen.findByText("Prywatny żart")).toBeInTheDocument();

    fireEvent.change(field, { target: { value: "Mój żart" } });
    fireEvent.click(screen.getByRole("button", { name: "Dodaj" }));

    // The 401 bubbles up to App, which shows the login screen; the private joke
    // from the previous session is no longer on screen.
    await screen.findByRole("form", { name: "Logowanie" });
    expect(screen.queryByText("Prywatny żart")).not.toBeInTheDocument();

    // Re-authenticate.
    fireEvent.change(screen.getByLabelText("Login"), { target: { value: "ja" } });
    fireEvent.change(screen.getByLabelText("Hasło"), { target: { value: "x" } });
    fireEvent.click(screen.getByRole("button", { name: "Zaloguj się" }));

    // Back on the main screen: the typed draft survived re-authentication...
    const restoredField = await screen.findByLabelText("Treść żartu");
    expect(restoredField).toHaveValue("Mój żart");
    // ...and the list was re-fetched fresh (no prior-session data lingering).
    expect(screen.queryByText("Prywatny żart")).not.toBeInTheDocument();

    // Retry the add — it now succeeds.
    fireEvent.click(screen.getByRole("button", { name: "Dodaj" }));
    expect(await screen.findByText("Mój żart")).toBeInTheDocument();
  });
});
