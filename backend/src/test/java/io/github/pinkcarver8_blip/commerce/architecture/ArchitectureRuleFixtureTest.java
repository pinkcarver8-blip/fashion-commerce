package io.github.pinkcarver8_blip.commerce.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.List;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * R0-06 시나리오 1~8·10: 규칙마다 일부러 어긴 예시({@code archfixture.sNN})에서 실패하고, 허용 예시({@code
 * archfixture.ok})에서 통과하는지 본다. 예시가 규칙과 상관없는 이유로 실패하지 않도록, 실패 내용에 예시 클래스 이름이 있는지까지 확인한다.
 */
class ArchitectureRuleFixtureTest {

    private static final String FIXTURE = "io.github.pinkcarver8_blip.archfixture";

    /** 허용 예시(archfixture.ok)의 공유 모듈 허용 목록. 실제 목록은 src/test/resources/architecture에 있다. */
    private static final Set<String> OK_ALLOWLIST =
            Set.of(
                    "money.Money",
                    "money.Money.<init>(long)",
                    "money.Money.won()",
                    "money.Money.of(long)",
                    "money.Money.plus(money.Money)",
                    "identity.CustomerPrincipal",
                    "identity.CustomerPrincipal.<init>(long)",
                    "identity.CustomerPrincipal.memberId()",
                    "error.ErrorCode",
                    "error.ErrorCode.code()",
                    "platform.WebConfig");

    /** s12의 허용 목록. shippingFee는 일부러 빼고, 코드에 없는 money.Gone을 일부러 넣었다. */
    private static final Set<String> S12_ALLOWLIST =
            Set.of("money.Money", "money.Money.<init>(long)", "money.Money.won()", "money.Gone");

    @TestFactory
    Stream<DynamicTest> R0_06_허용_예시는_모든_규칙을_통과한다() {
        String root = FIXTURE + ".ok";
        var classes = new ClassFileImporter().importPackages(root);
        return new CommerceArchitectureRules(root, OK_ALLOWLIST)
                .all().stream()
                        .map(
                                rule ->
                                        DynamicTest.dynamicTest(
                                                rule.getDescription(), () -> rule.check(classes)));
    }

    @Test
    void R0_06_1_inventory가_order를_부르면_실패한다() {
        assertViolation(
                "s01",
                CommerceArchitectureRules::moduleDependenciesFollowAdr001,
                "inventory → order 호출은 ADR-001 목록에 없다",
                "StockService");
    }

    @Test
    void R0_06_1_목록에_없는_모듈_패키지는_실패한다() {
        assertViolation(
                "s01",
                CommerceArchitectureRules::moduleDependenciesFollowAdr001,
                "목록에 없는 모듈 패키지 'coupon'",
                "CouponService");
    }

    @Test
    void R0_06_1_공유_모듈이_업무_모듈에_의존하면_실패한다() {
        assertViolation(
                "s01",
                CommerceArchitectureRules::moduleDependenciesFollowAdr001,
                "공유 모듈 money은 업무 모듈에 의존하지 않는다",
                "OrderCounter");
    }

    @Test
    void R0_06_12_아무_모듈도_platform을_부를_수_없다() {
        assertViolation(
                "s12",
                CommerceArchitectureRules::moduleDependenciesFollowAdr001,
                "platform은 아무도 부를 수 없다",
                "UsesPlatform");
    }

    @Test
    void R0_06_12_공유_모듈끼리는_정해_둔_방향만_부른다() {
        assertViolation(
                "s12",
                CommerceArchitectureRules::moduleDependenciesFollowAdr001,
                "공유 모듈 identity → money 방향은 정해 둔 목록(SHARED_CALLS)에 없다",
                "UsesMoney");
        assertViolation(
                "s12",
                CommerceArchitectureRules::moduleDependenciesFollowAdr001,
                "공유 모듈 money은 업무 모듈에 의존하지 않는다",
                "MoneyToOrder");
    }

    @Test
    void R0_06_12_common_패키지는_더_이상_없다() {
        assertViolation(
                "s12",
                CommerceArchitectureRules::moduleDependenciesFollowAdr001,
                "목록에 없는 모듈 패키지 'common'",
                "Junk");
    }

    @Test
    void R0_06_12_공유_모듈에는_DB와_업무_처리를_두지_않는다() {
        Function<CommerceArchitectureRules, ArchRule> rule =
                CommerceArchitectureRules::sharedModulesStayMinimal;
        assertViolation("s12", rule, "공유 모듈이 DB 도구를 쓴다", "IdLookup");
        assertViolation("s12", rule, "업무 처리 부품(@Service·@Repository)", "FeeService");
        assertViolation("s12", rule, "업무 처리 부품(@Service·@Repository)", "PlatformService");
        assertViolation("s12", rule, "@Transactional", "ErrorLog");
        assertViolation("s12", rule, "Spring 부품(@Component 계열)", "IdBean");
        assertViolation("s12", rule, "정적 함수는 자기 타입을 만드는 함수만", "Money.shippingFee(long)");
        // platform의 @Configuration은 허용된다.
        assertThat(evaluate("s12", rule).getFailureReport().getDetails())
                .noneMatch(line -> line.contains("WebConfig"));
    }

    @Test
    void R0_06_12_공유_모듈은_허용_목록과_같아야_한다() {
        String root = FIXTURE + ".s12";
        EvaluationResult result =
                new CommerceArchitectureRules(root, S12_ALLOWLIST)
                        .sharedModulesMatchAllowlist()
                        .evaluate(new ClassFileImporter().importPackages(root));
        List<String> details = result.getFailureReport().getDetails();
        assertThat(details)
                .anyMatch(line -> line.contains("허용 목록에 없다: money.Money.shippingFee(long)"));
        assertThat(details).anyMatch(line -> line.contains("허용 목록에 없다: money.FeeService"));
        assertThat(details).anyMatch(line -> line.contains("허용 목록에 없다: platform.PlatformService"));
        assertThat(details).anyMatch(line -> line.contains("허용 목록에 있지만 코드에 없다: money.Gone"));
        // 목록에 있는 것과 record가 스스로 만드는 equals·hashCode·toString은 실패하지 않는다.
        assertThat(details).noneMatch(line -> line.contains("허용 목록에 없다: money.Money.won()"));
        assertThat(details).noneMatch(line -> line.contains("money.Money.toString()"));
    }

    @Test
    void R0_06_2_payment와_order가_서로를_부르면_실패한다() {
        assertViolation(
                "s02",
                CommerceArchitectureRules::modulesAreFreeOfCycles,
                "Cycle detected",
                "order 모듈",
                "payment 모듈");
    }

    @Test
    void R0_06_3_다른_모듈의_엔티티를_직접_쓰면_실패한다() {
        assertViolation(
                "s03",
                CommerceArchitectureRules::moduleDependenciesFollowAdr001,
                "order의 공개 API(order.api)가 아닌 클래스를 쓴다",
                "PaymentService");
    }

    @Test
    void R0_06_3_공개_API가_내부_엔티티를_드러내면_실패한다() {
        assertViolation(
                "s03",
                CommerceArchitectureRules::publicApiDoesNotExposeInternals,
                "OrderApi.find(long)가 내부 타입",
                "order.domain.Order");
    }

    @Test
    void R0_06_3_공개_API가_상속이나_throws로_내부_타입을_드러내면_실패한다() {
        Function<CommerceArchitectureRules, ArchRule> rule =
                CommerceArchitectureRules::publicApiDoesNotExposeInternals;
        assertViolation("s03", rule, "OrderView가 상속·구현으로 내부 타입", "order.domain.Order");
        assertViolation("s03", rule, "OrderLookup.count()가 내부 타입", "order.domain.OrderMissing");
    }

    @Test
    void R0_06_4_admin_컨트롤러가_비슷한_경로_api_admins를_열면_실패한다() {
        assertViolation(
                "s04",
                CommerceArchitectureRules::adminControllersOnlyInAdmin,
                "admin 모듈의 컨트롤러는 클래스에 /api/admin으로 시작하는 경로를 단다",
                "LookalikeAdminController");
    }

    @Test
    void R0_06_4_인터페이스에서_물려받은_관리자_경로도_실패한다() {
        Function<CommerceArchitectureRules, ArchRule> rule =
                CommerceArchitectureRules::adminControllersOnlyInAdmin;
        assertViolation("s04", rule, "관리자 경로를 admin 모듈 밖에서 연다", "InheritedAdminController");
        assertViolation("s04", rule, "관리자 경로를 admin 모듈 밖에서 연다", "AdminRoutes");
    }

    @Test
    void R0_06_4_물음표_패턴도_관리자_경로와_겹칠_수_있어_실패한다() {
        assertViolation(
                "s04",
                CommerceArchitectureRules::adminControllersOnlyInAdmin,
                "관리자 경로와 겹칠 수 있는 패턴",
                "QuestionMarkController");
    }

    @Test
    void R0_06_4_admin_밖에서_관리자_경로를_열면_실패한다() {
        assertViolation(
                "s04",
                CommerceArchitectureRules::adminControllersOnlyInAdmin,
                "관리자 경로를 admin 모듈 밖에서 연다",
                "SneakyAdminController");
    }

    @Test
    void R0_06_4_클래스와_메서드_경로를_이어_관리자_경로가_되면_실패한다() {
        assertViolation(
                "s04",
                CommerceArchitectureRules::adminControllersOnlyInAdmin,
                "관리자 경로를 admin 모듈 밖에서 연다",
                "MethodLevelAdminController");
    }

    @Test
    void R0_06_4_관리자_경로와_겹칠_수_있는_패턴은_실패한다() {
        assertViolation(
                "s04",
                CommerceArchitectureRules::adminControllersOnlyInAdmin,
                "관리자 경로와 겹칠 수 있는 패턴",
                "PatternController");
    }

    @Test
    void R0_06_4_admin_컨트롤러가_관리자_경로_밖을_열면_실패한다() {
        assertViolation(
                "s04",
                CommerceArchitectureRules::adminControllersOnlyInAdmin,
                "admin 모듈의 컨트롤러는 클래스에 /api/admin으로 시작하는 경로를 단다",
                "WrongPathAdminController");
    }

    @Test
    void R0_06_5_조회_전용_패키지_밖에서_QueryDSL을_쓰면_실패한다() {
        assertViolation(
                "s05",
                CommerceArchitectureRules::queryDslOnlyInQueryPackages,
                "조회 전용 패키지 밖에서 QueryDSL을 쓴다",
                "ProductService");
    }

    @Test
    void R0_06_5_손으로_만든_Q_이름_클래스는_조회_전용_예외가_아니다() {
        assertViolation(
                "s05",
                CommerceArchitectureRules::queryDslOnlyInQueryPackages,
                "조회 전용 패키지 밖에서 QueryDSL을 쓴다",
                "QSneaky");
    }

    @Test
    void R0_06_5_QueryDSL로_데이터를_바꾸면_실패한다() {
        assertViolation(
                "s05",
                CommerceArchitectureRules::noQueryDslWrites,
                "QueryDSL 쓰기 메서드를 부른다",
                "ProductRenameQuery");
        assertViolation(
                "s05",
                CommerceArchitectureRules::noQueryDslWrites,
                "QueryDSL 쓰기 절을 쓴다",
                "ProductRenameQuery");
    }

    @Test
    void R0_06_6_소수_타입을_쓰면_실패한다() {
        Function<CommerceArchitectureRules, ArchRule> rule =
                CommerceArchitectureRules::noDecimalTypes;
        assertViolation("s06", rule, "소수 타입 필드", "discountRate");
        assertViolation("s06", rule, "소수 타입 필드", "boxedRate");
        assertViolation("s06", rule, "소수 타입 반환", "total(java.math.BigDecimal)");
        assertViolation("s06", rule, "소수 타입 매개변수", "total(java.math.BigDecimal)");
        assertViolation("s06", rule, "소수 타입을 쓰는 호출", "Math.round(double)");
        assertViolation("s06", rule, "소수 타입을 쓰는 호출", "BigDecimal.valueOf(long)");
    }

    @Test
    void R0_06_6_검토된_표시는_그_클래스_안에서만_통한다() {
        Function<CommerceArchitectureRules, ArchRule> rule =
                CommerceArchitectureRules::noDecimalTypes;
        assertViolation("s06", rule, "소수 타입을 쓰는 호출", "RatingReader.rounded", "Rating.score()");
        List<String> details = evaluate("s06", rule).getFailureReport().getDetails();
        // 표시를 단 Rating 자신은 실패하지 않는다.
        assertThat(details).noneMatch(line -> line.contains("Rating.<init>"));
    }

    @Test
    void R0_06_6_표시_단_필드도_표시_없는_메서드가_쓰면_실패한다() {
        Function<CommerceArchitectureRules, ArchRule> rule =
                CommerceArchitectureRules::noDecimalTypes;
        assertViolation("s06", rule, "소수 타입을 쓰는 호출", "DiscountPolicy.apply(long)", "rate");
        assertViolation("s06", rule, "소수 타입 필드", "DiscountPolicy.adjust");
        List<String> details = evaluate("s06", rule).getFailureReport().getDetails();
        // 표시를 단 생성자가 자기 필드를 채우는 것은 허용된다.
        assertThat(details).noneMatch(line -> line.contains("DiscountPolicy.<init>"));
    }

    @Test
    void R0_06_7_주문상품_필드를_생성자_밖에서_쓰면_실패한다() {
        Function<CommerceArchitectureRules, ArchRule> rule =
                CommerceArchitectureRules::orderItemFieldsWrittenOnlyInConstructor;
        assertViolation("s07", rule, "주문상품 필드를 생성자 밖에서 쓴다", "OrderItem.changeQuantity(int)");
        assertViolation("s07", rule, "주문상품 필드를 생성자 밖에서 쓴다", "OrderItemEditor.edit");
        assertViolation("s07", rule, "주문상품 필드 타입은 내용을 바꿀 수 있다", "OrderItem.options");
        assertViolation("s07", rule, "주문상품 필드 타입은 내용을 바꿀 수 있다", "OrderItem.amounts");
    }

    @Test
    void R0_06_8_주문_상태를_Order_밖에서_쓰면_실패한다() {
        EvaluationResult result =
                evaluate("s08", CommerceArchitectureRules::orderStatusWrittenOnlyByOrder);
        List<String> details = result.getFailureReport().getDetails();
        assertThat(details).anyMatch(line -> line.contains("OrderStatusChanger.forcePaid"));
        // Order 안의 도메인 메서드(markPaid)는 허용된다.
        assertThat(details).noneMatch(line -> line.contains("Order.markPaid"));
    }

    @Test
    void R0_06_10_엔티티에_Lombok_equals_hashCode_toString을_쓰면_실패한다() {
        Function<CommerceArchitectureRules, ArchRule> rule =
                CommerceArchitectureRules::noLombokEqualsHashCodeToStringOnEntities;
        assertViolation("s10", rule, "엔티티에 Lombok이 만든 메서드가 있다", "Order.toString()");
        assertViolation("s10", rule, "엔티티에 Lombok이 만든 메서드가 있다", "Order.equals(java.lang.Object)");
        assertViolation("s10", rule, "엔티티에 Lombok이 만든 메서드가 있다", "Order.hashCode()");
    }

    @Test
    void R1_01_platform_밖에서_보안_설정을_만들면_실패한다() {
        assertViolation(
                "s13",
                CommerceArchitectureRules::onlyPlatformConfiguresSecurity,
                "platform 밖에서 보안 설정을 만든다",
                "SneakySecurityConfig");
    }

    @Test
    void R1_01_platform_밖에서_주체를_만들면_실패하고_platform은_통과한다() {
        Function<CommerceArchitectureRules, ArchRule> rule =
                CommerceArchitectureRules::onlyPlatformCreatesPrincipals;
        assertViolation("s13", rule, "platform 밖에서 주체를 만든다", "PrincipalFromHeader.fromHeader");
        List<String> details = evaluate("s13", rule).getFailureReport().getDetails();
        assertThat(details).noneMatch(line -> line.contains("TokenConverter"));
        // 생성자 참조(CustomerPrincipal::new)도 잡는다.
        assertViolation("s13", rule, "platform 밖에서 주체를 만든다", "PrincipalByReference");
    }

    @Test
    void R1_01_컨트롤러가_주체를_표시_없이_받으면_실패한다() {
        Function<CommerceArchitectureRules, ArchRule> rule =
                CommerceArchitectureRules::principalParametersUseAuthenticationPrincipal;
        assertViolation("s13", rule, "@AuthenticationPrincipal이 없다", "MeController.forgotten");
        List<String> details = evaluate("s13", rule).getFailureReport().getDetails();
        assertThat(details).noneMatch(line -> line.contains("MeController.annotated"));
    }

    private static EvaluationResult evaluate(
            String scenario, Function<CommerceArchitectureRules, ArchRule> ruleOf) {
        String root = FIXTURE + "." + scenario;
        return ruleOf.apply(new CommerceArchitectureRules(root))
                .evaluate(new ClassFileImporter().importPackages(root));
    }

    /** 규칙이 실패하고, 실패 내용 한 줄에 기대한 문구가 모두 들어 있는지 본다. */
    private static void assertViolation(
            String scenario,
            Function<CommerceArchitectureRules, ArchRule> ruleOf,
            String... expectedFragments) {
        EvaluationResult result = evaluate(scenario, ruleOf);
        assertThat(result.hasViolation()).as("규칙이 실패해야 한다").isTrue();
        assertThat(result.getFailureReport().getDetails())
                .anyMatch(line -> Stream.of(expectedFragments).allMatch(line::contains));
    }
}
