import React, { useState, useEffect } from 'react';
import { X, Key, Shield, Sparkles, ExternalLink, Copy, Check, Lock, Github } from 'lucide-react';
import { AuthState } from '../types';

interface ConnectModalProps {
  isOpen: boolean;
  onClose: () => void;
  onConnectPAT: (token: string) => Promise<void>;
  onUseDemo: () => void;
  auth: AuthState;
}

export const ConnectModal: React.FC<ConnectModalProps> = ({
  isOpen,
  onClose,
  onConnectPAT,
  onUseDemo,
  auth,
}) => {
  const [activeTab, setActiveTab] = useState<'pat' | 'oauth' | 'demo'>('pat');
  const [tokenInput, setTokenInput] = useState('');
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [copied, setCopied] = useState(false);
  const [oauthConfig, setOauthConfig] = useState<{ configured: boolean; url?: string; redirectUri?: string; message?: string } | null>(null);

  const callbackUrl = `${window.location.origin}/auth/callback`;

  useEffect(() => {
    if (isOpen && activeTab === 'oauth') {
      fetch('/api/auth/github/url')
        .then(r => r.json())
        .then(data => setOauthConfig(data))
        .catch(() => setOauthConfig({ configured: false, message: 'Could not fetch OAuth configuration.' }));
    }
  }, [isOpen, activeTab]);

  if (!isOpen) return null;

  const handlePATSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!tokenInput.trim()) return;
    setIsSubmitting(true);
    try {
      await onConnectPAT(tokenInput.trim());
      onClose();
    } catch (err) {
      // Error handled via auth state
    } finally {
      setIsSubmitting(false);
    }
  };

  const copyCallbackUrl = () => {
    navigator.clipboard.writeText(callbackUrl);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  const handleOAuthLogin = () => {
    if (oauthConfig?.url) {
      window.open(oauthConfig.url, 'github_oauth_popup', 'width=600,height=700');
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-lg w-full p-6 text-slate-100 shadow-2xl relative my-8">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-2 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition"
        >
          <X className="w-5 h-5" />
        </button>

        <div className="flex items-center space-x-3 mb-6">
          <div className="p-2.5 rounded-xl bg-sky-500/10 border border-sky-500/20 text-sky-400">
            <Github className="w-6 h-6" />
          </div>
          <div>
            <h2 className="text-xl font-semibold text-slate-100">Connect to GitHub</h2>
            <p className="text-xs text-slate-400">Select your preferred authorization method</p>
          </div>
        </div>

        {/* Auth Method Tabs */}
        <div className="grid grid-cols-3 gap-1 bg-slate-950 p-1 rounded-xl border border-slate-800 mb-6">
          <button
            onClick={() => setActiveTab('pat')}
            className={`py-2 text-xs font-medium rounded-lg transition ${
              activeTab === 'pat'
                ? 'bg-sky-600 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            Personal Access Token
          </button>
          <button
            onClick={() => setActiveTab('oauth')}
            className={`py-2 text-xs font-medium rounded-lg transition ${
              activeTab === 'oauth'
                ? 'bg-sky-600 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            OAuth App
          </button>
          <button
            onClick={() => setActiveTab('demo')}
            className={`py-2 text-xs font-medium rounded-lg transition ${
              activeTab === 'demo'
                ? 'bg-sky-600 text-white shadow-sm'
                : 'text-slate-400 hover:text-slate-200 hover:bg-slate-900'
            }`}
          >
            Demo Mode
          </button>
        </div>

        {/* Error message display */}
        {auth.error && (
          <div className="mb-4 p-3 rounded-lg bg-red-950/60 border border-red-800/80 text-red-300 text-xs">
            {auth.error}
          </div>
        )}

        {/* Tab 1: Personal Access Token */}
        {activeTab === 'pat' && (
          <form onSubmit={handlePATSubmit} className="space-y-4">
            <div>
              <label className="block text-xs font-medium text-slate-300 mb-1.5">
                GitHub Personal Access Token (PAT)
              </label>
              <div className="relative">
                <input
                  type="password"
                  value={tokenInput}
                  onChange={(e) => setTokenInput(e.target.value)}
                  placeholder="ghp_xxxxxxxxxxxxxxxxxxxx or github_pat_xxxx"
                  className="w-full bg-slate-950 border border-slate-700 rounded-xl px-3.5 py-2.5 text-xs text-slate-100 placeholder-slate-500 focus:outline-none focus:border-sky-500 focus:ring-1 focus:ring-sky-500"
                  required
                />
                <Key className="w-4 h-4 text-slate-500 absolute right-3 top-3" />
              </div>
            </div>

            <div className="p-3 bg-slate-950/60 border border-slate-800 rounded-xl text-xs space-y-2">
              <div className="flex items-center space-x-1.5 font-medium text-slate-200">
                <Lock className="w-3.5 h-3.5 text-emerald-400" />
                <span>How to generate a token:</span>
              </div>
              <ol className="list-decimal list-inside text-slate-400 space-y-1 text-[11px]">
                <li>Go to GitHub <strong>Developer Settings &gt; Personal Access Tokens</strong>.</li>
                <li>Generate a token (Fine-Grained or Classic).</li>
                <li>Select permissions: <code>repo</code>, <code>read:org</code>, <code>user</code>.</li>
              </ol>
              <a
                href="https://github.com/settings/tokens/new"
                target="_blank"
                rel="noopener noreferrer"
                className="inline-flex items-center space-x-1 text-sky-400 hover:text-sky-300 font-medium text-[11px] pt-1"
              >
                <span>Create Token on GitHub.com</span>
                <ExternalLink className="w-3 h-3" />
              </a>
            </div>

            <div className="flex justify-end space-x-2 pt-2">
              <button
                type="button"
                onClick={onClose}
                className="px-4 py-2 text-xs font-medium text-slate-400 hover:text-slate-200 rounded-lg"
              >
                Cancel
              </button>
              <button
                type="submit"
                disabled={isSubmitting || !tokenInput.trim()}
                className="bg-sky-600 hover:bg-sky-500 text-white font-medium text-xs px-5 py-2 rounded-lg transition disabled:opacity-50 flex items-center space-x-1.5"
              >
                {isSubmitting ? 'Validating Token...' : 'Connect Token'}
              </button>
            </div>
          </form>
        )}

        {/* Tab 2: OAuth App Setup */}
        {activeTab === 'oauth' && (
          <div className="space-y-4">
            <div className="p-3 bg-slate-950 border border-slate-800 rounded-xl space-y-2">
              <p className="text-xs font-semibold text-slate-200">OAuth Callback URL</p>
              <p className="text-[11px] text-slate-400">
                Set this redirect URI when registering your GitHub OAuth Developer App:
              </p>
              <div className="flex items-center space-x-2 bg-slate-900 border border-slate-800 p-2 rounded-lg">
                <code className="text-sky-300 text-[11px] font-mono flex-1 break-all">
                  {callbackUrl}
                </code>
                <button
                  onClick={copyCallbackUrl}
                  className="p-1.5 text-slate-400 hover:text-white rounded bg-slate-800 hover:bg-slate-700 transition"
                  title="Copy Redirect URI"
                >
                  {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                </button>
              </div>
            </div>

            {oauthConfig?.configured ? (
              <div className="p-3 bg-emerald-950/40 border border-emerald-800/60 rounded-xl text-xs space-y-2">
                <p className="text-emerald-300 font-medium">GitHub OAuth App Configured in Server</p>
                <button
                  onClick={handleOAuthLogin}
                  className="w-full bg-emerald-600 hover:bg-emerald-500 text-white font-medium py-2 rounded-lg text-xs transition flex items-center justify-center space-x-2"
                >
                  <Github className="w-4 h-4" />
                  <span>Authorize with GitHub OAuth</span>
                </button>
              </div>
            ) : (
              <div className="p-3 bg-slate-950 border border-slate-800 rounded-xl text-xs space-y-2">
                <p className="text-amber-400 font-medium">Server OAuth Variables Missing</p>
                <p className="text-slate-400">
                  To enable GitHub OAuth, set <code>GITHUB_CLIENT_ID</code> in your <code>.env.example</code> or environment secrets.
                </p>
                <a
                  href="https://github.com/settings/applications/new"
                  target="_blank"
                  rel="noopener noreferrer"
                  className="inline-flex items-center space-x-1 text-sky-400 hover:text-sky-300 font-medium pt-1"
                >
                  <span>Register GitHub OAuth App</span>
                  <ExternalLink className="w-3 h-3" />
                </a>
              </div>
            )}
          </div>
        )}

        {/* Tab 3: Demo Mode */}
        {activeTab === 'demo' && (
          <div className="space-y-4">
            <div className="p-4 bg-slate-950 border border-slate-800 rounded-xl space-y-3">
              <div className="flex items-center space-x-2 text-amber-400 font-semibold text-xs">
                <Sparkles className="w-4 h-4" />
                <span>Explore Interactive Demo Mode</span>
              </div>
              <p className="text-xs text-slate-300 leading-relaxed">
                Test the full dashboard functionality—browse repositories, explore file trees, review pull requests, create issues, and test AI code audits—using Octocat sample repositories.
              </p>
            </div>

            <div className="flex justify-end pt-2">
              <button
                onClick={() => {
                  onUseDemo();
                  onClose();
                }}
                className="bg-amber-600 hover:bg-amber-500 text-white font-medium text-xs px-5 py-2.5 rounded-lg transition"
              >
                Launch Demo Mode
              </button>
            </div>
          </div>
        )}
      </div>
    </div>
  );
};
