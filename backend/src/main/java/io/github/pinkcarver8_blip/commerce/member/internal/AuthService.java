package io.github.pinkcarver8_blip.commerce.member.internal;

import io.github.pinkcarver8_blip.commerce.member.domain.Member;
import io.github.pinkcarver8_blip.commerce.member.domain.MemberRepository;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * 로그인·갱신·로그아웃(ADR-008 결정 2·3·7). 웹 계층은 이 클래스만 부른다.
 *
 * <p>로그인 실패는 없는 이메일과 틀린 비밀번호를 같은 답·비슷한 시간으로 준다(L2). 없는 이메일도 미리 만든 해시와 비교해 bcrypt 시간을 똑같이 쓴다.
 */
@Service
public class AuthService {

    private final MemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final LoginAttemptLimiter limiter;
    private final AccessTokenIssuer accessTokens;
    private final RefreshTokenService refreshTokens;

    /** 없는 이메일로 로그인할 때 비교할 해시. 시작할 때 한 번 만든다. 아무 비밀번호와도 맞지 않는다. */
    private final String dummyHash;

    AuthService(
            MemberRepository members,
            PasswordEncoder passwordEncoder,
            LoginAttemptLimiter limiter,
            AccessTokenIssuer accessTokens,
            RefreshTokenService refreshTokens) {
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.limiter = limiter;
        this.accessTokens = accessTokens;
        this.refreshTokens = refreshTokens;
        this.dummyHash = passwordEncoder.encode(OpaqueTokens.newToken());
    }

    public AuthSession login(String rawEmail, String password, String clientIp) {
        if (limiter.isBlocked(rawEmail, clientIp)) {
            throw new MemberException(MemberErrorCode.LOGIN_TEMPORARILY_BLOCKED);
        }
        Member member =
                EmailAddresses.normalize(rawEmail).flatMap(members::findByEmail).orElse(null);
        if (!passwordMatches(password, member)) {
            limiter.recordFailure(rawEmail, clientIp);
            throw new MemberException(MemberErrorCode.LOGIN_FAILED);
        }
        limiter.reset(rawEmail, clientIp);
        return startSession(member);
    }

    public AuthSession refresh(String rawRefreshToken) {
        Instant now = Instant.now();
        RefreshTokenService.Rotation rotation =
                refreshTokens
                        .rotate(rawRefreshToken, now)
                        .orElseThrow(() -> new MemberException(MemberErrorCode.REFRESH_REJECTED));
        AccessTokenIssuer.IssuedAccessToken access =
                accessTokens.issue(rotation.memberPublicId(), now);
        return new AuthSession(
                access.value(),
                access.expiresInSeconds(),
                rotation.refreshToken().value(),
                rotation.refreshToken().expiresAt());
    }

    public void logout(String rawRefreshToken) {
        refreshTokens.revokeFamilyOf(rawRefreshToken, Instant.now());
    }

    /** 가입 완료(SignupService)와 로그인이 같이 쓴다. */
    AuthSession startSession(Member member) {
        Instant now = Instant.now();
        AccessTokenIssuer.IssuedAccessToken access = accessTokens.issue(member.getPublicId(), now);
        RefreshTokenService.IssuedRefreshToken refresh = refreshTokens.issueNewFamily(member, now);
        return new AuthSession(
                access.value(), access.expiresInSeconds(), refresh.value(), refresh.expiresAt());
    }

    public MemberView me(UUID memberPublicId) {
        return members.findByPublicId(memberPublicId)
                .map(member -> new MemberView(member.getEmail(), member.getName()))
                .orElseThrow(() -> new MemberException(MemberErrorCode.MEMBER_NOT_FOUND));
    }

    private boolean passwordMatches(String password, Member member) {
        // bcrypt는 72바이트를 넘는 비밀번호에 오류를 낸다. 그런 비밀번호로는 가입할 수 없으니 실패로 본다(시간은 똑같이 쓴다).
        boolean tooLong = PasswordPolicy.utf8Bytes(password) > PasswordPolicy.MAX_UTF8_BYTES;
        String candidate = tooLong ? "" : password;
        if (member == null || tooLong) {
            passwordEncoder.matches(candidate, dummyHash);
            return false;
        }
        return passwordEncoder.matches(candidate, member.getPasswordHash());
    }

    public record MemberView(String email, String name) {}
}
