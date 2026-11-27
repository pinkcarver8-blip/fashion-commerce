import { ApiError } from '@commerce/api-client/store';
import { useState, type FormEvent } from 'react';
import { Link } from 'react-router';
import { useAuth } from '../auth/AuthContext';
import styles from './Form.module.css';

// 가입 신청(R1-01). 보내면 언제나 "인증 메일을 보냈습니다"가 나온다. 이미 회원인 이메일도 같다(회원 여부를 알려 주지 않는다).
export function SignupPage() {
  const { client } = useAuth();
  const [email, setEmail] = useState('');
  const [name, setName] = useState('');
  const [password, setPassword] = useState('');
  const [message, setMessage] = useState<string | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [sending, setSending] = useState(false);
  const length = [...password].length;

  const submit = async (event: FormEvent) => {
    event.preventDefault();
    setError(null);
    setSending(true);
    try {
      setMessage(await client.requestSignup(email, password, name));
      setPassword('');
    } catch (e) {
      setError(e instanceof ApiError ? e.message : '가입 신청을 보내지 못했습니다');
    } finally {
      setSending(false);
    }
  };

  if (message) {
    return (
      <section className={styles.card}>
        <h1 className={styles.title}>메일을 확인해 주세요</h1>
        <p role="status">{message}</p>
        <p className={styles.hint}>
          로컬 실행에서는 가짜 메일함(http://localhost:8025)에서 메일을 봅니다.
        </p>
      </section>
    );
  }

  return (
    <form className={styles.card} onSubmit={submit} noValidate>
      <h1 className={styles.title}>회원가입</h1>
      <label className={styles.field}>
        이메일
        <input
          type="email"
          autoComplete="email"
          value={email}
          onChange={(e) => setEmail(e.target.value)}
          required
        />
      </label>
      <label className={styles.field}>
        이름
        <input
          autoComplete="name"
          maxLength={50}
          value={name}
          onChange={(e) => setName(e.target.value)}
          required
        />
      </label>
      <label className={styles.field}>
        비밀번호
        <input
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          aria-describedby="password-rule"
          required
        />
      </label>
      <p id="password-rule" className={styles.hint}>
        8자 이상({length}자). 대문자·숫자·특수문자를 섞지 않아도 됩니다. 많이 쓰이는 비밀번호,
        이메일 아이디가 든 비밀번호는 쓸 수 없습니다.
      </p>
      {error && (
        <p role="alert" className={styles.error}>
          {error}
        </p>
      )}
      <button type="submit" className={styles.button} disabled={sending}>
        가입 신청
      </button>
      <p className={styles.hint}>
        이미 회원이면 <Link to="/login">로그인</Link>
      </p>
    </form>
  );
}
