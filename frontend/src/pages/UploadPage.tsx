import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError, importTransactions } from '../api/client';
import type { ImportResult } from '../api/types';
import styles from './UploadPage.module.css';

function UploadPage() {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [isSlow, setIsSlow] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!selectedFile) {
      setError('Selecione um arquivo CSV antes de enviar.');
      return;
    }

    setIsUploading(true);
    setIsSlow(false);
    setError(null);
    setResult(null);

    // Backend roda em plano free (Render), que hiberna apos inatividade e
    // pode levar ~1min pra acordar na primeira requisicao -- sem esse aviso,
    // a tela parece travada e sem feedback nenhum durante esse tempo.
    const slowHintTimer = setTimeout(() => setIsSlow(true), 5000);

    try {
      const importResult = await importTransactions(selectedFile);
      setResult(importResult);
      setSelectedFile(null);
      if (fileInputRef.current) fileInputRef.current.value = '';
    } catch (err) {
      setError(
        err instanceof ApiError
          ? err.message
          : 'Falha ao enviar o arquivo. Verifique se o backend está disponível.',
      );
    } finally {
      clearTimeout(slowHintTimer);
      setIsUploading(false);
      setIsSlow(false);
    }
  }

  return (
    <section className={styles.page}>
      <h2 className={styles.title}>Importar extrato</h2>
      <p className={styles.subtitle}>
        Envie um arquivo CSV com colunas de data, descrição e valor.
      </p>

      <form className={styles.form} onSubmit={handleSubmit}>
        <input
          ref={fileInputRef}
          type="file"
          accept=".csv,text/csv"
          className={styles.fileInput}
          onChange={(e) => setSelectedFile(e.target.files?.[0] ?? null)}
        />
        <button type="submit" className={styles.submitButton} disabled={isUploading}>
          {isUploading ? 'Enviando...' : 'Enviar CSV'}
        </button>
      </form>

      {isSlow && (
        <p className={styles.slowHint} role="status">
          O servidor está acordando (plano gratuito hiberna após inatividade) — pode levar
          até 1 minuto na primeira requisição. Aguarde, não recarregue a página.
        </p>
      )}

      {error && (
        <div className={styles.errorBox} role="alert">
          {error}
        </div>
      )}

      {result && (
        <div className={styles.resultBox}>
          <div className={styles.resultStats}>
            <div className={styles.stat}>
              <span className={styles.statValue}>{result.importadas}</span>
              <span className={styles.statLabel}>importadas</span>
            </div>
            <div className={styles.stat}>
              <span className={styles.statValue}>{result.ignoradasDuplicadas}</span>
              <span className={styles.statLabel}>ignoradas (duplicadas)</span>
            </div>
            <div className={styles.stat}>
              <span className={styles.statValue}>{result.invalidas.length}</span>
              <span className={styles.statLabel}>inválidas</span>
            </div>
          </div>

          {result.invalidas.length > 0 && (
            <ul className={styles.invalidList}>
              {result.invalidas.map((invalida) => (
                <li key={`${invalida.line}-${invalida.reason}`}>
                  Linha {invalida.line}: {invalida.reason}
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </section>
  );
}

export default UploadPage;
