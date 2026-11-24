package io.github.pinkcarver8_blip.commerce.architecture;

import static io.github.pinkcarver8_blip.commerce.architecture.CommerceArchitectureRules.COMMERCE_ROOT;
import static org.assertj.core.api.Assertions.assertThat;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

/** 시나리오 9(R0-06): 실제 백엔드 코드가 모든 구조 규칙을 통과한다. */
class CommerceArchitectureTest {

  private static final JavaClasses MAIN_CLASSES =
      new ClassFileImporter()
          .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
          .importPackages(COMMERCE_ROOT);

  @TestFactory
  Stream<DynamicTest> R0_06_9_실제_코드는_모든_구조_규칙을_통과한다() {
    return new CommerceArchitectureRules(COMMERCE_ROOT)
        .all().stream()
            .map(
                rule ->
                    DynamicTest.dynamicTest(
                        rule.getDescription(), () -> rule.check(MAIN_CLASSES)));
  }

  /**
   * 규칙의 모듈 목록과 실제 모듈 패키지(폴더)가 같다. 설명만 있는 package-info.java는 클래스 파일을 만들지 않아서 소스 폴더로 확인한다. 폴더 이름을
   * 잘못 지으면 규칙이 그 모듈을 검사하지 못하고 지나가는 것을 막는다.
   */
  @Test
  void R0_06_9_모듈_패키지는_B1의_R1_모듈과_common이다() throws IOException {
    Path rootDir = Path.of("src/main/java", COMMERCE_ROOT.replace('.', '/'));
    Set<String> modules;
    try (Stream<Path> children = Files.list(rootDir)) {
      modules =
          children
              .filter(Files::isDirectory)
              .map(path -> path.getFileName().toString())
              .collect(Collectors.toSet());
    }

    Set<String> expected =
        Stream.concat(
                CommerceArchitectureRules.ALLOWED_CALLS.keySet().stream(),
                Stream.of(CommerceArchitectureRules.COMMON))
            .collect(Collectors.toSet());
    assertThat(modules).isEqualTo(expected);
    assertThat(MAIN_CLASSES).as("실제 코드를 읽어 와야 한다").isNotEmpty();
  }
}
