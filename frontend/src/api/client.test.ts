import { afterEach, describe, expect, it, vi } from "vitest";
import { fetchHealth } from "./client.ts";

function mockFetchResponse(response: Partial<Response>): void {
  vi.stubGlobal(
    "fetch",
    vi.fn().mockResolvedValue(response as Response),
  );
}

describe("fetchHealth", () => {
  afterEach(() => {
    vi.unstubAllGlobals();
  });

  it("returns the parsed status when the backend reports healthy", async () => {
    mockFetchResponse({
      ok: true,
      status: 200,
      json: async () => ({ status: "UP" }),
    });

    const health = await fetchHealth();

    expect(health.status).toBe("UP");
    expect(fetch).toHaveBeenCalledWith("/actuator/health");
  });

  it("throws when the healthcheck responds with a non-ok status", async () => {
    mockFetchResponse({
      ok: false,
      status: 503,
      json: async () => ({ status: "DOWN" }),
    });

    await expect(fetchHealth()).rejects.toThrow("503");
  });
});
