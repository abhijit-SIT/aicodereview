package com.codereviewer.controller;

import com.codereviewer.dto.ReviewRequest;
import com.codereviewer.model.ReviewResult;
import com.codereviewer.service.ReviewService;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@CrossOrigin(origins = "*")
@RestController
@RequestMapping("/api/reviews")
public class ReviewController {

    private final ReviewService reviewService;

    public ReviewController(
            ReviewService reviewService) {

        this.reviewService =
                reviewService;
    }


    // =====================================================
    // POST /api/reviews
    // =====================================================

    @PostMapping
    public ResponseEntity<ReviewResult> reviewCode(
            @RequestBody ReviewRequest request) {

        ReviewResult result =
                reviewService.review(
                        request.getSourceCode(),
                        request.getFilename()
                );

        return ResponseEntity.ok(
                result
        );
    }


    // =====================================================
    // GET /api/reviews
    // =====================================================

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>>
    getAllReviews() {

        List<Map<String, Object>> reviews =
                reviewService.getAllReviews();

        return ResponseEntity.ok(
                reviews
        );
    }


    // =====================================================
    // GET /api/reviews/{id}
    // =====================================================

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>>
    getReviewById(
            @PathVariable("id") long id) {

        Map<String, Object> review =
                reviewService.getReviewById(
                        id
                );

        if (review == null) {

            return ResponseEntity
                    .notFound()
                    .build();
        }

        return ResponseEntity.ok(
                review
        );
    }
}