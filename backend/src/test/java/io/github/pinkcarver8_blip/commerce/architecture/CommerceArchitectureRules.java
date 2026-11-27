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
 * <p>규칙은 패키지 뿌리({@code root})를 받아 만든다. 실제 코드는 {@link #COMMERCE_ROOT}로, 규칙 위반 예시는 {@code
 * archfixture} 아래 패키지로 검사한다. 그래서 실제 코드와 예시가 같은 규칙을 거친다.
 */
final class CommerceArchitectureRules {

    static final String COMMERCE_ROOT = "io.github.pinkcarver8_blip.commerce";

    /**
     * 공유 모듈(ADR-001 보충): 업무 책임 없이 코드만 나눠 쓰는 작은 모듈. 값은 그 모듈이 부를 수 있는 다른 공유 모듈이다. 업무 모듈은 {@code
     * platform}을 뺀 공유 모듈을 모두 부를 수 있고, {@code platform}은 아무도 부를 수 없다. 공유 모듈은 업무 모듈을 부르지 않는다.
     */
    static final Map<String, Set<String>> SHARED_CALLS =
            Map.of(
                    "money", Set.of(),
                    "id", Set.of(),
                    "error", Set.of(),
                    "identity", Set.of(),
                    "platform", Set.of("identity", "error"));

    /** 내용을 허용 목록으로 고정하는 공유 모듈. platform은 아무도 부를 수 없어서 클래스 이름만 목록으로 본다. */
    static final Set<String> LISTED_SHARED_MODULES = Set.of("money", "id", "error", "identity");

    static final String PLATFORM = "platform";

    /**
     * ADR-001 동기 호출 목록 중 R1에 도입하는 것(보충의 {@code shipping → inventory} 포함). 왼쪽 모듈은 오른쪽 모듈의 공개 API만 부를
     * 수 있다. R2b 이후 모듈(coupon, drop, outbox, seller, settlement)은 도입할 때 ADR-001을 확인하고 여기에 더한다.
     */
    static final Map<String, Set<String>> ALLOWED_CALLS =
            Map.of(
                    "admin",
                            Set.of(
                                    "catalog",
                                    "inventory",
                                    "cart",
                                    "order",
                                    "payment",
                                    "shipping",
                                    "claim",
                                    "member"),
                    "claim", Set.of("order", "payment", "inventory"),
                    "payment", Set.of("order", "inventory"),
                    "shipping", Set.of("order", "inventory"),
                    "order", Set.of("catalog", "inventory", "cart", "member"),
                    "cart", Set.of("catalog"),
                    "inventory", Set.of(),
                    "catalog", Set.of(),
                    "member", Set.of());

    private static final String ALLOW_DECIMAL = COMMERCE_ROOT + ".money.AllowDecimal";

    private static final Set<String> DECIMAL_TYPES =
            Set.of(
                    "double",
                    "float",
                    "java.lang.Double",
                    "java.lang.Float",
                    "java.math.BigDecimal",
                    "java.util.OptionalDouble",
                    "java.util.DoubleSummaryStatistics",
                    "java.util.stream.DoubleStream",
                    "java.util.concurrent.atomic.DoubleAdder",
                    "java.util.concurrent.atomic.DoubleAccumulator");

    private static final String CONTROLLER = "org.springframework.stereotype.Controller";
    private static final String REQUEST_MAPPING =
            "org.springframework.web.bind.annotation.RequestMapping";

    private static final Set<String> QUERYDSL_WRITE_METHODS = Set.of("update", "delete", "insert");

    /** 공유 모듈에 둘 수 있는 데이터 접근 도구가 아닌 것을 가리기 위한 패키지 목록. */
    private static final List<String> DATA_ACCESS_PACKAGES =
            List.of(
                    "jakarta.persistence",
                    "org.springframework.data",
                    "org.springframework.jdbc",
                    "org.springframework.orm",
                    "org.hibernate",
                    "com.querydsl",
                    "java.sql",
                    "javax.sql");

    private static final String COMPONENT = "org.springframework.stereotype.Component";
    private static final String SERVICE = "org.springframework.stereotype.Service";
    private static final String REPOSITORY = "org.springframework.stereotype.Repository";
    private static final Set<String> TRANSACTIONAL =
            Set.of(
                    "org.springframework.transaction.annotation.Transactional",
                    "jakarta.transaction.Transactional");

    private final String root;

    /** 공유 모듈 허용 목록. 항목은 루트 아래 이름이다(예: {@code money.Money}, {@code money.Money.plus(…Money)}). */
    private final Set<String> sharedAllowlist;

    CommerceArchitectureRules(String root) {
        this(root, Set.of());
    }

    CommerceArchitectureRules(String root, Set<String> sharedAllowlist) {
        this.root = root;
        this.sharedAllowlist = sharedAllowlist;
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
                noLombokEqualsHashCodeToStringOnEntities(),
                sharedModulesStayMinimal(),
                sharedModulesMatchAllowlist(),
                onlyPlatformConfiguresSecurity(),
                onlyPlatformCreatesPrincipals());
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
                                    for (Dependency dependency :
                                            origin.getDirectDependenciesFromSelf()) {
                                        JavaClass target =
                                                dependency.getTargetClass().getBaseComponentType();
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
                                                            dependency,
                                                            problem
                                                                    + ": "
                                                                    + dependency.getDescription()));
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
        if (!isKnownModule(to)) {
            return "B1·ADR-001 목록에 없는 모듈 패키지 '" + to + "'에 의존한다";
        }
        if (SHARED_CALLS.containsKey(from)) {
            if (!SHARED_CALLS.containsKey(to)) {
                return "공유 모듈 " + from + "은 업무 모듈에 의존하지 않는다";
            }
            return SHARED_CALLS.get(from).contains(to)
                    ? null
                    : "공유 모듈 " + from + " → " + to + " 방향은 정해 둔 목록(SHARED_CALLS)에 없다";
        }
        if (PLATFORM.equals(to)) {
            return "platform은 아무도 부를 수 없다";
        }
        if (SHARED_CALLS.containsKey(to)) {
            return null;
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
                                    List<JavaType> supertypes =
                                            new ArrayList<>(apiClass.getInterfaces());
                                    apiClass.getSuperclass().ifPresent(supertypes::add);
                                    supertypes.stream()
                                            .flatMap(type -> type.getAllInvolvedRawTypes().stream())
                                            .filter(type -> isInternalOf(type, module.get()))
                                            .distinct()
                                            .forEach(
                                                    internal ->
                                                            events.add(
                                                                    SimpleConditionEvent.violated(
                                                                            apiClass,
                                                                            apiClass.getName()
                                                                                    + "가 상속·구현으로 내부 타입 "
                                                                                    + internal
                                                                                            .getName()
                                                                                    + "을 드러낸다")));
                                    List<JavaMember> members =
                                            new ArrayList<>(apiClass.getAllMethods());
                                    members.addAll(apiClass.getAllFields());
                                    members.addAll(apiClass.getConstructors());
                                    for (JavaMember member : members) {
                                        if (!member.getModifiers().contains(JavaModifier.PUBLIC)) {
                                            continue;
                                        }
                                        signatureTypes(member)
                                                .flatMap(
                                                        type ->
                                                                type
                                                                        .getAllInvolvedRawTypes()
                                                                        .stream())
                                                .map(JavaClass::getBaseComponentType)
                                                .filter(type -> isInternalOf(type, module.get()))
                                                .distinct()
                                                .forEach(
                                                        internal ->
                                                                events.add(
                                                                        SimpleConditionEvent
                                                                                .violated(
                                                                                        member,
                                                                                        member
                                                                                                        .getFullName()
                                                                                                + "가 내부 타입 "
                                                                                                + internal
                                                                                                        .getName()
                                                                                                + "을 드러낸다")));
                                    }
                                }));
    }

    /**
     * 시나리오 4: 관리자 컨트롤러는 admin 모듈에만 있고, 클래스 경로는 정확히 {@code /api/admin}이거나 그 아래다. 보안 설정은 {@code
     * /api/admin/**}만 관리자 체인으로 보내므로(ADR-008), {@code /api/admins} 같은 비슷한 경로는 관리자 화면인데 고객 체인에 걸린다.
     * admin 밖의 클래스·인터페이스는 관리자처럼 보이는 경로({@code /api/admin…})나 관리자 경로와 겹칠 수 있는 패턴(둘째 칸까지의 변수· {@code
     * *}·{@code ?})을 열지 않는다. Spring은 상위 클래스·인터페이스의 경로 표시도 합쳐 쓰므로 그것까지 본다(ADR-001·008).
     */
    ArchRule adminControllersOnlyInAdmin() {
        return classes()
                .should(
                        condition(
                                "관리자 컨트롤러는 admin 모듈에만 있고 /api/admin 경로만 연다",
                                (type, events) -> {
                                    if (!inRoot(type)) {
                                        return;
                                    }
                                    boolean controller = isController(type);
                                    List<String> classPaths = hierarchyClassPaths(type);
                                    List<String> methodPaths = hierarchyMethodPaths(type);
                                    if (!controller
                                            && classPaths.isEmpty()
                                            && methodPaths.isEmpty()) {
                                        return;
                                    }
                                    boolean inAdmin =
                                            moduleOf(type).filter("admin"::equals).isPresent();
                                    if (inAdmin) {
                                        if (controller
                                                && (classPaths.isEmpty()
                                                        || !classPaths.stream()
                                                                .allMatch(
                                                                        this
                                                                                ::isExactAdminPrefix))) {
                                            events.add(
                                                    SimpleConditionEvent.violated(
                                                            type,
                                                            "admin 모듈의 컨트롤러는 클래스에 /api/admin으로 시작하는 경로를 단다: "
                                                                    + type.getName()
                                                                    + " "
                                                                    + classPaths));
                                        }
                                        return;
                                    }
                                    for (String path : fullPaths(classPaths, methodPaths)) {
                                        if (looksLikeAdminPath(path)) {
                                            events.add(
                                                    SimpleConditionEvent.violated(
                                                            type,
                                                            "관리자 경로를 admin 모듈 밖에서 연다: "
                                                                    + type.getName()
                                                                    + " "
                                                                    + path));
                                        } else if (mayOverlapAdminPath(path)) {
                                            events.add(
                                                    SimpleConditionEvent.violated(
                                                            type,
                                                            "관리자 경로와 겹칠 수 있는 패턴을 admin 모듈 밖에서 연다: "
                                                                    + type.getName()
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
                                    if (!inRoot(origin)
                                            || isInQueryPackage(origin)
                                            || isQueryDslGenerated(origin)) {
                                        return;
                                    }
                                    for (Dependency dependency :
                                            origin.getDirectDependenciesFromSelf()) {
                                        if (dependency
                                                .getTargetClass()
                                                .getPackageName()
                                                .startsWith("com.querydsl")) {
                                            events.add(
                                                    SimpleConditionEvent.violated(
                                                            dependency,
                                                            "조회 전용 패키지 밖에서 QueryDSL을 쓴다: "
                                                                    + dependency.getDescription()));
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
                                    for (Dependency dependency :
                                            origin.getDirectDependenciesFromSelf()) {
                                        if (dependency
                                                .getTargetClass()
                                                .getBaseComponentType()
                                                .isAssignableTo(
                                                        "com.querydsl.core.dml.DMLClause")) {
                                            events.add(
                                                    SimpleConditionEvent.violated(
                                                            dependency,
                                                            "QueryDSL 쓰기 절을 쓴다: "
                                                                    + dependency.getDescription()));
                                        }
                                    }
                                    for (JavaMethodCall call : origin.getMethodCallsFromSelf()) {
                                        if (QUERYDSL_WRITE_METHODS.contains(call.getName())
                                                && call.getTargetOwner()
                                                        .isAssignableTo(
                                                                "com.querydsl.jpa.JPQLQueryFactory")) {
                                            events.add(
                                                    SimpleConditionEvent.violated(
                                                            call,
                                                            "QueryDSL 쓰기 메서드를 부른다: "
                                                                    + call.getDescription()));
                                        }
                                    }
                                }));
    }

    /**
     * 시나리오 6: 소수 타입({@code double}·{@code float}·{@code BigDecimal})을 필드·매개변수·반환 타입·호출에 쓰지 않는다. 돈인지
     * 따지지 않는다. 예외는 {@code @AllowDecimal}을 단 멤버뿐이다(ADR-010 보충).
     */
    ArchRule noDecimalTypes() {
        return classes()
                .should(
                        condition(
                                "소수 타입(double·float·BigDecimal)을 쓰지 않는다(예외는 @AllowDecimal)",
                                (owner, events) -> {
                                    // Q클래스는 엔티티 필드를 따라 만들어지고, 그 필드는 엔티티에서 이미 검사한다.
                                    if (!inRoot(owner) || isQueryDslGenerated(owner)) {
                                        return;
                                    }
                                    for (JavaField field : owner.getFields()) {
                                        if (!allowsDecimal(field)
                                                && involvesDecimal(field.getType())) {
                                            events.add(
                                                    SimpleConditionEvent.violated(
                                                            field,
                                                            "소수 타입 필드: " + field.getFullName()));
                                        }
                                    }
                                    for (JavaCodeUnit codeUnit : owner.getCodeUnits()) {
                                        if (allowsDecimal(codeUnit)) {
                                            continue;
                                        }
                                        if (involvesDecimal(codeUnit.getReturnType())) {
                                            events.add(
                                                    SimpleConditionEvent.violated(
                                                            codeUnit,
                                                            "소수 타입 반환: " + codeUnit.getFullName()));
                                        }
                                        for (JavaParameter parameter : codeUnit.getParameters()) {
                                            if (!allowsDecimal(parameter)
                                                    && involvesDecimal(parameter.getType())) {
                                                events.add(
                                                        SimpleConditionEvent.violated(
                                                                codeUnit,
                                                                "소수 타입 매개변수: "
                                                                        + codeUnit.getFullName()));
                                            }
                                        }
                                        for (JavaAccess<?> access :
                                                codeUnit.getAccessesFromSelf()) {
                                            if (accessUsesDecimal(access)) {
                                                events.add(
                                                        SimpleConditionEvent.violated(
                                                                access,
                                                                "소수 타입을 쓰는 호출: "
                                                                        + access.getDescription()));
                                            }
                                        }
                                    }
                                }));
    }

    /**
     * 시나리오 7: 주문상품({@code order} 모듈의 {@code OrderItem})은 만든 뒤 바뀌지 않는다(ADR-003). 필드는 그 클래스의 생성자에서만
     * 쓰고, 필드 타입은 안에서 값을 바꿀 수 없는 것(기본형, 문자열, enum, record, 시간·UUID, 연관 엔티티)만 쓴다. 목록·배열·일반 객체 필드는 생성자
     * 밖에서 내용을 바꿀 수 있어서 막는다.
     */
    ArchRule orderItemFieldsWrittenOnlyInConstructor() {
        return classes()
                .should(
                        condition(
                                "주문상품(OrderItem) 필드는 생성자에서만 쓰고, 바꿀 수 없는 타입만 쓴다",
                                (origin, events) -> {
                                    if (isOrderClass(origin, "OrderItem")) {
                                        for (JavaField field : origin.getFields()) {
                                            if (!field.getModifiers().contains(JavaModifier.STATIC)
                                                    && !isUnmodifiableFieldType(
                                                            field.getRawType())) {
                                                events.add(
                                                        SimpleConditionEvent.violated(
                                                                field,
                                                                "주문상품 필드 타입은 내용을 바꿀 수 있다: "
                                                                        + field.getFullName()
                                                                        + " ("
                                                                        + field.getRawType()
                                                                                .getName()
                                                                        + ")"));
                                            }
                                        }
                                    }
                                    for (JavaFieldAccess access :
                                            origin.getFieldAccessesFromSelf()) {
                                        JavaClass fieldOwner = access.getTargetOwner();
                                        if (access.getAccessType() != AccessType.SET
                                                || !isOrderClass(fieldOwner, "OrderItem")) {
                                            continue;
                                        }
                                        boolean inOwnConstructor =
                                                access.getOrigin() instanceof JavaConstructor
                                                        && access.getOriginOwner()
                                                                .equals(fieldOwner);
                                        if (!inOwnConstructor) {
                                            events.add(
                                                    SimpleConditionEvent.violated(
                                                            access,
                                                            "주문상품 필드를 생성자 밖에서 쓴다: "
                                                                    + access.getDescription()));
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
                                    for (JavaFieldAccess access :
                                            origin.getFieldAccessesFromSelf()) {
                                        JavaClass fieldOwner = access.getTargetOwner();
                                        boolean isStatusField =
                                                access.getTarget()
                                                        .getRawType()
                                                        .getSimpleName()
                                                        .equals("OrderStatus");
                                        if (access.getAccessType() == AccessType.SET
                                                && isOrderClass(fieldOwner, "Order")
                                                && isStatusField
                                                && !access.getOriginOwner().equals(fieldOwner)) {
                                            events.add(
                                                    SimpleConditionEvent.violated(
                                                            access,
                                                            "주문 상태를 Order 밖에서 쓴다: "
                                                                    + access.getDescription()));
                                        }
                                    }
                                }));
    }

    /**
     * 시나리오 10: JPA 엔티티에 Lombok이 만든 equals·hashCode·toString이 없다(ADR-010 보충).
     * {@code @EqualsAndHashCode}· {@code @ToString}은 컴파일 뒤에 남지 않지만, Lombok이 만든 메서드에 붙이는
     * {@code @lombok.Generated}(lombok.config)로 찾는다.
     */
    ArchRule noLombokEqualsHashCodeToStringOnEntities() {
        return classes()
                .should(
                        condition(
                                "엔티티에 Lombok equals·hashCode·toString을 쓰지 않는다",
                                (entity, events) -> {
                                    if (!entity.isAnnotatedWith("jakarta.persistence.Entity")
                                            && !entity.isAnnotatedWith(
                                                    "jakarta.persistence.MappedSuperclass")) {
                                        return;
                                    }
                                    for (JavaMethod method : entity.getMethods()) {
                                        boolean objectMethod =
                                                Set.of("equals", "hashCode", "toString")
                                                        .contains(method.getName());
                                        if (objectMethod
                                                && method.isAnnotatedWith("lombok.Generated")) {
                                            events.add(
                                                    SimpleConditionEvent.violated(
                                                            method,
                                                            "엔티티에 Lombok이 만든 메서드가 있다: "
                                                                    + method.getFullName()));
                                        }
                                    }
                                }));
    }

    /**
     * 시나리오 12(공유 모듈): 공유 모듈은 업무 처리를 담지 않는다. DB 도구를 쓰지 않고, {@code @Service}·{@code @Repository}·
     * {@code @Transactional}을 쓰지 않는다. 목록에 고정하는 공유 모듈(money·id·error·identity)은 Spring
     * 부품({@code @Component} 계열)도 아니고, 정적 함수는 자기 타입을 만드는 함수만 둔다(예: {@code Money.of(1000)}은 되고 {@code
     * Money.fee(…)}는 안 된다).
     */
    ArchRule sharedModulesStayMinimal() {
        return classes()
                .should(
                        condition(
                                "공유 모듈은 DB·업무 처리 없이 값과 앱 설정만 담는다",
                                (type, events) -> {
                                    Optional<String> module =
                                            moduleOf(type).filter(SHARED_CALLS::containsKey);
                                    if (module.isEmpty()) {
                                        return;
                                    }
                                    for (Dependency dependency :
                                            type.getDirectDependenciesFromSelf()) {
                                        String target =
                                                dependency.getTargetClass().getPackageName();
                                        if (DATA_ACCESS_PACKAGES.stream()
                                                .anyMatch(
                                                        name ->
                                                                target.equals(name)
                                                                        || target.startsWith(
                                                                                name + "."))) {
                                            events.add(
                                                    SimpleConditionEvent.violated(
                                                            dependency,
                                                            "공유 모듈이 DB 도구를 쓴다: "
                                                                    + dependency.getDescription()));
                                        }
                                    }
                                    if (hasAnnotation(type, SERVICE)
                                            || hasAnnotation(type, REPOSITORY)) {
                                        events.add(
                                                SimpleConditionEvent.violated(
                                                        type,
                                                        "공유 모듈에 업무 처리 부품(@Service·@Repository)이 있다: "
                                                                + type.getName()));
                                    }
                                    boolean transactional =
                                            TRANSACTIONAL.stream()
                                                            .anyMatch(
                                                                    name ->
                                                                            hasAnnotation(
                                                                                    type, name))
                                                    || type.getCodeUnits().stream()
                                                            .anyMatch(
                                                                    unit ->
                                                                            TRANSACTIONAL.stream()
                                                                                    .anyMatch(
                                                                                            unit
                                                                                                    ::isAnnotatedWith));
                                    if (transactional) {
                                        events.add(
                                                SimpleConditionEvent.violated(
                                                        type,
                                                        "공유 모듈에 @Transactional이 있다: "
                                                                + type.getName()));
                                    }
                                    if (!LISTED_SHARED_MODULES.contains(module.get())) {
                                        return;
                                    }
                                    if (hasAnnotation(type, COMPONENT)) {
                                        events.add(
                                                SimpleConditionEvent.violated(
                                                        type,
                                                        "값 공유 모듈에 Spring 부품(@Component 계열)이 있다: "
                                                                + type.getName()));
                                    }
                                    for (JavaMethod method : type.getMethods()) {
                                        boolean isStatic =
                                                method.getModifiers().contains(JavaModifier.STATIC);
                                        boolean synthetic =
                                                method.getModifiers()
                                                        .contains(JavaModifier.SYNTHETIC);
                                        boolean makesOwnType =
                                                method.getRawReturnType()
                                                        .getBaseComponentType()
                                                        .equals(type);
                                        if (isStatic && !synthetic && !makesOwnType) {
                                            events.add(
                                                    SimpleConditionEvent.violated(
                                                            method,
                                                            "값 공유 모듈의 정적 함수는 자기 타입을 만드는 함수만 둔다: "
                                                                    + method.getFullName()));
                                        }
                                    }
                                }));
    }

    /**
     * 시나리오 12(공유 모듈): 공유 모듈의 클래스와 공개 멤버는 허용 목록과 똑같다. 목록에 없는 것이 생기거나, 목록에 있는데 코드에 없으면 실패한다. 목록을 바꾸는
     * 일은 단위를 시작할 때 사용자 승인을 받는다(작업진행.md A3-8). platform은 클래스 이름만 본다. 컴파일러가 만드는 멤버(record의
     * equals·hashCode·toString, enum의 values·valueOf)는 업무 함수가 아니라 목록에서 뺀다.
     */
    ArchRule sharedModulesMatchAllowlist() {
        return classes()
                .should(
                        new ArchCondition<JavaClass>("공유 모듈의 클래스와 공개 멤버는 허용 목록과 같다") {
                            private final Set<String> present = new java.util.HashSet<>();

                            @Override
                            public void check(JavaClass type, ConditionEvents events) {
                                sharedEntries(type)
                                        .peek(present::add)
                                        .filter(entry -> !sharedAllowlist.contains(entry))
                                        .forEach(
                                                entry ->
                                                        events.add(
                                                                SimpleConditionEvent.violated(
                                                                        type,
                                                                        "허용 목록에 없다: " + entry)));
                            }

                            @Override
                            public void finish(ConditionEvents events) {
                                sharedAllowlist.stream()
                                        .filter(entry -> !present.contains(entry))
                                        .sorted()
                                        .forEach(
                                                entry ->
                                                        events.add(
                                                                SimpleConditionEvent.violated(
                                                                        entry,
                                                                        "허용 목록에 있지만 코드에 없다: "
                                                                                + entry)));
                            }
                        });
    }

    /** 보안 필터 체인을 만드는 Spring 타입. platform 밖에서 쓰면 모듈마다 따로 길을 여는 뒷문이 된다. */
    private static final Set<String> SECURITY_CONFIG_TYPES =
            Set.of(
                    "org.springframework.security.config.annotation.web.builders.HttpSecurity",
                    "org.springframework.security.web.SecurityFilterChain",
                    "org.springframework.security.config.annotation.web.configuration.WebSecurityCustomizer");

    /**
     * 시나리오 13(R1-01, 공유 보안 설정 막는 장치 4번): 보안 설정(필터 체인, {@code HttpSecurity})은 platform에서만 만든다. 공개
     * 경로는 platform의 목록 한 곳에서만 연다(.claude/rules/shared-modules.md).
     */
    ArchRule onlyPlatformConfiguresSecurity() {
        return classes()
                .should(
                        condition(
                                "보안 설정(필터 체인)은 platform에서만 만든다",
                                (origin, events) -> {
                                    if (!inRoot(origin)
                                            || moduleOf(origin)
                                                    .filter(PLATFORM::equals)
                                                    .isPresent()) {
                                        return;
                                    }
                                    for (Dependency dependency :
                                            origin.getDirectDependenciesFromSelf()) {
                                        String target =
                                                dependency
                                                        .getTargetClass()
                                                        .getBaseComponentType()
                                                        .getName();
                                        if (SECURITY_CONFIG_TYPES.contains(target)) {
                                            events.add(
                                                    SimpleConditionEvent.violated(
                                                            dependency,
                                                            "platform 밖에서 보안 설정을 만든다: "
                                                                    + dependency.getDescription()));
                                        }
                                    }
                                }));
    }

    /**
     * 시나리오 14(R1-01, 공유 보안 설정 막는 장치 2번): 로그인한 사람 정보(identity의 주체)는 platform의 토큰 변환기만 만든다. 업무 모듈이 요청
     * 값으로 주체를 만들면 남의 회원 ID로 행세할 수 있다(S2, S12).
     */
    ArchRule onlyPlatformCreatesPrincipals() {
        return classes()
                .should(
                        condition(
                                "identity의 주체는 platform에서만 만든다",
                                (origin, events) -> {
                                    if (moduleOf(origin).filter(PLATFORM::equals).isPresent()) {
                                        return;
                                    }
                                    for (var call : origin.getConstructorCallsFromSelf()) {
                                        JavaClass owner = call.getTargetOwner();
                                        boolean principal =
                                                moduleOf(owner)
                                                                .filter("identity"::equals)
                                                                .isPresent()
                                                        && owner.getSimpleName()
                                                                .endsWith("Principal");
                                        if (principal && !owner.equals(origin)) {
                                            events.add(
                                                    SimpleConditionEvent.violated(
                                                            call,
                                                            "platform 밖에서 주체를 만든다: "
                                                                    + call.getDescription()));
                                        }
                                    }
                                }));
    }

    /** 한 클래스가 허용 목록에 차지하는 항목들. 공유 모듈 밖이면 없다. */
    private Stream<String> sharedEntries(JavaClass type) {
        Optional<String> module = moduleOf(type).filter(SHARED_CALLS::containsKey);
        if (module.isEmpty()) {
            return Stream.empty();
        }
        String className = relativeName(type.getName());
        if (!LISTED_SHARED_MODULES.contains(module.get())) {
            return Stream.of(className);
        }
        Stream<String> members =
                type.getMembers().stream()
                        .filter(member -> member.getModifiers().contains(JavaModifier.PUBLIC))
                        .filter(member -> !member.getModifiers().contains(JavaModifier.SYNTHETIC))
                        .filter(member -> !isCompilerGenerated(type, member))
                        .map(member -> relativeName(member.getFullName()));
        return Stream.concat(Stream.of(className), members);
    }

    private static boolean isCompilerGenerated(JavaClass type, JavaMember member) {
        String name = member.getName();
        boolean objectMethod =
                member instanceof JavaMethod
                        && (name.equals("equals")
                                || name.equals("hashCode")
                                || name.equals("toString"));
        boolean enumMethod =
                type.isEnum()
                        && member instanceof JavaMethod
                        && (name.equals("values") || name.equals("valueOf"));
        boolean enumConstant =
                type.isEnum()
                        && member instanceof JavaField field
                        && field.getRawType().equals(type);
        return (objectMethod && type.isAssignableTo("java.lang.Record"))
                || enumMethod
                || enumConstant;
    }

    private String relativeName(String fullName) {
        return fullName.replace(root + ".", "");
    }

    private static boolean hasAnnotation(JavaClass type, String annotation) {
        return type.isAnnotatedWith(annotation) || type.isMetaAnnotatedWith(annotation);
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
        return SHARED_CALLS.containsKey(module) || ALLOWED_CALLS.containsKey(module);
    }

    /** 같은 모듈의 클래스인데 공개 API 패키지 밖에 있다. */
    private boolean isInternalOf(JavaClass type, String module) {
        JavaClass base = type.getBaseComponentType();
        return inRoot(base)
                && moduleOf(base).filter(module::equals).isPresent()
                && !isPublicApi(base, module);
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

    private static final Set<String> UNMODIFIABLE_TYPES =
            Set.of(
                    "java.lang.String",
                    "java.lang.Long",
                    "java.lang.Integer",
                    "java.lang.Short",
                    "java.lang.Byte",
                    "java.lang.Boolean",
                    "java.lang.Character",
                    "java.time.Instant",
                    "java.time.LocalDate",
                    "java.util.UUID");

    private static boolean isUnmodifiableFieldType(JavaClass type) {
        if (type.isArray()) {
            return false;
        }
        return type.isPrimitive()
                || UNMODIFIABLE_TYPES.contains(type.getName())
                || type.isEnum()
                || type.isAssignableTo("java.lang.Record")
                || type.isAnnotatedWith("jakarta.persistence.Entity");
    }

    /**
     * 빌드가 만드는 Q클래스: 이름이 Q + X이고 QueryDSL {@code Path}를 구현하며, 같은 패키지에 JPA 표시가 붙은 X가 있다. 손으로 만든 Q 이름
     * 클래스가 규칙을 피하지 못하게 X까지 확인한다.
     */
    private static boolean isQueryDslGenerated(JavaClass javaClass) {
        String name = javaClass.getSimpleName();
        if (!name.startsWith("Q") || !javaClass.isAssignableTo("com.querydsl.core.types.Path")) {
            return false;
        }
        String source = name.substring(1);
        return javaClass.getPackage().getClasses().stream()
                .filter(candidate -> candidate.getSimpleName().equals(source))
                .anyMatch(
                        candidate ->
                                candidate.isAnnotatedWith("jakarta.persistence.Entity")
                                        || candidate.isAnnotatedWith(
                                                "jakarta.persistence.Embeddable")
                                        || candidate.isAnnotatedWith(
                                                "jakarta.persistence.MappedSuperclass"));
    }

    private static Stream<JavaType> signatureTypes(JavaMember member) {
        if (member instanceof JavaField field) {
            return Stream.of(field.getType());
        }
        if (member instanceof JavaCodeUnit codeUnit) {
            List<JavaType> types = new ArrayList<>();
            types.add(codeUnit.getReturnType());
            codeUnit.getParameters().forEach(parameter -> types.add(parameter.getType()));
            types.addAll(codeUnit.getThrowsClause().getTypes());
            return types.stream();
        }
        return Stream.empty();
    }

    private static boolean isDecimal(JavaClass type) {
        JavaClass base = type.getBaseComponentType();
        String name = base.getName();
        if (DECIMAL_TYPES.contains(name)) {
            return true;
        }
        // double을 다루는 함수형 타입(DoubleUnaryOperator, ToDoubleFunction 등)
        return base.getPackageName().equals("java.util.function")
                && (base.getSimpleName().startsWith("Double")
                        || base.getSimpleName().startsWith("ToDouble"));
    }

    private static boolean involvesDecimal(JavaType type) {
        return type.getAllInvolvedRawTypes().stream()
                .anyMatch(CommerceArchitectureRules::isDecimal);
    }

    private static boolean accessUsesDecimal(JavaAccess<?> access) {
        if (isDecimal(access.getTargetOwner())) {
            return true;
        }
        if (access instanceof JavaFieldAccess fieldAccess) {
            // 예외 표시를 단 필드는 표시 없이 쓸 수 없다. 단, 그 클래스의 생성자는 자기 필드를 채울 수 있다(예: record 생성자).
            // 표시를 단 메서드는 이 검사까지 오지 않는다(noDecimalTypes에서 건너뛴다).
            boolean ownConstructorFillsAllowedField =
                    fieldAccess.getOrigin() instanceof JavaConstructor
                            && fieldAccess.getOriginOwner().equals(fieldAccess.getTargetOwner())
                            && fieldAccess
                                    .getTarget()
                                    .resolveMember()
                                    .map(field -> allowsDecimal(field))
                                    .orElse(false);
            return !ownConstructorFillsAllowedField
                    && isDecimal(fieldAccess.getTarget().getRawType());
        }
        if (access.getTarget() instanceof CodeUnitCallTarget target) {
            return isDecimal(target.getRawReturnType())
                    || target.getRawParameterTypes().stream()
                            .anyMatch(CommerceArchitectureRules::isDecimal);
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

    /** 자기 자신과 모든 상위 클래스·인터페이스에 붙은 클래스 경로. */
    private static List<String> hierarchyClassPaths(JavaClass type) {
        List<String> paths = new ArrayList<>(mappingPaths(type));
        type.getAllRawSuperclasses().forEach(superclass -> paths.addAll(mappingPaths(superclass)));
        type.getAllRawInterfaces().forEach(anInterface -> paths.addAll(mappingPaths(anInterface)));
        return paths;
    }

    /** 자기 메서드와 물려받은 메서드에 붙은 메서드 경로. */
    private static List<String> hierarchyMethodPaths(JavaClass type) {
        List<String> paths = new ArrayList<>();
        type.getAllMethods().forEach(method -> paths.addAll(mappingPaths(method)));
        return paths;
    }

    /** 클래스 경로와 메서드 경로를 이어 붙인 전체 경로. Spring은 둘을 이어서 연다. */
    private static List<String> fullPaths(List<String> classPaths, List<String> methodPaths) {
        List<String> prefixes = classPaths.isEmpty() ? List.of("") : classPaths;
        List<String> result = new ArrayList<>(prefixes);
        for (String methodPath : methodPaths) {
            for (String prefix : prefixes) {
                result.add(prefix + "/" + methodPath);
            }
        }
        return result;
    }

    private static List<String> segments(String path) {
        return Stream.of(path.split("/")).filter(segment -> !segment.isEmpty()).toList();
    }

    /** admin 컨트롤러의 클래스 경로: 첫째 칸 api, 둘째 칸 정확히 admin. 보안 설정의 /api/admin/** 와 같다. */
    private boolean isExactAdminPrefix(String path) {
        List<String> segments = segments(path);
        return segments.size() >= 2
                && segments.get(0).equals("api")
                && segments.get(1).equals("admin");
    }

    /** admin 밖에서 막는 경로: 둘째 칸이 admin으로 시작하는 /api 경로. 비슷한 이름(/api/administrators)도 막는다. */
    private boolean looksLikeAdminPath(String path) {
        List<String> segments = segments(path);
        return segments.size() >= 2
                && segments.get(0).equals("api")
                && segments.get(1).toLowerCase(Locale.ROOT).startsWith("admin");
    }

    /** 첫째·둘째 칸에 변수나 와일드카드(중괄호, *, ?)가 있으면 /api/admin 요청과 겹칠 수 있다. */
    private static boolean mayOverlapAdminPath(String path) {
        return segments(path).stream()
                .limit(2)
                .anyMatch(
                        segment ->
                                segment.contains("{")
                                        || segment.contains("*")
                                        || segment.contains("?"));
    }
}
