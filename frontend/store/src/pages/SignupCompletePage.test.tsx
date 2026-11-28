// @vitest-environment jsdom
// R1-01 V2: 메일 링크의 토큰(주소의 # 뒤)을 읽어 가입을 마친다. 읽자마자 주소창에서 지운다.
import type { AuthClient } from '@commerce/api-client/store';
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { afterEach, describe, expect, it } from 'vitest';
import { AuthProvider } from '../auth/AuthContext';
import { SignupCompletePage } from './SignupCompletePage';

function fakeClient(calls: Array<[string, string]>): AuthClient {
  let loggedIn = false;
  return {
    isLoggedIn: () => loggedIn,
    requestSignup: async () => '',
    completeSignup: async (token, password) => {
      calls.push([token, password]);
      loggedIn = true;
    },
    login: async () => undefined,
    logout: async () => undefined,
    restore: async () => false,
    request: async () => new Response(null, { status: 401 }),
  };
}

describe('R1-01 가입 마치기 화면', () => {
  afterEach(cleanup);

  it('V2: 주소의 토큰과 입력한 비밀번호로 가입을 마치고, 토큰은 주소창에서 지운다', async () => {
    window.history.replaceState(null, '', '/signup/complete#token=abcDEF123');
    const calls: Array<[string, string]> = [];

    render(
      <AuthProvider client={fakeClient(calls)}>
        <MemoryRouter>
          <SignupCompletePage />
        </MemoryRouter>
      </AuthProvider>,
    );

    expect(window.location.hash).toBe('');
    fireEvent.change(screen.getByLabelText('비밀번호'), { target: { value: 'blue coffee rain' } });
    fireEvent.click(screen.getByRole('button', { name: '가입 마치기' }));
    await waitFor(() => expect(calls).toEqual([['abcDEF123', 'blue coffee rain']]));
  });

  it('토큰이 없으면 다시 가입하라고 안내한다', () => {
    window.history.replaceState(null, '', '/signup/complete');

    render(
      <AuthProvider client={fakeClient([])}>
        <MemoryRouter>
          <SignupCompletePage />
        </MemoryRouter>
      </AuthProvider>,
    );

    expect(screen.getByRole('heading', { name: '인증 링크가 없습니다' })).toBeTruthy();
  });
});
