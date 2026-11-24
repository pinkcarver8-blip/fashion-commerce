import styles from './App.module.css';

// 고객 앱(store). 모바일 우선(A8). 상단에 데모 배너를 늘 보여 준다(A5-10).
export function App() {
  return (
    <main className={styles.page}>
      <p className={styles.banner} role="note">
        포트폴리오 데모입니다. 실제 판매·결제는 없습니다.
      </p>
      <h1 className={styles.title}>스토어</h1>
      <p className={styles.muted}>준비 중입니다.</p>
    </main>
  );
}
