export interface ProblemDetail {
  type?: string;
  title?: string;
  status?: number;
  detail?: string;
  instance?: string;
  properties?: Record<string, unknown>;
  [key: string]: unknown;
}

export class HttpError extends Error {
  readonly status: number;
  readonly problem?: ProblemDetail;

  constructor(status: number, message: string, problem?: ProblemDetail) {
    super(message);
    this.name = 'HttpError';
    this.status = status;
    this.problem = problem;
  }
}

/**
 * Reads a cookie value by name from document.cookie.
 */
export function getCookie(name: string): string | null {
  if (typeof document === 'undefined') {
    return null;
  }
  const match = document.cookie.match(new RegExp(`(?:^|;\\s*)${name}=([^;]*)`));
  return match ? decodeURIComponent(match[1]) : null;
}

export interface RequestOptions extends RequestInit {
  params?: Record<string, string | number | boolean | undefined | null>;
}

export async function request<T>(url: string, options: RequestOptions = {}): Promise<T> {
  const { params, headers: customHeaders, ...restOptions } = options;

  let requestUrl = url;
  if (params) {
    const searchParams = new URLSearchParams();
    Object.entries(params).forEach(([key, value]) => {
      if (value !== undefined && value !== null && value !== '') {
        searchParams.append(key, String(value));
      }
    });
    const queryString = searchParams.toString();
    if (queryString) {
      requestUrl += (requestUrl.includes('?') ? '&' : '?') + queryString;
    }
  }

  const headers = new Headers(customHeaders || {});

  // For non-GET / HEAD / OPTIONS methods, inject X-XSRF-TOKEN if cookie exists
  const method = (restOptions.method || 'GET').toUpperCase();
  if (!['GET', 'HEAD', 'OPTIONS'].includes(method)) {
    const xsrfToken = getCookie('XSRF-TOKEN');
    if (xsrfToken && !headers.has('X-XSRF-TOKEN')) {
      headers.set('X-XSRF-TOKEN', xsrfToken);
    }
  }

  if (restOptions.body && typeof restOptions.body === 'string' && !headers.has('Content-Type')) {
    headers.set('Content-Type', 'application/json');
  }

  const response = await fetch(requestUrl, {
    credentials: 'same-origin',
    ...restOptions,
    headers,
  });

  if (!response.ok) {
    let problem: ProblemDetail | undefined;
    let errorMessage = `HTTP ${response.status}: ${response.statusText || 'Error'}`;

    if (typeof response.json === 'function') {
      try {
        const errorJson = await response.json();
        problem = errorJson as ProblemDetail;
        if (problem.detail) {
          errorMessage = problem.detail;
        } else if (problem.title) {
          errorMessage = problem.title;
        }
      } catch {
        // Fallback
      }
    }

    throw new HttpError(response.status, errorMessage, problem);
  }

  if (response.status === 204) {
    return null as unknown as T;
  }

  const contentType = response.headers?.get?.('content-type');
  if (contentType && contentType.includes('application/json')) {
    return (await response.json()) as T;
  }

  if (typeof response.json === 'function') {
    try {
      return (await response.json()) as T;
    } catch {
      // Fallback
    }
  }

  if (typeof response.text === 'function') {
    return (await response.text()) as unknown as T;
  }

  return null as unknown as T;
}

export const apiClient = {
  get: <T>(url: string, options?: RequestOptions) =>
    request<T>(url, { ...options, method: 'GET' }),

  post: <T>(url: string, body?: unknown, options?: RequestOptions) =>
    request<T>(url, {
      ...options,
      method: 'POST',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }),

  put: <T>(url: string, body?: unknown, options?: RequestOptions) =>
    request<T>(url, {
      ...options,
      method: 'PUT',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }),

  patch: <T>(url: string, body?: unknown, options?: RequestOptions) =>
    request<T>(url, {
      ...options,
      method: 'PATCH',
      body: body !== undefined ? JSON.stringify(body) : undefined,
    }),

  delete: <T>(url: string, options?: RequestOptions) =>
    request<T>(url, { ...options, method: 'DELETE' }),
};

export default apiClient;
