import React, { useState, useMemo } from 'react';
import { Search, Star, GitFork, AlertCircle, ExternalLink, Filter, Code2, Lock, Globe } from 'lucide-react';
import { GitHubRepo } from '../types';

interface RepoListProps {
  repos: GitHubRepo[];
  selectedRepo: GitHubRepo | null;
  onSelectRepo: (repo: GitHubRepo) => void;
  isLoading: boolean;
}

const LANGUAGE_COLORS: Record<string, string> = {
  TypeScript: 'bg-blue-500',
  JavaScript: 'bg-yellow-400',
  Python: 'bg-emerald-500',
  HTML: 'bg-orange-500',
  CSS: 'bg-indigo-500',
  Go: 'bg-cyan-500',
  Rust: 'bg-amber-600',
  Java: 'bg-red-500',
};

export const RepoList: React.FC<RepoListProps> = ({
  repos,
  selectedRepo,
  onSelectRepo,
  isLoading,
}) => {
  const [search, setSearch] = useState('');
  const [selectedLanguage, setSelectedLanguage] = useState<string>('all');
  const [filterType, setFilterType] = useState<'all' | 'public' | 'private' | 'sources' | 'forks'>('all');

  const languages = useMemo(() => {
    const langs = new Set<string>();
    repos.forEach(r => {
      if (r.language) langs.add(r.language);
    });
    return Array.from(langs);
  }, [repos]);

  const filteredRepos = useMemo(() => {
    return repos.filter(repo => {
      const matchesSearch = 
        repo.name.toLowerCase().includes(search.toLowerCase()) ||
        (repo.description && repo.description.toLowerCase().includes(search.toLowerCase()));
      
      const matchesLang = selectedLanguage === 'all' || repo.language === selectedLanguage;

      let matchesType = true;
      if (filterType === 'public') matchesType = !repo.private;
      if (filterType === 'private') matchesType = repo.private;
      if (filterType === 'sources') matchesType = !repo.fork;
      if (filterType === 'forks') matchesType = repo.fork;

      return matchesSearch && matchesLang && matchesType;
    });
  }, [repos, search, selectedLanguage, filterType]);

  return (
    <div className="space-y-4">
      {/* Search and Filter Controls */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-4 space-y-3">
        <div className="flex flex-col sm:flex-row gap-3">
          {/* Search Input */}
          <div className="relative flex-1">
            <Search className="w-4 h-4 text-slate-500 absolute left-3 top-3" />
            <input
              type="text"
              value={search}
              onChange={(e) => setSearch(e.target.value)}
              placeholder="Search repositories..."
              className="w-full bg-slate-950 border border-slate-800 rounded-lg pl-9 pr-3 py-2 text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-sky-500 focus:ring-1 focus:ring-sky-500"
            />
          </div>

          {/* Filter Type Pills */}
          <div className="flex items-center space-x-1 bg-slate-950 p-1 rounded-lg border border-slate-800 overflow-x-auto">
            {(['all', 'public', 'private', 'sources', 'forks'] as const).map(type => (
              <button
                key={type}
                onClick={() => setFilterType(type)}
                className={`px-2.5 py-1 text-[11px] font-medium rounded-md transition capitalize whitespace-nowrap ${
                  filterType === type
                    ? 'bg-slate-800 text-sky-400 font-semibold'
                    : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                {type}
              </button>
            ))}
          </div>
        </div>

        {/* Language selector */}
        {languages.length > 0 && (
          <div className="flex items-center space-x-2 pt-1 border-t border-slate-800/60 overflow-x-auto">
            <span className="text-[11px] font-medium text-slate-500 flex items-center space-x-1">
              <Filter className="w-3 h-3" />
              <span>Language:</span>
            </span>
            <button
              onClick={() => setSelectedLanguage('all')}
              className={`px-2 py-0.5 text-[11px] rounded transition ${
                selectedLanguage === 'all' ? 'bg-sky-950 text-sky-300 font-medium' : 'text-slate-400 hover:text-slate-200'
              }`}
            >
              All
            </button>
            {languages.map(lang => (
              <button
                key={lang}
                onClick={() => setSelectedLanguage(lang)}
                className={`px-2 py-0.5 text-[11px] rounded transition ${
                  selectedLanguage === lang ? 'bg-sky-950 text-sky-300 font-medium' : 'text-slate-400 hover:text-slate-200'
                }`}
              >
                {lang}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Repo Cards List */}
      {isLoading ? (
        <div className="py-12 text-center space-y-3">
          <div className="w-8 h-8 border-2 border-sky-500 border-t-transparent rounded-full animate-spin mx-auto" />
          <p className="text-xs text-slate-400">Loading repositories from GitHub...</p>
        </div>
      ) : filteredRepos.length === 0 ? (
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-8 text-center space-y-2">
          <Code2 className="w-8 h-8 text-slate-600 mx-auto" />
          <p className="text-sm font-medium text-slate-300">No repositories found</p>
          <p className="text-xs text-slate-500">Try adjusting your search query or filter settings.</p>
        </div>
      ) : (
        <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-3">
          {filteredRepos.map((repo) => {
            const isSelected = selectedRepo?.id === repo.id;

            return (
              <div
                key={repo.id}
                onClick={() => onSelectRepo(repo)}
                className={`group cursor-pointer bg-slate-900 border rounded-xl p-4 transition-all duration-150 flex flex-col justify-between ${
                  isSelected
                    ? 'border-sky-500 ring-1 ring-sky-500/50 bg-slate-900/90'
                    : 'border-slate-800 hover:border-slate-700 hover:bg-slate-850'
                }`}
              >
                <div>
                  {/* Repo Header */}
                  <div className="flex items-start justify-between space-x-2 mb-2">
                    <div className="flex items-center space-x-2 min-w-0">
                      {repo.private ? (
                        <Lock className="w-3.5 h-3.5 text-amber-400 shrink-0" />
                      ) : (
                        <Globe className="w-3.5 h-3.5 text-slate-400 shrink-0" />
                      )}
                      <h3 className="font-semibold text-xs text-slate-100 group-hover:text-sky-400 truncate">
                        {repo.name}
                      </h3>
                    </div>

                    <a
                      href={repo.html_url}
                      target="_blank"
                      rel="noopener noreferrer"
                      onClick={(e) => e.stopPropagation()}
                      className="text-slate-500 hover:text-slate-300 p-1 rounded hover:bg-slate-800"
                      title="View on GitHub.com"
                    >
                      <ExternalLink className="w-3.5 h-3.5" />
                    </a>
                  </div>

                  {/* Description */}
                  <p className="text-xs text-slate-400 line-clamp-2 mb-3 min-h-[2rem]">
                    {repo.description || 'No description provided.'}
                  </p>
                </div>

                {/* Topics / Meta */}
                <div className="space-y-2.5 pt-2 border-t border-slate-800/60">
                  {repo.topics && repo.topics.length > 0 && (
                    <div className="flex flex-wrap gap-1">
                      {repo.topics.slice(0, 3).map((topic) => (
                        <span
                          key={topic}
                          className="px-1.5 py-0.5 rounded text-[10px] bg-sky-950/60 text-sky-400 border border-sky-900/40"
                        >
                          {topic}
                        </span>
                      ))}
                    </div>
                  )}

                  <div className="flex items-center justify-between text-[11px] text-slate-400">
                    <div className="flex items-center space-x-3">
                      {repo.language && (
                        <span className="flex items-center space-x-1.5">
                          <span className={`w-2 h-2 rounded-full ${LANGUAGE_COLORS[repo.language] || 'bg-slate-400'}`} />
                          <span>{repo.language}</span>
                        </span>
                      )}

                      <span className="flex items-center space-x-1">
                        <Star className="w-3 h-3 text-amber-400" />
                        <span>{repo.stargazers_count}</span>
                      </span>

                      <span className="flex items-center space-x-1">
                        <GitFork className="w-3 h-3 text-slate-400" />
                        <span>{repo.forks_count}</span>
                      </span>
                    </div>

                    {repo.open_issues_count > 0 && (
                      <span className="flex items-center space-x-1 text-sky-400">
                        <AlertCircle className="w-3 h-3" />
                        <span>{repo.open_issues_count}</span>
                      </span>
                    )}
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
};
