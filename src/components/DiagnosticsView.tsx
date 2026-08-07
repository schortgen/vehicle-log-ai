import React, { useState } from 'react';
import { Vehicle, VehicleEvent, ReviewItem } from '../types';
import { Database, Download, Upload, RefreshCw, CheckCircle2, ShieldCheck, FileJson, AlertCircle } from 'lucide-react';

interface DiagnosticsViewProps {
  vehicles: Vehicle[];
  events: VehicleEvent[];
  reviewItems: ReviewItem[];
  onImportBackup: (data: { vehicles: Vehicle[]; events: VehicleEvent[]; reviewItems: ReviewItem[] }) => void;
  onResetSampleData: () => void;
}

export const DiagnosticsView: React.FC<DiagnosticsViewProps> = ({
  vehicles,
  events,
  reviewItems,
  onImportBackup,
  onResetSampleData,
}) => {
  const [importStatus, setImportStatus] = useState<string | null>(null);

  const handleExportJSON = () => {
    const data = {
      exportDate: new Date().toISOString(),
      schemaVersion: 6,
      appName: 'Vehicle Log AI',
      vehicles,
      events,
      reviewItems,
    };

    const blob = new Blob([JSON.stringify(data, null, 2)], { type: 'application/json' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = `vehicle_log_ai_backup_${new Date().toISOString().split('T')[0]}.json`;
    a.click();
    URL.revokeObjectURL(url);
  };

  const handleImportJSON = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onload = (event) => {
      try {
        const json = JSON.parse(event.target?.result as string);
        if (json.vehicles && json.events) {
          onImportBackup({
            vehicles: json.vehicles,
            events: json.events,
            reviewItems: json.reviewItems || [],
          });
          setImportStatus('Successfully restored database from backup!');
        } else {
          setImportStatus('Invalid backup file format.');
        }
      } catch (err) {
        setImportStatus('Failed to parse JSON backup file.');
      }
    };
    reader.readAsText(file);
  };

  return (
    <div id="diagnostics-view" className="space-y-6">
      {/* Header */}
      <div className="bg-gradient-to-r from-blue-600/20 via-slate-900 to-slate-900 border border-blue-500/30 rounded-2xl p-6 text-slate-100">
        <div className="flex items-center space-x-3">
          <div className="w-12 h-12 rounded-xl bg-blue-500/20 text-blue-400 flex items-center justify-center shrink-0">
            <Database className="w-6 h-6" />
          </div>
          <div>
            <h1 className="text-2xl font-extrabold tracking-tight">Database & Backup Tools</h1>
            <p className="text-sm text-slate-400">
              Manage database schema, export full JSON database backups, or restore vehicle data.
            </p>
          </div>
        </div>
      </div>

      {/* Database Statistics */}
      <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-2">
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider block">Vehicles Table</span>
          <div className="text-3xl font-extrabold text-white">{vehicles.length}</div>
          <p className="text-xs text-slate-400">Active Fleet Records</p>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-2">
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider block">Events Log Table</span>
          <div className="text-3xl font-extrabold text-white">{events.length}</div>
          <p className="text-xs text-slate-400">Fuel & Service Records</p>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-5 space-y-2">
          <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider block">Review Queue Table</span>
          <div className="text-3xl font-extrabold text-amber-400">{reviewItems.length}</div>
          <p className="text-xs text-slate-400">AI Receipt OCR Items</p>
        </div>
      </div>

      {/* Export & Import Tools */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
          <div className="flex items-center space-x-2 text-emerald-400 font-bold">
            <Download className="w-5 h-5" />
            <span>Export Database Backup</span>
          </div>
          <p className="text-xs text-slate-400">
            Download your full vehicle history, fuel receipts, and mileage records as a structured JSON file.
          </p>
          <button
            onClick={handleExportJSON}
            className="w-full py-3 bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-sm rounded-xl shadow-lg shadow-emerald-600/30 transition-all flex items-center justify-center space-x-2"
          >
            <FileJson className="w-4 h-4" />
            <span>Export Backup (.JSON)</span>
          </button>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
          <div className="flex items-center space-x-2 text-blue-400 font-bold">
            <Upload className="w-5 h-5" />
            <span>Restore Backup</span>
          </div>
          <p className="text-xs text-slate-400">
            Upload a previously saved JSON backup file to restore vehicles and fuel log history.
          </p>
          <label className="w-full py-3 bg-slate-800 hover:bg-slate-700 text-slate-200 border border-slate-700 font-bold text-sm rounded-xl transition-all flex items-center justify-center space-x-2 cursor-pointer">
            <Upload className="w-4 h-4" />
            <span>Select Backup File</span>
            <input type="file" accept=".json" onChange={handleImportJSON} className="hidden" />
          </label>
        </div>
      </div>

      {importStatus && (
        <div className="p-4 bg-blue-500/10 border border-blue-500/30 rounded-xl text-blue-300 text-xs flex items-center space-x-2">
          <ShieldCheck className="w-4 h-4 text-blue-400 shrink-0" />
          <span>{importStatus}</span>
        </div>
      )}

      {/* Reset to Default */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div className="space-y-1">
          <h3 className="text-sm font-bold text-white">Reset Demo Data</h3>
          <p className="text-xs text-slate-400">
            Re-populate vehicles and sample fuel logs with default data.
          </p>
        </div>
        <button
          onClick={onResetSampleData}
          className="px-4 py-2 bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white border border-slate-700 font-semibold text-xs rounded-xl transition-all flex items-center space-x-1 shrink-0"
        >
          <RefreshCw className="w-4 h-4" />
          <span>Reset Sample Data</span>
        </button>
      </div>
    </div>
  );
};
