import React from 'react';
import { GitPullRequest, GitBranch, CheckCircle2, Sparkles, ExternalLink, Clock } from 'lucide-react';
import { GitHubPullRequest } from '../types';

interface PullRequestManagerProps {
  owner: string;
  repo: string;
  pullRequests: GitHubPullRequest[];
  onAnalyzePR: (prTitle: string, prBody: string) => void;
  isLoading: boolean;
}

export const PullRequestManager: React.FC<PullRequestManagerProps> = ({
  owner,
  repo,
  pullRequests,
  onAnalyzePR,
  isLoading,
}) => {
  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-4 flex items-center justify-between">
        <div className="flex items-center space-x-2 text-xs font-semibold text-slate-200">
          <GitPullRequest className="w-4 h-4 text-sky-400" />
          <span>Pull Requests ({pullRequests.length})</span>
        </div>
      </div>

      {isLoading ? (
        <div className="py-12 text-center text-xs text-slate-400">Loading pull requests...</div>
      ) : pullRequests.length === 0 ? (
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-8 text-center space-y-2">
          <GitPullRequest className="w-8 h-8 text-slate-600 mx-auto" />
          <p className="text-sm font-medium text-slate-300">No open pull requests</p>
          <p className="text-xs text-slate-500">All features and branches are currently merged into the default branch.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {pullRequests.map((pr) => (
            <div
              key={pr.id}
              className="bg-slate-900 border border-slate-800 rounded-xl p-4 hover:border-slate-700 transition space-y-3"
            >
              <div className="flex items-start justify-between space-x-3">
                <div className="flex items-start space-x-2.5 min-w-0">
                  <GitPullRequest className="w-4 h-4 text-emerald-400 mt-0.5 shrink-0" />
                  <div>
                    <div className="flex items-center space-x-2 flex-wrap gap-y-1">
                      <a
                        href={pr.html_url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="font-semibold text-sm text-slate-100 hover:text-sky-400 transition"
                      >
                        {pr.title}
                      </a>
                      <span className="text-xs text-slate-500 font-mono">#{pr.number}</span>
                    </div>

                    {/* Branch Info */}
                    <div className="flex items-center space-x-2 mt-1.5 text-[11px] text-slate-400">
                      <span className="flex items-center space-x-1 font-mono bg-slate-950 px-2 py-0.5 rounded border border-slate-800 text-sky-300">
                        <GitBranch className="w-3 h-3 text-sky-400" />
                        <span>{pr.head.ref}</span>
                      </span>
                      <span>into</span>
                      <span className="font-mono bg-slate-950 px-2 py-0.5 rounded border border-slate-800 text-slate-300">
                        {pr.base.ref}
                      </span>
                    </div>

                    {pr.body && (
                      <p className="text-xs text-slate-400 mt-2 line-clamp-2">{pr.body}</p>
                    )}
                  </div>
                </div>

                <button
                  onClick={() => onAnalyzePR(pr.title, pr.body || '')}
                  className="flex items-center space-x-1.5 px-3 py-1.5 bg-sky-950/80 hover:bg-sky-900/80 border border-sky-800/80 text-sky-300 rounded-lg text-xs font-medium transition shrink-0"
                  title="Run Gemini Automated PR Code Review"
                >
                  <Sparkles className="w-3.5 h-3.5 text-sky-400" />
                  <span>AI PR Review</span>
                </button>
              </div>

              {/* Author & Meta */}
              <div className="flex items-center justify-between text-[11px] text-slate-400 pt-2 border-t border-slate-800/60">
                <div className="flex items-center space-x-2">
                  <img src={pr.user.avatar_url} alt={pr.user.login} className="w-4 h-4 rounded-full" />
                  <span>by <strong className="text-slate-300">{pr.user.login}</strong></span>
                </div>

                <div className="flex items-center space-x-2">
                  <Clock className="w-3 h-3 text-slate-500" />
                  <span>{new Date(pr.created_at).toLocaleDateString()}</span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}
    </div>
  );
};
