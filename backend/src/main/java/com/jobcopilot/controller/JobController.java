package com.jobcopilot.controller;

import com.jobcopilot.dto.common.PageResponse;
import com.jobcopilot.dto.posting.DismissJobRequest;
import com.jobcopilot.dto.posting.PostingResponse;
import com.jobcopilot.dto.posting.SaveJobRequest;
import com.jobcopilot.service.PostingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Job list endpoints: search / filter / sort / paginate, plus save, unsave, and dismiss.
 */
@RestController
@RequestMapping("/api/jobs")
@RequiredArgsConstructor
@Tag(name = "Jobs", description = "Browse, search, save and dismiss job postings")
public class JobController {

    private final PostingService postingService;

    @GetMapping
    @Operation(summary = "List postings with search, filtering, sorting and pagination")
    public ResponseEntity<PageResponse<PostingResponse>> list(
            @Parameter(description = "Free-text search across title, company, location")
            @RequestParam(required = false) String query,
            @Parameter(description = "Minimum score (0-100)")
            @RequestParam(required = false) Integer minScore,
            @RequestParam(required = false) Boolean remote,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String company,
            @RequestParam(defaultValue = "false") boolean includeDismissed,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @Parameter(description = "Sort field: score|postedAt|createdAt|company|title")
            @RequestParam(defaultValue = "score") String sortBy,
            @Parameter(description = "Sort direction: asc|desc")
            @RequestParam(defaultValue = "desc") String sortDir) {
        return ResponseEntity.ok(postingService.list(query, minScore, remote, source, company,
                includeDismissed, page, size, sortBy, sortDir));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a single posting")
    public ResponseEntity<PostingResponse> get(@PathVariable Long id) {
        return ResponseEntity.ok(postingService.getById(id));
    }

    @PostMapping("/{id}/save")
    @Operation(summary = "Save (bookmark) a posting")
    public ResponseEntity<PostingResponse> save(@PathVariable Long id,
                                                @RequestBody(required = false) SaveJobRequest request) {
        return ResponseEntity.ok(postingService.save(id, request));
    }

    @DeleteMapping("/{id}/save")
    @Operation(summary = "Remove a posting from saved")
    public ResponseEntity<Void> unsave(@PathVariable Long id) {
        postingService.unsave(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/dismiss")
    @Operation(summary = "Dismiss (hide) a posting")
    public ResponseEntity<PostingResponse> dismiss(@PathVariable Long id,
                                                   @RequestBody(required = false) DismissJobRequest request) {
        return ResponseEntity.ok(postingService.dismiss(id, request));
    }
}
