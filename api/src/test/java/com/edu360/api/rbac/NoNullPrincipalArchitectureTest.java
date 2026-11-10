package com.edu360.api.rbac;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

/**
 * Fails the build if production code checks a permission with no caller, for example
 * {@code new RbacPrincipal(tenantId, null)}. Such a check used to pass for everyone (audit
 * finding C3), so every permission check must carry the signed-in user.
 */
class NoNullPrincipalArchitectureTest {

    private static final Pattern NULL_PRINCIPAL = Pattern.compile("new\\s+RbacPrincipal\\([^,()]+,\\s*null\\s*\\)");

    @Test
    void productionCodeNeverBuildsAPrincipalWithoutAUser() throws IOException {
        Path main = Path.of("src", "main", "java");
        List<String> offenders;
        try (Stream<Path> files = Files.walk(main)) {
            offenders = files
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> {
                        try {
                            return NULL_PRINCIPAL.matcher(Files.readString(p)).find();
                        } catch (IOException e) {
                            throw new IllegalStateException(e);
                        }
                    })
                    .map(p -> main.relativize(p).toString())
                    .toList();
        }
        assertThat(offenders).as("files that check a permission with no user").isEmpty();
    }
}
