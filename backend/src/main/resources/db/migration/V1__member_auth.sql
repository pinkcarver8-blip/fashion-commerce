-- R1-01 고객 회원·인증(ADR-008, 작업진행.md A5-9 S4·S13). 앞으로만 추가한다(A5-4).
-- 시각은 모두 UTC(DATETIME(6), ADR-010). 이메일은 앱이 소문자로 맞춘 값을 그대로 비교한다(utf8mb4_bin).
-- 기본 정렬 규칙(utf8mb4_0900_ai_ci)은 대소문자·악센트를 무시해 'josé'와 'jose'를 같은 값으로 본다.

-- 회원. public_id는 밖에 보이는 회원 ID(UUIDv4)다. 순차 PK(id)는 토큰·응답에 내보내지 않는다(A5-9 S12, ADR-010).
CREATE TABLE member (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    public_id     BINARY(16)   NOT NULL,
    email         VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    password_hash VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name          VARCHAR(50)  NOT NULL,
    created_at    DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_public_id UNIQUE (public_id),
    CONSTRAINT uk_member_email UNIQUE (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 가입 신청(이메일 인증 전). 메일의 링크를 연 사람이 이 신청의 비밀번호를 다시 넣어야 회원 행이 생긴다(S13).
-- 토큰은 SHA-256 해시만 둔다. 같은 이메일로 여러 신청이 있을 수 있고, 가입이 끝나면 그 이메일의 신청을 모두 지운다.
CREATE TABLE member_signup (
    id              BIGINT       NOT NULL AUTO_INCREMENT,
    email           VARCHAR(254) CHARACTER SET utf8mb4 COLLATE utf8mb4_bin NOT NULL,
    password_hash   VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    name            VARCHAR(50)  NOT NULL,
    token_hash      BINARY(32)   NOT NULL,
    failed_attempts INT          NOT NULL DEFAULT 0,
    expires_at      DATETIME(6)  NOT NULL,
    created_at      DATETIME(6)  NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_signup_token_hash UNIQUE (token_hash),
    CONSTRAINT ck_member_signup_failed_attempts CHECK (failed_attempts >= 0),
    INDEX ix_member_signup_email (email)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;

-- 리프레시 토큰(ADR-008 결정 3). 값은 SHA-256 해시만 둔다. 한 번 로그인에서 나온 토큰들은 family_id가 같다.
-- 쓴 토큰(used_at)이 다시 오면 같은 family_id를 모두 폐기(revoked_at)한다. 만료 시각은 계열 전체가 같다(로그인 후 14일).
CREATE TABLE member_refresh_token (
    id         BIGINT      NOT NULL AUTO_INCREMENT,
    member_id  BIGINT      NOT NULL,
    family_id  BINARY(16)  NOT NULL,
    token_hash BINARY(32)  NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    used_at    DATETIME(6) NULL,
    revoked_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_member_refresh_token_hash UNIQUE (token_hash),
    CONSTRAINT fk_member_refresh_token_member FOREIGN KEY (member_id) REFERENCES member (id),
    INDEX ix_member_refresh_token_family (family_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COLLATE = utf8mb4_0900_ai_ci;
