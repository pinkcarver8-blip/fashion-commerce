package io.github.pinkcarver8_blip.commerce.money;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 소수 타입({@code double}·{@code float}·{@code BigDecimal}) 금지 규칙의 예외 표시(ADR-010 보충).
 *
 * <p>금액은 원 단위 정수({@code long})다. 소수 타입은 돈이 아니어도 백엔드 전체에서 금지하고, 꼭 필요한 곳만 이 표시를 단다. 이 표시를 더하는 PR은
 * 리뷰에서 이유를 확인한다.
 */
@Retention(RetentionPolicy.CLASS)
@Target({ElementType.FIELD, ElementType.METHOD, ElementType.CONSTRUCTOR, ElementType.PARAMETER})
public @interface AllowDecimal {

    /** 소수 타입이 필요한 이유. 돈이 아니라는 설명을 포함한다. */
    String reason();
}
