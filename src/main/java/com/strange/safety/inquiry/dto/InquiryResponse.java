package com.strange.safety.inquiry.dto;

import com.strange.safety.inquiry.entity.Inquiry;
import com.strange.safety.inquiry.entity.InquiryCategory;
import com.strange.safety.inquiry.entity.InquiryStatus;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class InquiryResponse {
    private Long id;
    private String userEmail;
    private String userName;
    private String userRole;
    private InquiryCategory category;
    private String categoryDescription;
    private String title;
    private String content;
    private InquiryStatus status;
    private String statusDescription;
    private String replyContent;
    private String repliedByName;
    private LocalDateTime repliedAt;
    private LocalDateTime createdAt;

    public static InquiryResponse from(Inquiry inquiry) {
        return InquiryResponse.builder()
                .id(inquiry.getId())
                .userEmail(inquiry.getUser().getEmail())
                .userName(inquiry.getUser().getName())
                .userRole(inquiry.getUser().getRole().name())
                .category(inquiry.getCategory())
                .categoryDescription(inquiry.getCategory().getDescription())
                .title(inquiry.getTitle())
                .content(inquiry.getContent())
                .status(inquiry.getStatus())
                .statusDescription(inquiry.getStatus().getDescription())
                .replyContent(inquiry.getReplyContent())
                .repliedByName(inquiry.getRepliedBy() != null ? inquiry.getRepliedBy().getName() : null)
                .repliedAt(inquiry.getRepliedAt())
                .createdAt(inquiry.getCreatedAt())
                .build();
    }
}
