package io.github.pinkcarver8_blip.commerce.member.domain;

import jakarta.persistence.LockModeType;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface MemberSignupRepository extends JpaRepository<MemberSignup, Long> {

    /** 가입 완료는 신청 행을 잠그고 한다. 같은 링크로 두 번 눌러도 회원이 둘 생기지 않고, 틀린 횟수가 빠지지 않는다. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from MemberSignup s where s.tokenHash = :tokenHash")
    Optional<MemberSignup> findByTokenHashForUpdate(byte[] tokenHash);

    @Modifying
    @Query("delete from MemberSignup s where s.email = :email")
    int deleteAllByEmail(String email);
}
