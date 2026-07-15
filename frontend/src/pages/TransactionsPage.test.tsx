import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import type { Transaction, Category } from '../api/types';
import TransactionsPage from './TransactionsPage';

const { listTransactionsMock, listCategoriesMock, categorizeTransactionMock } = vi.hoisted(() => ({
  listTransactionsMock: vi.fn(),
  listCategoriesMock: vi.fn(),
  categorizeTransactionMock: vi.fn(),
}));

vi.mock('../api/client', () => ({
  ApiError: class ApiError extends Error {},
  listTransactions: listTransactionsMock,
  listCategories: listCategoriesMock,
  categorizeTransaction: categorizeTransactionMock,
}));

const alimentacao: Category = { id: 'cat-1', nome: 'Alimentacao', tipo: 'DESPESA', criadoEm: '2026-01-01' };

const transactions: Transaction[] = [
  {
    id: 't-1',
    data: '2026-07-01',
    descricao: 'Supermercado XPTO',
    valor: -150,
    categoria: alimentacao,
    origemImportacao: 'csv',
    statusCategorizacao: 'categorizada_manual',
  },
  {
    id: 't-2',
    data: '2026-07-02',
    descricao: 'Uber',
    valor: -30,
    categoria: null,
    origemImportacao: 'csv',
    statusCategorizacao: 'sem_categoria',
  },
  {
    id: 't-3',
    data: '2026-07-03',
    descricao: 'Supermercado ABC',
    valor: -80,
    categoria: null,
    origemImportacao: 'csv',
    statusCategorizacao: 'sem_categoria',
  },
];

function setup() {
  listTransactionsMock.mockResolvedValue({ content: transactions, page: 0, size: 20, totalElements: 3 });
  listCategoriesMock.mockResolvedValue([alimentacao]);
  return render(<TransactionsPage />);
}

describe('TransactionsPage', () => {
  it('renderiza as 3 transacoes e o icone de categoria correto para cada uma', async () => {
    setup();

    expect(await screen.findByText('Supermercado XPTO')).toBeInTheDocument();
    const rows = screen.getAllByRole('row');
    // header + 3 linhas de dados
    expect(rows).toHaveLength(4);
    expect(screen.getByText('🍔')).toBeInTheDocument(); // Alimentacao
    expect(screen.getAllByText('❓')).toHaveLength(2); // as 2 sem categoria
  });

  it('busca textual filtra por descricao (case-insensitive)', async () => {
    setup();
    await screen.findByText('Supermercado XPTO');

    const searchInput = screen.getByPlaceholderText('Buscar por descrição...');
    await userEvent.type(searchInput, 'supermercado');

    expect(screen.getByText('Supermercado XPTO')).toBeInTheDocument();
    expect(screen.getByText('Supermercado ABC')).toBeInTheDocument();
    expect(screen.queryByText('Uber')).not.toBeInTheDocument();
  });

  it('filtro "Sem categoria" mostra so as transacoes sem categoria', async () => {
    setup();
    await screen.findByText('Supermercado XPTO');

    const categorySelects = screen.getAllByRole('combobox');
    const filterSelect = categorySelects[0]; // primeiro select da pagina e o filtro, nao o de uma linha
    await userEvent.selectOptions(filterSelect, 'SEM_CATEGORIA');

    expect(screen.queryByText('Supermercado XPTO')).not.toBeInTheDocument();
    expect(screen.getByText('Uber')).toBeInTheDocument();
    expect(screen.getByText('Supermercado ABC')).toBeInTheDocument();
  });

  it('busca + filtro combinados usam AND, nao OR', async () => {
    setup();
    await screen.findByText('Supermercado XPTO');

    await userEvent.type(screen.getByPlaceholderText('Buscar por descrição...'), 'supermercado');
    const categorySelects = screen.getAllByRole('combobox');
    await userEvent.selectOptions(categorySelects[0], 'SEM_CATEGORIA');

    expect(screen.getByText('Supermercado ABC')).toBeInTheDocument();
    expect(screen.queryByText('Supermercado XPTO')).not.toBeInTheDocument();
    expect(screen.queryByText('Uber')).not.toBeInTheDocument();
  });

  it('mostra estado vazio dedicado quando o filtro nao encontra nada', async () => {
    setup();
    await screen.findByText('Supermercado XPTO');

    await userEvent.type(screen.getByPlaceholderText('Buscar por descrição...'), 'zzz-nao-existe');

    expect(await screen.findByText('Nenhuma transação corresponde ao filtro/busca atual.')).toBeInTheDocument();
  });
});
