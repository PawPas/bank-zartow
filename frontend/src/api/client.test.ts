import { afterEach, describe, expect, it, vi } from "vitest";
import {
  createJoke,
  fetchHealth,
  fetchJokes,
  fetchSession,
  InvalidCredentialsError,
  login,
  logout,
  UnauthorizedError,
} from "./client.ts";

function mockFetchResponse(response: Partial<Response>): void {
  vi.stubGlobal("fetch", vi.fn().mockResolvedValue(response as Response));
}

afterEach(() => {
  vi.unstubAllGlobals();
  document.cookie = "XSRF-TOKEN=; expires=Thu, 01 Jan 1970 00:00:00 GMT";
});

describe("fetchHealth", () => {
  it("returns the parsed status when the backend reports healthy", async () => {
    mockFetchResponse({ ok: true, status: 200, json: async () => ({ status: "UP" }) });

    const health = await fetchHealth();

    expect(health.status).toBe("UP");
    expect(fetch).toHaveBeenCalledWith("/actuator/health");
  });

  it("throws when the healthcheck responds with a non-ok status", async () => {
    mockFetchResponse({ ok: false, status: 503, json: async () => ({ status: "DOWN" }) });

    await expect(fetchHealth()).rejects.toThrow("503");
  });
});

describe("fetchSession", () => {
  it("returns the authentication state", async () => {
    mockFetchResponse({
      ok: true,
      status: 200,
      json: async () => ({ authenticated: true, username: "ja" }),
    });

    const session = await fetchSession();

    expect(session.authenticated).toBe(true);
    expect(session.username).toBe("ja");
    expect(fetch).toHaveBeenCalledWith(
      "/api/auth/session",
      expect.objectContaining({ credentials: "include" }),
    );
  });
});

describe("login", () => {
  it("posts the credentials and echoes the CSRF cookie in the header", async () => {
    document.cookie = "XSRF-TOKEN=tok-123";
    mockFetchResponse({ ok: true, status: 204 });

    await login("ja", "tajne");

    const [url, init] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("/api/auth/login");
    expect(init?.method).toBe("POST");
    expect((init?.headers as Record<string, string>)["X-XSRF-TOKEN"]).toBe("tok-123");
    expect((init?.body as URLSearchParams).toString()).toBe(
      "username=ja&password=tajne",
    );
  });

  it("throws InvalidCredentialsError on a 401", async () => {
    mockFetchResponse({ ok: false, status: 401 });

    await expect(login("ja", "zle")).rejects.toBeInstanceOf(InvalidCredentialsError);
  });
});

describe("logout", () => {
  it("posts with the CSRF header", async () => {
    document.cookie = "XSRF-TOKEN=tok-456";
    mockFetchResponse({ ok: true, status: 204 });

    await logout();

    const [url, init] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("/api/auth/logout");
    expect(init?.method).toBe("POST");
    expect((init?.headers as Record<string, string>)["X-XSRF-TOKEN"]).toBe("tok-456");
  });
});

describe("fetchJokes", () => {
  it("returns the jokes the backend sends", async () => {
    const jokes = [{ id: 1, content: "Żart", createdAt: "2026-11-02T10:00:00Z", version: 0 }];
    mockFetchResponse({ ok: true, status: 200, json: async () => jokes });

    const result = await fetchJokes();

    expect(result).toEqual(jokes);
    expect(fetch).toHaveBeenCalledWith(
      "/api/jokes",
      expect.objectContaining({ credentials: "include" }),
    );
  });

  it("throws UnauthorizedError on a 401", async () => {
    mockFetchResponse({ ok: false, status: 401 });

    await expect(fetchJokes()).rejects.toBeInstanceOf(UnauthorizedError);
  });
});

describe("createJoke", () => {
  it("posts the content as JSON and echoes the CSRF cookie in the header", async () => {
    document.cookie = "XSRF-TOKEN=tok-789";
    const created = { id: 7, content: "Nowy", createdAt: "2026-11-02T10:00:00Z", version: 0 };
    mockFetchResponse({ ok: true, status: 201, json: async () => created });

    const result = await createJoke("Nowy");

    expect(result).toEqual(created);
    const [url, init] = vi.mocked(fetch).mock.calls[0];
    expect(url).toBe("/api/jokes");
    expect(init?.method).toBe("POST");
    expect((init?.headers as Record<string, string>)["Content-Type"]).toBe("application/json");
    expect((init?.headers as Record<string, string>)["X-XSRF-TOKEN"]).toBe("tok-789");
    expect(init?.body).toBe(JSON.stringify({ content: "Nowy" }));
  });

  it("throws on a non-ok response so the UI can show an error", async () => {
    mockFetchResponse({ ok: false, status: 400 });

    await expect(createJoke("")).rejects.toThrow("400");
  });

  it("throws UnauthorizedError on a 401 so the UI can show the login screen", async () => {
    mockFetchResponse({ ok: false, status: 401 });

    await expect(createJoke("x")).rejects.toBeInstanceOf(UnauthorizedError);
  });
});
