package io.github.pinkcarver8_blip.commerce.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import com.tngtech.archunit.core.domain.AccessTarget.CodeUnitCallTarget;
import com.tngtech.archunit.core.domain.Dependency;
import com.tngtech.archunit.core.domain.JavaAccess;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaCodeUnit;
import com.tngtech.archunit.core.domain.JavaConstructor;
import com.tngtech.archunit.core.domain.JavaField;
import com.tngtech.archunit.core.domain.JavaFieldAccess;
import com.tngtech.archunit.core.domain.JavaFieldAccess.AccessType;
import com.tngtech.archunit.core.domain.JavaMember;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.core.domain.JavaModifier;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.domain.JavaType;
import com.tngtech.archunit.core.domain.properties.CanBeAnnotated;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import com.tngtech.archunit.library.dependencies.SlicesRuleDefinition;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.stream.Stream;

/**
 * 백엔드 구조 규칙(R0-06). ADR-001·003·004·007·010의 "강제" 칸 중 ArchUnit이 맡는 것을 모았다.
 *
 * <p>규칙은 패키지 뿌리({@code root})를 받아 만든다. 실제 코드는 {@link #COMMERCE_ROOT}로, 규칙 위반 예시는 {@code archfixture}
 * 아래 패키지로 검사한다. 그래서 실제 코드와 예시가 같은 규칙을 거친다.
 */
final class CommerceArchitectureRules {

  static final String COMMERCE_ROOT = "io.github.pinkcarver8_blip.commerce";

  static final String COMMON = "common";

  /**
   * ADR-001 동기 호출 목록 중 R1에 도입하는 것(보충의 {@code shipping → inventory} 포함). 왼쪽 모듈은 오른쪽 모듈의 공개 API만 부를 수
   * 있다. R2b 이후 모듈(coupon, drop, outbox, seller, settlement)은 도입할 때 ADR-001을 확인하고 여기에 더한다.
   */
  static final Map<String, Set<String>> ALLOWED_CALLS =
      Map.of(
          "admin",
              Set.of(
                  "catalog", "inventory", "cart", "order", "payment", "shipping", "claim", "member"),
          "claim", Set.of("order", "payment", "inventory"),
          "payment", Set.of("order", "inventory"),
          "shipping", Set.of("order", "inventory"),
          "order", Set.of("catalog", "inventory", "cart", "member"),
          "cart", Set.of("catalog"),
          "inventory", Set.of(),
          "catalog", Set.of(),
          "member", Set.of());

  private static final String ALLOW_DECIMAL = COMMERCE_ROOT + ".common.AllowDecimal";

  private static final Set<String> DECIMAL_TYPES =
      Set.of("double", "float", "java.lang.Double", "java.lang.Float", "java.math.BigDecimal");

  private static final String CONTROLLER = "org.springframework.stereotype.Controller";
  private static final String REQUEST_MAPPING =
      "org.springframework.web.bind.annotation.RequestMapping";

  private static final Set<String> QUERYDSL_WRITE_METHODS = Set.of("update", "delete", "insert");

  private final String root;

  CommerceArchitectureRules(String root) {
    this.root = root;
  }

  List<ArchRule> all() {
    return List.of(
        moduleDependenciesFollowAdr001(),
        modulesAreFreeOfCycles(),
        publicApiDoesNotExposeInternals(),
        adminControllersOnlyInAdmin(),
        queryDslOnlyInQueryPackages(),
        noQueryDslWrites(),
        noDecimalTypes(),
        orderItemFieldsWrittenOnlyInConstructor(),
        orderStatusWrittenOnlyByOrder(),
        noLombokEqualsHashCodeToStringOnEntities());
  }

  /** 시나리오 1·3: 목록(ADR-001)에 있는 방향으로, 상대 모듈의 공개 API({@code <모듈>.api})만 쓴다. */
  ArchRule moduleDependenciesFollowAdr001() {
    return classes()
        .should(
            condition(
                "ADR-001 동기 호출 목록에 있는 모듈의 공개 API만 쓴다",
                (origin, events) -> {
                  if (!inRoot(origin)) {
                    return;
                  }
                  String from = moduleOf(origin).orElse(null);
                  if (from != null && !isKnownModule(from)) {
                    events.add(
                        SimpleConditionEvent.violated(
                            origin,
                            "B1·ADR-001 목록에 없는 모듈 패키지 '"
                                + from
                                + "'에 있다: "
                                + origin.getName()));
                    return;
                  }
                  for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    JavaClass target = dependency.getTargetClass().getBaseComponentType();
                    if (!inRoot(target)) {
                      continue;
                    }
                    String to = moduleOf(target).orElse(null);
                    if (Objects.equals(from, to)) {
                      continue;
                    }
                    String problem = dependencyProblem(from, to, target);
                    if (problem != null) {
                      events.add(
                          SimpleConditionEvent.violated(
                              dependency, problem + ": " + dependency.getDescription()));
                    }
                  }
                }));
  }

  private String dependencyProblem(String from, String to, JavaClass target) {
    if (to == null) {
      return "루트 패키지의 클래스에 의존한다";
    }
    if (from == null) {
      return "루트 패키지는 모듈에 의존하지 않는다";
    }
    if (COMMON.equals(from)) {
      return "common은 모듈에 의존하지 않는다";
    }
    if (COMMON.equals(to)) {
      return null;
    }
    if (!isKnownModule(to)) {
      return "B1·ADR-001 목록에 없는 모듈 패키지 '" + to + "'에 의존한다";
    }
    if (!ALLOWED_CALLS.get(from).contains(to)) {
      return from + " → " + to + " 호출은 ADR-001 목록에 없다";
    }
    if (!isPublicApi(target, to)) {
      return to + "의 공개 API(" + to + ".api)가 아닌 클래스를 쓴다";
    }
    return null;
  }

  /** 시나리오 2: 모듈 사이에 순환이 없다. */
  ArchRule modulesAreFreeOfCycles() {
    return SlicesRuleDefinition.slices()
        .matching(root + ".(*)..")
        .namingSlices("$1 모듈")
        .should()
        .beFreeOfCycles();
  }

  /** 시나리오 3: 공개 API의 public 필드·메서드·생성자가 같은 모듈의 내부 타입(엔티티 등)을 드러내지 않는다. */
  ArchRule publicApiDoesNotExposeInternals() {
    return classes()
        .should(
            condition(
                "공개 API는 같은 모듈의 내부 타입을 드러내지 않는다",
                (apiClass, events) -> {
                  Optional<String> module = moduleOf(apiClass);
                  if (module.isEmpty() || !isPublicApi(apiClass, module.get())) {
                    return;
                  }
                  for (JavaMember member : apiClass.getMembers()) {
                    if (!member.getModifiers().contains(JavaModifier.PUBLIC)) {
                      continue;
                    }
                    signatureTypes(member)
                        .flatMap(type -> type.getAllInvolvedRawTypes().stream())
                        .map(JavaClass::getBaseComponentType)
                        .filter(type -> inRoot(type))
                        .filter(type -> moduleOf(type).equals(module))
                        .filter(type -> !isPublicApi(type, module.get()))
                        .distinct()
                        .forEach(
                            internal ->
                                events.add(
                                    SimpleConditionEvent.violated(
                                        member,
                                        member.getFullName()
                                            + "가 내부 타입 "
                                            + internal.getName()
                                            + "을 드러낸다")));
                  }
                }));
  }

  /**
   * 시나리오 4: 관리자 컨트롤러는 admin 모듈에만 있고 경로는 {@code /api/admin}으로 시작한다. admin 밖의 컨트롤러는 관리자 경로나, 관리자 경로와
   * 겹칠 수 있는 패턴(둘째 칸까지의 {@code {변수}}·{@code *})을 열지 않는다(ADR-001·008).
   */
  ArchRule adminControllersOnlyInAdmin() {
    return classes()
        .should(
            condition(
                "관리자 컨트롤러는 admin 모듈에만 있고 /api/admin 경로만 연다",
                (controller, events) -> {
                  if (!inRoot(controller) || !isController(controller)) {
                    return;
                  }
                  List<String> classPaths = mappingPaths(controller);
                  boolean inAdmin = moduleOf(controller).filter("admin"::equals).isPresent();
                  if (inAdmin) {
                    if (classPaths.isEmpty() || !classPaths.stream().allMatch(this::isAdminPath)) {
                      events.add(
                          SimpleConditionEvent.violated(
                              controller,
                              "admin 모듈의 컨트롤러는 클래스에 /api/admin으로 시작하는 경로를 단다: "
                                  + controller.getName()
                                  + " "
                                  + classPaths));
                    }
                    return;
                  }
                  for (String path : fullPaths(controller, classPaths)) {
                    if (isAdminPath(path)) {
                      events.add(
                          SimpleConditionEvent.violated(
                              controller,
                              "관리자 경로를 admin 모듈 밖에서 연다: "
                                  + controller.getName()
                                  + " "
                                  + path));
                    } else if (mayOverlapAdminPath(path)) {
                      events.add(
                          SimpleConditionEvent.violated(
                              controller,
                              "관리자 경로와 겹칠 수 있는 패턴을 admin 모듈 밖에서 연다: "
                                  + controller.getName()
                                  + " "
                                  + path));
                    }
                  }
                }));
  }

  /** 시나리오 5: QueryDSL은 조회 전용 패키지({@code <모듈>.query})에서만 쓴다. 빌드가 만드는 Q클래스는 뺀다(ADR-007). */
  ArchRule queryDslOnlyInQueryPackages() {
    return classes()
        .should(
            condition(
                "QueryDSL은 조회 전용 패키지(<모듈>.query)에서만 쓴다",
                (origin, events) -> {
                  if (!inRoot(origin) || isInQueryPackage(origin) || isQueryDslGenerated(origin)) {
                    return;
                  }
                  for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    if (dependency.getTargetClass().getPackageName().startsWith("com.querydsl")) {
                      events.add(
                          SimpleConditionEvent.violated(
                              dependency,
                              "조회 전용 패키지 밖에서 QueryDSL을 쓴다: " + dependency.getDescription()));
                    }
                  }
                }));
  }

  /** 시나리오 5: QueryDSL로 데이터를 바꾸지 않는다(update·delete·insert 절과 그 호출 금지, ADR-007). */
  ArchRule noQueryDslWrites() {
    return classes()
        .should(
            condition(
                "QueryDSL의 update·delete·insert를 쓰지 않는다",
                (origin, events) -> {
                  if (!inRoot(origin)) {
                    return;
                  }
                  for (Dependency dependency : origin.getDirectDependenciesFromSelf()) {
                    if (dependency
                        .getTargetClass()
                        .getBaseComponentType()
                        .isAssignableTo("com.querydsl.core.dml.DMLClause")) {
                      events.add(
                          SimpleConditionEvent.violated(
                              dependency,
                              "QueryDSL 쓰기 절을 쓴다: " + dependency.getDescription()));
                    }
                  }
                  for (JavaMethodCall call : origin.getMethodCallsFromSelf()) {
                    if (QUERYDSL_WRITE_METHODS.contains(call.getName())
                        && call.getTargetOwner().isAssignableTo("com.querydsl.jpa.JPQLQueryFactory")) {
                      events.add(
                          SimpleConditionEvent.violated(
                              call, "QueryDSL 쓰기 메서드를 부른다: " + call.getDescription()));
                    }
                  }
                }));
  }

  /**
   * 시나리오 6: 소수 타입({@code double}·{@code float}·{@code BigDecimal})을 필드·매개변수·반환 타입·호출에 쓰지 않는다. 돈인지 따지지
   * 않는다. 예외는 {@code @AllowDecimal}을 단 멤버뿐이다(ADR-010 보충).
   */
  ArchRule noDecimalTypes() {
    return classes()
        .should(
            condition(
                "소수 타입(double·float·BigDecimal)을 쓰지 않는다(예외는 @AllowDecimal)",
                (owner, events) -> {
                  if (!inRoot(owner)) {
                    return;
                  }
                  for (JavaField field : owner.getFields()) {
                    if (!allowsDecimal(field) && involvesDecimal(field.getType())) {
                      events.add(
                          SimpleConditionEvent.violated(
                              field, "소수 타입 필드: " + field.getFullName()));
                    }
                  }
                  for (JavaCodeUnit codeUnit : owner.getCodeUnits()) {
                    if (allowsDecimal(codeUnit)) {
                      continue;
                    }
                    if (involvesDecimal(codeUnit.getReturnType())) {
                      events.add(
                          SimpleConditionEvent.violated(
                              codeUnit, "소수 타입 반환: " + codeUnit.getFullName()));
                    }
                    for (JavaParameter parameter : codeUnit.getParameters()) {
                      if (!allowsDecimal(parameter) && involvesDecimal(parameter.getType())) {
                        events.add(
                            SimpleConditionEvent.violated(
                                codeUnit, "소수 타입 매개변수: " + codeUnit.getFullName()));
                      }
                    }
                    for (JavaAccess<?> access : codeUnit.getAccessesFromSelf()) {
                      if (accessUsesDecimal(access)) {
                        events.add(
                            SimpleConditionEvent.violated(
                                access, "소수 타입을 쓰는 호출: " + access.getDescription()));
                      }
                    }
                  }
                }));
  }

  /** 시나리오 7: 주문상품({@code order} 모듈의 {@code OrderItem}) 필드는 그 클래스의 생성자에서만 쓴다(ADR-003). */
  ArchRule orderItemFieldsWrittenOnlyInConstructor() {
    return classes()
        .should(
            condition(
                "주문상품(OrderItem) 필드는 생성자에서만 쓴다",
                (origin, events) -> {
                  for (JavaFieldAccess access : origin.getFieldAccessesFromSelf()) {
                    JavaClass fieldOwner = access.getTargetOwner();
                    if (access.getAccessType() != AccessType.SET || !isOrderClass(fieldOwner, "OrderItem")) {
                      continue;
                    }
                    boolean inOwnConstructor =
                        access.getOrigin() instanceof JavaConstructor
                            && access.getOriginOwner().equals(fieldOwner);
                    if (!inOwnConstructor) {
                      events.add(
                          SimpleConditionEvent.violated(
                              access, "주문상품 필드를 생성자 밖에서 쓴다: " + access.getDescription()));
                    }
                  }
                }));
  }

  /** 시나리오 8: 주문 상태({@code Order}의 {@code OrderStatus} 타입 필드)는 {@code Order} 안에서만 쓴다(ADR-004). */
  ArchRule orderStatusWrittenOnlyByOrder() {
    return classes()
        .should(
            condition(
                "주문 상태는 Order 안에서만 쓴다",
                (origin, events) -> {
                  for (JavaFieldAccess access : origin.getFieldAccessesFromSelf()) {
                    JavaClass fieldOwner = access.getTargetOwner();
                    boolean isStatusField =
                        access.getTarget().getRawType().getSimpleName().equals("OrderStatus");
                    if (access.getAccessType() == AccessType.SET
                        && isOrderClass(fieldOwner, "Order")
                        && isStatusField
                        && !access.getOriginOwner().equals(fieldOwner)) {
                      events.add(
                          SimpleConditionEvent.violated(
                              access, "주문 상태를 Order 밖에서 쓴다: " + access.getDescription()));
                    }
                  }
                }));
  }

  /**
   * 시나리오 10: JPA 엔티티에 Lombok이 만든 equals·hashCode·toString이 없다(ADR-010 보충). {@code @EqualsAndHashCode}·
   * {@code @ToString}은 컴파일 뒤에 남지 않지만, Lombok이 만든 메서드에 붙이는 {@code @lombok.Generated}(lombok.config)로 찾는다.
   */
  ArchRule noLombokEqualsHashCodeToStringOnEntities() {
    return classes()
        .should(
            condition(
                "엔티티에 Lombok equals·hashCode·toString을 쓰지 않는다",
                (entity, events) -> {
                  if (!entity.isAnnotatedWith("jakarta.persistence.Entity")
                      && !entity.isAnnotatedWith("jakarta.persistence.MappedSuperclass")) {
                    return;
                  }
                  for (JavaMethod method : entity.getMethods()) {
                    boolean objectMethod =
                        Set.of("equals", "hashCode", "toString").contains(method.getName());
                    if (objectMethod && method.isAnnotatedWith("lombok.Generated")) {
                      events.add(
                          SimpleConditionEvent.violated(
                              method, "엔티티에 Lombok이 만든 메서드가 있다: " + method.getFullName()));
                    }
                  }
                }));
  }

  // ---- 도움 함수 ----

  private static ArchCondition<JavaClass> condition(
      String description, BiConsumer<JavaClass, ConditionEvents> check) {
    return new ArchCondition<>(description) {
      @Override
      public void check(JavaClass item, ConditionEvents events) {
        check.accept(item, events);
      }
    };
  }

  private boolean inRoot(JavaClass javaClass) {
    String packageName = javaClass.getPackageName();
    return packageName.equals(root) || packageName.startsWith(root + ".");
  }

  /** 루트 바로 아래 패키지 이름. 루트 패키지 자체의 클래스는 빈 값이다. */
  private Optional<String> moduleOf(JavaClass javaClass) {
    String packageName = javaClass.getPackageName();
    if (!packageName.startsWith(root + ".")) {
      return Optional.empty();
    }
    String rest = packageName.substring(root.length() + 1);
    int dot = rest.indexOf('.');
    return Optional.of(dot < 0 ? rest : rest.substring(0, dot));
  }

  private static boolean isKnownModule(String module) {
    return COMMON.equals(module) || ALLOWED_CALLS.containsKey(module);
  }

  private boolean isPublicApi(JavaClass javaClass, String module) {
    return isInPackage(javaClass, root + "." + module + ".api");
  }

  private boolean isInQueryPackage(JavaClass javaClass) {
    return moduleOf(javaClass)
        .map(module -> isInPackage(javaClass, root + "." + module + ".query"))
        .orElse(false);
  }

  private static boolean isInPackage(JavaClass javaClass, String packageName) {
    String actual = javaClass.getPackageName();
    return actual.equals(packageName) || actual.startsWith(packageName + ".");
  }

  private boolean isOrderClass(JavaClass javaClass, String simpleName) {
    return javaClass.getSimpleName().equals(simpleName)
        && moduleOf(javaClass).filter("order"::equals).isPresent();
  }

  private static boolean isQueryDslGenerated(JavaClass javaClass) {
    return javaClass.getSimpleName().startsWith("Q")
        && javaClass.isAssignableTo("com.querydsl.core.types.Path");
  }

  private static Stream<JavaType> signatureTypes(JavaMember member) {
    if (member instanceof JavaField field) {
      return Stream.of(field.getType());
    }
    if (member instanceof JavaCodeUnit codeUnit) {
      List<JavaType> types = new ArrayList<>();
      types.add(codeUnit.getReturnType());
      codeUnit.getParameters().forEach(parameter -> types.add(parameter.getType()));
      return types.stream();
    }
    return Stream.empty();
  }

  private static boolean isDecimal(JavaClass type) {
    return DECIMAL_TYPES.contains(type.getBaseComponentType().getName());
  }

  private static boolean involvesDecimal(JavaType type) {
    return type.getAllInvolvedRawTypes().stream().anyMatch(CommerceArchitectureRules::isDecimal);
  }

  private static boolean accessUsesDecimal(JavaAccess<?> access) {
    if (isDecimal(access.getTargetOwner())) {
      return true;
    }
    if (access instanceof JavaFieldAccess fieldAccess) {
      // 예외 표시를 단 필드는 그 클래스 안에서만 쓸 수 있다(예: record 생성자가 자기 필드를 채운다). 다른 클래스가 쓰려면 그쪽도 표시가 필요하다.
      boolean ownAllowedField =
          fieldAccess.getOriginOwner().equals(fieldAccess.getTargetOwner())
              && fieldAccess.getTarget().resolveMember().map(field -> allowsDecimal(field)).orElse(false);
      return !ownAllowedField && isDecimal(fieldAccess.getTarget().getRawType());
    }
    if (access.getTarget() instanceof CodeUnitCallTarget target) {
      return isDecimal(target.getRawReturnType())
          || target.getRawParameterTypes().stream().anyMatch(CommerceArchitectureRules::isDecimal);
    }
    return false;
  }

  private static boolean allowsDecimal(CanBeAnnotated element) {
    return element.isAnnotatedWith(ALLOW_DECIMAL);
  }

  private static boolean isController(JavaClass javaClass) {
    return javaClass.isAnnotatedWith(CONTROLLER) || javaClass.isMetaAnnotatedWith(CONTROLLER);
  }

  /** {@code @RequestMapping}과 그것을 단 표시({@code @GetMapping} 등)의 경로 값. */
  private static List<String> mappingPaths(CanBeAnnotated element) {
    List<String> paths = new ArrayList<>();
    Set<? extends JavaAnnotation<?>> annotations =
        element instanceof JavaClass javaClass
            ? javaClass.getAnnotations()
            : ((JavaMember) element).getAnnotations();
    for (JavaAnnotation<?> annotation : annotations) {
      JavaClass type = annotation.getRawType();
      if (!type.getName().equals(REQUEST_MAPPING) && !type.isAnnotatedWith(REQUEST_MAPPING)) {
        continue;
      }
      for (String attribute : List.of("value", "path")) {
        annotation
            .get(attribute)
            .filter(String[].class::isInstance)
            .map(String[].class::cast)
            .ifPresent(values -> paths.addAll(List.of(values)));
      }
    }
    return paths;
  }

  /** 클래스 경로와 메서드 경로를 이어 붙인 전체 경로. Spring은 둘을 이어서 연다. */
  private static List<String> fullPaths(JavaClass controller, List<String> classPaths) {
    List<String> prefixes = classPaths.isEmpty() ? List.of("") : classPaths;
    List<String> result = new ArrayList<>(prefixes);
    for (JavaMethod method : controller.getMethods()) {
      for (String methodPath : mappingPaths(method)) {
        for (String prefix : prefixes) {
          result.add(prefix + "/" + methodPath);
        }
      }
    }
    return result;
  }

  private static List<String> segments(String path) {
    return Stream.of(path.split("/")).filter(segment -> !segment.isEmpty()).toList();
  }

  /** 둘째 칸이 admin으로 시작하는 /api 경로. 비슷한 이름(/api/administrators)도 관리자 경로로 본다. */
  private boolean isAdminPath(String path) {
    List<String> segments = segments(path);
    return segments.size() >= 2
        && segments.get(0).equals("api")
        && segments.get(1).toLowerCase(Locale.ROOT).startsWith("admin");
  }

  /** 첫째·둘째 칸에 변수나 와일드카드가 있으면 /api/admin 요청과 겹칠 수 있다. */
  private static boolean mayOverlapAdminPath(String path) {
    return segments(path).stream()
        .limit(2)
        .anyMatch(segment -> segment.contains("{") || segment.contains("*"));
  }
}
