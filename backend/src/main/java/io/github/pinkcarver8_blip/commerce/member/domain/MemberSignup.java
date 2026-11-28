package io.github.pinkcarver8_blip.commerce.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 이메일 인증 전의 가입 신청(작업진행.md A5-9 S13). 메일 링크의 토큰과, 신청 때 정한 비밀번호를 함께 받아야 회원이 된다.
 *
 * <p>장면: 공격자가 남의 이메일로 먼저 신청해 둬도, 메일 주인은 공격자의 비밀번호를 모르니 그 신청으로는 가입이 끝나지 않는다.
 */
@Entity
@Table(name = "member_signup")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class MemberSignup {

    /** 한 신청에 비밀번호를 이만큼 틀리면 그 신청은 쓸 수 없다(V3). */
    public static final int MAX_FAILED_ATTEMPTS = 5;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false)
    private String email;

    @Column(name = "password_hash", nullable = false, updatable = false)
    private String passwordHash;

    @Column(nullable = false, updatable = false)
    private String name;

    @Column(
            name = "token_hash",
            nullable = false,
            updatable = false,
            columnDefinition = "binary(32)")
    private byte[] tokenHash;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public MemberSignup(
            String email,
            String passwordHash,
            String name,
            byte[] tokenHash,
            Instant createdAt,
            Instant expiresAt) {
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.tokenHash = tokenHash.clone();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    public boolean isUsableAt(Instant now) {
        return now.isBefore(expiresAt) && failedAttempts < MAX_FAILED_ATTEMPTS;
    }

    public void recordFailedAttempt() {
        failedAttempts++;
    }
}
