import { useCallback, useEffect, useState } from "react";
import "./App.css";
import { fetchSession, logout } from "./api/client.ts";
import LoginScreen from "./auth/LoginScreen.tsx";

type AuthState =
  | { status: "loading" }
  | { status: "anonymous" }
  | { status: "authenticated"; username: string | null };

export default function App() {
  const [auth, setAuth] = useState<AuthState>({ status: "loading" });

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

  async function handleLogout() {
    try {
      await logout();
    } finally {
      setAuth({ status: "anonymous" });
    }
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
      <p>Narzędzie do zapisywania żartów i przygotowywania postów.</p>
    </main>
  );
}
