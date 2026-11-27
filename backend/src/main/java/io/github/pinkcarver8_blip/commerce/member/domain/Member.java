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
 * 고객 회원(ADR-008 결정 1). 이메일 인증을 마친 사람만 이 행이 생긴다(작업진행.md A5-9 S13).
 *
 * <p>{@code publicId}는 밖에 보이는 회원 ID(토큰의 sub)다. 순차 PK {@code id}는 같은 모듈의 표끼리만 쓴다(S12, ADR-010).
 */
@Entity
@Table(name = "member")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(
            name = "public_id",
            nullable = false,
            updatable = false,
            columnDefinition = "binary(16)")
    private UUID publicId;

    @Column(nullable = false, updatable = false)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(nullable = false)
    private String name;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /** 인증을 마친 가입 신청에서만 만든다(SignupService). 이메일은 이미 소문자로 맞춘 값, 비밀번호는 해시다. */
    public Member(String email, String passwordHash, String name, Instant createdAt) {
        this.publicId = UUID.randomUUID();
        this.email = email;
        this.passwordHash = passwordHash;
        this.name = name;
        this.createdAt = createdAt;
    }
}
