import React, { useState, useEffect } from 'react';
import { Folder, FileText, ChevronRight, ArrowLeft, Copy, Check, Sparkles, RefreshCw } from 'lucide-react';
import { FileItem } from '../types';
import { GitHubService } from '../services/github';

interface FileBrowserProps {
  owner: string;
  repo: string;
  githubService: GitHubService;
  onAnalyzeFile: (content: string, filename: string) => void;
}

export const FileBrowser: React.FC<FileBrowserProps> = ({
  owner,
  repo,
  githubService,
  onAnalyzeFile,
}) => {
  const [currentPath, setCurrentPath] = useState('');
  const [items, setItems] = useState<FileItem[]>([]);
  const [selectedFile, setSelectedFile] = useState<FileItem | null>(null);
  const [fileContent, setFileContent] = useState<string>('');
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [isContentLoading, setIsContentLoading] = useState<boolean>(false);
  const [copied, setCopied] = useState<boolean>(false);

  // Load directory contents
  useEffect(() => {
    let isMounted = true;
    setIsLoading(true);
    setSelectedFile(null);
    setFileContent('');

    githubService
      .getRepoContents(owner, repo, currentPath)
      .then((data) => {
        if (isMounted) setItems(data);
      })
      .catch((err) => {
        console.error('Failed to fetch folder contents', err);
      })
      .finally(() => {
        if (isMounted) setIsLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [owner, repo, currentPath, githubService]);

  const handleItemClick = (item: FileItem) => {
    if (item.type === 'dir') {
      setCurrentPath(item.path);
    } else {
      setSelectedFile(item);
      setIsContentLoading(true);
      githubService
        .getFileContent(owner, repo, item.path)
        .then((content) => setFileContent(content))
        .catch((err) => setFileContent(`// Error loading file content: ${err.message}`))
        .finally(() => setIsContentLoading(false));
    }
  };

  const navigateUp = () => {
    if (!currentPath) return;
    const parts = currentPath.split('/');
    parts.pop();
    setCurrentPath(parts.join('/'));
  };

  const copyContent = () => {
    navigator.clipboard.writeText(fileContent);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const pathBreadcrumbs = currentPath.split('/').filter(Boolean);

  return (
    <div className="grid grid-cols-1 lg:grid-cols-12 gap-4">
      {/* Directory File Tree */}
      <div className="lg:col-span-4 bg-slate-900 border border-slate-800 rounded-xl overflow-hidden flex flex-col h-[500px]">
        {/* Breadcrumb Header */}
        <div className="bg-slate-950 p-3 border-b border-slate-800 flex items-center justify-between text-xs">
          <div className="flex items-center space-x-1.5 overflow-x-auto text-slate-300">
            {currentPath ? (
              <button
                onClick={navigateUp}
                className="p-1 text-slate-400 hover:text-white rounded hover:bg-slate-800 transition mr-1"
                title="Go up one level"
              >
                <ArrowLeft className="w-3.5 h-3.5" />
              </button>
            ) : null}

            <button
              onClick={() => setCurrentPath('')}
              className="hover:text-sky-400 font-medium text-slate-200"
            >
              root
            </button>

            {pathBreadcrumbs.map((part, index) => (
              <React.Fragment key={index}>
                <ChevronRight className="w-3 h-3 text-slate-600 shrink-0" />
                <button
                  onClick={() => {
                    const newPath = pathBreadcrumbs.slice(0, index + 1).join('/');
                    setCurrentPath(newPath);
                  }}
                  className="hover:text-sky-400 font-medium text-slate-300 truncate max-w-[100px]"
                >
                  {part}
                </button>
              </React.Fragment>
            ))}
          </div>
        </div>

        {/* Directory List */}
        <div className="flex-1 overflow-y-auto divide-y divide-slate-800/40">
          {isLoading ? (
            <div className="p-8 text-center space-y-2 text-slate-400 text-xs">
              <RefreshCw className="w-5 h-5 animate-spin mx-auto text-sky-400" />
              <p>Fetching file tree...</p>
            </div>
          ) : items.length === 0 ? (
            <div className="p-8 text-center text-xs text-slate-500">Empty directory</div>
          ) : (
            items.map((item) => {
              const isSelected = selectedFile?.path === item.path;
              return (
                <button
                  key={item.path}
                  onClick={() => handleItemClick(item)}
                  className={`w-full text-left px-3.5 py-2.5 flex items-center justify-between text-xs transition ${
                    isSelected
                      ? 'bg-sky-950/80 text-sky-300 font-medium border-l-2 border-sky-400'
                      : 'text-slate-300 hover:bg-slate-800/70'
                  }`}
                >
                  <div className="flex items-center space-x-2.5 min-w-0">
                    {item.type === 'dir' ? (
                      <Folder className="w-4 h-4 text-sky-400 shrink-0" />
                    ) : (
                      <FileText className="w-4 h-4 text-slate-400 shrink-0" />
                    )}
                    <span className="truncate">{item.name}</span>
                  </div>

                  {item.type === 'file' && item.size > 0 && (
                    <span className="text-[10px] text-slate-500 font-mono">
                      {(item.size / 1024).toFixed(1)} KB
                    </span>
                  )}
                </button>
              );
            })
          )}
        </div>
      </div>

      {/* Code Viewer Panel */}
      <div className="lg:col-span-8 bg-slate-900 border border-slate-800 rounded-xl overflow-hidden flex flex-col h-[500px]">
        {selectedFile ? (
          <>
            {/* Code Viewer Header */}
            <div className="bg-slate-950 px-4 py-2.5 border-b border-slate-800 flex items-center justify-between text-xs">
              <div className="flex items-center space-x-2 min-w-0">
                <FileText className="w-4 h-4 text-sky-400 shrink-0" />
                <span className="font-mono text-slate-200 truncate">{selectedFile.path}</span>
              </div>

              <div className="flex items-center space-x-2">
                <button
                  onClick={() => onAnalyzeFile(fileContent, selectedFile.name)}
                  disabled={isContentLoading || !fileContent}
                  className="flex items-center space-x-1.5 px-3 py-1 bg-purple-600 hover:bg-purple-500 text-white rounded-lg text-xs font-medium transition disabled:opacity-50"
                  title="Run Gemini Security & Refactor Audit"
                >
                  <Sparkles className="w-3.5 h-3.5" />
                  <span>AI Audit</span>
                </button>

                <button
                  onClick={copyContent}
                  className="p-1.5 text-slate-400 hover:text-white rounded bg-slate-800 hover:bg-slate-700 transition"
                  title="Copy code"
                >
                  {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                </button>
              </div>
            </div>

            {/* Code View Canvas */}
            <div className="flex-1 overflow-auto bg-slate-950 p-4 font-mono text-xs text-slate-200 leading-relaxed">
              {isContentLoading ? (
                <div className="py-20 text-center text-slate-500 space-y-2">
                  <RefreshCw className="w-6 h-6 animate-spin mx-auto text-sky-400" />
                  <p>Loading file content...</p>
                </div>
              ) : (
                <pre className="whitespace-pre-wrap break-words">{fileContent}</pre>
              )}
            </div>
          </>
        ) : (
          <div className="flex-1 flex flex-col items-center justify-center p-8 text-center text-slate-500 space-y-3">
            <FileText className="w-10 h-10 text-slate-700" />
            <p className="text-sm font-medium text-slate-400">Select a file from the tree to preview code</p>
            <p className="text-xs text-slate-600">You can also run automated Gemini AI security audits on any file.</p>
          </div>
        )}
      </div>
    </div>
  );
};
