/** Тело ошибки API — RFC 9457 problem+json с кодом приложения (doc/api.md §1.1). */
export interface ProblemDetail {
  type?: string;
  title?: string;
  status: number;
  detail?: string;
  code?: string;
  [property: string]: unknown;
}

/** Ошибка запроса к API. `code` — код приложения (`VERSION_CONFLICT`, `NOT_FOUND` …) или `NETWORK_ERROR`. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;
  readonly detail: string | undefined;
  readonly problem: ProblemDetail | undefined;

  constructor(status: number, code: string, detail?: string, problem?: ProblemDetail) {
    super(detail ?? code);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
    this.detail = detail;
    this.problem = problem;
  }

  static network(cause: unknown): ApiError {
    const error = new ApiError(0, 'NETWORK_ERROR', undefined);
    error.cause = cause;
    return error;
  }

  static async fromResponse(response: Response): Promise<ApiError> {
    const contentType = response.headers.get('Content-Type') ?? '';
    if (contentType.includes('json')) {
      try {
        const problem = (await response.json()) as ProblemDetail;
        return new ApiError(response.status, problem.code ?? `HTTP_${response.status}`, problem.detail, problem);
      } catch {
        // тело не разобрать — ниже вернём ошибку по статусу
      }
    }
    return new ApiError(response.status, `HTTP_${response.status}`);
  }
}

/** Ошибки полей из `400 VALIDATION_FAILED` (`errors: [{field, message}]`): поле → сообщение. */
export function fieldErrors(error: unknown): Record<string, string> {
  if (!(error instanceof ApiError) || !Array.isArray(error.problem?.errors)) return {};
  const result: Record<string, string> = {};
  for (const item of error.problem.errors as unknown[]) {
    if (typeof item !== 'object' || item === null) continue;
    const { field, message } = item as { field?: unknown; message?: unknown };
    if (typeof field === 'string' && typeof message === 'string' && !(field in result)) result[field] = message;
  }
  return result;
}

