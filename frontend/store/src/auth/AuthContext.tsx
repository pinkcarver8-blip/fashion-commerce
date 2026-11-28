import { createAuthClient, type AuthClient } from '@commerce/api-client/store';
import { createContext, useCallback, useContext, useEffect, useState, type ReactNode } from 'react';

// 로그인 상태를 화면 전체에 알려 준다(R1-01). 토큰 값은 이 파일도 모른다. api-client 안의 메모리에만 있다(ADR-008).
type Status = 'checking' | 'loggedIn' | 'loggedOut';

interface AuthState {
  status: Status;
  client: AuthClient;
  /** 로그인·로그아웃 뒤 화면 상태를 클라이언트에 맞춘다. */
  sync: () => void;
}

const AuthContext = createContext<AuthState | null>(null);

export function AuthProvider({ client, children }: { client?: AuthClient; children: ReactNode }) {
  const [status, setStatus] = useState<Status>('checking');
  // 클라이언트는 처음 한 번만 만든다. 다시 만들면 메모리의 토큰을 잃는다.
  const [authClient] = useState<AuthClient>(
    () =>
      client ??
      createAuthClient({
        onChange: (loggedIn) => setStatus(loggedIn ? 'loggedIn' : 'loggedOut'),
      }),
  );
  const sync = useCallback(
    () => setStatus(authClient.isLoggedIn() ? 'loggedIn' : 'loggedOut'),
    [authClient],
  );

  useEffect(() => {
    let active = true;
    // 새로고침 직후: 리프레시 쿠키로 로그인을 이어 간다(F4). 같은 탭의 겹친 호출은 클라이언트가 한 번으로 합친다.
    // 연결이 끊겨 실패해도 "확인하는 중"에 멈추지 않고 로그아웃 상태로 보여 준다.
    authClient
      .restore()
      .catch(() => false)
      .then(() => {
        if (active) {
          sync();
        }
      });
    return () => {
      active = false;
    };
  }, [authClient, sync]);

  return (
    <AuthContext.Provider value={{ status, client: authClient, sync }}>
      {children}
    </AuthContext.Provider>
  );
}

export function useAuth(): AuthState {
  const state = useContext(AuthContext);
  if (!state) {
    throw new Error('AuthProvider 안에서만 쓴다');
  }
  return state;
}
