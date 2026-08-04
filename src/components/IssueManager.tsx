import React, { useState } from 'react';
import { AlertCircle, MessageSquare, Plus, Sparkles, X, Send, User, CheckCircle2 } from 'lucide-react';
import { GitHubIssue } from '../types';

interface IssueManagerProps {
  owner: string;
  repo: string;
  issues: GitHubIssue[];
  onCreateIssue: (title: string, body: string) => Promise<void>;
  onAnalyzeIssue: (issueTitle: string, issueBody: string) => void;
  isLoading: boolean;
}

export const IssueManager: React.FC<IssueManagerProps> = ({
  owner,
  repo,
  issues,
  onCreateIssue,
  onAnalyzeIssue,
  isLoading,
}) => {
  const [filterState, setFilterState] = useState<'all' | 'open' | 'closed'>('open');
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [titleInput, setTitleInput] = useState('');
  const [bodyInput, setBodyInput] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);

  const filteredIssues = issues.filter((issue) => {
    if (filterState === 'all') return true;
    return issue.state === filterState;
  });

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!titleInput.trim()) return;
    setIsSubmitting(true);
    try {
      await onCreateIssue(titleInput.trim(), bodyInput.trim());
      setTitleInput('');
      setBodyInput('');
      setIsModalOpen(false);
    } catch (err) {
      console.error('Failed to create issue', err);
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <div className="space-y-4">
      {/* Issues Header & Actions */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-4 flex flex-col sm:flex-row items-center justify-between gap-3">
        <div className="flex items-center space-x-2">
          <div className="flex items-center space-x-1 bg-slate-950 p-1 rounded-lg border border-slate-800">
            {(['open', 'closed', 'all'] as const).map((state) => (
              <button
                key={state}
                onClick={() => setFilterState(state)}
                className={`px-3 py-1 text-xs font-medium rounded-md transition capitalize ${
                  filterState === state
                    ? 'bg-sky-600 text-white'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                {state} Issues
              </button>
            ))}
          </div>
          <span className="text-xs text-slate-400">({filteredIssues.length})</span>
        </div>

        <button
          onClick={() => setIsModalOpen(true)}
          className="bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-medium px-4 py-2 rounded-lg transition flex items-center space-x-1.5 w-full sm:w-auto justify-center"
        >
          <Plus className="w-4 h-4" />
          <span>New Issue</span>
        </button>
      </div>

      {/* Issues List */}
      {isLoading ? (
        <div className="py-12 text-center text-xs text-slate-400">Loading issues...</div>
      ) : filteredIssues.length === 0 ? (
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-8 text-center space-y-2">
          <CheckCircle2 className="w-8 h-8 text-slate-600 mx-auto" />
          <p className="text-sm font-medium text-slate-300">No {filterState} issues found</p>
          <p className="text-xs text-slate-500">Create a new issue to track tasks, bugs, or feature requests.</p>
        </div>
      ) : (
        <div className="space-y-3">
          {filteredIssues.map((issue) => (
            <div
              key={issue.id}
              className="bg-slate-900 border border-slate-800 rounded-xl p-4 hover:border-slate-700 transition space-y-3"
            >
              <div className="flex items-start justify-between space-x-3">
                <div className="flex items-start space-x-2.5 min-w-0">
                  <AlertCircle
                    className={`w-4 h-4 mt-0.5 shrink-0 ${
                      issue.state === 'open' ? 'text-emerald-400' : 'text-purple-400'
                    }`}
                  />
                  <div>
                    <div className="flex items-center space-x-2 flex-wrap gap-y-1">
                      <a
                        href={issue.html_url}
                        target="_blank"
                        rel="noopener noreferrer"
                        className="font-semibold text-sm text-slate-100 hover:text-sky-400 transition"
                      >
                        {issue.title}
                      </a>
                      <span className="text-xs text-slate-500 font-mono">#{issue.number}</span>
                    </div>

                    <p className="text-xs text-slate-400 mt-1 line-clamp-2">
                      {issue.body || 'No description provided.'}
                    </p>
                  </div>
                </div>

                {/* AI Solution Assistant trigger */}
                <button
                  onClick={() => onAnalyzeIssue(issue.title, issue.body || '')}
                  className="flex items-center space-x-1 px-3 py-1.5 bg-purple-950/80 hover:bg-purple-900/80 border border-purple-800/80 text-purple-300 rounded-lg text-xs font-medium transition shrink-0"
                  title="Generate AI Solution Strategy"
                >
                  <Sparkles className="w-3.5 h-3.5" />
                  <span className="hidden sm:inline">AI Solution</span>
                </button>
              </div>

              {/* Meta & Labels */}
              <div className="flex items-center justify-between text-[11px] text-slate-400 pt-2 border-t border-slate-800/60">
                <div className="flex items-center space-x-2">
                  <img
                    src={issue.user.avatar_url}
                    alt={issue.user.login}
                    className="w-4 h-4 rounded-full"
                  />
                  <span>opened by <strong className="text-slate-300">{issue.user.login}</strong></span>
                  <span>• {new Date(issue.created_at).toLocaleDateString()}</span>
                </div>

                <div className="flex items-center space-x-3">
                  {issue.labels.map((label) => (
                    <span
                      key={label.id}
                      className="px-2 py-0.5 rounded-full text-[10px] font-medium"
                      style={{
                        backgroundColor: `#${label.color}20`,
                        color: `#${label.color}`,
                        borderColor: `#${label.color}50`,
                        borderWidth: '1px',
                      }}
                    >
                      {label.name}
                    </span>
                  ))}

                  <span className="flex items-center space-x-1 text-slate-400">
                    <MessageSquare className="w-3.5 h-3.5" />
                    <span>{issue.comments}</span>
                  </span>
                </div>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* New Issue Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 backdrop-blur-sm p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-lg w-full p-6 text-slate-100 shadow-2xl relative">
            <button
              onClick={() => setIsModalOpen(false)}
              className="absolute top-4 right-4 p-2 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition"
            >
              <X className="w-5 h-5" />
            </button>

            <h3 className="text-lg font-semibold text-slate-100 mb-4">Create New GitHub Issue</h3>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1">Issue Title</label>
                <input
                  type="text"
                  value={titleInput}
                  onChange={(e) => setTitleInput(e.target.value)}
                  placeholder="e.g. Add unit test coverage for user authentication routes"
                  className="w-full bg-slate-950 border border-slate-700 rounded-xl px-3.5 py-2.5 text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-emerald-500"
                  required
                />
              </div>

              <div>
                <label className="block text-xs font-medium text-slate-300 mb-1">Description (Markdown)</label>
                <textarea
                  value={bodyInput}
                  onChange={(e) => setBodyInput(e.target.value)}
                  rows={5}
                  placeholder="Describe the bug or feature request in detail..."
                  className="w-full bg-slate-950 border border-slate-700 rounded-xl px-3.5 py-2.5 text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-emerald-500 font-mono"
                />
              </div>

              <div className="flex justify-end space-x-2 pt-2">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-4 py-2 text-xs font-medium text-slate-400 hover:text-slate-200"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  disabled={isSubmitting || !titleInput.trim()}
                  className="bg-emerald-600 hover:bg-emerald-500 text-white font-medium text-xs px-5 py-2 rounded-lg transition disabled:opacity-50 flex items-center space-x-1"
                >
                  <Send className="w-3.5 h-3.5" />
                  <span>{isSubmitting ? 'Creating...' : 'Submit Issue'}</span>
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
