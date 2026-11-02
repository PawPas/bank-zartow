import { useState, type FormEvent } from "react";
import { InvalidCredentialsError, login } from "../api/client.ts";

interface LoginScreenProps {
  onLoggedIn: () => void;
}

/**
 * Login screen for the single account. Shows a generic error on a rejected
 * attempt so it never reveals which field was wrong. All text is in Polish.
 */
export default function LoginScreen({ onLoggedIn }: LoginScreenProps) {
  const [username, setUsername] = useState("");
  const [password, setPassword] = useState("");
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await login(username, password);
      onLoggedIn();
    } catch (cause) {
      if (cause instanceof InvalidCredentialsError) {
        setError("Nieprawidłowy login lub hasło.");
      } else {
        setError("Logowanie nie powiodło się. Spróbuj ponownie.");
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <main className="app">
      <h1>Bank żartów</h1>
      <form className="login" onSubmit={handleSubmit} aria-label="Logowanie">
        <label htmlFor="username">Login</label>
        <input
          id="username"
          name="username"
          autoComplete="username"
          value={username}
          onChange={(event) => setUsername(event.target.value)}
          required
        />

        <label htmlFor="password">Hasło</label>
        <input
          id="password"
          name="password"
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(event) => setPassword(event.target.value)}
          required
        />

        {error && (
          <p className="login-error" role="alert">
            {error}
          </p>
        )}

        <button type="submit" disabled={submitting}>
          {submitting ? "Logowanie…" : "Zaloguj się"}
        </button>
      </form>
    </main>
  );
}
