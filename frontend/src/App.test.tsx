import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import App from "./App.tsx";
import { fetchSession, logout } from "./api/client.ts";

vi.mock("./api/client.ts", () => ({
  fetchSession: vi.fn(),
  logout: vi.fn(),
  login: vi.fn(),
  InvalidCredentialsError: class extends Error {},
}));

const fetchSessionMock = vi.mocked(fetchSession);
const logoutMock = vi.mocked(logout);

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
    fetchSessionMock.mockResolvedValue({ authenticated: true, username: "ja" });
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
});
