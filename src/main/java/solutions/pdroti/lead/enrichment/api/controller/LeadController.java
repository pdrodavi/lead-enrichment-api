package solutions.pdroti.lead.enrichment.api.controller;

import jakarta.validation.Valid;

import lombok.Getter;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import solutions.pdroti.lead.enrichment.api.model.Lead;
import solutions.pdroti.lead.enrichment.api.service.LeadService;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobParametersBuilder;

@RestController
@RequestMapping("/api/v1/leads")
public class LeadController {

    private final LeadService leadService;
    private final JobLauncher jobLauncher;
    private final Job enrichmentJob;

    public LeadController(LeadService leadService, JobLauncher jobLauncher, Job enrichmentJob) {
        this.leadService = leadService;
        this.jobLauncher = jobLauncher;
        this.enrichmentJob = enrichmentJob;
    }

/*    @SuppressWarnings("null")
    @PostMapping("/process")
    public String triggerJob() throws Exception {
        jobLauncher.run(enrichmentJob,
                new JobParametersBuilder().addLong("time", System.currentTimeMillis()).toJobParameters());
        return "Job started successfully";
    }*/

    @PostMapping("/enrich")
    public ResponseEntity<Lead> enrichLead(@Valid @RequestBody LeadRequest request) {
        Lead enrichedLead = leadService.enrich(request.getEmail(), request.getDomain());
        return ResponseEntity.ok(enrichedLead);
    }

    @GetMapping("/{id}")
    public ResponseEntity<Lead> getLeadById(@PathVariable String id) {
        return leadService.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @Getter
    public static class LeadRequest {

        private String email;
        private String domain;

        public void setEmail(String email) {
            this.email = email;
        }

        public void setDomain(String domain) {
            this.domain = domain;
        }
    }
}
