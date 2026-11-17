package com.rams.web;

import com.rams.domain.UserAccount;
import com.rams.domain.UserRole;
import com.rams.service.AssistantDirectoryService;
import com.rams.service.AuthService;
import com.rams.service.CurrentUser;
import com.rams.web.dto.AssistantDtos.AssistantProfileResponse;
import com.rams.web.dto.AssistantDtos.CreateResearchAssistantRequest;
import com.rams.web.dto.AuthDtos.UpdateProfileRequest;
import com.rams.web.dto.AuthDtos.UserResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api")
public class UserController {

    private final CurrentUser currentUser;
    private final AuthService authService;
    private final AssistantDirectoryService assistants;

    public UserController(CurrentUser currentUser, AuthService authService, AssistantDirectoryService assistants) {
        this.currentUser = currentUser;
        this.authService = authService;
        this.assistants = assistants;
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "up");
    }

    @GetMapping("/me")
    public UserResponse me() {
        return authService.toResponse(currentUser.require());
    }

    @PatchMapping("/me")
    public UserResponse updateMe(@Valid @RequestBody UpdateProfileRequest request) {
        return authService.updateProfile(currentUser.require(), request);
    }

    @PostMapping(path = "/me/profile-picture", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public UserResponse updateProfilePicture(@RequestPart("file") MultipartFile file) {
        return authService.updateProfilePicture(currentUser.require(), file);
    }

    @GetMapping("/users")
    public List<UserResponse> list(@RequestParam(required = false) UserRole role) {
        UserAccount user = currentUser.require();
        if (user.getRole() == UserRole.SUPERVISOR && role == UserRole.RESEARCH_ASSISTANT) {
            return authService.listActiveByRole(role);
        }
        if (user.getRole() == UserRole.RESEARCH_ASSISTANT && role == UserRole.SUPERVISOR) {
            return authService.listActiveByRole(role);
        }
        if (user.getRole() == UserRole.RESEARCH_ASSISTANT && role == UserRole.RESEARCH_ASSISTANT) {
            return authService.listActiveByRole(role);
        }
        if (user.getRole() == UserRole.SUPERVISOR) {
            throw ApiException.badRequest("List research assistants with role=RESEARCH_ASSISTANT");
        }
        if (user.getRole() == UserRole.RESEARCH_ASSISTANT) {
            throw ApiException.badRequest("List supervisors with role=SUPERVISOR or research assistants with role=RESEARCH_ASSISTANT");
        }
        throw ApiException.forbidden("You cannot list accounts");
    }

    @GetMapping("/research-assistants")
    public List<AssistantProfileResponse> researchAssistants() {
        return assistants.listForSupervisor(currentUser.require());
    }

    @PostMapping("/research-assistants")
    public ResponseEntity<AssistantProfileResponse> createResearchAssistant(
            @Valid @RequestBody CreateResearchAssistantRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(assistants.createForSupervisor(currentUser.require(), request));
    }
}
