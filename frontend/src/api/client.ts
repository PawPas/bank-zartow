// Single entry point for talking to the backend. Components must not call
// `fetch` directly; they go through the functions exported here.

export interface HealthStatus {
  status: string;
}

// In production the frontend is served by Spring Boot from the same origin, so
// an empty base means "same host". Overridable for local development via Vite.
const baseUrl: string = import.meta.env.VITE_API_BASE_URL ?? "";

export async function fetchHealth(): Promise<HealthStatus> {
  const response = await fetch(`${baseUrl}/actuator/health`);
  if (!response.ok) {
    throw new Error(`Healthcheck failed with status ${response.status}`);
  }
  const body = (await response.json()) as HealthStatus;
  return body;
}
