# Repository management

Module 04 connects repository metadata to an authenticated Secret Shield user. A
connection stores identity and display metadata only. It does not perform GitHub
OAuth, clone a repository, verify that a remote exists, or accept a personal access
token. Those values must never be placed in repository requests or fixtures.

## Terms and validation

- **Application owner:** the Secret Shield user who created the connection. The API
  derives this immutable relationship from the bearer token.
- **GitHub owner:** the normalized GitHub user or organization segment.
- **GitHub name:** the normalized repository-name segment.
- **Canonical URL:** server-derived `https://github.com/{owner}/{name}`. Clients do
  not submit it.
- **Default branch:** branch metadata used as the default for future manual scans.
- **Active:** whether future scans may be created. Deactivation is idempotent and
  retains the repository row and later history.

GitHub identity is trimmed and lowercased. Owner accepts 1–39 ASCII letters, digits,
and hyphens, with no leading/trailing or consecutive hyphen. Name accepts 1–100 ASCII
letters, digits, `.`, `_`, and `-`; `.` and `..` are rejected. These limits align with
GitHub's published [account-name](https://docs.github.com/en/enterprise-cloud@latest/admin/managing-iam/iam-configuration-reference/username-considerations-for-external-authentication)
and [repository-name](https://docs.github.com/en/repositories/creating-and-managing-repositories/creating-a-new-repository)
constraints while keeping application normalization deterministic.

The same normalized GitHub owner/name can be connected once per application owner.
A second application user may connect that identity independently. Invalid input is
400; a duplicate is 409.

## Endpoints

All routes require `Authorization: Bearer <JWT>`.

| Method and route | Result |
| --- | --- |
| `POST /api/repositories` | 201; create from `githubOwner`, `githubName`, optional `defaultBranch` |
| `GET /api/repositories` | 200; compact page; developer sees owned rows, admin sees all |
| `GET /api/repositories/{id}` | 200 for owner/admin |
| `PATCH /api/repositories/{id}` | 200; update `defaultBranch` only |
| `POST /api/repositories/{id}/deactivate` | 204; repeat calls remain 204 |

List parameters are `page` (default 0), `size` (default 20, maximum 100), optional
`active`, case-insensitive `q` over GitHub owner/name, and `sort`. Sort accepts only
`createdAt,asc|desc` or `id,asc|desc`; default is `createdAt,desc` with ID as the
deterministic tie-breaker. Invalid values return 400.

Developers receive 403 for a known repository owned by another user. Administrators
may read and manage any repository. Unknown positive IDs return 404 after authentication;
malformed IDs return 400. There is no delete operation and ownership cannot be changed.

## Page copy and fixtures

Suggested title: **My repositories** for developers and **All repositories** for
administrators. Suggested action: **Connect repository**. An inactive row uses the
badge **Inactive** and helper text **Scanning is disabled; existing history is retained.**
The empty state is **No repositories connected yet. Connect GitHub repository metadata
to begin configuring scans.**

Synthetic active, inactive, and empty-page payloads are in
[`fixtures/repositories.json`](fixtures/repositories.json). They contain no token,
credential, clone URL, real account, or secret value.
