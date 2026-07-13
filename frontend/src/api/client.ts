import type {
  AiSummary,
  Category,
  CategorySummary,
  CategoryType,
  CategorizationStatus,
  ImportResult,
  MonthlyTotal,
  Transaction,
  TransactionPage,
} from './types';

// Configuravel via .env (VITE_API_BASE). Sem valor definido, assume backend
// local na porta padrao do docker-compose (E-1).
export const API_BASE = import.meta.env.VITE_API_BASE ?? 'http://localhost:8080';

export class ApiError extends Error {
  status: number;

  constructor(status: number, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
  }
}

async function readErrorMessage(res: Response): Promise<string> {
  try {
    const body = (await res.json()) as { message?: string };
    return body.message ?? `Erro ${res.status}`;
  } catch {
    return `Erro ${res.status}`;
  }
}

async function handleResponse<T>(res: Response): Promise<T> {
  if (!res.ok) {
    throw new ApiError(res.status, await readErrorMessage(res));
  }
  return res.json() as Promise<T>;
}

export async function importTransactions(file: File): Promise<ImportResult> {
  const formData = new FormData();
  formData.append('file', file);
  const res = await fetch(`${API_BASE}/transactions/import`, {
    method: 'POST',
    body: formData,
  });
  return handleResponse<ImportResult>(res);
}

export interface ListTransactionsParams {
  yearMonth?: string;
  status?: CategorizationStatus;
  page?: number;
  size?: number;
}

export async function listTransactions(
  params: ListTransactionsParams = {},
): Promise<TransactionPage> {
  const search = new URLSearchParams();
  if (params.yearMonth) search.set('yearMonth', params.yearMonth);
  if (params.status) search.set('status', params.status);
  if (params.page !== undefined) search.set('page', String(params.page));
  if (params.size !== undefined) search.set('size', String(params.size));
  const qs = search.toString();
  const res = await fetch(`${API_BASE}/transactions${qs ? `?${qs}` : ''}`);
  return handleResponse<TransactionPage>(res);
}

export async function categorizeTransaction(
  id: string,
  categoriaId: string,
): Promise<Transaction> {
  const res = await fetch(`${API_BASE}/transactions/${id}/category`, {
    method: 'PATCH',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ categoriaId }),
  });
  return handleResponse<Transaction>(res);
}

export async function listCategories(tipo?: CategoryType): Promise<Category[]> {
  const qs = tipo ? `?tipo=${tipo}` : '';
  const res = await fetch(`${API_BASE}/categories${qs}`);
  return handleResponse<Category[]>(res);
}

export async function getMonthlySummary(yearMonth: string): Promise<CategorySummary> {
  const res = await fetch(`${API_BASE}/summary/${yearMonth}`);
  return handleResponse<CategorySummary>(res);
}

export async function getSpendingHistory(months = 6): Promise<MonthlyTotal[]> {
  const res = await fetch(`${API_BASE}/summary/history?months=${months}`);
  return handleResponse<MonthlyTotal[]>(res);
}

/**
 * Retorna o resumo mensal via IA, ou null quando ainda nao foi gerado
 * (202, conforme contrato). Erros 400/503 sao propagados como ApiError
 * para o chamador decidir a mensagem de fallback apropriada.
 */
export async function getMonthlyAiSummary(yearMonth: string): Promise<AiSummary | null> {
  const res = await fetch(`${API_BASE}/summary/${yearMonth}/ai`);
  if (res.status === 202 || res.status === 404) {
    return null;
  }
  return handleResponse<AiSummary>(res);
}
