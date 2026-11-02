import { afterEach, describe, expect, it, vi } from "vitest";
import {
  fetchHealth,
  fetchSession,
  InvalidCredentialsError,
  login,
  logout,
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
