import { fireEvent, render, screen } from "@testing-library/react";
import { afterEach, describe, expect, it, vi } from "vitest";
import LoginScreen from "./LoginScreen.tsx";
import { InvalidCredentialsError, login } from "../api/client.ts";

vi.mock("../api/client.ts", async () => {
  const actual =
    await vi.importActual<typeof import("../api/client.ts")>("../api/client.ts");
  return { ...actual, login: vi.fn() };
});

const loginMock = vi.mocked(login);

function fillAndSubmit(user: string, pass: string): void {
  fireEvent.change(screen.getByLabelText("Login"), { target: { value: user } });
  fireEvent.change(screen.getByLabelText("Hasło"), { target: { value: pass } });
  fireEvent.click(screen.getByRole("button", { name: "Zaloguj się" }));
}

describe("LoginScreen", () => {
  afterEach(() => {
    vi.clearAllMocks();
  });

  it("shows the Polish login form", () => {
    render(<LoginScreen onLoggedIn={() => {}} />);

    expect(screen.getByLabelText("Login")).toBeInTheDocument();
    expect(screen.getByLabelText("Hasło")).toBeInTheDocument();
    expect(
      screen.getByRole("button", { name: "Zaloguj się" }),
    ).toBeInTheDocument();
  });

  it("calls onLoggedIn after a successful login", async () => {
    loginMock.mockResolvedValue();
    const onLoggedIn = vi.fn();
    render(<LoginScreen onLoggedIn={onLoggedIn} />);

    fillAndSubmit("ja", "tajne");

    await vi.waitFor(() => expect(onLoggedIn).toHaveBeenCalledOnce());
    expect(loginMock).toHaveBeenCalledWith("ja", "tajne");
  });

  it("shows a generic error that does not reveal which field was wrong", async () => {
    loginMock.mockRejectedValue(new InvalidCredentialsError());
    const onLoggedIn = vi.fn();
    render(<LoginScreen onLoggedIn={onLoggedIn} />);

    fillAndSubmit("ja", "zle-haslo");

    const alert = await screen.findByRole("alert");
    expect(alert).toHaveTextContent("Nieprawidłowy login lub hasło.");
    expect(onLoggedIn).not.toHaveBeenCalled();
  });
});
