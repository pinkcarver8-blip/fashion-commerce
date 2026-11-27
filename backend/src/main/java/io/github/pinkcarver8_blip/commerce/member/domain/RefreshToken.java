package io.github.pinkcarver8_blip.commerce.member.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 리프레시 토큰 한 개(ADR-008 결정 3). 값은 해시만 둔다. 쓴 표시와 폐기는 조건부 UPDATE로만 한다(RefreshTokenRepository).
 *
 * <p>한 번 로그인에서 회전으로 이어진 토큰들은 {@code familyId}가 같고 만료 시각도 같다(로그인 후 14일).
 */
@Entity
@Table(name = "member_refresh_token")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "member_id", nullable = false, updatable = false)
    private Long memberId;

    @Column(
            name = "family_id",
            nullable = false,
            updatable = false,
            columnDefinition = "binary(16)")
    private UUID familyId;

    @Column(
            name = "token_hash",
            nullable = false,
            updatable = false,
            columnDefinition = "binary(32)")
    private byte[] tokenHash;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private Instant expiresAt;

    @Column(name = "used_at", insertable = false, updatable = false)
    private Instant usedAt;

    @Column(name = "revoked_at", insertable = false, updatable = false)
    private Instant revokedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public RefreshToken(
            Long memberId, UUID familyId, byte[] tokenHash, Instant createdAt, Instant expiresAt) {
        this.memberId = memberId;
        this.familyId = familyId;
        this.tokenHash = tokenHash.clone();
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }
}
