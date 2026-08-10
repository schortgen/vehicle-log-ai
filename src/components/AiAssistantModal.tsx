import React, { useState, useEffect } from 'react';
import { X, Sparkles, Copy, Check, RefreshCw, AlertCircle } from 'lucide-react';

interface AiAssistantModalProps {
  isOpen: boolean;
  onClose: () => void;
  title: string;
  codeOrTask: string;
  filename?: string;
  taskType: 'security' | 'pr_review' | 'issue_solution';
}

export const AiAssistantModal: React.FC<AiAssistantModalProps> = ({
  isOpen,
  onClose,
  title,
  codeOrTask,
  filename,
  taskType,
}) => {
  const [result, setResult] = useState<string>('');
  const [isLoading, setIsLoading] = useState<boolean>(false);
  const [error, setError] = useState<string | null>(null);
  const [copied, setCopied] = useState<boolean>(false);

  useEffect(() => {
    if (isOpen && codeOrTask) {
      setIsLoading(true);
      setError(null);
      setResult('');

      fetch('/api/ai/analyze-code', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          code: codeOrTask,
          filename,
          taskType,
        }),
      })
        .then((r) => r.json())
        .then((data) => {
          if (data.error) throw new Error(data.error);
          setResult(data.result);
        })
        .catch((err) => {
          setError(err.message || 'Failed to analyze content.');
        })
        .finally(() => {
          setIsLoading(false);
        });
    }
  }, [isOpen, codeOrTask, filename, taskType]);

  if (!isOpen) return null;

  const handleCopy = () => {
    navigator.clipboard.writeText(result);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-950/70 backdrop-blur-sm p-4 overflow-y-auto">
      <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-3xl w-full p-6 text-slate-100 shadow-2xl relative my-8">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 p-2 text-slate-400 hover:text-white rounded-lg hover:bg-slate-800 transition"
        >
          <X className="w-5 h-5" />
        </button>

        {/* Header */}
        <div className="flex items-center space-x-3 mb-4">
          <div className="p-2.5 rounded-xl bg-purple-500/10 border border-purple-500/20 text-purple-400">
            <Sparkles className="w-6 h-6" />
          </div>
          <div>
            <h2 className="text-lg font-semibold text-slate-100">{title}</h2>
            <p className="text-xs text-slate-400">Powered by Google Gemini API</p>
          </div>
        </div>

        {/* Content area */}
        <div className="bg-slate-950 border border-slate-800 rounded-xl p-5 min-h-[300px] max-h-[500px] overflow-y-auto text-xs text-slate-200 leading-relaxed font-sans">
          {isLoading ? (
            <div className="py-24 text-center space-y-3">
              <RefreshCw className="w-8 h-8 text-purple-400 animate-spin mx-auto" />
              <p className="font-medium text-slate-300">Gemini AI is analyzing your code...</p>
              <p className="text-slate-500 text-[11px]">Evaluating security vulnerabilities, clean code refactoring, and logic edge cases.</p>
            </div>
          ) : error ? (
            <div className="p-4 bg-red-950/60 border border-red-800/80 rounded-lg text-red-300 flex items-start space-x-2">
              <AlertCircle className="w-4 h-4 mt-0.5 shrink-0" />
              <div>
                <p className="font-semibold">AI Analysis Failed</p>
                <p className="text-[11px] mt-1 text-red-400">{error}</p>
              </div>
            </div>
          ) : (
            <div className="whitespace-pre-wrap font-sans space-y-2">{result}</div>
          )}
        </div>

        {/* Footer */}
        <div className="mt-4 flex items-center justify-between">
          <span className="text-[11px] text-slate-500">
            {filename ? `Target: ${filename}` : 'AI Code Assistant'}
          </span>

          <div className="flex items-center space-x-2">
            {result && (
              <button
                onClick={handleCopy}
                className="flex items-center space-x-1.5 px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 rounded-lg text-xs font-medium transition"
              >
                {copied ? <Check className="w-4 h-4 text-emerald-400" /> : <Copy className="w-4 h-4" />}
                <span>{copied ? 'Copied' : 'Copy Result'}</span>
              </button>
            )}
            <button
              onClick={onClose}
              className="bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium px-4 py-1.5 rounded-lg transition"
            >
              Close
            </button>
          </div>
        </div>
      </div>
    </div>
  );
};
