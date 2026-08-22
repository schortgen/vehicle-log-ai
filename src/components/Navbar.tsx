import React from 'react';
import { Vehicle } from '../types';
import { Car, Fuel, Scan, Clock, ShieldCheck, Database, Plus, CheckCircle2 } from 'lucide-react';

interface NavbarProps {
  currentTab: string;
  setCurrentTab: (tab: string) => void;
  vehicles: Vehicle[];
  activeVehicleId: number | null;
  setActiveVehicleId: (id: number) => void;
  pendingReviewCount: number;
}

export const Navbar: React.FC<NavbarProps> = ({
  currentTab,
  setCurrentTab,
  vehicles,
  activeVehicleId,
  setActiveVehicleId,
  pendingReviewCount,
}) => {
  const activeVehicle = vehicles.find((v) => v.id === activeVehicleId) || vehicles[0];

  return (
    <header id="app-navbar" className="bg-slate-900 border-b border-slate-800 text-slate-100 sticky top-0 z-40">
      <div className="max-w-7xl mx-auto px-4 sm:px-6 lg:px-8">
        <div className="flex items-center justify-between h-16">
          {/* Brand Logo */}
          <div className="flex items-center space-x-3 cursor-pointer" onClick={() => setCurrentTab('dashboard')}>
            <div className="w-10 h-10 rounded-xl bg-gradient-to-tr from-blue-600 to-emerald-500 flex items-center justify-center text-white shadow-lg shadow-blue-500/20">
              <Car className="w-6 h-6" />
            </div>
            <div>
              <div className="flex items-center space-x-1.5">
                <span className="font-bold text-lg tracking-tight bg-gradient-to-r from-white via-slate-100 to-slate-300 bg-clip-text text-transparent">
                  Vehicle Log
                </span>
                <span className="text-xs font-semibold px-1.5 py-0.5 rounded bg-emerald-500/20 text-emerald-400 border border-emerald-500/30">
                  AI
                </span>
              </div>
              <p className="text-[11px] text-slate-400 font-medium">Smart Fleet & Receipt Scanner</p>
            </div>
          </div>

          {/* Active Vehicle Selector Dropdown */}
          {vehicles.length > 0 && (
            <div className="hidden md:flex items-center space-x-2 bg-slate-800/80 px-3 py-1.5 rounded-lg border border-slate-700/60">
              <Car className="w-4 h-4 text-emerald-400" />
              <select
                id="vehicle-selector-dropdown"
                value={activeVehicleId || ''}
                onChange={(e) => setActiveVehicleId(Number(e.target.value))}
                className="bg-transparent text-sm font-medium text-slate-200 focus:outline-none cursor-pointer pr-2"
              >
                {vehicles.map((v) => (
                  <option key={v.id} value={v.id} className="bg-slate-900 text-slate-100">
                    {v.nickname || `${v.year} ${v.make} ${v.model}`} ({v.currentMileage?.toLocaleString()} mi)
                  </option>
                ))}
              </select>
            </div>
          )}

          {/* Navigation Links */}
          <nav className="hidden lg:flex items-center space-x-1">
            <button
              id="nav-tab-dashboard"
              onClick={() => setCurrentTab('dashboard')}
              className={`px-3 py-2 rounded-lg text-sm font-medium transition-all flex items-center space-x-2 ${
                currentTab === 'dashboard'
                  ? 'bg-blue-600 text-white shadow-md shadow-blue-600/30'
                  : 'text-slate-300 hover:text-white hover:bg-slate-800'
              }`}
            >
              <Car className="w-4 h-4" />
              <span>Dashboard</span>
            </button>

            <button
              id="nav-tab-vehicles"
              onClick={() => setCurrentTab('vehicles')}
              className={`px-3 py-2 rounded-lg text-sm font-medium transition-all flex items-center space-x-2 ${
                currentTab === 'vehicles'
                  ? 'bg-blue-600 text-white shadow-md shadow-blue-600/30'
                  : 'text-slate-300 hover:text-white hover:bg-slate-800'
              }`}
            >
              <Car className="w-4 h-4" />
              <span>Vehicles</span>
            </button>

            <button
              id="nav-tab-fuel-entry"
              onClick={() => setCurrentTab('fuel-entry')}
              className={`px-3 py-2 rounded-lg text-sm font-medium transition-all flex items-center space-x-2 ${
                currentTab === 'fuel-entry'
                  ? 'bg-blue-600 text-white shadow-md shadow-blue-600/30'
                  : 'text-slate-300 hover:text-white hover:bg-slate-800'
              }`}
            >
              <Fuel className="w-4 h-4 text-amber-400" />
              <span>Add Event</span>
            </button>

            <button
              id="nav-tab-events"
              onClick={() => setCurrentTab('events')}
              className={`px-3 py-2 rounded-lg text-sm font-medium transition-all flex items-center space-x-2 ${
                currentTab === 'events'
                  ? 'bg-blue-600 text-white shadow-md shadow-blue-600/30'
                  : 'text-slate-300 hover:text-white hover:bg-slate-800'
              }`}
            >
              <Clock className="w-4 h-4" />
              <span>Timeline</span>
            </button>

            <button
              id="nav-tab-scan"
              onClick={() => setCurrentTab('scan')}
              className={`px-3 py-2 rounded-lg text-sm font-medium transition-all flex items-center space-x-2 ${
                currentTab === 'scan'
                  ? 'bg-emerald-600 text-white shadow-md shadow-emerald-600/30'
                  : 'text-slate-300 hover:text-white hover:bg-slate-800'
              }`}
            >
              <Scan className="w-4 h-4 text-emerald-400" />
              <span>AI Scan</span>
            </button>

            <button
              id="nav-tab-review"
              onClick={() => setCurrentTab('review')}
              className={`px-3 py-2 rounded-lg text-sm font-medium transition-all flex items-center space-x-2 relative ${
                currentTab === 'review'
                  ? 'bg-blue-600 text-white shadow-md shadow-blue-600/30'
                  : 'text-slate-300 hover:text-white hover:bg-slate-800'
              }`}
            >
              <ShieldCheck className="w-4 h-4 text-amber-400" />
              <span>Review Queue</span>
              {pendingReviewCount > 0 && (
                <span className="ml-1 px-1.5 py-0.5 text-xs font-bold bg-amber-500 text-slate-950 rounded-full animate-pulse">
                  {pendingReviewCount}
                </span>
              )}
            </button>

            <button
              id="nav-tab-diagnostics"
              onClick={() => setCurrentTab('diagnostics')}
              className={`px-3 py-2 rounded-lg text-sm font-medium transition-all flex items-center space-x-2 ${
                currentTab === 'diagnostics'
                  ? 'bg-blue-600 text-white shadow-md shadow-blue-600/30'
                  : 'text-slate-300 hover:text-white hover:bg-slate-800'
              }`}
            >
              <Database className="w-4 h-4" />
              <span>Data & Backup</span>
            </button>
          </nav>
        </div>

        {/* Mobile Navigation bar */}
        <div className="lg:hidden flex items-center overflow-x-auto space-x-2 py-2 border-t border-slate-800 text-xs">
          <button
            onClick={() => setCurrentTab('dashboard')}
            className={`px-2.5 py-1.5 rounded-md whitespace-nowrap font-medium ${
              currentTab === 'dashboard' ? 'bg-blue-600 text-white' : 'text-slate-300 hover:bg-slate-800'
            }`}
          >
            Dashboard
          </button>
          <button
            onClick={() => setCurrentTab('vehicles')}
            className={`px-2.5 py-1.5 rounded-md whitespace-nowrap font-medium ${
              currentTab === 'vehicles' ? 'bg-blue-600 text-white' : 'text-slate-300 hover:bg-slate-800'
            }`}
          >
            Vehicles
          </button>
          <button
            onClick={() => setCurrentTab('fuel-entry')}
            className={`px-2.5 py-1.5 rounded-md whitespace-nowrap font-medium ${
              currentTab === 'fuel-entry' ? 'bg-blue-600 text-white' : 'text-slate-300 hover:bg-slate-800'
            }`}
          >
            Add Event
          </button>
          <button
            onClick={() => setCurrentTab('events')}
            className={`px-2.5 py-1.5 rounded-md whitespace-nowrap font-medium ${
              currentTab === 'events' ? 'bg-blue-600 text-white' : 'text-slate-300 hover:bg-slate-800'
            }`}
          >
            Timeline
          </button>
          <button
            onClick={() => setCurrentTab('scan')}
            className={`px-2.5 py-1.5 rounded-md whitespace-nowrap font-medium ${
              currentTab === 'scan' ? 'bg-emerald-600 text-white' : 'text-slate-300 hover:bg-slate-800'
            }`}
          >
            AI Scan
          </button>
          <button
            onClick={() => setCurrentTab('review')}
            className={`px-2.5 py-1.5 rounded-md whitespace-nowrap font-medium flex items-center space-x-1 ${
              currentTab === 'review' ? 'bg-blue-600 text-white' : 'text-slate-300 hover:bg-slate-800'
            }`}
          >
            <span>Review</span>
            {pendingReviewCount > 0 && (
              <span className="bg-amber-500 text-slate-950 font-bold px-1 rounded-full text-[10px]">
                {pendingReviewCount}
              </span>
            )}
          </button>
          <button
            onClick={() => setCurrentTab('diagnostics')}
            className={`px-2.5 py-1.5 rounded-md whitespace-nowrap font-medium ${
              currentTab === 'diagnostics' ? 'bg-blue-600 text-white' : 'text-slate-300 hover:bg-slate-800'
            }`}
          >
            Data
          </button>
        </div>
      </div>
    </header>
  );
};
