// @vitest-environment jsdom
// R1-01 F1~F4: 고객 인증 클라이언트(ADR-008 결정 2·3). 서버는 실제 규칙(쓴 리프레시 토큰이 다시 오면 로그인 계열 폐기)을 흉내 낸 가짜다.
// 실제 브라우저 두 탭 확인은 R1 첫 체크포인트 E2E에서 한다(이슈 #80 F3).
import { describe, expect, it } from 'vitest';
import { createAuthClient, type LockManagerLike } from './index';

/** 한 브라우저(쿠키 통 하나)와 서버. 탭은 이 위에 클라이언트를 여러 개 만들어 흉내 낸다. */
function fakeBrowserAndServer() {
  let cookie: string | null = null;
  let seq = 0;
  const unused = new Set<string>();
  const used = new Set<string>();
  const validAccess = new Set<string>();
  let revoked = false;
  let refreshCalls = 0;
  let inFlightRefresh = 0;
  let maxInFlightRefresh = 0;

  const json = (status: number, body: unknown) =>
    new Response(JSON.stringify(body), { status, headers: { 'Content-Type': 'application/json' } });
  const issue = () => {
    seq += 1;
    const refresh = `rt-${seq}`;
    const access = `at-${seq}`;
    unused.add(refresh);
    validAccess.add(access);
    cookie = refresh;
    return json(200, { accessToken: access, tokenType: 'Bearer', expiresIn: 900 });
  };
  const tick = () => new Promise((resolve) => setTimeout(resolve, 5));

  const fetchImpl = async (input: RequestInfo | URL, init: RequestInit = {}) => {
    const path = String(input);
    const headers = new Headers(init.headers);
    if (path.startsWith('/api/auth/')) {
      expect(headers.get('X-Requested-With')).toBe('XMLHttpRequest');
    }
    if (path === '/api/auth/login') {
      return issue();
    }
    if (path === '/api/auth/refresh') {
      refreshCalls += 1;
      inFlightRefresh += 1;
      maxInFlightRefresh = Math.max(maxInFlightRefresh, inFlightRefresh);
      // 요청이 나갈 때 브라우저가 붙인 쿠키(그 순간의 값)
      const sent = cookie;
      await tick();
      inFlightRefresh -= 1;
      if (revoked || sent === null) {
        return json(401, { code: 'REFRESH_REJECTED' });
      }
      if (used.has(sent)) {
        revoked = true;
        cookie = null;
        validAccess.clear();
        return json(401, { code: 'REFRESH_REJECTED' });
      }
      unused.delete(sent);
      used.add(sent);
      return issue();
    }
    if (path === '/api/auth/logout') {
      revoked = true;
      cookie = null;
      return new Response(null, { status: 204 });
    }
    if (path === '/api/members/me') {
      const token = headers.get('Authorization')?.replace('Bearer ', '') ?? '';
      return validAccess.has(token) ? json(200, { email: 'me@example.com' }) : json(401, {});
    }
    return json(404, {});
  };

  return {
    fetch: fetchImpl as typeof fetch,
    expireAllAccessTokens: () => validAccess.clear(),
    get cookie() {
      return cookie;
    },
    get revoked() {
      return revoked;
    },
    get refreshCalls() {
      return refreshCalls;
    },
    get maxInFlightRefresh() {
      return maxInFlightRefresh;
    },
  };
}

/** 브라우저 Web Locks처럼 같은 이름의 잠금은 한 번에 하나만 잡힌다. */
function fakeLocks(): LockManagerLike {
  const tails = new Map<string, Promise<unknown>>();
  return {
    request<T>(name: string, callback: () => Promise<T>): Promise<T> {
      const previous = tails.get(name) ?? Promise.resolve();
      const run = previous.then(callback, callback);
      tails.set(
        name,
        run.catch(() => undefined),
      );
      return run;
    },
  };
}

describe('R1-01 고객 인증 클라이언트', () => {
  it('F1: 액세스 토큰은 메모리에만 있고 브라우저 저장소와 쿠키에 없다', async () => {
    const server = fakeBrowserAndServer();
    const client = createAuthClient({ fetch: server.fetch, locks: fakeLocks() });

    await client.login('me@example.com', 'blue coffee rain');

    expect(client.isLoggedIn()).toBe(true);
    // eslint-disable-next-line no-restricted-syntax -- 시험: 저장소가 비어 있는지 확인만 한다
    expect(window.localStorage.length).toBe(0);
    // eslint-disable-next-line no-restricted-syntax -- 시험: 저장소가 비어 있는지 확인만 한다
    expect(window.sessionStorage.length).toBe(0);
    expect(document.cookie).not.toContain('at-');
  });

  it('F2: 401이면 한 번 갱신하고 원래 요청을 다시 보낸다', async () => {
    const server = fakeBrowserAndServer();
    const client = createAuthClient({ fetch: server.fetch, locks: fakeLocks() });
    await client.login('me@example.com', 'pw');
    server.expireAllAccessTokens();

    const response = await client.request('/api/members/me');

    expect(response.status).toBe(200);
    expect(server.refreshCalls).toBe(1);
  });

  it('F2: 갱신도 실패하면 로그아웃 상태가 되고 401을 그대로 돌려준다', async () => {
    const server = fakeBrowserAndServer();
    const changes: boolean[] = [];
    const client = createAuthClient({
      fetch: server.fetch,
      locks: fakeLocks(),
      onChange: (loggedIn) => changes.push(loggedIn),
    });
    await client.login('me@example.com', 'pw');
    server.expireAllAccessTokens();
    // 다른 곳(예: 다른 탭의 로그아웃)에서 로그인 계열이 이미 끊긴 상황
    await server.fetch('/api/auth/logout', {
      method: 'POST',
      headers: { 'X-Requested-With': 'XMLHttpRequest' },
    });

    const response = await client.request('/api/members/me');

    expect(response.status).toBe(401);
    expect(client.isLoggedIn()).toBe(false);
    expect(changes.at(-1)).toBe(false);
  });

  it('F2: 한 탭에서 401이 여러 개 동시에 나도 갱신은 한 번만 보낸다', async () => {
    const server = fakeBrowserAndServer();
    const client = createAuthClient({ fetch: server.fetch, locks: fakeLocks() });
    await client.login('me@example.com', 'pw');
    server.expireAllAccessTokens();

    const responses = await Promise.all([
      client.request('/api/members/me'),
      client.request('/api/members/me'),
      client.request('/api/members/me'),
    ]);

    expect(responses.map((r) => r.status)).toEqual([200, 200, 200]);
    expect(server.refreshCalls).toBe(1);
  });

  it('F3: 두 탭이 동시에 401을 받아도 갱신은 차례로 나가고 로그인이 끊기지 않는다', async () => {
    const server = fakeBrowserAndServer();
    const locks = fakeLocks();
    const tabA = createAuthClient({ fetch: server.fetch, locks });
    await tabA.login('me@example.com', 'pw');
    const tabB = createAuthClient({ fetch: server.fetch, locks });
    await tabB.restore();
    server.expireAllAccessTokens();

    const [a, b] = await Promise.all([
      tabA.request('/api/members/me'),
      tabB.request('/api/members/me'),
    ]);

    expect([a.status, b.status]).toEqual([200, 200]);
    expect(server.maxInFlightRefresh).toBe(1);
    expect(server.revoked).toBe(false);
  });

  it('F3(대조): 잠금이 없으면 두 탭의 갱신이 겹쳐 서버가 재사용으로 보고 로그인을 끊는다', async () => {
    const server = fakeBrowserAndServer();
    const tabA = createAuthClient({ fetch: server.fetch, locks: null });
    await tabA.login('me@example.com', 'pw');
    const tabB = createAuthClient({ fetch: server.fetch, locks: null });
    server.expireAllAccessTokens();

    await Promise.all([tabA.request('/api/members/me'), tabB.restore()]);

    expect(server.maxInFlightRefresh).toBe(2);
    expect(server.revoked).toBe(true);
  });

  it('F4: 새로고침 뒤에도 리프레시 쿠키로 로그인이 이어지고, 쿠키가 없으면 로그아웃 상태다', async () => {
    const server = fakeBrowserAndServer();
    await createAuthClient({ fetch: server.fetch, locks: fakeLocks() }).login(
      'me@example.com',
      'pw',
    );

    const reloaded = createAuthClient({ fetch: server.fetch, locks: fakeLocks() });
    expect(reloaded.isLoggedIn()).toBe(false);
    expect(await reloaded.restore()).toBe(true);
    expect((await reloaded.request('/api/members/me')).status).toBe(200);

    const stranger = fakeBrowserAndServer();
    const fresh = createAuthClient({ fetch: stranger.fetch, locks: fakeLocks() });
    expect(await fresh.restore()).toBe(false);
  });
});
