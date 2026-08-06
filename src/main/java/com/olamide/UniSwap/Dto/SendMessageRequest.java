package com.olamide.UniSwap.Dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SendMessageRequest {

    @NotNull(message = "Receiver is required")
    private Long receiverId;

    @Size(max = 2000, message = "Message must be at most 2000 characters")
    private String message;
}
