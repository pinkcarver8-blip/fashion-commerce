// 고객 인증 클라이언트(R1-01, ADR-008 결정 2·3).
// - 액세스 토큰은 이 객체 안의 변수(메모리)에만 둔다. localStorage·sessionStorage·쿠키에 두지 않는다(F1).
// - 리프레시 토큰은 서버가 HttpOnly 쿠키로 준다. 자바스크립트는 그 값을 볼 수 없고, 브라우저가 /api/auth 요청에만 붙인다.
// - API가 401을 주면 한 번 갱신하고 원래 요청을 다시 보낸다. 갱신도 실패하면 로그아웃 상태로 바꾼다(F2).
// - 여러 탭이 동시에 갱신하면 브라우저의 Web Locks로 한 번에 한 탭만 갱신한다. 기다린 탭은 앞 탭이 바꾼 새 쿠키로 갱신한다(F3).
//   서버는 이미 쓴 리프레시 토큰이 다시 오면 봐주지 않고 로그인을 끊는다. 그래서 차례가 꼭 필요하다.

/** 브라우저 Web Locks 중 이 모듈이 쓰는 부분. 시험에서는 가짜를 넣는다. */
export interface LockManagerLike {
  request<T>(name: string, callback: () => Promise<T>): Promise<T>;
}

export interface AuthClientOptions {
  /** 기본값: 브라우저 fetch. 같은 출처(/api)로만 보낸다(ADR-008 결정 4). */
  fetch?: typeof fetch;
  /** 기본값: navigator.locks. 없는 브라우저에서는 차례 없이 갱신한다(두 탭이면 로그아웃될 수 있다. 안전한 쪽으로 실패). */
  locks?: LockManagerLike | null;
  /** 로그인 상태가 바뀔 때 부른다(화면이 다시 그리도록). */
  onChange?: (loggedIn: boolean) => void;
}

/** 서버 오류 응답(RFC 9457 + code, ADR-010). 화면에는 message만 보여 준다. */
export class ApiError extends Error {
  readonly status: number;
  readonly code: string;

  constructor(status: number, code: string, message: string) {
    super(message);
    this.name = 'ApiError';
    this.status = status;
    this.code = code;
  }
}

const REFRESH_LOCK = 'commerce-store-refresh';
const AUTH_HEADERS = { 'Content-Type': 'application/json', 'X-Requested-With': 'XMLHttpRequest' };

export interface AuthClient {
  isLoggedIn(): boolean;
  /** 가입 신청. 새 이메일이든 이미 회원이든 같은 답이다(서버 G5). */
  requestSignup(email: string, password: string, name: string): Promise<string>;
  /** 메일 링크의 토큰 + 가입 때 정한 비밀번호로 가입을 마치고 로그인한다. */
  completeSignup(token: string, password: string): Promise<void>;
  login(email: string, password: string): Promise<void>;
  logout(): Promise<void>;
  /** 새로고침 직후: 리프레시 쿠키로 액세스 토큰을 다시 받는다(F4). 쿠키가 없으면 로그아웃 상태로 끝난다. */
  restore(): Promise<boolean>;
  /** 로그인이 필요한 API 요청. 401이면 한 번 갱신하고 다시 보낸다. */
  request(path: string, init?: RequestInit): Promise<Response>;
}

export function createAuthClient(options: AuthClientOptions = {}): AuthClient {
  const doFetch = options.fetch ?? ((input, init) => fetch(input, init));
  const locks =
    options.locks === undefined
      ? typeof navigator !== 'undefined' && 'locks' in navigator
        ? (navigator.locks as unknown as LockManagerLike)
        : null
      : options.locks;
  let accessToken: string | null = null;
  // 같은 탭 안에서 401이 여러 개 동시에 나도 갱신은 한 번만 보낸다.
  let refreshing: Promise<boolean> | null = null;

  const setToken = (token: string | null) => {
    const before = accessToken !== null;
    accessToken = token;
    if (before !== (token !== null)) {
      options.onChange?.(token !== null);
    }
  };

  const postAuth = (path: string, body?: unknown) =>
    doFetch(path, {
      method: 'POST',
      headers: AUTH_HEADERS,
      credentials: 'same-origin',
      body: body === undefined ? undefined : JSON.stringify(body),
    });

  const takeToken = async (response: Response) => {
    if (!response.ok) {
      throw await toError(response);
    }
    const body = (await response.json()) as { accessToken: string };
    setToken(body.accessToken);
  };

  const refreshNow = async (): Promise<boolean> => {
    const response = await postAuth('/api/auth/refresh');
    if (!response.ok) {
      setToken(null);
      return false;
    }
    await takeToken(response);
    return true;
  };

  const refresh = (): Promise<boolean> => {
    if (!refreshing) {
      const run = locks ? locks.request(REFRESH_LOCK, refreshNow) : refreshNow();
      refreshing = run.finally(() => {
        refreshing = null;
      });
    }
    return refreshing;
  };

  const withToken = (init: RequestInit = {}): RequestInit => {
    const headers = new Headers(init.headers);
    if (accessToken) {
      headers.set('Authorization', `Bearer ${accessToken}`);
    }
    return { ...init, headers, credentials: 'same-origin' };
  };

  return {
    isLoggedIn: () => accessToken !== null,

    async requestSignup(email, password, name) {
      const response = await postAuth('/api/auth/signup', { email, password, name });
      if (!response.ok) {
        throw await toError(response);
      }
      const body = (await response.json()) as { message: string };
      return body.message;
    },

    async completeSignup(token, password) {
      await takeToken(await postAuth('/api/auth/signup/complete', { token, password }));
    },

    async login(email, password) {
      await takeToken(await postAuth('/api/auth/login', { email, password }));
    },

    async logout() {
      try {
        await postAuth('/api/auth/logout');
      } finally {
        setToken(null);
      }
    },

    restore: () => refresh(),

    async request(path, init) {
      const first = await doFetch(path, withToken(init));
      if (first.status !== 401) {
        return first;
      }
      if (!(await refresh())) {
        return first;
      }
      return doFetch(path, withToken(init));
    },
  };
}

async function toError(response: Response): Promise<ApiError> {
  try {
    const body = (await response.json()) as { code?: string; detail?: string; title?: string };
    return new ApiError(
      response.status,
      body.code ?? 'UNKNOWN',
      body.detail ?? body.title ?? '요청을 처리하지 못했습니다',
    );
  } catch {
    return new ApiError(response.status, 'UNKNOWN', '요청을 처리하지 못했습니다');
  }
}
