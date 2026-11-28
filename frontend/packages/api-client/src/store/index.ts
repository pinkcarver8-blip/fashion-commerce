// 고객 앱(store)용 API 클라이언트(ADR-009). store는 이 진입점만 쓴다. 관리자용(../admin)을 가져오지 않는다.
export { ApiError, createAuthClient } from './auth';
export type { AuthClient, AuthClientOptions, LockManagerLike } from './auth';
