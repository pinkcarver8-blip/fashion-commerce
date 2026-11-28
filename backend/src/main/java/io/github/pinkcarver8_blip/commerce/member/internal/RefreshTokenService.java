package io.github.pinkcarver8_blip.commerce.member.internal;

import io.github.pinkcarver8_blip.commerce.member.domain.Member;
import io.github.pinkcarver8_blip.commerce.member.domain.MemberRepository;
import io.github.pinkcarver8_blip.commerce.member.domain.RefreshToken;
import io.github.pinkcarver8_blip.commerce.member.domain.RefreshTokenRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 리프레시 토큰의 발급·회전·폐기(ADR-008 결정 3, E36).
 *
 * <ul>
 *   <li>갱신할 때마다 새 토큰으로 바꾸고 옛 토큰에 "썼다"를 남긴다(R1).
 *   <li>쓴 토큰이 다시 오면 시간과 상관없이 탈취로 보고 그 로그인 계열을 모두 폐기한다. 봐주기는 없다(R3).
 *   <li>같은 토큰으로 두 요청이 동시에 오면 조건부 UPDATE가 하나만 통과시킨다. 진 쪽은 재사용으로 처리한다(R4).
 *   <li>폐기는 거절 응답과 상관없이 저장돼야 한다. 그래서 거절을 예외가 아니라 결과 값으로 돌려준다(예외면 트랜잭션이 되돌려져 폐기가 사라진다).
 * </ul>
 */
@Service
class RefreshTokenService {

    static final Duration LIFETIME = Duration.ofDays(14);

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);

    private final RefreshTokenRepository tokens;
    private final MemberRepository members;

    RefreshTokenService(RefreshTokenRepository tokens, MemberRepository members) {
        this.tokens = tokens;
        this.members = members;
    }

    /** 로그인(가입 완료 포함) 때 새 계열을 연다. 만료는 지금부터 14일이고, 회전해도 늘어나지 않는다. */
    @Transactional
    IssuedRefreshToken issueNewFamily(Member member, Instant now) {
        return issue(member.getId(), UUID.randomUUID(), now, now.plus(LIFETIME));
    }

    @Transactional
    Optional<Rotation> rotate(String rawToken, Instant now) {
        Optional<byte[]> hash = OpaqueTokens.hashOf(rawToken);
        if (hash.isEmpty()) {
            return Optional.empty();
        }
        Optional<RefreshToken> found = tokens.findByTokenHash(hash.get());
        if (found.isEmpty()) {
            return Optional.empty();
        }
        RefreshToken token = found.get();
        if (token.getRevokedAt() != null) {
            return Optional.empty();
        }
        if (token.getUsedAt() != null) {
            revokeForReuse(token, now);
            return Optional.empty();
        }
        if (!now.isBefore(token.getExpiresAt())) {
            return Optional.empty();
        }
        if (tokens.markUsed(token.getId(), now) == 0) {
            // 읽은 뒤 다른 요청이 먼저 썼다(동시 갱신). 이것도 재사용이다.
            revokeForReuse(token, now);
            return Optional.empty();
        }
        Member member =
                members.findById(token.getMemberId())
                        .orElseThrow(() -> new IllegalStateException("토큰의 회원이 없다"));
        IssuedRefreshToken next =
                issue(token.getMemberId(), token.getFamilyId(), now, token.getExpiresAt());
        return Optional.of(new Rotation(member.getPublicId(), next));
    }

    /** 로그아웃. 모르는 토큰이어도 조용히 끝낸다(로그아웃은 언제나 성공으로 답한다). */
    @Transactional
    void revokeFamilyOf(String rawToken, Instant now) {
        OpaqueTokens.hashOf(rawToken)
                .flatMap(tokens::findByTokenHash)
                .ifPresent(token -> tokens.revokeFamily(token.getFamilyId(), now));
    }

    private void revokeForReuse(RefreshToken token, Instant now) {
        int revoked = tokens.revokeFamily(token.getFamilyId(), now);
        // 토큰 값과 회원 이메일은 남기지 않는다. 계열 ID는 무작위 값이라 개인정보가 아니다.
        log.warn(
                "이미 쓴 리프레시 토큰이 다시 왔다. 계열을 폐기했다. family={}, revoked={}",
                token.getFamilyId(),
                revoked);
    }

    private IssuedRefreshToken issue(Long memberId, UUID familyId, Instant now, Instant expiresAt) {
        String raw = OpaqueTokens.newToken();
        tokens.save(new RefreshToken(memberId, familyId, OpaqueTokens.sha256(raw), now, expiresAt));
        return new IssuedRefreshToken(raw, expiresAt);
    }

    record IssuedRefreshToken(String value, Instant expiresAt) {

        @Override
        public String toString() {
            return "IssuedRefreshToken[value=(숨김), expiresAt=" + expiresAt + "]";
        }
    }

    record Rotation(UUID memberPublicId, IssuedRefreshToken refreshToken) {}
}
