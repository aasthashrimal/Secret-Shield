package com.secretshield;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;

import com.secretshield.dto.RepositoryCreateRequest;
import com.secretshield.entity.ProjectRepository;
import com.secretshield.entity.User;
import com.secretshield.entity.UserRole;
import com.secretshield.exception.ConflictException;
import com.secretshield.repository.ProjectRepositoryRepository;
import com.secretshield.repository.UserRepository;
import com.secretshield.security.JwtService;
import com.secretshield.service.ProjectRepositoryService;
import com.secretshield.support.PostgresApiTest;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class RepositoryManagementIT extends PostgresApiTest {
    @Autowired UserRepository users;
    @Autowired ProjectRepositoryRepository projectRepositories;
    @Autowired ProjectRepositoryService repositoryService;
    @Autowired JwtService tokens;
    @Autowired JdbcTemplate jdbc;

    private User user(String name, UserRole role) {
        return users.saveAndFlush(User.builder().username(name)
                .email(name + "@example.invalid").password("synthetic-hash")
                .role(role).build());
    }

    private String token(User user) {
        return tokens.generateToken(user.getEmail(), user.getRole().name());
    }

    private ProjectRepository repository(User owner, String githubOwner, String githubName, boolean active) {
        return projectRepositories.saveAndFlush(ProjectRepository.builder().owner(owner)
                .githubOwner(githubOwner).githubName(githubName)
                .canonicalUrl("https://github.com/" + githubOwner + "/" + githubName)
                .defaultBranch("main").active(active).build());
    }

    @Test void createCanonicalizesIdentityDerivesUrlAndDefaultsBranch() throws Exception {
        User owner = user("repository-owner", UserRole.DEVELOPER);
        mvc.perform(post("/api/repositories").header("Authorization", "Bearer " + token(owner))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of(
                                "githubOwner", " Demo-Team ", "githubName", "Synthetic_Repo"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isString())
                .andExpect(jsonPath("$.ownerId").value(owner.getId().toString()))
                .andExpect(jsonPath("$.githubOwner").value("demo-team"))
                .andExpect(jsonPath("$.githubName").value("synthetic_repo"))
                .andExpect(jsonPath("$.canonicalUrl")
                        .value("https://github.com/demo-team/synthetic_repo"))
                .andExpect(jsonPath("$.defaultBranch").value("main"))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.endsWith("Z")))
                .andExpect(jsonPath("$.updatedAt").value(org.hamcrest.Matchers.endsWith("Z")));
        assertEquals(1, projectRepositories.count());
    }

    @Test void invalidMetadataAndRequestShapesReturn400WithoutPersistence() throws Exception {
        User owner = user("invalid-owner", UserRole.DEVELOPER);
        String bearer = "Bearer " + token(owner);
        for (Map<String, String> body : java.util.List.of(
                Map.of("githubOwner", "-invalid", "githubName", "repo"),
                Map.of("githubOwner", "valid", "githubName", "repo name"),
                Map.of("githubOwner", "valid", "githubName", "repo", "defaultBranch", "../main"))) {
            mvc.perform(post("/api/repositories").header("Authorization", bearer)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(json.writeValueAsString(body)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("BAD_REQUEST"));
        }
        assertEquals(0, projectRepositories.count());
    }

    @Test void duplicateIdentityIsPerApplicationOwnerAndCaseInsensitive() throws Exception {
        User first = user("duplicate-first", UserRole.DEVELOPER);
        User second = user("duplicate-second", UserRole.DEVELOPER);
        Map<String, String> initial = Map.of("githubOwner", "Demo-Team", "githubName", "Example");
        Map<String, String> duplicate = Map.of("githubOwner", "demo-team", "githubName", "example");
        postWithToken(first, initial).andExpect(status().isCreated());
        postWithToken(first, duplicate).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"));
        postWithToken(second, duplicate).andExpect(status().isCreated());
        assertEquals(2, projectRepositories.count());
    }

    @Test void databaseEnforcesOwnerScopedIdentity() {
        User owner = user("database-owner", UserRole.DEVELOPER);
        repository(owner, "demo", "same", true);
        assertThrows(DataIntegrityViolationException.class,
                () -> repository(owner, "demo", "same", true));
    }

    @Test void databaseRetainsRepositoryOwner() {
        User owner = user("retained-owner", UserRole.DEVELOPER);
        repository(owner, "demo", "retained", true);
        assertThrows(DataIntegrityViolationException.class,
                () -> jdbc.update("DELETE FROM users WHERE id = ?", owner.getId()));
    }

    @Test void developerListIsOwnerScopedAndSupportsPaginationFiltersAndSearch() throws Exception {
        User first = user("list-first", UserRole.DEVELOPER);
        User second = user("list-second", UserRole.DEVELOPER);
        repository(first, "demo-team", "alpha", true);
        repository(first, "demo-team", "beta", false);
        repository(second, "demo-team", "hidden", true);

        mvc.perform(get("/api/repositories?page=0&size=1&active=true&q=ALPHA&sort=id,asc")
                        .header("Authorization", "Bearer " + token(first)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].githubName").value("alpha"))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(1))
                .andExpect(jsonPath("$.totalElements").value(1))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.pageable").doesNotExist());
    }

    @Test void developerCannotReadOrMutateAnotherOwnersRepositoryButAdminCan() throws Exception {
        User owner = user("access-owner", UserRole.DEVELOPER);
        User other = user("access-other", UserRole.DEVELOPER);
        User admin = user("access-admin", UserRole.ADMIN);
        ProjectRepository stored = repository(owner, "demo", "protected", true);

        mvc.perform(get("/api/repositories/{id}", stored.getId())
                        .header("Authorization", "Bearer " + token(other)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/repositories/{id}", stored.getId())
                        .header("Authorization", "Bearer " + token(other))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultBranch\":\"develop\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/repositories/{id}/deactivate", stored.getId())
                        .header("Authorization", "Bearer " + token(other)))
                .andExpect(status().isForbidden());

        mvc.perform(patch("/api/repositories/{id}", stored.getId())
                        .header("Authorization", "Bearer " + token(admin))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultBranch\":\"release/v1\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultBranch").value("release/v1"));
        mvc.perform(get("/api/repositories").header("Authorization", "Bearer " + token(admin)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.totalElements").value(1));
    }

    @Test void ownerCanUpdateOnlyBranchAndDeactivateIdempotentlyWithoutDeletingRecord() throws Exception {
        User owner = user("lifecycle-owner", UserRole.DEVELOPER);
        ProjectRepository stored = repository(owner, "demo", "lifecycle", true);
        String bearer = "Bearer " + token(owner);

        mvc.perform(patch("/api/repositories/{id}", stored.getId()).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultBranch\":\"main\",\"githubOwner\":\"changed\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/repositories/{id}", stored.getId()).header("Authorization", bearer)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"defaultBranch\":\"feature/m04\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.githubOwner").value("demo"))
                .andExpect(jsonPath("$.defaultBranch").value("feature/m04"));
        mvc.perform(post("/api/repositories/{id}/deactivate", stored.getId())
                        .header("Authorization", bearer)).andExpect(status().isNoContent());
        mvc.perform(post("/api/repositories/{id}/deactivate", stored.getId())
                        .header("Authorization", bearer)).andExpect(status().isNoContent());

        ProjectRepository retained = projectRepositories.findById(stored.getId()).orElseThrow();
        assertFalse(retained.isActive());
        assertEquals("feature/m04", retained.getDefaultBranch());
        assertThrows(ConflictException.class,
                () -> repositoryService.loadActiveAuthorized(owner, retained.getId()));
    }

    @Test void invalidIdsPaginationAndSortReturn400WhileUnknownIdReturns404() throws Exception {
        User owner = user("query-owner", UserRole.DEVELOPER);
        String bearer = "Bearer " + token(owner);
        for (String id : new String[]{"0", "01", "-1", "9223372036854775808"}) {
            mvc.perform(get("/api/repositories/{id}", id).header("Authorization", bearer))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/repositories/999999").header("Authorization", bearer))
                .andExpect(status().isNotFound());
        for (String query : new String[]{"page=-1", "size=0", "size=101", "sort=owner,asc", "sort=id,sideways"}) {
            mvc.perform(get("/api/repositories?" + query).header("Authorization", bearer))
                    .andExpect(status().isBadRequest());
        }
    }

    @Test void repositoryRoutesRequireAuthentication() throws Exception {
        mvc.perform(get("/api/repositories")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/repositories").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"githubOwner\":\"demo\",\"githubName\":\"repo\"}"))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions postWithToken(
            User actor, Map<String, String> body) throws Exception {
        return mvc.perform(post("/api/repositories")
                .header("Authorization", "Bearer " + token(actor))
                .contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(body)));
    }
}
