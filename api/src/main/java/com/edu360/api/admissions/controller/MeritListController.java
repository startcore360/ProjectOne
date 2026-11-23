package com.edu360.api.admissions.controller;

import com.edu360.api.admissions.service.AdmissionService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/tenants/{tenantId}/admissions","/v1/tenants/{tenantId}/admissions"})
public class MeritListController {
    private final AdmissionService service;
    public MeritListController(AdmissionService service){this.service=service;}
    @GetMapping("/cycles/{cycleId}/merit-lists")
    public Object list(@PathVariable UUID tenantId,@PathVariable UUID cycleId,Authentication auth){
        return service.meritLists(AdmissionCycleController.actor(auth,tenantId),cycleId);
    }
    @PostMapping("/cycles/{cycleId}/merit-lists")
    public ResponseEntity<Map<String,Object>> generate(@PathVariable UUID tenantId,@PathVariable UUID cycleId,@RequestParam(defaultValue="1") int round,Authentication auth){
        return ResponseEntity.status(HttpStatus.CREATED).body(service.generateMeritList(AdmissionCycleController.actor(auth,tenantId),cycleId,round));
    }
    @PostMapping("/merit-lists/{id}/approval")
    public Map<String,Object> approve(@PathVariable UUID tenantId,@PathVariable UUID id,Authentication auth){
        return service.approveMeritList(AdmissionCycleController.actor(auth,tenantId),id);
    }
    @PostMapping("/merit-lists/{id}/seat-allocation")
    public Map<String,Object> allocate(@PathVariable UUID tenantId,@PathVariable UUID id,Authentication auth){
        return service.allocateSeats(AdmissionCycleController.actor(auth,tenantId),id);
    }
    @GetMapping("/cycles/{cycleId}/seat-allotments")
    public Object seatAllotments(@PathVariable UUID tenantId,@PathVariable UUID cycleId,Authentication auth){
        return service.seatAllotmentsForCycle(AdmissionCycleController.actor(auth,tenantId),cycleId);
    }
    @PostMapping("/seat-allotments/{id}/decision")
    public Map<String,Object> decide(@PathVariable UUID tenantId,@PathVariable UUID id,@RequestBody Map<String,String> body,Authentication auth){
        return service.decideSeat(AdmissionCycleController.actor(auth,tenantId),id,body.get("decision"));
    }
}
