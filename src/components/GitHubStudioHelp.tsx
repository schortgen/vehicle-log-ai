import React from 'react';
import { X, ExternalLink, Github, Key, Sparkles, Download, CheckCircle, ArrowRight } from 'lucide-react';

interface GitHubStudioHelpProps {
  isOpen: boolean;
  onClose: () => void;
  onOpenConnectModal: () => void;
}

export const GitHubStudioHelp: React.FC<GitHubStudioHelpProps> = ({
  isOpen,
  onClose,
  onOpenConnectModal,
}) => {
  if (!isOpen) return null;

  const currentAppUrl = window.location.origin;
  const callbackUrl = `${currentAppUrl}/auth/callback`;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-2xl w-full p-6 text-slate-100 shadow-2xl relative my-8">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-2 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="flex items-center space-x-3 mb-4">
          <div className="p-2.5 rounded-xl bg-sky-500/10 border border-sky-500/20 text-sky-400">
            <Github className="w-6 h-6" />
          </div>
          <div>
            <h2 className="text-xl font-semibold text-slate-100">Connecting AI Studio & GitHub</h2>
            <p className="text-xs text-slate-400">Two seamless options to sync your code and work with GitHub</p>
          </div>
        </div>

        <div className="space-y-6">
          {/* Method 1: AI Studio UI Export/Import */}
          <div className="bg-slate-800/60 border border-slate-700/80 rounded-xl p-4">
            <div className="flex items-center space-x-2 text-sky-400 font-medium text-sm mb-2">
              <Download className="w-4 h-4" />
              <span>Option 1: AI Studio Export & Sync (Built-in UI)</span>
            </div>
            <p className="text-xs text-slate-300 leading-relaxed mb-3">
              Google AI Studio features a direct GitHub export mechanism built right into the platform interface.
            </p>
            <ul className="text-xs text-slate-400 space-y-1.5 list-disc list-inside bg-slate-900/50 p-3 rounded-lg border border-slate-800">
              <li>Open the top-right <strong>Settings / Export</strong> menu in AI Studio.</li>
              <li>Click <strong>Export to GitHub</strong> or <strong>Export ZIP</strong>.</li>
              <li>Authorize your GitHub account to push this entire app repository directly to your GitHub profile.</li>
            </ul>
          </div>

          {/* Method 2: In-App GitHub API & OAuth */}
          <div className="bg-slate-800/60 border border-slate-700/80 rounded-xl p-4">
            <div className="flex items-center space-x-2 text-emerald-400 font-medium text-sm mb-2">
              <Key className="w-4 h-4" />
              <span>Option 2: In-App GitHub API Integration (PAT or OAuth)</span>
            </div>
            <p className="text-xs text-slate-300 leading-relaxed mb-3">
              You can connect your personal GitHub account right inside this dashboard to manage existing repositories, view pull requests, create issues, and run Gemini AI code reviews.
            </p>

            <div className="space-y-2 text-xs">
              <div className="p-2.5 bg-slate-900/60 border border-slate-800 rounded-lg">
                <p className="font-semibold text-slate-200 mb-1">🔑 Personal Access Token (PAT) - Quickest</p>
                <p className="text-slate-400">
                  Generate a Personal Access Token on GitHub with <code>repo</code> & <code>user</code> permissions and paste it into the Connect modal.
                </p>
              </div>

              <div className="p-2.5 bg-slate-900/60 border border-slate-800 rounded-lg">
                <p className="font-semibold text-slate-200 mb-1">🌐 GitHub OAuth App Setup</p>
                <p className="text-slate-400 mb-1">
                  Add this exact redirect callback URL to your GitHub Developer OAuth application settings:
                </p>
                <code className="block bg-slate-950 p-2 rounded text-sky-300 text-[11px] font-mono break-all border border-slate-800">
                  {callbackUrl}
                </code>
              </div>
            </div>
          </div>

          {/* Method 3: Gemini AI Feature */}
          <div className="bg-slate-800/60 border border-slate-700/80 rounded-xl p-4">
            <div className="flex items-center space-x-2 text-purple-400 font-medium text-sm mb-2">
              <Sparkles className="w-4 h-4" />
              <span>AI Code Audit & PR Reviewer</span>
            </div>
            <p className="text-xs text-slate-300 leading-relaxed">
              This app includes server-side Gemini API endpoints to review pull requests, detect potential security vulnerabilities, and propose code fixes for open GitHub issues.
            </p>
          </div>
        </div>

        <div className="mt-6 flex items-center justify-between border-t border-slate-800 pt-4">
          <a
            href="https://github.com/settings/tokens"
            target="_blank"
            rel="noopener noreferrer"
            className="text-xs text-sky-400 hover:text-sky-300 flex items-center space-x-1"
          >
            <span>GitHub Developer Tokens</span>
            <ExternalLink className="w-3.5 h-3.5" />
          </a>

          <div className="flex space-x-2">
            <button
              onClick={() => {
                onClose();
                onOpenConnectModal();
              }}
              className="bg-sky-600 hover:bg-sky-500 text-white text-xs font-medium px-4 py-2 rounded-lg transition flex items-center space-x-1"
            >
              <span>Connect GitHub Account</span>
              <ArrowRight className="w-3.5 h-3.5" />
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
