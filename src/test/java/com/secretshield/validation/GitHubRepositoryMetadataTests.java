package com.secretshield.validation;

import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolationException;

import static org.junit.jupiter.api.Assertions.*;

class GitHubRepositoryMetadataTests {
    @Test void identityIsTrimmedLowercasedAndUsedForCanonicalUrl() {
        String owner = GitHubRepositoryMetadata.owner(" Demo-Team ");
        String name = GitHubRepositoryMetadata.name(" Secret_Shield.Java ");
        assertEquals("demo-team", owner);
        assertEquals("secret_shield.java", name);
        assertEquals("https://github.com/demo-team/secret_shield.java",
                GitHubRepositoryMetadata.canonicalUrl(owner, name));
    }

    @Test void omittedBranchDefaultsToMainAndCommonNestedBranchIsAccepted() {
        assertEquals("main", GitHubRepositoryMetadata.defaultBranch(null));
        assertEquals("feature/M04.repository-api",
                GitHubRepositoryMetadata.defaultBranch(" feature/M04.repository-api "));
    }

    @Test void unsafeOrNonCanonicalMetadataIsRejected() {
        for (String owner : new String[]{"-owner", "owner-", "owner--name", "owner/name", ""}) {
            assertThrows(ConstraintViolationException.class,
                    () -> GitHubRepositoryMetadata.owner(owner));
        }
        for (String name : new String[]{"repo name", "owner/repo", ".", "..", "repo.git?token=value"}) {
            assertThrows(ConstraintViolationException.class,
                    () -> GitHubRepositoryMetadata.name(name));
        }
        for (String branch : new String[]{"../main", "feature//work", "bad branch", "refs/@{bad}", "x.lock"}) {
            assertThrows(ConstraintViolationException.class,
                    () -> GitHubRepositoryMetadata.defaultBranch(branch));
        }
    }
}
