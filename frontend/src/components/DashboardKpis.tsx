import { useEffect, useState } from 'react';
import { ApiError, getMonthlySummary } from '../api/client';
import type { CategorySummary } from '../api/types';
import styles from './DashboardKpis.module.css';

const currencyFormatter = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
});

const percentFormatter = new Intl.NumberFormat('pt-BR', {
  style: 'percent',
  maximumFractionDigits: 1,
});

function previousYearMonth(yearMonth: string): string {
  const [year, month] = yearMonth.split('-').map(Number);
  const date = new Date(Date.UTC(year, month - 2, 1));
  return `${date.getUTCFullYear()}-${String(date.getUTCMonth() + 1).padStart(2, '0')}`;
}

// Mesma convencao de MonthlySummaryService#apenasDespesas (backend): total
// gasto exclui categorias RECEITA; transacao sem categoria conta como
// gasto (status desconhecido, nao receita conhecida). CategorySummary.itens
// ja vem em ABS(SUM(valor)) por categoria (magnitude, nao saldo), entao
// saldo = receitas - gasto e reconstruido a partir do tipo de cada
// categoria, nao lido diretamente de um campo "saldo".
function totalGasto(summary: CategorySummary): number {
  return summary.itens
    .filter((item) => item.categoria === null || item.categoria.tipo !== 'RECEITA')
    .reduce((acc, item) => acc + item.total, 0);
}

function totalReceita(summary: CategorySummary): number {
  return summary.itens
    .filter((item) => item.categoria?.tipo === 'RECEITA')
    .reduce((acc, item) => acc + item.total, 0);
}

interface DashboardKpisProps {
  yearMonth: string;
}

function DashboardKpis({ yearMonth }: DashboardKpisProps) {
  const [current, setCurrent] = useState<CategorySummary | null>(null);
  const [previous, setPrevious] = useState<CategorySummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;
    async function load() {
      setIsLoading(true);
      setError(null);
      try {
        const [currentSummary, previousSummary] = await Promise.all([
          getMonthlySummary(yearMonth),
          getMonthlySummary(previousYearMonth(yearMonth)),
        ]);
        if (!cancelled) {
          setCurrent(currentSummary);
          setPrevious(previousSummary);
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof ApiError ? err.message : 'Falha ao carregar os indicadores do mês.',
          );
        }
      } finally {
        if (!cancelled) setIsLoading(false);
      }
    }
    void load();
    return () => {
      cancelled = true;
    };
  }, [yearMonth]);

  if (isLoading) {
    return <p className={styles.loading}>Carregando indicadores...</p>;
  }

  if (error || !current || !previous) {
    return (
      <div className={styles.errorBox} role="alert">
        {error ?? 'Falha ao carregar os indicadores do mês.'}
      </div>
    );
  }

  const gastoAtual = totalGasto(current);
  const gastoAnterior = totalGasto(previous);
  const saldo = totalReceita(current) - gastoAtual;
  const variacao = gastoAnterior === 0 ? null : (gastoAtual - gastoAnterior) / gastoAnterior;

  return (
    <div className={styles.kpiGrid}>
      <div className={styles.kpiCard}>
        <span className={styles.kpiLabel}>Saldo do mês</span>
        <span className={saldo >= 0 ? styles.kpiValuePositive : styles.kpiValueNegative}>
          {currencyFormatter.format(saldo)}
        </span>
      </div>

      <div className={styles.kpiCard}>
        <span className={styles.kpiLabel}>Total gasto</span>
        <span className={styles.kpiValue}>{currencyFormatter.format(gastoAtual)}</span>
      </div>

      <div className={styles.kpiCard}>
        <span className={styles.kpiLabel}>Vs. mês anterior</span>
        {variacao === null ? (
          <span className={styles.kpiValue}>Sem dados do mês anterior</span>
        ) : (
          <span className={variacao > 0 ? styles.kpiDeltaUp : styles.kpiDeltaDown}>
            {variacao > 0 ? '▲' : '▼'} {percentFormatter.format(Math.abs(variacao))}
          </span>
        )}
      </div>
    </div>
  );
}

export default DashboardKpis;
