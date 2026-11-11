package com.edu360.api.idempotency;
import static org.assertj.core.api.Assertions.assertThat;
import java.util.Map;
import org.junit.jupiter.api.Test;
class IdempotencyServiceTest {
 @Test void requestHashIsDeterministicForMapKeyOrder(){assertThat(IdempotencyService.requestHash(Map.of("a",1,"b",2))).isEqualTo(IdempotencyService.requestHash(Map.of("b",2,"a",1)));}
 @Test void requestHashChangesWhenBodyChanges(){assertThat(IdempotencyService.requestHash(Map.of("status","PRESENT"))).isNotEqualTo(IdempotencyService.requestHash(Map.of("status","ABSENT")));}
}
