import { ApiError } from '@commerce/api-client/store';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import styles from './Form.module.css';

// 메일 링크로 가입 마치기(R1-01 V2·V3). 토큰은 주소의 # 뒤에 온다(서버로 보내지지 않아 접속 기록에 남지 않는다).
// 읽자마자 주소창에서 지운다. 브라우저 방문 기록과 화면 공유에 토큰이 남지 않게 한다.
function takeTokenFromHash(): string {
  const params = new URLSearchParams(window.location.hash.replace(/^#/, ''));
  const token = params.get('token') ?? '';
  if (window.location.hash) {
    window.history.replaceState(null, '', window.location.pathname + window.location.search);
  }
  return token;
}

export function SignupCompletePage() {
  const { client, sync } = useAuth();
  const navigate = useNavigate();
  const [token] = useState(takeTokenFromHash);
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  if (!token) {
    return (
      <section className={styles.card}>
        <h1 className={styles.title}>인증 링크가 없습니다</h1>
        <p>메일의 링크를 다시 열어 주세요.</p>
        <Link to="/signup">다시 가입하기</Link>
      </section>
    );
  }

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSending(true);
    try {
      await client.completeSignup(token, password);
      sync();
      navigate('/', { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '가입을 마치지 못했습니다');
    } finally {
      setSending(false);
    }
  };

  return (
    <form className={styles.card} onSubmit={submit} noValidate>
      <h1 className={styles.title}>가입 마치기</h1>
      <p>가입을 신청할 때 정한 비밀번호를 넣어 주세요.</p>
      <label className={styles.field}>
        비밀번호
        <input
          type="password"
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          required
        />
      </label>
      {error && (
        <p role="alert" className={styles.error}>
          {error}
        </p>
      )}
      <button type="submit" className={styles.button} disabled={sending}>
        가입 마치기
      </button>
    </form>
  );
}
