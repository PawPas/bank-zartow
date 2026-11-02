import { useCallback, useEffect, useState } from "react";
import "./App.css";
import { fetchSession, logout } from "./api/client.ts";
import LoginScreen from "./auth/LoginScreen.tsx";
import JokeList from "./joke/JokeList.tsx";

type AuthState =
  | { status: "loading" }
  | { status: "anonymous" }
  | { status: "authenticated"; username: string | null };

export default function App() {
  const [auth, setAuth] = useState<AuthState>({ status: "loading" });
  const [logoutError, setLogoutError] = useState<string | null>(null);
  // The quick-add draft lives here so it survives the login screen shown on an
  // expired session. The joke list is NOT kept this way: JokeList unmounts, so a
  // fresh authenticated fetch replaces it and no prior-session data is shown.
  const [draft, setDraft] = useState("");

  const refreshSession = useCallback(async () => {
    try {
      const session = await fetchSession();
      setAuth(
        session.authenticated
          ? { status: "authenticated", username: session.username }
          : { status: "anonymous" },
      );
    } catch {
      // Treat any failure to confirm the session as "not logged in", which sends
      // the user to the login screen (the 401 redirect behaviour).
      setAuth({ status: "anonymous" });
    }
  }, []);

  useEffect(() => {
    void refreshSession();
  }, [refreshSession]);

  const handleSessionExpired = useCallback(() => {
    // A 401 from a data call means the session expired (T-006): show the login
    // screen. The draft is preserved above; the joke list is dropped so nothing
    // from the previous session is shown before a fresh, authenticated fetch.
    setAuth({ status: "anonymous" });
  }, []);

  async function handleLogout() {
    setLogoutError(null);
    try {
      await logout();
    } catch {
      // The server session may still be valid, so stay logged in and let the user
      // retry rather than falsely showing a logged-out state.
      setLogoutError("Nie udało się wylogować. Spróbuj ponownie.");
      return;
    }
    // Logout succeeded. Re-fetch the session: this both flips the UI to the login
    // screen and makes the backend set a fresh XSRF-TOKEN cookie (logout clears
    // it), so logging in again works without reloading the page.
    await refreshSession();
  }

  if (auth.status === "loading") {
    return (
      <main className="app">
        <p>Ładowanie…</p>
      </main>
    );
  }

  if (auth.status === "anonymous") {
    return <LoginScreen onLoggedIn={() => void refreshSession()} />;
  }

  return (
    <main className="app">
      <header className="app-header">
        <h1>Bank żartów</h1>
        <button type="button" onClick={handleLogout}>
          Wyloguj się
        </button>
      </header>
      {logoutError && (
        <p className="login-error" role="alert">
          {logoutError}
        </p>
      )}
      <JokeList
        draft={draft}
        onDraftChange={setDraft}
        onSessionExpired={handleSessionExpired}
      />
    </main>
  );
}
