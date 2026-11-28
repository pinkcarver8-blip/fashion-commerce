import { BrowserRouter, Route, Routes } from 'react-router';
import styles from './App.module.css';
import { AuthProvider } from './auth/AuthContext';
import { HomePage } from './pages/HomePage';
import { LoginPage } from './pages/LoginPage';
import { SignupCompletePage } from './pages/SignupCompletePage';
import { SignupPage } from './pages/SignupPage';

// 고객 앱(store). 모바일 우선(A8). 상단에 데모 배너를 늘 보여 준다(A5-10).
export function App() {
  return (
    <main className={styles.page}>
      <p className={styles.banner} role="note">
        포트폴리오 데모입니다. 실제 판매·결제는 없습니다.
      </p>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/" element={<HomePage />} />
            <Route path="/signup" element={<SignupPage />} />
            <Route path="/signup/complete" element={<SignupCompletePage />} />
            <Route path="/login" element={<LoginPage />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </main>
  );
}
