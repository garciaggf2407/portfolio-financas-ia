import { useEffect, useState } from 'react';
import { ApiError, getMonthlyAiSummary } from '../api/client';
import type { AiSummary } from '../api/types';
import styles from './AiSummaryCard.module.css';

interface AiSummaryCardProps {
  yearMonth: string;
}

const dateTimeFormatter = new Intl.DateTimeFormat('pt-BR', {
  dateStyle: 'short',
  timeStyle: 'short',
});

function AiSummaryCard({ yearMonth }: AiSummaryCardProps) {
  const [summary, setSummary] = useState<AiSummary | null>(null);
  const [isLoading, setIsLoading] = useState(true);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);

  useEffect(() => {
    let cancelled = false;

    async function load() {
      setIsLoading(true);
      setErrorMessage(null);
      setSummary(null);
      try {
        const result = await getMonthlyAiSummary(yearMonth);
        if (!cancelled) setSummary(result);
      } catch (err) {
        if (!cancelled) {
          if (err instanceof ApiError && err.status === 503) {
            setErrorMessage('Serviço de IA indisponível no momento. Tente novamente mais tarde.');
          } else {
            setErrorMessage(
              err instanceof ApiError ? err.message : 'Falha ao carregar o resumo da IA.',
            );
          }
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

  return (
    <div className={styles.card}>
      <h3 className={styles.title}>Resumo mensal (IA)</h3>

      {isLoading && <p>Gerando resumo...</p>}

      {!isLoading && errorMessage && (
        <div className={styles.errorBox} role="alert">
          {errorMessage}
        </div>
      )}

      {!isLoading && !errorMessage && summary && (
        <>
          <p className={styles.text}>{summary.texto}</p>
          <p className={styles.meta}>
            Gerado em {dateTimeFormatter.format(new Date(summary.geradoEm))}
          </p>
        </>
      )}

      {!isLoading && !errorMessage && !summary && (
        <p className={styles.fallback}>Resumo ainda não gerado para este mês.</p>
      )}
    </div>
  );
}

export default AiSummaryCard;
