import { useSession } from './session';

export function useActions() {
  const { request } = useSession();
  return {
    createRepository: (githubOwner: string, githubName: string, defaultBranch: string) =>
      request('/api/repositories', { method: 'POST', body: JSON.stringify({ githubOwner, githubName, defaultBranch }) }),
    updateBranch: (repositoryId: string, defaultBranch: string) =>
      request(`/api/repositories/${repositoryId}`, { method: 'PATCH', body: JSON.stringify({ defaultBranch }) }),
    deactivateRepository: (repositoryId: string) =>
      request(`/api/repositories/${repositoryId}/deactivate`, { method: 'POST' }),
    createScan: (repositoryId: string, branch: string, commitSha: string) =>
      request(`/api/repositories/${repositoryId}/scans`, { method: 'POST', body: JSON.stringify({ branch, commitSha }) }),
    startScan: (repositoryId: string, scanId: string) =>
      request(`/api/repositories/${repositoryId}/scans/${scanId}/start`, { method: 'POST' }),
    transitionIncident: (repositoryId: string, incidentId: string, status: 'OPEN' | 'RESOLVED' | 'IGNORED', reason: string, version: number) =>
      request(`/api/repositories/${repositoryId}/incidents/${incidentId}`, { method: 'PATCH', body: JSON.stringify({ status, reason, version }) }),
  };
}
