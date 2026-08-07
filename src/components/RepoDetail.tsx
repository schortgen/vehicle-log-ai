import React, { useState, useEffect } from 'react';
import { 
  ArrowLeft, Star, GitFork, AlertCircle, FileText, GitPullRequest, 
  History, Sparkles, ExternalLink, Globe, Lock, Code2, Plus, Terminal
} from 'lucide-react';
import { GitHubRepo, GitHubIssue, GitHubPullRequest, GitHubCommit } from '../types';
import { GitHubService } from '../services/github';
import { FileBrowser } from './FileBrowser';
import { IssueManager } from './IssueManager';
import { PullRequestManager } from './PullRequestManager';

interface RepoDetailProps {
  repo: GitHubRepo;
  githubService: GitHubService;
  onBack: () => void;
  onRunAiAudit: (title: string, content: string, filename?: string, taskType?: 'security' | 'pr_review' | 'issue_solution') => void;
}

export const RepoDetail: React.FC<RepoDetailProps> = ({
  repo,
  githubService,
  onBack,
  onRunAiAudit,
}) => {
  const [activeTab, setActiveTab] = useState<'files' | 'issues' | 'pulls' | 'commits'>('files');
  const [issues, setIssues] = useState<GitHubIssue[]>([]);
  const [pulls, setPulls] = useState<GitHubPullRequest[]>([]);
  const [commits, setCommits] = useState<GitHubCommit[]>([]);
  const [isLoadingTab, setIsLoadingTab] = useState(false);

  useEffect(() => {
    let isMounted = true;
    setIsLoadingTab(true);

    if (activeTab === 'issues') {
      githubService.getIssues(repo.owner.login, repo.name)
        .then(data => { if (isMounted) setIssues(data); })
        .catch(err => console.error('Failed to load issues', err))
        .finally(() => { if (isMounted) setIsLoadingTab(false); });
    } else if (activeTab === 'pulls') {
      githubService.getPullRequests(repo.owner.login, repo.name)
        .then(data => { if (isMounted) setPulls(data); })
        .catch(err => console.error('Failed to load PRs', err))
        .finally(() => { if (isMounted) setIsLoadingTab(false); });
    } else if (activeTab === 'commits') {
      githubService.getCommits(repo.owner.login, repo.name)
        .then(data => { if (isMounted) setCommits(data); })
        .catch(err => console.error('Failed to load commits', err))
        .finally(() => { if (isMounted) setIsLoadingTab(false); });
    } else {
      setIsLoadingTab(false);
    }

    return () => { isMounted = false; };
  }, [activeTab, repo, githubService]);

  const handleCreateIssue = async (title: string, body: string) => {
    const newIssue = await githubService.createIssue(repo.owner.login, repo.name, title, body);
    setIssues(prev => [newIssue, ...prev]);
  };

  return (
    <div className="space-y-6">
      {/* Top Navigation & Repo Info Card */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-3 border-b border-slate-800/80 pb-4">
          <div className="flex items-center space-x-3">
            <button
              onClick={onBack}
              className="p-2 text-slate-400 hover:text-white rounded-lg bg-slate-800 hover:bg-slate-700 transition"
              title="Back to all repositories"
            >
              <ArrowLeft className="w-4 h-4" />
            </button>

            <div className="flex items-center space-x-2">
              <img src={repo.owner.avatar_url} alt={repo.owner.login} className="w-6 h-6 rounded-full" />
              <span className="text-xs text-slate-400 font-mono">{repo.owner.login} /</span>
              <h2 className="text-base font-bold text-slate-100">{repo.name}</h2>
              {repo.private ? (
                <span className="px-2 py-0.5 rounded text-[10px] font-medium bg-amber-950/80 text-amber-400 border border-amber-800/60">
                  Private
                </span>
              ) : (
                <span className="px-2 py-0.5 rounded text-[10px] font-medium bg-slate-800 text-slate-300">
                  Public
                </span>
              )}
            </div>
          </div>

          <div className="flex items-center space-x-2">
            <button
              onClick={() => onRunAiAudit(
                `Full Security Audit: ${repo.full_name}`,
                `Repository Description: ${repo.description}\nPrimary Language: ${repo.language}\nBranch: ${repo.default_branch}`,
                repo.name,
                'security'
              )}
              className="flex items-center space-x-1.5 px-3 py-1.5 bg-purple-600 hover:bg-purple-500 text-white rounded-lg text-xs font-medium transition shadow-sm"
            >
              <Sparkles className="w-3.5 h-3.5" />
              <span>Full AI Audit</span>
            </button>

            <a
              href={repo.html_url}
              target="_blank"
              rel="noopener noreferrer"
              className="flex items-center space-x-1 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-medium transition"
            >
              <span>GitHub</span>
              <ExternalLink className="w-3.5 h-3.5" />
            </a>
          </div>
        </div>

        {/* Stats bar */}
        <div className="flex flex-wrap items-center justify-between gap-4 text-xs text-slate-400 pt-1">
          <p className="text-slate-300 max-w-2xl">{repo.description || 'No description provided.'}</p>

          <div className="flex items-center space-x-4 shrink-0">
            <span className="flex items-center space-x-1">
              <Star className="w-3.5 h-3.5 text-amber-400" />
              <strong className="text-slate-200">{repo.stargazers_count}</strong>
              <span className="text-slate-500">stars</span>
            </span>

            <span className="flex items-center space-x-1">
              <GitFork className="w-3.5 h-3.5 text-slate-400" />
              <strong className="text-slate-200">{repo.forks_count}</strong>
              <span className="text-slate-500">forks</span>
            </span>

            <span className="flex items-center space-x-1">
              <AlertCircle className="w-3.5 h-3.5 text-sky-400" />
              <strong className="text-slate-200">{repo.open_issues_count}</strong>
              <span className="text-slate-500">issues</span>
            </span>
          </div>
        </div>

        {/* View Tabs */}
        <div className="flex items-center space-x-1 bg-slate-950 p-1 rounded-xl border border-slate-800">
          <button
            onClick={() => setActiveTab('files')}
            className={`flex items-center space-x-2 px-4 py-2 text-xs font-medium rounded-lg transition ${
              activeTab === 'files'
                ? 'bg-sky-600 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <FileText className="w-3.5 h-3.5" />
            <span>Code Files</span>
          </button>

          <button
            onClick={() => setActiveTab('issues')}
            className={`flex items-center space-x-2 px-4 py-2 text-xs font-medium rounded-lg transition ${
              activeTab === 'issues'
                ? 'bg-sky-600 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <AlertCircle className="w-3.5 h-3.5" />
            <span>Issues</span>
          </button>

          <button
            onClick={() => setActiveTab('pulls')}
            className={`flex items-center space-x-2 px-4 py-2 text-xs font-medium rounded-lg transition ${
              activeTab === 'pulls'
                ? 'bg-sky-600 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <GitPullRequest className="w-3.5 h-3.5" />
            <span>Pull Requests</span>
          </button>

          <button
            onClick={() => setActiveTab('commits')}
            className={`flex items-center space-x-2 px-4 py-2 text-xs font-medium rounded-lg transition ${
              activeTab === 'commits'
                ? 'bg-sky-600 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            <History className="w-3.5 h-3.5" />
            <span>Commits</span>
          </button>
        </div>
      </div>

      {/* Tab Panels */}
      {activeTab === 'files' && (
        <FileBrowser
          owner={repo.owner.login}
          repo={repo.name}
          githubService={githubService}
          onAnalyzeFile={(content, filename) =>
            onRunAiAudit(`Code Security & Refactor Audit: ${filename}`, content, filename, 'security')
          }
        />
      )}

      {activeTab === 'issues' && (
        <IssueManager
          owner={repo.owner.login}
          repo={repo.name}
          issues={issues}
          onCreateIssue={handleCreateIssue}
          onAnalyzeIssue={(title, body) =>
            onRunAiAudit(`AI Issue Solution Strategy: ${title}`, body || title, title, 'issue_solution')
          }
          isLoading={isLoadingTab}
        />
      )}

      {activeTab === 'pulls' && (
        <PullRequestManager
          owner={repo.owner.login}
          repo={repo.name}
          pullRequests={pulls}
          onAnalyzePR={(title, body) =>
            onRunAiAudit(`AI Automated PR Review: ${title}`, body || title, title, 'pr_review')
          }
          isLoading={isLoadingTab}
        />
      )}

      {activeTab === 'commits' && (
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-4 space-y-3">
          <h3 className="text-xs font-semibold text-slate-200 flex items-center space-x-2">
            <History className="w-4 h-4 text-sky-400" />
            <span>Recent Commits History</span>
          </h3>

          {isLoadingTab ? (
            <div className="py-8 text-center text-xs text-slate-400">Loading commits...</div>
          ) : commits.length === 0 ? (
            <div className="text-center py-6 text-xs text-slate-500">No commits found.</div>
          ) : (
            <div className="space-y-2">
              {commits.map((c) => (
                <div
                  key={c.sha}
                  className="bg-slate-950 border border-slate-800/80 rounded-lg p-3 flex items-center justify-between text-xs"
                >
                  <div className="flex items-center space-x-3 min-w-0">
                    {c.author ? (
                      <img src={c.author.avatar_url} alt={c.author.login} className="w-5 h-5 rounded-full" />
                    ) : (
                      <div className="w-5 h-5 rounded-full bg-slate-700" />
                    )}
                    <div className="min-w-0">
                      <p className="font-semibold text-slate-200 truncate">{c.commit.message}</p>
                      <p className="text-[11px] text-slate-500">
                        {c.commit.author.name} • {new Date(c.commit.author.date).toLocaleString()}
                      </p>
                    </div>
                  </div>

                  <a
                    href={c.html_url}
                    target="_blank"
                    rel="noopener noreferrer"
                    className="font-mono text-[11px] text-sky-400 hover:underline shrink-0 bg-slate-900 px-2 py-1 rounded border border-slate-800"
                  >
                    {c.sha.substring(0, 7)}
                  </a>
                </div>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
};
