package com.edu360.api.platformops.release;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import com.edu360.api.config.PlatformPrincipal;
import com.edu360.api.exception.DuplicateResourceException;
import com.edu360.api.exception.UnauthorizedException;
import com.edu360.api.platform.PlatformRole;
import com.edu360.api.platformops.PlatformPermissionService;
import com.edu360.api.platformops.release.dto.*;
class PlatformReleaseControllerTest {
 private PlatformReleaseRepository repo; private PlatformPermissionService permissions; private PlatformReleaseService service; private PlatformReleaseController controller;
 private static final UUID USER_ID=UUID.randomUUID();
 @BeforeEach void setUp(){repo=mock(PlatformReleaseRepository.class);permissions=com.edu360.api.platformops.PlatformTestSupport.permissions(com.edu360.api.platformops.PlatformAccessLookup.standardRolesOnly());service=new PlatformReleaseService(repo,org.mockito.Mockito.mock(com.edu360.api.platformops.role.PlatformAuditWriter.class));controller=new PlatformReleaseController(service,permissions);}
 private Authentication authFor(PlatformRole role){PlatformPrincipal p=new PlatformPrincipal(USER_ID,role,"release@edu360.local",false);return new UsernamePasswordAuthenticationToken(p,null,List.of());}
 @Test void createRelease(){CreateReleaseRequest req=new CreateReleaseRequest("v4.0.0","Release 4.0.0 GA",false,"v4.0.0");when(repo.findByVersionLabel("v4.0.0")).thenReturn(Optional.empty());when(repo.save(any(PlatformRelease.class))).thenAnswer(i->i.getArgument(0));ResponseEntity<ReleaseResponse> res=controller.createRelease(req,authFor(PlatformRole.RELEASE_MANAGER));assertThat(res.getStatusCode()).isEqualTo(HttpStatus.CREATED);assertThat(res.getBody().status()).isEqualTo("UPLOADED");}
 @Test void duplicateVersion(){when(repo.findByVersionLabel("v4.0.0")).thenReturn(Optional.of(new PlatformRelease()));assertThatThrownBy(()->controller.createRelease(new CreateReleaseRequest("v4.0.0","Notes",false,"v4.0.0"),authFor(PlatformRole.RELEASE_MANAGER))).isInstanceOf(DuplicateResourceException.class);}
 @Test void scheduleStagingTestedRelease(){UUID id=UUID.randomUUID();PlatformRelease r=PlatformRelease.builder().releaseId(id).status("STAGING_TESTED").build();when(repo.findById(id)).thenReturn(Optional.of(r));when(repo.save(any(PlatformRelease.class))).thenAnswer(i->i.getArgument(0));Instant at=Instant.now().plus(2,ChronoUnit.HOURS);ReleaseResponse res=controller.scheduleRelease(id,new ScheduleReleaseRequest(at),authFor(PlatformRole.RELEASE_MANAGER)).getBody();assertThat(res.scheduledAt()).isEqualTo(at);assertThat(res.status()).isEqualTo("STAGING_TESTED");}
 @Test void scheduleUploadedReturns409(){UUID id=UUID.randomUUID();when(repo.findById(id)).thenReturn(Optional.of(PlatformRelease.builder().releaseId(id).status("UPLOADED").build()));assertThatThrownBy(()->controller.scheduleRelease(id,new ScheduleReleaseRequest(Instant.now().plus(1,ChronoUnit.HOURS)),authFor(PlatformRole.RELEASE_MANAGER))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("NOT_STAGING_TESTED");}
 @Test void deployUploadedReturns409EvenForOwner(){UUID id=UUID.randomUUID();when(repo.findById(id)).thenReturn(Optional.of(PlatformRelease.builder().releaseId(id).status("UPLOADED").build()));assertThatThrownBy(()->controller.transitionRelease(id,new ReleaseTransitionRequest("DEPLOYED","emergency"),authFor(PlatformRole.PRODUCT_OWNER))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class).hasMessageContaining("NOT_STAGING_TESTED");}
 @Test void productOwnerCanDeployEarlyWithReason(){UUID id=UUID.randomUUID();when(repo.findById(id)).thenReturn(Optional.of(PlatformRelease.builder().releaseId(id).status("STAGING_TESTED").build()));when(repo.save(any(PlatformRelease.class))).thenAnswer(i->i.getArgument(0));ReleaseResponse res=controller.transitionRelease(id,new ReleaseTransitionRequest("DEPLOYED","Emergency correction"),authFor(PlatformRole.PRODUCT_OWNER)).getBody();assertThat(res.status()).isEqualTo("DEPLOYED");}
 @Test void releaseManagerCannotDeployWithoutDueSchedule(){UUID id=UUID.randomUUID();when(repo.findById(id)).thenReturn(Optional.of(PlatformRelease.builder().releaseId(id).status("STAGING_TESTED").build()));assertThatThrownBy(()->controller.transitionRelease(id,new ReleaseTransitionRequest("DEPLOYED",null),authFor(PlatformRole.RELEASE_MANAGER))).isInstanceOf(org.springframework.web.server.ResponseStatusException.class);}
 @Test void unauthorizedRole(){assertThatThrownBy(()->controller.createRelease(new CreateReleaseRequest("v4.0.1","Hotfix",true,"v4.0.1"),authFor(PlatformRole.SUPPORT_MANAGER))).isInstanceOf(UnauthorizedException.class);}
}
