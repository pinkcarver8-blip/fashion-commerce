import { ApiError } from '@commerce/api-client/store';
import { useState, type FormEvent } from 'react';
import { Link, useNavigate } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import styles from './Form.module.css';

// 로그인(R1-01). 실패 이유(없는 이메일·틀린 비밀번호)는 서버가 나누지 않는다. 화면도 서버 문장을 그대로 보여 준다.
export function LoginPage() {
  const { client, sync } = useAuth();
  const navigate = useNavigate();
  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSending(true);
    try {
      await client.login(email, password);
      sync();
      navigate('/', { replace: true });
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '로그인하지 못했습니다');
      setPassword('');
    } finally {
      setSending(false);
    }
  };

  return (
    <form className={styles.card} onSubmit={submit} noValidate>
      <h1 className={styles.title}>로그인</h1>
      <label className={styles.field}>
        이메일
        <input
          type="email"
          autoComplete="username"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
      </label>
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
        로그인
      </button>
      <p className={styles.hint}>
        처음이면 <Link to="/signup">회원가입</Link>
      </p>
    </form>
  );
}
