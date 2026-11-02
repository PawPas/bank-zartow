// Single entry point for talking to the backend. Components must not call
// `fetch` directly; they go through the functions exported here.

export interface HealthStatus {
  status: string;
}

export interface SessionInfo {
  authenticated: boolean;
  username: string | null;
}

// In production the frontend is served by Spring Boot from the same origin, so
// an empty base means "same host". Overridable for local development via Vite.
const baseUrl: string = import.meta.env.VITE_API_BASE_URL ?? "";

/** Thrown when the backend answers a protected call with 401. */
export class UnauthorizedError extends Error {
  constructor() {
    super("Unauthorized");
    this.name = "UnauthorizedError";
  }
}

/** Thrown when login is rejected. The message is generic on purpose. */
export class InvalidCredentialsError extends Error {
  constructor() {
    super("Nieprawidłowy login lub hasło.");
    this.name = "InvalidCredentialsError";
  }
}

export async function fetchHealth(): Promise<HealthStatus> {
  const response = await fetch(`${baseUrl}/actuator/health`);
  if (!response.ok) {
    throw new Error(`Healthcheck failed with status ${response.status}`);
  }
  const body = (await response.json()) as HealthStatus;
  return body;
}

// Reads the CSRF token the backend publishes in the readable XSRF-TOKEN cookie.
// It must be echoed in the X-XSRF-TOKEN header on every state-changing request.
function readCsrfToken(): string | null {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? decodeURIComponent(match[1]) : null;
}

function csrfHeaders(): Record<string, string> {
  const token = readCsrfToken();
  return token ? { "X-XSRF-TOKEN": token } : {};
}

/**
 * Current authentication state. Also makes the backend set the XSRF-TOKEN cookie,
 * so it should be called before the first login attempt.
 */
export async function fetchSession(): Promise<SessionInfo> {
  const response = await fetch(`${baseUrl}/api/auth/session`, {
    credentials: "include",
  });
  if (!response.ok) {
    throw new Error(`Session check failed with status ${response.status}`);
  }
  return (await response.json()) as SessionInfo;
}

/**
 * Logs in with the single account's credentials. Resolves on success; throws
 * {@link InvalidCredentialsError} when the backend rejects the credentials.
 */
export async function login(username: string, password: string): Promise<void> {
  const body = new URLSearchParams({ username, password });
  const response = await fetch(`${baseUrl}/api/auth/login`, {
    method: "POST",
    credentials: "include",
    headers: {
      "Content-Type": "application/x-www-form-urlencoded",
      ...csrfHeaders(),
    },
    body,
  });
  if (response.status === 401) {
    throw new InvalidCredentialsError();
  }
  if (!response.ok) {
    throw new Error(`Login failed with status ${response.status}`);
  }
}

export async function logout(): Promise<void> {
  const response = await fetch(`${baseUrl}/api/auth/logout`, {
    method: "POST",
    credentials: "include",
    headers: { ...csrfHeaders() },
  });
  if (!response.ok) {
    throw new Error(`Logout failed with status ${response.status}`);
  }
}
