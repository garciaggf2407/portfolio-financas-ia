import { useState } from 'react';
import styles from './App.module.css';
import UploadPage from './pages/UploadPage';

// Navegacao simples via estado local (sem React Router): a app tem 3 telas
// fixas e nao ha necessidade de URLs profundas/bookmarkaveis nesta fase.
type Page = 'upload' | 'transactions' | 'dashboard';

const NAV_ITEMS: { id: Page; label: string }[] = [
  { id: 'upload', label: 'Upload' },
  { id: 'transactions', label: 'Transações' },
  { id: 'dashboard', label: 'Dashboard' },
];

function App() {
  const [page, setPage] = useState<Page>('upload');

  return (
    <div className={styles.app}>
      <header className={styles.header}>
        <h1 className={styles.logo}>Portfólio Finanças IA</h1>
        <nav className={styles.nav}>
          {NAV_ITEMS.map((item) => (
            <button
              key={item.id}
              type="button"
              className={
                page === item.id
                  ? `${styles.navButton} ${styles.navButtonActive}`
                  : styles.navButton
              }
              onClick={() => setPage(item.id)}
            >
              {item.label}
            </button>
          ))}
        </nav>
      </header>

      <main className={styles.main}>
        {page === 'upload' && <UploadPage />}
        {page === 'transactions' && <p>Tela de transações em construção.</p>}
        {page === 'dashboard' && <p>Dashboard em construção.</p>}
      </main>
    </div>
  );
}

export default App;
