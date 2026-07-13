// Tipos derivados de docs/openapi.yaml (contrato da API backend).

export type CategoryType = 'DESPESA' | 'RECEITA';

export type CategorizationStatus =
  | 'sem_categoria'
  | 'categorizada_manual'
  | 'categorizada_ia';

export interface Category {
  id: string;
  nome: string;
  tipo: CategoryType;
  criadoEm: string;
}

export interface Transaction {
  id: string;
  data: string;
  descricao: string;
  valor: number;
  categoria: Category | null;
  origemImportacao: string;
  statusCategorizacao: CategorizationStatus;
}

export interface TransactionPage {
  content: Transaction[];
  page: number;
  size: number;
  totalElements: number;
}

export interface ImportRowError {
  line: number;
  rawContent?: string;
  reason: string;
}

export interface ImportResult {
  importadas: number;
  ignoradasDuplicadas: number;
  invalidas: ImportRowError[];
}

export interface CategorySummaryItem {
  // null = transacoes sem categoria no mes (decisao CP-2). total sempre
  // reflete magnitude de gasto (valor absoluto).
  categoria: Category | null;
  total: number;
}

export interface CategorySummary {
  yearMonth: string;
  itens: CategorySummaryItem[];
}

export interface MonthlyTotal {
  yearMonth: string;
  total: number;
}

export interface AiSummary {
  yearMonth: string;
  texto: string;
  geradoEm: string;
}

export interface ErrorResponse {
  status: number;
  message: string;
  details?: string[];
}
