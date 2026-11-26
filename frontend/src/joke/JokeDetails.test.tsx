import { act, fireEvent, render, screen, waitFor } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import JokeDetails from "./JokeDetails.tsx";
import {
  deleteJoke,
  fetchJokeDetail,
  NotFoundError,
  UnauthorizedError,
  updateJoke,
  VersionConflictError,
  type JokeDetail,
} from "../api/client.ts";

vi.mock("../api/client.ts", () => ({
  fetchJokeDetail: vi.fn(),
  updateJoke: vi.fn(),
  deleteJoke: vi.fn(),
  UnauthorizedError: class UnauthorizedError extends Error {},
  VersionConflictError: class VersionConflictError extends Error {},
  NotFoundError: class NotFoundError extends Error {},
}));

const fetchJokeDetailMock = vi.mocked(fetchJokeDetail);
const updateJokeMock = vi.mocked(updateJoke);
const deleteJokeMock = vi.mocked(deleteJoke);

const CONFLICT_MESSAGE =
  "Żart został w międzyczasie zmieniony. Odśwież dane i spróbuj ponownie.";

function detail(overrides: Partial<JokeDetail> = {}): JokeDetail {
  return {
    id: 7,
    content: "Pierwotna treść",
    createdAt: "2026-11-20T10:00:00Z",
    updatedAt: "2026-11-21T11:00:00Z",
    version: 2,
    ...overrides,
  };
}

function renderDetails(
  props: { onBack?: () => void; onSessionExpired?: () => void } = {},
) {
  const onBack = props.onBack ?? vi.fn();
  const onSessionExpired = props.onSessionExpired ?? vi.fn();
  const view = render(
    <JokeDetails jokeId={7} onBack={onBack} onSessionExpired={onSessionExpired} />,
  );
  return { onBack, onSessionExpired, view };
}

describe("JokeDetails", () => {
  beforeEach(() => {
    fetchJokeDetailMock.mockResolvedValue(detail());
  });

  afterEach(() => {
    vi.clearAllMocks();
  });

  it("renders the details fetched from the API client", async () => {
    renderDetails();

    expect(await screen.findByLabelText("Treść żartu")).toHaveValue("Pierwotna treść");
    expect(fetchJokeDetailMock).toHaveBeenCalledWith(7);
    // The timestamps are shown so the user can tell the joke's history apart.
    expect(screen.getByText(/Utworzono:/)).toBeInTheDocument();
    expect(screen.getByText(/Zaktualizowano:/)).toBeInTheDocument();
  });

  it("saves the edited content with the current version and tracks the new one", async () => {
    updateJokeMock
      .mockResolvedValueOnce(detail({ content: "Nowa treść", version: 3 }))
      .mockResolvedValueOnce(detail({ content: "Jeszcze nowsza", version: 4 }));
    renderDetails();
    const field = await screen.findByLabelText("Treść żartu");

    fireEvent.change(field, { target: { value: "Nowa treść" } });
    fireEvent.click(screen.getByRole("button", { name: "Zapisz" }));
    await waitFor(() => expect(updateJokeMock).toHaveBeenCalledWith(7, "Nowa treść", 2));

    // A second save must carry the version the first save brought back, not the
    // original one — otherwise every second edit would falsely conflict.
    fireEvent.change(field, { target: { value: "Jeszcze nowsza" } });
    fireEvent.click(screen.getByRole("button", { name: "Zapisz" }));
    await waitFor(() =>
      expect(updateJokeMock).toHaveBeenCalledWith(7, "Jeszcze nowsza", 3),
    );
  });

  it("keeps a newer draft typed while a slow save is still in flight", async () => {
    let resolveUpdate!: (value: JokeDetail) => void;
    updateJokeMock.mockReturnValueOnce(
      new Promise<JokeDetail>((resolve) => {
        resolveUpdate = resolve;
      }),
    );
    renderDetails();
    const field = await screen.findByLabelText("Treść żartu");

    fireEvent.change(field, { target: { value: "Edycja A" } });
    fireEvent.click(screen.getByRole("button", { name: "Zapisz" }));
    // The user keeps typing while the PUT response is still on the wire.
    fireEvent.change(field, { target: { value: "Edycja B" } });

    await act(async () => {
      resolveUpdate(detail({ content: "Edycja A", version: 3 }));
      await Promise.resolve();
    });

    // The late success must not roll the field back to the content it saved —
    // that would silently erase the newer draft.
    expect(field).toHaveValue("Edycja B");

    // The bumped version was still picked up: the next save sends the newer
    // draft with it instead of falsely conflicting.
    updateJokeMock.mockResolvedValue(detail({ content: "Edycja B", version: 4 }));
    fireEvent.click(screen.getByRole("button", { name: "Zapisz" }));
    await waitFor(() =>
      expect(updateJokeMock).toHaveBeenLastCalledWith(7, "Edycja B", 3),
    );
  });

  it("does not call the API when the edited content is blank", async () => {
    renderDetails();
    const field = await screen.findByLabelText("Treść żartu");

    fireEvent.change(field, { target: { value: "   " } });
    fireEvent.click(screen.getByRole("button", { name: "Zapisz" }));

    expect(updateJokeMock).not.toHaveBeenCalled();
  });

  it("shows the Polish conflict message on a 409 and refreshes the data on demand", async () => {
    updateJokeMock.mockRejectedValue(new VersionConflictError());
    renderDetails();
    const field = await screen.findByLabelText("Treść żartu");

    fireEvent.change(field, { target: { value: "Moja edycja" } });
    fireEvent.click(screen.getByRole("button", { name: "Zapisz" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(CONFLICT_MESSAGE);

    // "Odśwież" reloads the current server state: fresh content and version.
    fetchJokeDetailMock.mockResolvedValue(detail({ content: "Cudza edycja", version: 5 }));
    fireEvent.click(screen.getByRole("button", { name: "Odśwież" }));

    await waitFor(() => expect(field).toHaveValue("Cudza edycja"));
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();

    // Saving after the refresh uses the refreshed version.
    updateJokeMock.mockResolvedValue(detail({ content: "Po konflikcie", version: 6 }));
    fireEvent.change(field, { target: { value: "Po konflikcie" } });
    fireEvent.click(screen.getByRole("button", { name: "Zapisz" }));
    await waitFor(() =>
      expect(updateJokeMock).toHaveBeenLastCalledWith(7, "Po konflikcie", 5),
    );
  });

  it("deletes the joke through the API client and goes back to the list", async () => {
    deleteJokeMock.mockResolvedValue();
    const { onBack } = renderDetails();
    await screen.findByLabelText("Treść żartu");

    fireEvent.click(screen.getByRole("button", { name: "Usuń" }));

    await waitFor(() => expect(deleteJokeMock).toHaveBeenCalledWith(7, 2));
    await waitFor(() => expect(onBack).toHaveBeenCalledOnce());
  });

  it("shows the conflict message when deleting hits a 409 and stays on the screen", async () => {
    deleteJokeMock.mockRejectedValue(new VersionConflictError());
    const { onBack } = renderDetails();
    await screen.findByLabelText("Treść żartu");

    fireEvent.click(screen.getByRole("button", { name: "Usuń" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(CONFLICT_MESSAGE);
    expect(onBack).not.toHaveBeenCalled();
  });

  it("says the joke is gone when it no longer exists", async () => {
    fetchJokeDetailMock.mockRejectedValue(new NotFoundError());
    const { onBack } = renderDetails();

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Ten żart już nie istnieje.",
    );
    fireEvent.click(screen.getByRole("button", { name: "Wróć" }));
    expect(onBack).toHaveBeenCalledOnce();
  });

  it("says the joke is gone when a save hits a 404 (deleted elsewhere)", async () => {
    updateJokeMock.mockRejectedValue(new NotFoundError());
    renderDetails();
    const field = await screen.findByLabelText("Treść żartu");

    fireEvent.change(field, { target: { value: "Edycja usuniętego" } });
    fireEvent.click(screen.getByRole("button", { name: "Zapisz" }));

    expect(await screen.findByRole("alert")).toHaveTextContent(
      "Ten żart już nie istnieje.",
    );
  });

  it("hands an expired session to App when loading returns 401", async () => {
    fetchJokeDetailMock.mockRejectedValue(new UnauthorizedError());
    const { onSessionExpired } = renderDetails();

    await waitFor(() => expect(onSessionExpired).toHaveBeenCalledOnce());
    expect(screen.queryByRole("alert")).not.toBeInTheDocument();
  });

  it("hands an expired session to App when saving returns 401", async () => {
    updateJokeMock.mockRejectedValue(new UnauthorizedError());
    const { onSessionExpired } = renderDetails();
    const field = await screen.findByLabelText("Treść żartu");

    fireEvent.change(field, { target: { value: "Po wygaśnięciu" } });
    fireEvent.click(screen.getByRole("button", { name: "Zapisz" }));

    await waitFor(() => expect(onSessionExpired).toHaveBeenCalledOnce());
  });

  it("ignores a late delete response after unmounting (no navigation from a dead view)", async () => {
    let resolveDelete!: () => void;
    deleteJokeMock.mockReturnValue(
      new Promise<void>((resolve) => {
        resolveDelete = resolve;
      }),
    );
    const { onBack, view } = renderDetails();
    await screen.findByLabelText("Treść żartu");

    fireEvent.click(screen.getByRole("button", { name: "Usuń" }));
    view.unmount();

    // The response arrives only after the view is gone — it must be dropped.
    await act(async () => {
      resolveDelete();
      await Promise.resolve();
    });

    expect(onBack).not.toHaveBeenCalled();
  });

  it("ignores a late load response after unmounting", async () => {
    const errorSpy = vi.spyOn(console, "error").mockImplementation(() => {});
    let resolveFetch!: (value: JokeDetail) => void;
    fetchJokeDetailMock.mockReturnValue(
      new Promise<JokeDetail>((resolve) => {
        resolveFetch = resolve;
      }),
    );
    const { view } = renderDetails();
    view.unmount();

    // Resolving after unmount must be dropped silently: no state update on the
    // dead view, which React would report through console.error.
    await act(async () => {
      resolveFetch(detail());
      await Promise.resolve();
    });

    expect(errorSpy).not.toHaveBeenCalled();
    errorSpy.mockRestore();
  });

  it("ignores a late load 401 after unmounting (no login screen from a dead view)", async () => {
    let rejectFetch!: (reason: unknown) => void;
    fetchJokeDetailMock.mockReturnValue(
      new Promise<JokeDetail>((_resolve, reject) => {
        rejectFetch = reject;
      }),
    );
    const { onSessionExpired, view } = renderDetails();
    view.unmount();

    // The 401 lands only after the view is gone — it must not drag the app to
    // the login screen.
    await act(async () => {
      rejectFetch(new UnauthorizedError());
      await Promise.resolve();
    });

    expect(onSessionExpired).not.toHaveBeenCalled();
  });

  it("goes back to the list with the back button", async () => {
    const { onBack } = renderDetails();
    await screen.findByLabelText("Treść żartu");

    fireEvent.click(screen.getByRole("button", { name: "Wróć" }));

    expect(onBack).toHaveBeenCalledOnce();
  });
});
