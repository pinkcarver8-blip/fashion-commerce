package io.github.pinkcarver8_blip.commerce.member.domain;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(byte[] tokenHash);

    /**
     * 아직 안 쓰고 폐기되지 않은 토큰만 "썼다"로 바꾼다. 바뀐 행이 0이면 누군가 먼저 썼다는 뜻이다(R4: 동시 요청 두 개 중 하나만 성공). 행 잠금은 MySQL이
     * UPDATE 때 건다. 두 번째 요청은 첫 요청이 끝날 때까지 기다린 뒤 0행을 받는다.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "update RefreshToken t set t.usedAt = :now"
                    + " where t.id = :id and t.usedAt is null and t.revokedAt is null")
    int markUsed(Long id, Instant now);

    /** 한 로그인 계열의 토큰을 모두 폐기한다(재사용 감지, 로그아웃). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(
            "update RefreshToken t set t.revokedAt = :now"
                    + " where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(UUID familyId, Instant now);
}
