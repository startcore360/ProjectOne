package com.edu360.api.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class InstitutionCodeFormatTest {
    @Test
    void S2_ID_format_acceptsInstitutionSessionAndSequenceTokens() {
        var format = InstitutionCodeFormat.parse("{inst}-{yy}-{seq:5}");
        assertThat(format.render("abc", 2026, 1)).isEqualTo("ABC-26-00001");
    }

    @Test
    void S2_ID_format_requiresExactlyOneSequenceToken() {
        assertThatThrownBy(() -> InstitutionCodeFormat.parse("{INST}-{YY}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstitutionCodeFormat.parse("{SEQ:4}-{SEQ:5}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void S2_ID_format_rejectsUnknownTokensAndIllegalLiteralCharacters() {
        assertThatThrownBy(() -> InstitutionCodeFormat.parse("{INST}-{MM}-{SEQ:4}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstitutionCodeFormat.parse("ABC space-{SEQ:4}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void S2_ID_format_enforcesSequenceWidthAndBoundaryCharacters() {
        assertThatThrownBy(() -> InstitutionCodeFormat.parse("ABC-{SEQ:2}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstitutionCodeFormat.parse("-ABC{SEQ:4}"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> InstitutionCodeFormat.parse("ABC{SEQ:11}"))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void S2_ID_format_fixedTextClashUsesLiteralPrefixes() {
        var alpha = InstitutionCodeFormat.parse("ABC-{YY}-{SEQ:4}");
        var longer = InstitutionCodeFormat.parse("ABC-{YYYY}-X-{SEQ:4}");
        var separate = InstitutionCodeFormat.parse("XYZ-{YY}-{SEQ:4}");
        var withInstitution = InstitutionCodeFormat.parse("{INST}-{YY}-{SEQ:4}");
        assertThat(alpha.clashesWith(longer)).isTrue();
        assertThat(alpha.clashesWith(separate)).isFalse();
        assertThat(withInstitution.includesInstitutionToken()).isTrue();
        assertThat(alpha.includesInstitutionToken()).isFalse();
    }

    @Test
    void S2_ID_format_usesAcademicSessionStartYearAndEnforcesRenderedLength() {
        var format = InstitutionCodeFormat.parse("{INST}-{YYYY}-{SEQ:3}");
        assertThat(format.render("SCH1", 2026, 12)).isEqualTo("SCH1-2026-012");
        assertThatThrownBy(() -> InstitutionCodeFormat.parse("{INST}-ABCDEFGHIJKLMNOPQRSTUVWXYZ-{SEQ:10}")
                .render("LONGINSTITUTION", 2026, 1))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
