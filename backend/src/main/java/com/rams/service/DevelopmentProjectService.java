package com.rams.service;

import com.rams.domain.DevelopmentProject;
import com.rams.domain.DevelopmentProjectStatus;
import com.rams.domain.UserAccount;
import com.rams.domain.UserRole;
import com.rams.repository.DevelopmentProjectRepository;
import com.rams.repository.UserRepository;
import com.rams.web.ApiException;
import com.rams.web.Text;
import com.rams.web.dto.DevelopmentProjectDtos.CreateDevelopmentProjectRequest;
import com.rams.web.dto.DevelopmentProjectDtos.DevelopmentProjectResponse;
import com.rams.web.dto.DevelopmentProjectDtos.UpdateDevelopmentProjectRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Service
@Transactional(readOnly = true)
public class DevelopmentProjectService {

    private final DevelopmentProjectRepository developmentProjects;
    private final UserRepository users;

    public DevelopmentProjectService(DevelopmentProjectRepository developmentProjects, UserRepository users) {
        this.developmentProjects = developmentProjects;
        this.users = users;
    }

    @Transactional
    public DevelopmentProjectResponse create(UserAccount user, CreateDevelopmentProjectRequest request) {
        requireResearchAssistant(user);
        DevelopmentProject item = new DevelopmentProject();
        item.setAssistantId(user.getId());
        item.setTitle(Text.required(request.title(), 200, "Title"));
        item.setDescription(Text.optional(request.description(), 20000, "Description"));
        item.setObjectives(Text.optional(request.objectives(), 20000, "Objectives"));
        item.setRepositoryUrl(normalizeRepositoryUrl(request.repositoryUrl()));
        item.setStatus(request.status() == null ? DevelopmentProjectStatus.PLANNED : request.status());
        item.setProgressNote(Text.optional(request.progressNote(), 20000, "Progress note"));
        developmentProjects.save(item);
        return toResponses(List.of(item)).get(0);
    }

    public List<DevelopmentProjectResponse> list(UserAccount user) {
        List<DevelopmentProject> found;
        if (user.getRole() == UserRole.SUPERVISOR) {
            found = developmentProjects.findAllByOrderByUpdatedAtDescIdDesc();
        } else if (user.getRole() == UserRole.RESEARCH_ASSISTANT) {
            found = developmentProjects.findByAssistantIdOrderByUpdatedAtDescIdDesc(user.getId());
        } else {
            throw ApiException.forbidden("You cannot list development projects");
        }
        return toResponses(found);
    }

    public DevelopmentProjectResponse get(UserAccount user, Long id) {
        return toResponses(List.of(requireVisible(user, id))).get(0);
    }

    @Transactional
    public DevelopmentProjectResponse update(UserAccount user, Long id, UpdateDevelopmentProjectRequest request) {
        DevelopmentProject item = requireOwner(user, id);
        if (request.title() != null) {
            item.setTitle(Text.required(request.title(), 200, "Title"));
        }
        if (request.description() != null) {
            item.setDescription(Text.optional(request.description(), 20000, "Description"));
        }
        if (request.objectives() != null) {
            item.setObjectives(Text.optional(request.objectives(), 20000, "Objectives"));
        }
        if (request.repositoryUrl() != null) {
            item.setRepositoryUrl(normalizeRepositoryUrl(request.repositoryUrl()));
        }
        if (request.status() != null) {
            item.setStatus(request.status());
        }
        if (request.progressNote() != null) {
            item.setProgressNote(Text.optional(request.progressNote(), 20000, "Progress note"));
        }
        developmentProjects.save(item);
        return toResponses(List.of(item)).get(0);
    }

    private DevelopmentProject requireVisible(UserAccount user, Long id) {
        DevelopmentProject item = developmentProjects.findById(id)
                .orElseThrow(() -> ApiException.notFound("Development project not found"));
        if (user.getRole() == UserRole.SUPERVISOR) {
            return item;
        }
        if (user.getRole() == UserRole.RESEARCH_ASSISTANT && item.getAssistantId().equals(user.getId())) {
            return item;
        }
        throw ApiException.notFound("Development project not found");
    }

    private DevelopmentProject requireOwner(UserAccount user, Long id) {
        DevelopmentProject item = requireVisible(user, id);
        if (user.getRole() != UserRole.RESEARCH_ASSISTANT || !item.getAssistantId().equals(user.getId())) {
            throw ApiException.forbidden("Only the research assistant who owns this development project can update it");
        }
        return item;
    }

    private static void requireResearchAssistant(UserAccount user) {
        if (user.getRole() != UserRole.RESEARCH_ASSISTANT) {
            throw ApiException.forbidden("Only a research assistant can create a development project");
        }
    }

    private List<DevelopmentProjectResponse> toResponses(List<DevelopmentProject> found) {
        if (found.isEmpty()) {
            return List.of();
        }
        Map<Long, String> assistantNames = new HashMap<>();
        users.findAllById(found.stream().map(DevelopmentProject::getAssistantId).distinct().toList())
                .forEach(person -> assistantNames.put(person.getId(), person.getName()));
        return found.stream()
                .map(item -> new DevelopmentProjectResponse(
                        item.getId(),
                        item.getAssistantId(),
                        assistantNames.get(item.getAssistantId()),
                        item.getTitle(),
                        item.getDescription(),
                        item.getObjectives(),
                        item.getRepositoryUrl(),
                        item.getStatus(),
                        item.getProgressNote(),
                        item.getCreatedAt(),
                        item.getUpdatedAt()
                ))
                .toList();
    }

    private static String normalizeRepositoryUrl(String repositoryUrl) {
        String value = Text.trimToNull(repositoryUrl);
        if (value == null) {
            return null;
        }
        if (value.length() > 1000) {
            throw ApiException.badRequest("Repository URL must be at most 1000 characters");
        }
        try {
            URI uri = new URI(value);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null) {
                throw new IllegalArgumentException();
            }
        } catch (URISyntaxException | IllegalArgumentException exception) {
            throw ApiException.badRequest("Enter a valid http(s) repository or demo URL");
        }
        return value;
    }
}
