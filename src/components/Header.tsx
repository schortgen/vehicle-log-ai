import React from 'react';
import { Github, Key, HelpCircle, User, LogOut, CheckCircle2, ShieldAlert } from 'lucide-react';
import { AuthState } from '../types';

interface HeaderProps {
  auth: AuthState;
  onOpenConnectModal: () => void;
  onOpenHelpModal: () => void;
  onDisconnect: () => void;
}

export const Header: React.FC<HeaderProps> = ({
  auth,
  onOpenConnectModal,
  onOpenHelpModal,
  onDisconnect,
}) => {
  return (
    <header className="bg-slate-900 border-b border-slate-800 text-slate-100 sticky top-0 z-30 shadow-md">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8 h-16 flex items-center justify-between">
        {/* Logo & Title */}
        <div className="flex items-center space-x-3">
          <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-sky-500 to-blue-600 flex items-center justify-center text-white shadow-lg shadow-sky-500/20">
            <Github className="w-6 h-6" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <h1 className="font-semibold text-lg text-slate-100 tracking-tight">GitHub Workspace</h1>
              <span className="text-xs px-2 py-0.5 rounded-full bg-sky-950 text-sky-400 border border-sky-800 font-medium">
                AI Studio Connected
              </span>
            </div>
            <p className="text-xs text-slate-400 hidden sm:block">Repository Management, PR Audits & AI Assistant</p>
          </div>
        </div>

        {/* Status Badge & Actions */}
        <div className="flex items-center space-x-3">
          {/* Help button */}
          <button
            onClick={onOpenHelpModal}
            className="flex items-center space-x-1.5 px-3 py-1.5 text-xs font-medium rounded-lg text-slate-300 hover:text-white bg-slate-800 hover:bg-slate-700 border border-slate-700 transition"
            title="How AI Studio connects with GitHub"
          >
            <HelpCircle className="w-4 h-4 text-sky-400" />
            <span className="hidden md:inline">GitHub Sync Info</span>
          </button>

          {/* Auth Status & Account Button */}
          {auth.user ? (
            <div className="flex items-center space-x-2 bg-slate-800/80 border border-slate-700/80 rounded-lg p-1 pr-3">
              <img
                src={auth.user.avatar_url}
                alt={auth.user.login}
                className="w-7 h-7 rounded-full border border-slate-600"
              />
              <div className="text-left hidden sm:block">
                <p className="text-xs font-medium text-slate-200 leading-tight">{auth.user.name || auth.user.login}</p>
                <div className="flex items-center space-x-1 text-[10px] text-slate-400">
                  {auth.authMethod === 'demo' ? (
                    <span className="text-amber-400 flex items-center space-x-0.5">
                      <ShieldAlert className="w-3 h-3" />
                      <span>Demo Mode</span>
                    </span>
                  ) : (
                    <span className="text-emerald-400 flex items-center space-x-0.5">
                      <CheckCircle2 className="w-3 h-3" />
                      <span>{auth.authMethod.toUpperCase()} Connected</span>
                    </span>
                  )}
                </div>
              </div>

              <button
                onClick={onOpenConnectModal}
                className="ml-2 p-1.5 text-slate-400 hover:text-white rounded-md hover:bg-slate-700 transition"
                title="Change Connection Settings"
              >
                <Key className="w-4 h-4" />
              </button>

              <button
                onClick={onDisconnect}
                className="p-1.5 text-slate-400 hover:text-red-400 rounded-md hover:bg-slate-700 transition"
                title="Disconnect Account"
              >
                <LogOut className="w-4 h-4" />
              </button>
            </div>
          ) : (
            <button
              onClick={onOpenConnectModal}
              className="flex items-center space-x-2 bg-sky-600 hover:bg-sky-500 text-white text-xs font-medium px-4 py-2 rounded-lg shadow-sm transition"
            >
              <Key className="w-4 h-4" />
              <span>Connect GitHub</span>
            </button>
          )}
        </div>
      </div>
    </header>
  );
};
