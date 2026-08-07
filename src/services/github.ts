import { GitHubUser, GitHubRepo, FileItem, GitHubIssue, GitHubPullRequest, GitHubCommit } from '../types';

const GITHUB_API_BASE = 'https://api.github.com';

export class GitHubService {
  private token: string | null = null;

  constructor(token?: string | null) {
    this.token = token || null;
  }

  setToken(token: string | null) {
    this.token = token;
  }

  private getHeaders(): Record<string, string> {
    const headers: Record<string, string> = {
      'Accept': 'application/vnd.github.v3+json',
    };
    if (this.token) {
      headers['Authorization'] = `token ${this.token}`;
    }
    return headers;
  }

  async getUserProfile(): Promise<GitHubUser> {
    if (!this.token) {
      return MOCK_USER;
    }
    const res = await fetch(`${GITHUB_API_BASE}/user`, {
      headers: this.getHeaders(),
    });
    if (!res.ok) {
      throw new Error(`GitHub API Error (${res.status}): ${await res.text()}`);
    }
    return res.json();
  }

  async getUserRepos(username?: string): Promise<GitHubRepo[]> {
    if (!this.token) {
      return MOCK_REPOS;
    }
    const endpoint = username 
      ? `${GITHUB_API_BASE}/users/${username}/repos?sort=updated&per_page=100` 
      : `${GITHUB_API_BASE}/user/repos?sort=updated&per_page=100&affiliation=owner,collaborator,organization_member`;
      
    const res = await fetch(endpoint, {
      headers: this.getHeaders(),
    });
    if (!res.ok) {
      throw new Error(`Failed to fetch repositories (${res.status})`);
    }
    return res.json();
  }

  async getRepoDetails(owner: string, repo: string): Promise<GitHubRepo> {
    if (!this.token) {
      const found = MOCK_REPOS.find(r => r.name.toLowerCase() === repo.toLowerCase());
      if (found) return found;
      return MOCK_REPOS[0];
    }
    const res = await fetch(`${GITHUB_API_BASE}/repos/${owner}/${repo}`, {
      headers: this.getHeaders(),
    });
    if (!res.ok) {
      throw new Error(`Failed to fetch repo details (${res.status})`);
    }
    return res.json();
  }

  async getRepoContents(owner: string, repo: string, path: string = ''): Promise<FileItem[]> {
    if (!this.token) {
      return MOCK_FILES[path] || MOCK_FILES[''] || [];
    }
    const cleanPath = path.startsWith('/') ? path.substring(1) : path;
    const url = `${GITHUB_API_BASE}/repos/${owner}/${repo}/contents/${cleanPath}`;
    const res = await fetch(url, {
      headers: this.getHeaders(),
    });
    if (!res.ok) {
      throw new Error(`Failed to fetch contents (${res.status})`);
    }
    const data = await res.json();
    return Array.isArray(data) ? data : [data];
  }

  async getFileContent(owner: string, repo: string, path: string): Promise<string> {
    if (!this.token) {
      return MOCK_FILE_CONTENTS[path] || `// Code file content preview for ${path}\n\nfunction example() {\n  console.log("Connected to repository: ${owner}/${repo}");\n}\n`;
    }
    const cleanPath = path.startsWith('/') ? path.substring(1) : path;
    const res = await fetch(`${GITHUB_API_BASE}/repos/${owner}/${repo}/contents/${cleanPath}`, {
      headers: this.getHeaders(),
    });
    if (!res.ok) {
      throw new Error(`Failed to fetch file content (${res.status})`);
    }
    const data = await res.json();
    if (data.encoding === 'base64' && data.content) {
      return atob(data.content.replace(/\n/g, ''));
    }
    return data.content || '';
  }

  async getIssues(owner: string, repo: string): Promise<GitHubIssue[]> {
    if (!this.token) {
      return MOCK_ISSUES;
    }
    const res = await fetch(`${GITHUB_API_BASE}/repos/${owner}/${repo}/issues?state=all&per_page=30`, {
      headers: this.getHeaders(),
    });
    if (!res.ok) {
      throw new Error(`Failed to fetch issues (${res.status})`);
    }
    const data = await res.json();
    // Filter out pull requests which GitHub API returns under issues
    return data.filter((item: any) => !item.pull_request);
  }

  async createIssue(owner: string, repo: string, title: string, body: string): Promise<GitHubIssue> {
    if (!this.token) {
      const newIssue: GitHubIssue = {
        id: Date.now(),
        number: MOCK_ISSUES.length + 101,
        title,
        body,
        state: 'open',
        user: {
          login: MOCK_USER.login,
          avatar_url: MOCK_USER.avatar_url,
        },
        labels: [{ id: 1, name: 'enhancement', color: 'a2eeef' }],
        comments: 0,
        created_at: new Date().toISOString(),
        updated_at: new Date().toISOString(),
        html_url: `https://github.com/${owner}/${repo}/issues/${MOCK_ISSUES.length + 101}`,
      };
      MOCK_ISSUES.unshift(newIssue);
      return newIssue;
    }
    const res = await fetch(`${GITHUB_API_BASE}/repos/${owner}/${repo}/issues`, {
      method: 'POST',
      headers: {
        ...this.getHeaders(),
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ title, body }),
    });
    if (!res.ok) {
      throw new Error(`Failed to create issue (${res.status})`);
    }
    return res.json();
  }

  async getPullRequests(owner: string, repo: string): Promise<GitHubPullRequest[]> {
    if (!this.token) {
      return MOCK_PULL_REQUESTS;
    }
    const res = await fetch(`${GITHUB_API_BASE}/repos/${owner}/${repo}/pulls?state=all&per_page=30`, {
      headers: this.getHeaders(),
    });
    if (!res.ok) {
      throw new Error(`Failed to fetch pull requests (${res.status})`);
    }
    return res.json();
  }

  async getCommits(owner: string, repo: string): Promise<GitHubCommit[]> {
    if (!this.token) {
      return MOCK_COMMITS;
    }
    const res = await fetch(`${GITHUB_API_BASE}/repos/${owner}/${repo}/commits?per_page=20`, {
      headers: this.getHeaders(),
    });
    if (!res.ok) {
      throw new Error(`Failed to fetch commits (${res.status})`);
    }
    return res.json();
  }
}

// Demo fallback data for seamless initial preview
export const MOCK_USER: GitHubUser = {
  login: 'octocat',
  id: 583231,
  avatar_url: 'https://avatars.githubusercontent.com/u/583231?v=4',
  html_url: 'https://github.com/octocat',
  name: 'The Octocat',
  company: '@github',
  blog: 'https://github.blog',
  location: 'San Francisco',
  email: 'octocat@github.com',
  bio: 'Building open-source tools & AI-driven development workflows.',
  public_repos: 8,
  public_gists: 12,
  followers: 9840,
  following: 9,
  created_at: '2011-01-25T18:44:36Z',
};

export const MOCK_REPOS: GitHubRepo[] = [
  {
    id: 101,
    name: 'ai-studio-app',
    full_name: 'octocat/ai-studio-app',
    private: false,
    owner: {
      login: 'octocat',
      avatar_url: 'https://avatars.githubusercontent.com/u/583231?v=4',
      html_url: 'https://github.com/octocat',
    },
    html_url: 'https://github.com/octocat/ai-studio-app',
    description: 'Full-stack TypeScript application integrated with Gemini API & Cloud Run deployment.',
    fork: false,
    url: 'https://api.github.com/repos/octocat/ai-studio-app',
    created_at: '2024-01-10T10:00:00Z',
    updated_at: '2024-02-15T14:30:00Z',
    pushed_at: '2024-02-15T14:30:00Z',
    git_url: 'git://github.com/octocat/ai-studio-app.git',
    clone_url: 'https://github.com/octocat/ai-studio-app.git',
    stargazers_count: 342,
    watchers_count: 342,
    language: 'TypeScript',
    forks_count: 48,
    open_issues_count: 3,
    default_branch: 'main',
    topics: ['react', 'typescript', 'vite', 'gemini-api', 'cloud-run'],
  },
  {
    id: 102,
    name: 'smart-code-auditor',
    full_name: 'octocat/smart-code-auditor',
    private: false,
    owner: {
      login: 'octocat',
      avatar_url: 'https://avatars.githubusercontent.com/u/583231?v=4',
      html_url: 'https://github.com/octocat',
    },
    html_url: 'https://github.com/octocat/smart-code-auditor',
    description: 'Automated PR reviewer & security vulnerability scanner powered by Google Gemini.',
    fork: false,
    url: 'https://api.github.com/repos/octocat/smart-code-auditor',
    created_at: '2023-11-05T09:12:00Z',
    updated_at: '2024-02-10T11:20:00Z',
    pushed_at: '2024-02-10T11:20:00Z',
    git_url: 'git://github.com/octocat/smart-code-auditor.git',
    clone_url: 'https://github.com/octocat/smart-code-auditor.git',
    stargazers_count: 1250,
    watchers_count: 1250,
    language: 'TypeScript',
    forks_count: 195,
    open_issues_count: 7,
    default_branch: 'main',
    topics: ['code-security', 'ai', 'github-actions'],
  },
  {
    id: 103,
    name: 'developer-portfolio',
    full_name: 'octocat/developer-portfolio',
    private: false,
    owner: {
      login: 'octocat',
      avatar_url: 'https://avatars.githubusercontent.com/u/583231?v=4',
      html_url: 'https://github.com/octocat',
    },
    html_url: 'https://github.com/octocat/developer-portfolio',
    description: 'Clean responsive developer portfolio template with Tailwind CSS & Motion animations.',
    fork: false,
    url: 'https://api.github.com/repos/octocat/developer-portfolio',
    created_at: '2023-08-14T15:00:00Z',
    updated_at: '2024-01-20T08:15:00Z',
    pushed_at: '2024-01-20T08:15:00Z',
    git_url: 'git://github.com/octocat/developer-portfolio.git',
    clone_url: 'https://github.com/octocat/developer-portfolio.git',
    stargazers_count: 89,
    watchers_count: 89,
    language: 'HTML',
    forks_count: 14,
    open_issues_count: 1,
    default_branch: 'main',
    topics: ['portfolio', 'tailwindcss', 'responsive'],
  },
];

export const MOCK_FILES: Record<string, FileItem[]> = {
  '': [
    { name: 'src', path: 'src', sha: 'f1', size: 0, type: 'dir', url: '', download_url: null },
    { name: 'public', path: 'public', sha: 'f2', size: 0, type: 'dir', url: '', download_url: null },
    { name: 'package.json', path: 'package.json', sha: 'f3', size: 1240, type: 'file', url: '', download_url: '' },
    { name: 'README.md', path: 'README.md', sha: 'f4', size: 2800, type: 'file', url: '', download_url: '' },
    { name: 'vite.config.ts', path: 'vite.config.ts', sha: 'f5', size: 650, type: 'file', url: '', download_url: '' },
    { name: 'server.ts', path: 'server.ts', sha: 'f6', size: 1400, type: 'file', url: '', download_url: '' },
  ],
  'src': [
    { name: 'components', path: 'src/components', sha: 's1', size: 0, type: 'dir', url: '', download_url: null },
    { name: 'services', path: 'src/services', sha: 's2', size: 0, type: 'dir', url: '', download_url: null },
    { name: 'App.tsx', path: 'src/App.tsx', sha: 's3', size: 3400, type: 'file', url: '', download_url: '' },
    { name: 'main.tsx', path: 'src/main.tsx', sha: 's4', size: 350, type: 'file', url: '', download_url: '' },
    { name: 'types.ts', path: 'src/types.ts', sha: 's5', size: 2100, type: 'file', url: '', download_url: '' },
  ],
};

export const MOCK_FILE_CONTENTS: Record<string, string> = {
  'README.md': `# AI Studio GitHub App

This workspace app connects directly with GitHub to browse repositories, audit codebase security, manage PRs & issues, and sync workspace projects.

## Features
- 🔑 **GitHub Connection**: Supports Personal Access Tokens (PAT) & OAuth authorization.
- 📁 **Repository Browser**: Tree view code exploration and diff visualizer.
- 🤖 **AI Code Auditor**: Gemini-powered security scanning and code review.
- 🚀 **Issue & PR Manager**: Create, comment, and auto-summarize issues.
`,
  'package.json': `{
  "name": "ai-studio-github-app",
  "private": true,
  "version": "1.0.0",
  "scripts": {
    "dev": "tsx server.ts",
    "build": "vite build"
  }
}`,
  'src/App.tsx': `import React from 'react';

export default function App() {
  return (
    <div className="min-h-screen bg-slate-900 text-slate-100 p-8">
      <h1>GitHub App Connected</h1>
    </div>
  );
}`,
};

export const MOCK_ISSUES: GitHubIssue[] = [
  {
    id: 1,
    number: 42,
    title: 'Add automatic Gemini code review on pull request submission',
    user: { login: 'octocat', avatar_url: 'https://avatars.githubusercontent.com/u/583231?v=4' },
    labels: [
      { id: 10, name: 'feature', color: '1d76db' },
      { id: 11, name: 'ai-enhancement', color: '5319e7' },
    ],
    state: 'open',
    comments: 4,
    created_at: '2024-02-12T16:20:00Z',
    updated_at: '2024-02-14T09:10:00Z',
    body: 'We should trigger an automated Gemini review whenever a PR is submitted to check for edge cases, performance bottlenecks, and security flaws.',
    html_url: 'https://github.com/octocat/ai-studio-app/issues/42',
  },
  {
    id: 2,
    number: 39,
    title: 'Optimize Tailwind CSS v4 bundle size for production build',
    user: { login: 'dev-alex', avatar_url: 'https://avatars.githubusercontent.com/u/1021430?v=4' },
    labels: [{ id: 12, name: 'performance', color: 'd4c5f9' }],
    state: 'open',
    comments: 2,
    created_at: '2024-02-08T11:45:00Z',
    updated_at: '2024-02-09T14:00:00Z',
    body: 'Ensure unreferenced utility classes are purged correctly during esbuild production bundle generation.',
    html_url: 'https://github.com/octocat/ai-studio-app/issues/39',
  },
];

export const MOCK_PULL_REQUESTS: GitHubPullRequest[] = [
  {
    id: 201,
    number: 45,
    title: 'feat: add GitHub OAuth flow and PAT token manager',
    user: { login: 'octocat', avatar_url: 'https://avatars.githubusercontent.com/u/583231?v=4' },
    state: 'open',
    created_at: '2024-02-14T18:00:00Z',
    updated_at: '2024-02-15T10:30:00Z',
    body: 'Implements OAuth authorization modal, postMessage message listener, and secure local token persistence.',
    html_url: 'https://github.com/octocat/ai-studio-app/pull/45',
    head: { ref: 'feat/oauth-auth', sha: 'a7b9c1d' },
    base: { ref: 'main' },
  },
];

export const MOCK_COMMITS: GitHubCommit[] = [
  {
    sha: '8f2a1b3',
    commit: {
      author: { name: 'The Octocat', email: 'octocat@github.com', date: '2024-02-15T14:30:00Z' },
      message: 'feat: integrate Gemini API server proxy for code auditing',
    },
    author: { login: 'octocat', avatar_url: 'https://avatars.githubusercontent.com/u/583231?v=4' },
    html_url: 'https://github.com/octocat/ai-studio-app/commit/8f2a1b3',
  },
  {
    sha: '3c4d5e6',
    commit: {
      author: { name: 'The Octocat', email: 'octocat@github.com', date: '2024-02-14T09:15:00Z' },
      message: 'docs: update deployment and OAuth configuration instructions',
    },
    author: { login: 'octocat', avatar_url: 'https://avatars.githubusercontent.com/u/583231?v=4' },
    html_url: 'https://github.com/octocat/ai-studio-app/commit/3c4d5e6',
  },
];
