// Thin wrapper over fetch for the interface endpoints. Authentication is carried by the browser
// (HTTP Basic) once credentials are known, so requests just send and read JSON. Errors from the
// server arrive as application/problem+json with a French message, surfaced here as Error.detail.

/** An error carrying the HTTP status and the server's French detail message. */
export class ApiError extends Error {
  readonly status: number;
  constructor(status: number, detail: string) {
    super(detail);
    this.status = status;
    this.name = 'ApiError';
  }
}

async function request<T>(method: string, path: string, body?: unknown, headers: Record<string, string> = {}): Promise<T> {
  const init: RequestInit = { method, headers: { ...headers } };
  if (body !== undefined) {
    (init.headers as Record<string, string>)['Content-Type'] = 'application/json';
    init.body = JSON.stringify(body);
  }
  const response = await fetch(path, init);
  if (!response.ok) {
    let detail = `Erreur ${response.status}`;
    try {
      const problem = await response.json();
      if (problem && typeof problem.detail === 'string') {
        detail = problem.detail;
      }
    } catch {
      // no JSON body; keep the generic message
    }
    throw new ApiError(response.status, detail);
  }
  if (response.status === 204) {
    return undefined as T;
  }
  return (await response.json()) as T;
}

/** Issues a GET and returns the parsed JSON. */
export const apiGet = <T>(path: string): Promise<T> => request<T>('GET', path);

/** Issues a GET and returns the raw body as text (for non-JSON payloads such as SEPA XML or CSV). */
export async function apiGetText(path: string): Promise<string> {
  const response = await fetch(path, { method: 'GET' });
  if (!response.ok) {
    let detail = `Erreur ${response.status}`;
    try {
      const problem = await response.json();
      if (problem && typeof problem.detail === 'string') {
        detail = problem.detail;
      }
    } catch {
      // no JSON body; keep the generic message
    }
    throw new ApiError(response.status, detail);
  }
  return response.text();
}

/** Issues a POST with a JSON body and returns the parsed JSON. */
export const apiPost = <T>(path: string, body: unknown): Promise<T> => request<T>('POST', path, body);

/** Issues a PUT with a JSON body and optional headers and returns the parsed JSON. */
export const apiPut = <T>(path: string, body: unknown, headers?: Record<string, string>): Promise<T> =>
  request<T>('PUT', path, body, headers);

/** Issues a DELETE. */
export const apiDelete = (path: string): Promise<void> => request<void>('DELETE', path);
