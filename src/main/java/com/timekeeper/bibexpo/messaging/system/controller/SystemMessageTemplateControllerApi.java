package com.timekeeper.bibexpo.messaging.system.controller;

import com.timekeeper.bibexpo.exception.ErrorResponse;
import com.timekeeper.bibexpo.messaging.shared.enums.MessageChannel;
import com.timekeeper.bibexpo.messaging.shared.enums.SystemTemplatePurpose;
import com.timekeeper.bibexpo.messaging.system.model.dto.request.SaveSystemMessageTemplateRequest;
import com.timekeeper.bibexpo.messaging.system.model.dto.response.SystemMessageTemplateResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Root-only management of the system message templates (what a message says: body/variables and
 * the registered template + sender ids), per purpose × channel.
 */
@Tag(name = "System Message Templates",
        description = "Root configuration of the per-purpose, per-channel content for system messages")
@RequestMapping("/api/system/message-templates")
@SecurityRequirement(name = "bearerAuth")
public interface SystemMessageTemplateControllerApi {

    @Operation(summary = "List all system message templates",
            description = "Returns every configured template across purposes and channels. Root only.")
    @ApiResponse(responseCode = "200", description = "Templates retrieved")
    @GetMapping
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<List<SystemMessageTemplateResponse>> list();

    @Operation(summary = "Get one purpose × channel template",
            description = "Returns the template for the purpose and channel. Root only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Template retrieved",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SystemMessageTemplateResponse.class))),
            @ApiResponse(responseCode = "404", description = "No template configured for the purpose and channel",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{purpose}/{channel}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<SystemMessageTemplateResponse> get(
            @Parameter(description = "Message purpose", example = "INVITE") @PathVariable SystemTemplatePurpose purpose,
            @Parameter(description = "Delivery channel", example = "SMS") @PathVariable MessageChannel channel);

    @Operation(summary = "Create or replace a purpose × channel template",
            description = """
                    Upserts the template content for the purpose and channel. Use body for \
                    client-rendered channels (SMS) and variables for provider-rendered channels \
                    (WhatsApp). Root only.""")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Template saved",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = SystemMessageTemplateResponse.class))),
            @ApiResponse(responseCode = "400", description = "Validation failure",
                    content = @Content(mediaType = "application/json",
                            schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping("/{purpose}/{channel}")
    @PreAuthorize("hasAnyRole('ROLE_ROOT')")
    ResponseEntity<SystemMessageTemplateResponse> save(
            @Parameter(description = "Message purpose", example = "INVITE") @PathVariable SystemTemplatePurpose purpose,
            @Parameter(description = "Delivery channel", example = "SMS") @PathVariable MessageChannel channel,
            @Valid @RequestBody SaveSystemMessageTemplateRequest request);
}
