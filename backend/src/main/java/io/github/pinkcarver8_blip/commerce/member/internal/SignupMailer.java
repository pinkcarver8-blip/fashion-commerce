package io.github.pinkcarver8_blip.commerce.member.internal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * 가입 메일(작업진행.md A5-9 S13). 로컬은 가짜 메일함(Mailpit)으로 간다.
 *
 * <ul>
 *   <li>인증 링크의 토큰은 주소의 {@code #} 뒤에 둔다. {@code #} 뒤는 브라우저가 서버로 보내지 않아 Nginx 접속 기록에 남지 않는다.
 *   <li>메일에는 고객이 입력한 값(이름 등)을 넣지 않는다. 남의 이메일로 신청해 이상한 문장을 그 사람에게 보내는 데 쓰지 못하게.
 * </ul>
 */
@Component
class SignupMailer {

    private static final Logger log = LoggerFactory.getLogger(SignupMailer.class);

    private final JavaMailSender sender;
    private final String from;
    private final String storeBaseUrl;

    SignupMailer(
            JavaMailSender sender,
            @Value("${commerce.mail.from}") String from,
            @Value("${commerce.store.base-url}") String storeBaseUrl) {
        this.sender = sender;
        this.from = from;
        this.storeBaseUrl = storeBaseUrl.replaceAll("/+$", "");
    }

    void sendVerification(String email, String token) {
        send(
                email,
                "[패션 커머스 데모] 이메일 인증",
                "아래 링크를 열고, 가입할 때 정한 비밀번호를 넣으면 가입이 끝납니다. 링크는 24시간 동안 쓸 수 있습니다.\n\n"
                        + storeBaseUrl
                        + "/signup/complete#token="
                        + token
                        + "\n\n직접 가입을 신청하지 않았다면 이 메일을 무시하세요. 링크를 열지 않으면 가입되지 않습니다.");
    }

    void sendAlreadyRegistered(String email) {
        send(
                email,
                "[패션 커머스 데모] 이미 가입된 이메일입니다",
                "이 이메일로 가입 신청이 들어왔지만, 이미 가입되어 있습니다. 로그인해 주세요.\n\n"
                        + storeBaseUrl
                        + "/login\n\n직접 신청하지 않았다면 이 메일을 무시하세요. 계정에는 아무것도 바뀌지 않았습니다.");
    }

    private void send(String to, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(to);
        message.setSubject(subject);
        message.setText(text);
        try {
            sender.send(message);
        } catch (MailException e) {
            // 받는 주소와 메일 내용(토큰)은 로그에 남기지 않는다.
            log.error("가입 메일을 보내지 못했다: {}", e.getClass().getSimpleName());
            throw new MemberException(MemberErrorCode.MAIL_UNAVAILABLE);
        }
    }
}
