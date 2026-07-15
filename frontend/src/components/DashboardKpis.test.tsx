import { render, screen } from '@testing-library/react';
import { describe, expect, it, vi } from 'vitest';
import type { CategorySummary } from '../api/types';
import DashboardKpis from './DashboardKpis';

const { getMonthlySummaryMock } = vi.hoisted(() => ({
  getMonthlySummaryMock: vi.fn(),
}));

vi.mock('../api/client', () => ({
  ApiError: class ApiError extends Error {},
  getMonthlySummary: getMonthlySummaryMock,
}));

const salario = { id: 'cat-salario', nome: 'Salario', tipo: 'RECEITA' as const, criadoEm: '2026-01-01' };
const alimentacao = { id: 'cat-alimentacao', nome: 'Alimentacao', tipo: 'DESPESA' as const, criadoEm: '2026-01-01' };

function summary(yearMonth: string, itens: CategorySummary['itens']): CategorySummary {
  return { yearMonth, itens };
}

describe('DashboardKpis', () => {
  it('renderiza saldo, total gasto e queda percentual quando o gasto diminui vs. mes anterior', async () => {
    getMonthlySummaryMock.mockImplementation((yearMonth: string) => {
      if (yearMonth === '2026-07') {
        return Promise.resolve(
          summary('2026-07', [
            { categoria: salario, total: 3000 },
            { categoria: alimentacao, total: 450.5 },
          ]),
        );
      }
      // mes anterior: gasto maior (900), entao o mes atual representa queda
      return Promise.resolve(summary('2026-06', [{ categoria: alimentacao, total: 900 }]));
    });

    render(<DashboardKpis yearMonth="2026-07" />);

    expect(await screen.findByText('R$ 2.549,50')).toBeInTheDocument(); // saldo = 3000 - 450.5
    expect(screen.getByText('R$ 450,50')).toBeInTheDocument(); // total gasto
    // gasto caiu de 900 para 450.50 -> queda de (450.5-900)/900 = 49,9%, seta pra baixo (bom)
    expect(screen.getByText(/▼/)).toBeInTheDocument();
    expect(screen.getByText(/49,9%/)).toBeInTheDocument();
  });

  it('mostra "sem dados do mes anterior" em vez de dividir por zero', async () => {
    getMonthlySummaryMock.mockImplementation((yearMonth: string) => {
      if (yearMonth === '2026-07') {
        return Promise.resolve(summary('2026-07', [{ categoria: alimentacao, total: 200 }]));
      }
      return Promise.resolve(summary('2026-06', [])); // mes anterior sem nenhuma transacao
    });

    render(<DashboardKpis yearMonth="2026-07" />);

    expect(await screen.findByText('Sem dados do mês anterior')).toBeInTheDocument();
  });

  it('exibe mensagem de erro quando a API falha', async () => {
    getMonthlySummaryMock.mockRejectedValue(new Error('boom'));

    render(<DashboardKpis yearMonth="2026-07" />);

    expect(await screen.findByRole('alert')).toBeInTheDocument();
  });
});
