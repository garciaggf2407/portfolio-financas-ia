import { useEffect, useState } from 'react';
import {
  ApiError,
  categorizeTransaction,
  listCategories,
  listTransactions,
} from '../api/client';
import type { Category, Transaction } from '../api/types';
import styles from './TransactionsPage.module.css';

const currencyFormatter = new Intl.NumberFormat('pt-BR', {
  style: 'currency',
  currency: 'BRL',
});

// data vem como 'yyyy-MM-dd'; evita usar Date() para nao sofrer deslocamento
// de fuso horario ao formatar uma data "pura" (sem horario).
function formatDate(isoDate: string): string {
  const [year, month, day] = isoDate.split('-');
  return `${day}/${month}/${year}`;
}

function TransactionsPage() {
  const [transactions, setTransactions] = useState<Transaction[]>([]);
  const [categories, setCategories] = useState<Category[]>([]);
  const [isLoading, setIsLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [savingId, setSavingId] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setError(null);
      try {
        const [transactionPage, categoryList] = await Promise.all([
          listTransactions(),
          listCategories(),
        ]);
        if (!cancelled) {
          setTransactions(transactionPage.content);
          setCategories(categoryList);
        }
      } catch (err) {
        if (!cancelled) {
          setError(
            err instanceof ApiError
              ? err.message
              : 'Falha ao carregar transações. Verifique se o backend está disponível.',
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
  }, []);

  async function handleCategoryChange(transactionId: string, categoriaId: string) {
    if (!categoriaId) return;
    setSavingId(transactionId);
    setError(null);
    try {
      const updated = await categorizeTransaction(transactionId, categoriaId);
      setTransactions((prev) =>
        prev.map((t) => (t.id === updated.id ? updated : t)),
      );
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : 'Falha ao categorizar a transação.',
      );
    } finally {
      setSavingId(null);
    }
  }

  return (
    <section className={styles.page}>
      <h2 className={styles.title}>Transações</h2>

      {error && (
        <div className={styles.errorBox} role="alert">
          {error}
        </div>
      )}

      {isLoading ? (
        <p>Carregando transações...</p>
      ) : transactions.length === 0 ? (
        <p className={styles.emptyState}>
          Nenhuma transação encontrada. Importe um CSV na tela de Upload.
        </p>
      ) : (
        <table className={styles.table}>
          <thead>
            <tr>
              <th>Data</th>
              <th>Descrição</th>
              <th>Valor</th>
              <th>Categoria</th>
            </tr>
          </thead>
          <tbody>
            {transactions.map((transaction) => {
              const isUncategorized = transaction.statusCategorizacao === 'sem_categoria';
              return (
                <tr
                  key={transaction.id}
                  className={isUncategorized ? styles.rowUncategorized : undefined}
                >
                  <td>{formatDate(transaction.data)}</td>
                  <td>
                    {transaction.descricao}
                    {isUncategorized && (
                      <span className={styles.uncategorizedLabel}>sem categoria</span>
                    )}
                  </td>
                  <td className={transaction.valor >= 0 ? styles.valorPositivo : styles.valorNegativo}>
                    {currencyFormatter.format(transaction.valor)}
                  </td>
                  <td>
                    <select
                      className={styles.select}
                      value={transaction.categoria?.id ?? ''}
                      disabled={savingId === transaction.id}
                      onChange={(e) => handleCategoryChange(transaction.id, e.target.value)}
                    >
                      <option value="" disabled>
                        Selecione...
                      </option>
                      {categories.map((category) => (
                        <option key={category.id} value={category.id}>
                          {category.nome}
                        </option>
                      ))}
                    </select>
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </section>
  );
}

export default TransactionsPage;
