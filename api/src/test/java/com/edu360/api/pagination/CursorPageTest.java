package com.edu360.api.pagination;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import java.util.Set;import java.util.UUID;
import org.junit.jupiter.api.Test;
import com.edu360.api.exception.ApiException;
class CursorPageTest {
 @Test void defaultLimitAndStableSortAreApplied(){var r=CursorPage.request(null,null,"createdAt","ASC",Set.of("createdAt","name"));assertThat(r.limit()).isEqualTo(50);assertThat(r.orderBy("id")).isEqualTo("createdAt ASC, id ASC");}
 @Test void rejectsUnknownSortAndOversizedLimit(){assertThatThrownBy(()->CursorPage.request(201,null,"createdAt","ASC",Set.of("createdAt"))).isInstanceOf(ApiException.class);assertThatThrownBy(()->CursorPage.request(50,null,"unsafe","ASC",Set.of("createdAt"))).isInstanceOf(ApiException.class);}
 @Test void cursorRoundTripsAndRejectsTampering(){UUID id=UUID.randomUUID();String t=CursorPage.encode("createdAt","2026-11-11T10:00:00Z",id);assertThat(CursorPage.decode(t,"createdAt").id()).isEqualTo(id);assertThatThrownBy(()->CursorPage.decode(t,"name")).isInstanceOf(ApiException.class);}
}
