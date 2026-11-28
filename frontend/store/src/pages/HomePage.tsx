import { useEffect, useState } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import styles from './Form.module.css';

interface Me {
  email: string;
  name: string;
}

// 첫 화면(임시). 로그인했으면 내 정보를, 아니면 로그인·가입 링크를 보여 준다. 상품 화면은 R1-04부터.
export function HomePage() {
  const { status, client, sync } = useAuth();
  const [me, setMe] = useState<Me | null>(null);

  useEffect(() => {
    if (status !== 'loggedIn') {
      return;
    }
    let active = true;
    client.request('/api/members/me').then(async (response) => {
      if (!active) {
        return;
      }
      if (response.ok) {
        setMe((await response.json()) as Me);
      } else {
        sync();
      }
    });
    return () => {
      active = false;
    };
  }, [status, client, sync]);

  if (status === 'checking') {
    return <p className={styles.hint}>확인하는 중…</p>;
  }
  if (status === 'loggedOut') {
    return (
      <section className={styles.card}>
        <h1 className={styles.title}>스토어</h1>
        <p>
          <Link to="/login">로그인</Link> · <Link to="/signup">회원가입</Link>
        </p>
      </section>
    );
  }
  return (
    <section className={styles.card}>
      <h1 className={styles.title}>스토어</h1>
      {me && (
        <p>
          {me.name}님({me.email}) 로그인했습니다.
        </p>
      )}
      <button
        type="button"
        className={styles.button}
        onClick={async () => {
          await client.logout();
          setMe(null);
          sync();
        }}
      >
        로그아웃
      </button>
    </section>
  );
}
