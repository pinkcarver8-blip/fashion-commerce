package io.github.pinkcarver8_blip.commerce.architecture;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 시나리오 11(R0-06): 실제 {@code lombok.config} 때문에 {@code @Setter}·{@code @Data}가 컴파일에 실패한다(ADR-010
 * 보충).
 *
 * <p>{@code @Setter}·{@code @Data}는 컴파일 뒤에 남지 않아 ArchUnit으로 볼 수 없다. 그래서 backend의 {@code
 * lombok.config}를 임시 폴더에 복사하고, 그 옆에 둔 예시 소스를 Lombok과 함께 직접 컴파일해 본다.
 */
class LombokConfigTest {

    private static final Path LOMBOK_CONFIG = Path.of("lombok.config");

    @Test
    void R0_06_11_Setter를_쓰면_컴파일이_실패한다(@TempDir Path dir) throws IOException {
        Result result =
                compile(
                        dir,
                        "Sample",
                        "import lombok.Setter; @Setter class Sample { private int x; }");

        assertThat(result.success()).isFalse();
        assertThat(result.errors())
                .anyMatch(message -> message.contains("@Setter") && message.contains("flagged"));
    }

    @Test
    void R0_06_11_Data를_쓰면_컴파일이_실패한다(@TempDir Path dir) throws IOException {
        Result result =
                compile(dir, "Sample", "import lombok.Data; @Data class Sample { private int x; }");

        assertThat(result.success()).isFalse();
        assertThat(result.errors())
                .anyMatch(message -> message.contains("@Data") && message.contains("flagged"));
    }

    /** 대조군: 같은 방법으로 @Getter는 컴파일된다. 위 실패가 Lombok 설정 때문이라는 것을 보인다. */
    @Test
    void R0_06_11_Getter는_컴파일된다(@TempDir Path dir) throws IOException {
        Result result =
                compile(
                        dir,
                        "Sample",
                        "import lombok.Getter; @Getter class Sample { private int x; }");

        assertThat(result.errors()).isEmpty();
        assertThat(result.success()).isTrue();
    }

    /** 하위 폴더의 lombok.config가 위 설정을 덮어쓰지 못하게, backend 루트 밖의 lombok.config를 두지 않는다. */
    @Test
    void R0_06_11_lombok_config는_backend_루트에_하나만_있다() throws IOException {
        assertThat(LOMBOK_CONFIG).exists();
        try (Stream<Path> paths = Files.walk(Path.of("src"))) {
            assertThat(paths.filter(path -> path.getFileName().toString().equals("lombok.config")))
                    .isEmpty();
        }
    }

    private record Result(boolean success, List<String> errors) {}

    private static Result compile(Path dir, String className, String source) throws IOException {
        Files.copy(LOMBOK_CONFIG, dir.resolve("lombok.config"));
        Path sourceFile = Files.writeString(dir.resolve(className + ".java"), source);
        Path out = Files.createDirectories(dir.resolve("out"));
        String lombokJar = System.getProperty("lombok.jar");
        assertThat(lombokJar).as("build.gradle이 lombok.jar 위치를 넘겨야 한다").isNotBlank();

        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files =
                javac.getStandardFileManager(diagnostics, Locale.ROOT, StandardCharsets.UTF_8)) {
            List<String> options =
                    List.of(
                            "-classpath",
                            lombokJar,
                            "-processorpath",
                            lombokJar,
                            "-d",
                            out.toString());
            boolean success =
                    javac.getTask(
                                    null,
                                    files,
                                    diagnostics,
                                    options,
                                    null,
                                    files.getJavaFileObjects(sourceFile))
                            .call();
            List<String> errors =
                    diagnostics.getDiagnostics().stream()
                            .filter(diagnostic -> diagnostic.getKind() == Diagnostic.Kind.ERROR)
                            .map(diagnostic -> diagnostic.getMessage(Locale.ROOT))
                            .toList();
            return new Result(success, errors);
        }
    }
}
