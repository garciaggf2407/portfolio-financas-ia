import { useRef, useState } from 'react';
import type { FormEvent } from 'react';
import { ApiError, importTransactions } from '../api/client';
import type { ImportResult } from '../api/types';
import styles from './UploadPage.module.css';

function UploadPage() {
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isUploading, setIsUploading] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [result, setResult] = useState<ImportResult | null>(null);

  async function handleSubmit(event: FormEvent) {
    event.preventDefault();
    if (!selectedFile) {
      setError('Selecione um arquivo CSV antes de enviar.');
      return;
    }

    setIsUploading(true);
    setError(null);
    setResult(null);

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
      setIsUploading(false);
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
