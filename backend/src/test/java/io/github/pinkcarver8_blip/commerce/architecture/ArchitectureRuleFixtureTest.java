package io.github.pinkcarver8_blip.commerce.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.EvaluationResult;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/**
 * R0-06 시나리오 1~8·10: 규칙마다 일부러 어긴 예시({@code archfixture.sNN})에서 실패하고, 허용 예시({@code archfixture.ok})에서
 * 통과하는지 본다. 예시가 규칙과 상관없는 이유로 실패하지 않도록, 실패 내용에 예시 클래스 이름이 있는지까지 확인한다.
 */
class ArchitectureRuleFixtureTest {

  private static final String FIXTURE = "io.github.pinkcarver8_blip.archfixture";

  @TestFactory
  Stream<DynamicTest> R0_06_허용_예시는_모든_규칙을_통과한다() {
    String root = FIXTURE + ".ok";
    var classes = new ClassFileImporter().importPackages(root);
    return new CommerceArchitectureRules(root)
        .all().stream()
            .map(
                rule ->
                    DynamicTest.dynamicTest(rule.getDescription(), () -> rule.check(classes)));
  }

  @Test
  void R0_06_1_inventory가_order를_부르면_실패한다() {
    assertViolation("s01", CommerceArchitectureRules::moduleDependenciesFollowAdr001,
        "inventory → order 호출은 ADR-001 목록에 없다", "StockService");
  }

  @Test
  void R0_06_1_목록에_없는_모듈_패키지는_실패한다() {
    assertViolation("s01", CommerceArchitectureRules::moduleDependenciesFollowAdr001,
        "목록에 없는 모듈 패키지 'coupon'", "CouponService");
  }

  @Test
  void R0_06_1_common이_모듈에_의존하면_실패한다() {
    assertViolation("s01", CommerceArchitectureRules::moduleDependenciesFollowAdr001,
        "common은 모듈에 의존하지 않는다", "OrderCounter");
  }

  @Test
  void R0_06_2_payment와_order가_서로를_부르면_실패한다() {
    assertViolation("s02", CommerceArchitectureRules::modulesAreFreeOfCycles,
        "Cycle detected", "order 모듈", "payment 모듈");
  }

  @Test
  void R0_06_3_다른_모듈의_엔티티를_직접_쓰면_실패한다() {
    assertViolation("s03", CommerceArchitectureRules::moduleDependenciesFollowAdr001,
        "order의 공개 API(order.api)가 아닌 클래스를 쓴다", "PaymentService");
  }

  @Test
  void R0_06_3_공개_API가_내부_엔티티를_드러내면_실패한다() {
    assertViolation("s03", CommerceArchitectureRules::publicApiDoesNotExposeInternals,
        "OrderApi.find(long)가 내부 타입", "order.domain.Order");
  }

  @Test
  void R0_06_4_admin_밖에서_관리자_경로를_열면_실패한다() {
    assertViolation("s04", CommerceArchitectureRules::adminControllersOnlyInAdmin,
        "관리자 경로를 admin 모듈 밖에서 연다", "SneakyAdminController");
  }

  @Test
  void R0_06_4_클래스와_메서드_경로를_이어_관리자_경로가_되면_실패한다() {
    assertViolation("s04", CommerceArchitectureRules::adminControllersOnlyInAdmin,
        "관리자 경로를 admin 모듈 밖에서 연다", "MethodLevelAdminController");
  }

  @Test
  void R0_06_4_관리자_경로와_겹칠_수_있는_패턴은_실패한다() {
    assertViolation("s04", CommerceArchitectureRules::adminControllersOnlyInAdmin,
        "관리자 경로와 겹칠 수 있는 패턴", "PatternController");
  }

  @Test
  void R0_06_4_admin_컨트롤러가_관리자_경로_밖을_열면_실패한다() {
    assertViolation("s04", CommerceArchitectureRules::adminControllersOnlyInAdmin,
        "admin 모듈의 컨트롤러는 클래스에 /api/admin으로 시작하는 경로를 단다", "WrongPathAdminController");
  }

  @Test
  void R0_06_5_조회_전용_패키지_밖에서_QueryDSL을_쓰면_실패한다() {
    assertViolation("s05", CommerceArchitectureRules::queryDslOnlyInQueryPackages,
        "조회 전용 패키지 밖에서 QueryDSL을 쓴다", "ProductService");
  }

  @Test
  void R0_06_5_QueryDSL로_데이터를_바꾸면_실패한다() {
    assertViolation("s05", CommerceArchitectureRules::noQueryDslWrites,
        "QueryDSL 쓰기 메서드를 부른다", "ProductRenameQuery");
    assertViolation("s05", CommerceArchitectureRules::noQueryDslWrites,
        "QueryDSL 쓰기 절을 쓴다", "ProductRenameQuery");
  }

  @Test
  void R0_06_6_소수_타입을_쓰면_실패한다() {
    Function<CommerceArchitectureRules, ArchRule> rule = CommerceArchitectureRules::noDecimalTypes;
    assertViolation("s06", rule, "소수 타입 필드", "discountRate");
    assertViolation("s06", rule, "소수 타입 필드", "boxedRate");
    assertViolation("s06", rule, "소수 타입 반환", "total(java.math.BigDecimal)");
    assertViolation("s06", rule, "소수 타입 매개변수", "total(java.math.BigDecimal)");
    assertViolation("s06", rule, "소수 타입을 쓰는 호출", "Math.round(double)");
    assertViolation("s06", rule, "소수 타입을 쓰는 호출", "BigDecimal.valueOf(long)");
  }

  @Test
  void R0_06_6_검토된_표시는_그_클래스_안에서만_통한다() {
    Function<CommerceArchitectureRules, ArchRule> rule = CommerceArchitectureRules::noDecimalTypes;
    assertViolation("s06", rule, "소수 타입을 쓰는 호출", "RatingReader.rounded", "Rating.score()");
    List<String> details = evaluate("s06", rule).getFailureReport().getDetails();
    // 표시를 단 Rating 자신은 실패하지 않는다.
    assertThat(details).noneMatch(line -> line.contains("Rating.<init>"));
  }

  @Test
  void R0_06_7_주문상품_필드를_생성자_밖에서_쓰면_실패한다() {
    Function<CommerceArchitectureRules, ArchRule> rule = CommerceArchitectureRules::orderItemFieldsWrittenOnlyInConstructor;
    assertViolation("s07", rule, "주문상품 필드를 생성자 밖에서 쓴다", "OrderItem.changeQuantity(int)");
    assertViolation("s07", rule, "주문상품 필드를 생성자 밖에서 쓴다", "OrderItemEditor.edit");
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
    Function<CommerceArchitectureRules, ArchRule> rule = CommerceArchitectureRules::noLombokEqualsHashCodeToStringOnEntities;
    assertViolation("s10", rule, "엔티티에 Lombok이 만든 메서드가 있다", "Order.toString()");
    assertViolation("s10", rule, "엔티티에 Lombok이 만든 메서드가 있다", "Order.equals(java.lang.Object)");
    assertViolation("s10", rule, "엔티티에 Lombok이 만든 메서드가 있다", "Order.hashCode()");
  }

  private static EvaluationResult evaluate(
      String scenario, Function<CommerceArchitectureRules, ArchRule> ruleOf) {
    String root = FIXTURE + "." + scenario;
    return ruleOf
        .apply(new CommerceArchitectureRules(root))
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
