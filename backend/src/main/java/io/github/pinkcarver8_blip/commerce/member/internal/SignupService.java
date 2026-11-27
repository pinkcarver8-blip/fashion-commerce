package io.github.pinkcarver8_blip.commerce.member.internal;

import io.github.pinkcarver8_blip.commerce.member.domain.Member;
import io.github.pinkcarver8_blip.commerce.member.domain.MemberRepository;
import io.github.pinkcarver8_blip.commerce.member.domain.MemberSignup;
import io.github.pinkcarver8_blip.commerce.member.domain.MemberSignupRepository;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 가입 신청과 이메일 인증으로 가입 완료(작업진행.md A5-9 S13, 2026-11-28 사용자 결정).
 *
 * <ol>
 *   <li>신청: 새 이메일이든 이미 회원인 이메일이든 같은 답을 준다. 새 이메일이면 인증 링크, 회원이면 "이미 가입" 메일을 보낸다. 두 경우 모두 비밀번호 해시를
 *       계산해 응답 시간으로 구별되지 않게 한다(G5).
 *   <li>완료: 링크의 토큰과 신청 때 정한 비밀번호가 둘 다 맞아야 회원 행이 생긴다(V2·V3). 그 이메일의 다른 신청은 지운다.
 * </ol>
 *
 * <p>메일은 DB 트랜잭션이 끝난 뒤 보낸다(외부 호출은 트랜잭션 밖, backend 규칙).
 */
@Service
public class SignupService {

    static final Duration LINK_LIFETIME = Duration.ofHours(24);
    private static final int MAX_NAME_CHARACTERS = 50;

    private final MemberRepository members;
    private final MemberSignupRepository signups;
    private final PasswordEncoder passwordEncoder;
    private final PasswordPolicy passwordPolicy;
    private final SignupLimiter limiter;
    private final SignupMailer mailer;
    private final AuthService authService;
    private final TransactionTemplate transaction;

    SignupService(
            MemberRepository members,
            MemberSignupRepository signups,
            PasswordEncoder passwordEncoder,
            PasswordPolicy passwordPolicy,
            SignupLimiter limiter,
            SignupMailer mailer,
            AuthService authService,
            TransactionTemplate transaction) {
        this.members = members;
        this.signups = signups;
        this.passwordEncoder = passwordEncoder;
        this.passwordPolicy = passwordPolicy;
        this.limiter = limiter;
        this.mailer = mailer;
        this.authService = authService;
        this.transaction = transaction;
    }

    public void requestSignup(String rawEmail, String password, String rawName, String clientIp) {
        String email =
                EmailAddresses.normalize(rawEmail)
                        .orElseThrow(() -> new MemberException(MemberErrorCode.INVALID_EMAIL));
        String name = normalizeName(rawName);
        passwordPolicy
                .check(password, email)
                .ifPresent(
                        problem -> {
                            throw new MemberException(problem);
                        });
        if (!limiter.allowRequestFrom(clientIp)) {
            throw new MemberException(MemberErrorCode.SIGNUP_RATE_LIMITED);
        }
        // 회원 여부와 상관없이 언제나 계산한다(시간으로 구별되지 않게).
        String passwordHash = passwordEncoder.encode(password);
        if (!limiter.allowMailTo(email)) {
            return;
        }
        Optional<String> verificationToken =
                transaction.execute(
                        status -> {
                            if (members.existsByEmail(email)) {
                                return Optional.<String>empty();
                            }
                            Instant now = Instant.now();
                            String token = OpaqueTokens.newToken();
                            signups.save(
                                    new MemberSignup(
                                            email,
                                            passwordHash,
                                            name,
                                            OpaqueTokens.sha256(token),
                                            now,
                                            now.plus(LINK_LIFETIME)));
                            return Optional.of(token);
                        });
        if (verificationToken.isPresent()) {
            mailer.sendVerification(email, verificationToken.get());
        } else {
            mailer.sendAlreadyRegistered(email);
        }
    }

    public AuthSession completeSignup(String token, String password) {
        byte[] tokenHash =
                OpaqueTokens.hashOf(token)
                        .orElseThrow(
                                () -> new MemberException(MemberErrorCode.SIGNUP_LINK_INVALID));
        Completion completion;
        try {
            completion = transaction.execute(status -> complete(tokenHash, password));
        } catch (DataIntegrityViolationException e) {
            // 같은 이메일의 다른 신청이 방금 먼저 끝났다(이메일 유니크 제약).
            throw new MemberException(MemberErrorCode.SIGNUP_ALREADY_COMPLETED);
        }
        if (completion.problem() != null) {
            throw new MemberException(completion.problem());
        }
        return authService.startSession(completion.member());
    }

    /**
     * 틀린 비밀번호 횟수를 저장해야 해서, 실패를 예외 대신 결과 값으로 돌려준다(예외면 트랜잭션이 되돌려진다). 신청 행은 잠그고 본다. 같은 링크로 동시에 보내도 횟수가
     * 빠지거나 회원이 둘 생기지 않는다.
     */
    private Completion complete(byte[] tokenHash, String password) {
        Instant now = Instant.now();
        Optional<MemberSignup> found = signups.findByTokenHashForUpdate(tokenHash);
        if (found.isEmpty() || !found.get().isUsableAt(now)) {
            return Completion.failed(MemberErrorCode.SIGNUP_LINK_INVALID);
        }
        MemberSignup signup = found.get();
        boolean tooLong = PasswordPolicy.utf8Bytes(password) > PasswordPolicy.MAX_UTF8_BYTES;
        if (tooLong || !passwordEncoder.matches(password, signup.getPasswordHash())) {
            signup.recordFailedAttempt();
            return Completion.failed(MemberErrorCode.SIGNUP_PASSWORD_MISMATCH);
        }
        if (members.existsByEmail(signup.getEmail())) {
            signups.deleteAllByEmail(signup.getEmail());
            return Completion.failed(MemberErrorCode.SIGNUP_ALREADY_COMPLETED);
        }
        Member member =
                members.saveAndFlush(
                        new Member(
                                signup.getEmail(),
                                signup.getPasswordHash(),
                                signup.getName(),
                                now));
        signups.deleteAllByEmail(signup.getEmail());
        return new Completion(member, null);
    }

    private static String normalizeName(String rawName) {
        String name = rawName == null ? "" : rawName.strip();
        int characters = name.codePointCount(0, name.length());
        boolean hasControl = name.codePoints().anyMatch(Character::isISOControl);
        if (characters < 1 || characters > MAX_NAME_CHARACTERS || hasControl) {
            throw new MemberException(MemberErrorCode.INVALID_NAME);
        }
        return name;
    }

    private record Completion(Member member, MemberErrorCode problem) {

        static Completion failed(MemberErrorCode problem) {
            return new Completion(null, problem);
        }
    }
}
