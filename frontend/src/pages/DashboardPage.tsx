import { useEffect, useState } from 'react';
import {
  CartesianGrid,
  Cell,
  Legend,
  Line,
  LineChart,
  Pie,
  PieChart,
  ResponsiveContainer,
  Tooltip,
  XAxis,
  YAxis,
} from 'recharts';
import { ApiError, getMonthlySummary, getSpendingHistory } from '../api/client';
import type { CategorySummary, MonthlyTotal } from '../api/types';
import styles from './DashboardPage.module.css';

const currencyFormatter = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
});

const PIE_COLORS = ['#aa3bff', '#1a9c5b', '#e5484d', '#f5a524', '#2b8ce6', '#c084fc', '#6b6375'];

function getCurrentYearMonth(): string {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, '0');
  return `${now.getFullYear()}-${month}`;
}

function DashboardPage() {
  const [yearMonth, setYearMonth] = useState(getCurrentYearMonth());
  const [summary, setSummary] = useState<CategorySummary | null>(null);
  const [summaryLoading, setSummaryLoading] = useState(true);
  const [summaryError, setSummaryError] = useState<string | null>(null);

  const [history, setHistory] = useState<MonthlyTotal[]>([]);
  const [historyLoading, setHistoryLoading] = useState(true);
  const [historyError, setHistoryError] = useState<string | null>(null);

  // Grafico de gastos por categoria: reage a troca de mes.
  useEffect(() => {
    let cancelled = false;
    async function loadSummary() {
      setSummaryLoading(true);
      setSummaryError(null);
      try {
        const data = await getMonthlySummary(yearMonth);
        if (!cancelled) setSummary(data);
      } catch (err) {
        if (!cancelled) {
          setSummaryError(
            err instanceof ApiError
              ? err.message
              : 'Falha ao carregar o resumo do mês.',
          );
        }
      } finally {
        if (!cancelled) setSummaryLoading(false);
      }
    }
    void loadSummary();
    return () => {
      cancelled = true;
    };
  }, [yearMonth]);

  // Grafico de evolucao mensal: a API nao aceita mes-ancora, apenas
  // quantidade de meses de historico. Carregado uma vez (nao depende do
  // mes selecionado no seletor).
  useEffect(() => {
    let cancelled = false;
    async function loadHistory() {
      setHistoryLoading(true);
      setHistoryError(null);
      try {
        const data = await getSpendingHistory();
        if (!cancelled) setHistory(data.slice().reverse());
      } catch (err) {
        if (!cancelled) {
          setHistoryError(
            err instanceof ApiError
              ? err.message
              : 'Falha ao carregar o histórico de gastos.',
          );
        }
      } finally {
        if (!cancelled) setHistoryLoading(false);
      }
    }
    void loadHistory();
    return () => {
      cancelled = true;
    };
  }, []);

  const pieData = (summary?.itens ?? []).map((item) => ({
    name: item.categoria.nome,
    value: item.total,
  }));

  return (
    <section className={styles.page}>
      <div className={styles.headerRow}>
        <h2 className={styles.title}>Dashboard</h2>
        <input
          type="month"
          className={styles.monthInput}
          value={yearMonth}
          onChange={(e) => setYearMonth(e.target.value)}
        />
      </div>

      <div className={styles.chartsGrid}>
        <div className={styles.chartCard}>
          <h3 className={styles.chartTitle}>Gastos por categoria ({yearMonth})</h3>
          {summaryError && (
            <div className={styles.errorBox} role="alert">
              {summaryError}
            </div>
          )}
          {summaryLoading ? (
            <p>Carregando...</p>
          ) : pieData.length === 0 ? (
            <p className={styles.emptyState}>Sem gastos categorizados neste mês.</p>
          ) : (
            <ResponsiveContainer width="100%" height={280}>
              <PieChart>
                <Pie
                  data={pieData}
                  dataKey="value"
                  nameKey="name"
                  cx="50%"
                  cy="50%"
                  outerRadius={90}
                  label={(entry) => entry.name}
                >
                  {pieData.map((entry, index) => (
                    <Cell key={entry.name} fill={PIE_COLORS[index % PIE_COLORS.length]} />
                  ))}
                </Pie>
                <Tooltip formatter={(value: number) => currencyFormatter.format(value)} />
                <Legend />
              </PieChart>
            </ResponsiveContainer>
          )}
        </div>

        <div className={styles.chartCard}>
          <h3 className={styles.chartTitle}>Evolução mensal</h3>
          {historyError && (
            <div className={styles.errorBox} role="alert">
              {historyError}
            </div>
          )}
          {historyLoading ? (
            <p>Carregando...</p>
          ) : history.length === 0 ? (
            <p className={styles.emptyState}>Sem histórico disponível.</p>
          ) : (
            <ResponsiveContainer width="100%" height={280}>
              <LineChart data={history}>
                <CartesianGrid strokeDasharray="3 3" stroke="var(--border)" />
                <XAxis dataKey="yearMonth" />
                <YAxis />
                <Tooltip formatter={(value: number) => currencyFormatter.format(value)} />
                <Line type="monotone" dataKey="total" stroke="#aa3bff" strokeWidth={2} />
              </LineChart>
            </ResponsiveContainer>
          )}
        </div>
      </div>
    </section>
  );
}

export default DashboardPage;
