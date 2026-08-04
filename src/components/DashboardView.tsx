import React from 'react';
import { Vehicle, VehicleEvent, ReviewItem } from '../types';
import { Car, Fuel, Wrench, Scan, Clock, AlertTriangle, Plus, ChevronRight, DollarSign, Gauge, TrendingUp, ShieldCheck } from 'lucide-react';

interface DashboardViewProps {
  activeVehicle: Vehicle | undefined;
  vehicles: Vehicle[];
  events: VehicleEvent[];
  reviewItems: ReviewItem[];
  setCurrentTab: (tab: string) => void;
  onOpenAddEventModal: () => void;
}

export const DashboardView: React.FC<DashboardViewProps> = ({
  activeVehicle,
  vehicles,
  events,
  reviewItems,
  setCurrentTab,
  onOpenAddEventModal,
}) => {
  const vehicleEvents = events.filter((e) => !activeVehicle || e.vehicleId === activeVehicle.id);
  const pendingReviews = reviewItems.filter((r) => r.status === 'PENDING');

  // Calculate stats for current vehicle
  const fuelEvents = vehicleEvents.filter((e) => e.eventType === 'FUEL');
  const totalFuelCost = fuelEvents.reduce((acc, curr) => acc + (curr.totalCost || 0), 0);
  const totalGallons = fuelEvents.reduce((acc, curr) => acc + (curr.gallons || 0), 0);
  
  // Calculate estimated MPG if we have multiple fuel logs with odometer
  let estimatedMpg: number | null = null;
  const sortedFuelWithOdo = [...fuelEvents]
    .filter((e) => e.odometer && e.gallons)
    .sort((a, b) => (a.odometer || 0) - (b.odometer || 0));

  if (sortedFuelWithOdo.length >= 2) {
    const minOdo = sortedFuelWithOdo[0].odometer || 0;
    const maxOdo = sortedFuelWithOdo[sortedFuelWithOdo.length - 1].odometer || 0;
    const totalDist = maxOdo - minOdo;
    const gallonsUsed = sortedFuelWithOdo.slice(1).reduce((sum, e) => sum + (e.gallons || 0), 0);
    if (gallonsUsed > 0 && totalDist > 0) {
      estimatedMpg = Number((totalDist / gallonsUsed).toFixed(1));
    }
  }

  return (
    <div id="dashboard-view" className="space-y-6">
      {/* Top Banner & Active Vehicle Summary Card */}
      {activeVehicle ? (
        <div className="bg-gradient-to-r from-slate-900 via-slate-800 to-slate-900 rounded-2xl p-6 border border-slate-800 text-slate-100 shadow-xl relative overflow-hidden">
          <div className="absolute -right-10 -bottom-10 opacity-10 pointer-events-none">
            <Car className="w-80 h-80 text-blue-400" />
          </div>

          <div className="relative z-10 flex flex-col md:flex-row md:items-center justify-between gap-6">
            <div className="space-y-2">
              <div className="flex items-center space-x-3">
                <span className="px-2.5 py-1 text-xs font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 rounded-full">
                  ACTIVE VEHICLE
                </span>
                {activeVehicle.licensePlate && (
                  <span className="text-xs font-mono px-2 py-0.5 bg-slate-800 text-slate-300 rounded border border-slate-700">
                    PLATE: {activeVehicle.licensePlate}
                  </span>
                )}
              </div>
              <h1 className="text-2xl sm:text-3xl font-extrabold text-white tracking-tight">
                {activeVehicle.nickname || `${activeVehicle.year} ${activeVehicle.make} ${activeVehicle.model}`}
              </h1>
              <p className="text-sm text-slate-400 flex flex-wrap items-center gap-x-4 gap-y-1">
                <span>{activeVehicle.year} {activeVehicle.make} {activeVehicle.model}</span>
                {activeVehicle.vin && <span>• VIN: <code className="text-slate-300">{activeVehicle.vin}</code></span>}
              </p>
            </div>

            <div className="flex items-center space-x-4 bg-slate-800/80 p-4 rounded-xl border border-slate-700/80 backdrop-blur-sm">
              <div className="w-12 h-12 rounded-lg bg-blue-600/20 text-blue-400 flex items-center justify-center">
                <Gauge className="w-6 h-6" />
              </div>
              <div>
                <span className="text-xs font-medium text-slate-400 uppercase tracking-wider block">Odometer</span>
                <span className="text-xl font-bold text-white">
                  {activeVehicle.currentMileage ? `${activeVehicle.currentMileage.toLocaleString()} mi` : 'Not recorded'}
                </span>
              </div>
            </div>
          </div>
        </div>
      ) : (
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 text-center text-slate-300">
          <p className="text-lg font-medium">No active vehicle selected.</p>
          <button
            onClick={() => setCurrentTab('vehicles')}
            className="mt-3 inline-flex items-center space-x-2 px-4 py-2 rounded-lg bg-blue-600 text-white font-medium hover:bg-blue-500"
          >
            <Plus className="w-4 h-4" />
            <span>Add Vehicle</span>
          </button>
        </div>
      )}

      {/* Pending Review Queue Alert Banner */}
      {pendingReviews.length > 0 && (
        <div
          id="pending-reviews-alert"
          onClick={() => setCurrentTab('review')}
          className="bg-amber-500/10 border border-amber-500/30 rounded-xl p-4 flex items-center justify-between cursor-pointer hover:bg-amber-500/15 transition-all text-amber-200"
        >
          <div className="flex items-center space-x-3">
            <div className="w-10 h-10 rounded-lg bg-amber-500/20 text-amber-400 flex items-center justify-center shrink-0">
              <AlertTriangle className="w-5 h-5" />
            </div>
            <div>
              <p className="font-semibold text-sm text-amber-300">
                {pendingReviews.length} AI Scanned Receipt{pendingReviews.length > 1 ? 's' : ''} Pending Review
              </p>
              <p className="text-xs text-amber-200/80">
                Verify AI-extracted fuel data and add to vehicle log history.
              </p>
            </div>
          </div>
          <div className="flex items-center space-x-1 text-xs font-semibold text-amber-400 bg-amber-500/20 px-3 py-1.5 rounded-lg border border-amber-500/30">
            <span>Review Now</span>
            <ChevronRight className="w-4 h-4" />
          </div>
        </div>
      )}

      {/* Key Metric Stats Grid */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-2">
          <div className="flex items-center justify-between text-slate-400">
            <span className="text-xs font-medium uppercase tracking-wider">Total Fuel Spent</span>
            <div className="p-2 rounded-lg bg-emerald-500/10 text-emerald-400">
              <DollarSign className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-extrabold text-slate-100">
            ${totalFuelCost.toFixed(2)}
          </div>
          <p className="text-xs text-slate-400">{fuelEvents.length} total fuel refills</p>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-2">
          <div className="flex items-center justify-between text-slate-400">
            <span className="text-xs font-medium uppercase tracking-wider">Total Gallons</span>
            <div className="p-2 rounded-lg bg-blue-500/10 text-blue-400">
              <Fuel className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-extrabold text-slate-100">
            {totalGallons.toFixed(2)} gal
          </div>
          <p className="text-xs text-slate-400">Average ${(totalGallons > 0 ? totalFuelCost / totalGallons : 0).toFixed(2)} / gal</p>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-2">
          <div className="flex items-center justify-between text-slate-400">
            <span className="text-xs font-medium uppercase tracking-wider">Calculated MPG</span>
            <div className="p-2 rounded-lg bg-purple-500/10 text-purple-400">
              <TrendingUp className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-extrabold text-slate-100">
            {estimatedMpg ? `${estimatedMpg} MPG` : 'N/A'}
          </div>
          <p className="text-xs text-slate-400">{sortedFuelWithOdo.length >= 2 ? 'Based on odometer logs' : 'Log 2+ full refills'}</p>
        </div>

        <div className="bg-slate-900 border border-slate-800 rounded-xl p-5 space-y-2">
          <div className="flex items-center justify-between text-slate-400">
            <span className="text-xs font-medium uppercase tracking-wider">Logged Events</span>
            <div className="p-2 rounded-lg bg-amber-500/10 text-amber-400">
              <Clock className="w-4 h-4" />
            </div>
          </div>
          <div className="text-2xl font-extrabold text-slate-100">
            {vehicleEvents.length}
          </div>
          <p className="text-xs text-slate-400">Fuel & maintenance records</p>
        </div>
      </div>

      {/* Quick Action Grid */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
        <h2 className="text-lg font-bold text-white flex items-center space-x-2">
          <span>Quick Actions</span>
        </h2>
        <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
          <button
            id="quick-action-log-fuel"
            onClick={() => setCurrentTab('fuel-entry')}
            className="p-4 rounded-xl bg-slate-800 hover:bg-slate-700/80 border border-slate-700 transition-all text-left space-y-2 group"
          >
            <div className="w-10 h-10 rounded-lg bg-amber-500/20 text-amber-400 flex items-center justify-center group-hover:scale-110 transition-transform">
              <Fuel className="w-5 h-5" />
            </div>
            <div>
              <p className="font-semibold text-sm text-slate-100">Log Fuel</p>
              <p className="text-xs text-slate-400">Manual fuel entry</p>
            </div>
          </button>

          <button
            id="quick-action-scan-ai"
            onClick={() => setCurrentTab('scan')}
            className="p-4 rounded-xl bg-slate-800 hover:bg-slate-700/80 border border-slate-700 transition-all text-left space-y-2 group"
          >
            <div className="w-10 h-10 rounded-lg bg-emerald-500/20 text-emerald-400 flex items-center justify-center group-hover:scale-110 transition-transform">
              <Scan className="w-5 h-5" />
            </div>
            <div>
              <p className="font-semibold text-sm text-slate-100">Scan Receipt</p>
              <p className="text-xs text-slate-400">AI OCR Scanner</p>
            </div>
          </button>

          <button
            id="quick-action-add-event"
            onClick={onOpenAddEventModal}
            className="p-4 rounded-xl bg-slate-800 hover:bg-slate-700/80 border border-slate-700 transition-all text-left space-y-2 group"
          >
            <div className="w-10 h-10 rounded-lg bg-blue-500/20 text-blue-400 flex items-center justify-center group-hover:scale-110 transition-transform">
              <Wrench className="w-5 h-5" />
            </div>
            <div>
              <p className="font-semibold text-sm text-slate-100">Log Service</p>
              <p className="text-xs text-slate-400">Oil, repair, inspection</p>
            </div>
          </button>

          <button
            id="quick-action-vehicles"
            onClick={() => setCurrentTab('vehicles')}
            className="p-4 rounded-xl bg-slate-800 hover:bg-slate-700/80 border border-slate-700 transition-all text-left space-y-2 group"
          >
            <div className="w-10 h-10 rounded-lg bg-purple-500/20 text-purple-400 flex items-center justify-center group-hover:scale-110 transition-transform">
              <Car className="w-5 h-5" />
            </div>
            <div>
              <p className="font-semibold text-sm text-slate-100">Vehicles</p>
              <p className="text-xs text-slate-400">Manage fleet ({vehicles.length})</p>
            </div>
          </button>
        </div>
      </div>

      {/* Recent Activity Timeline Preview */}
      <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
        <div className="flex items-center justify-between">
          <h2 className="text-lg font-bold text-white flex items-center space-x-2">
            <Clock className="w-5 h-5 text-blue-400" />
            <span>Recent Logged Events</span>
          </h2>
          <button
            onClick={() => setCurrentTab('events')}
            className="text-xs font-semibold text-blue-400 hover:text-blue-300 flex items-center space-x-1"
          >
            <span>View All Timeline</span>
            <ChevronRight className="w-4 h-4" />
          </button>
        </div>

        {vehicleEvents.length === 0 ? (
          <p className="text-sm text-slate-400 italic py-4 text-center">No logged events for this vehicle yet.</p>
        ) : (
          <div className="space-y-3">
            {vehicleEvents.slice(0, 5).map((evt) => (
              <div
                key={evt.id}
                className="p-4 rounded-xl bg-slate-800/60 border border-slate-700/60 flex items-center justify-between gap-4"
              >
                <div className="flex items-center space-x-3">
                  <div className={`p-2.5 rounded-lg shrink-0 ${
                    evt.eventType === 'FUEL' ? 'bg-amber-500/20 text-amber-400' :
                    evt.eventType === 'MAINTENANCE' ? 'bg-blue-500/20 text-blue-400' :
                    evt.eventType === 'TIRE_ROTATION' ? 'bg-purple-500/20 text-purple-400' :
                    'bg-slate-700 text-slate-300'
                  }`}>
                    {evt.eventType === 'FUEL' ? <Fuel className="w-5 h-5" /> : <Wrench className="w-5 h-5" />}
                  </div>
                  <div>
                    <div className="flex items-center space-x-2">
                      <span className="font-bold text-sm text-slate-100">
                        {evt.eventType === 'FUEL' ? 'Fuel Purchase' : evt.eventType.replace('_', ' ')}
                      </span>
                      {evt.verified && (
                        <span className="inline-flex items-center text-[10px] font-semibold text-emerald-400 bg-emerald-500/10 px-1.5 py-0.5 rounded border border-emerald-500/20">
                          <ShieldCheck className="w-3 h-3 mr-0.5" /> Verified
                        </span>
                      )}
                    </div>
                    <p className="text-xs text-slate-400">
                      {new Date(evt.eventDate).toLocaleDateString()} {evt.location ? `• ${evt.location}` : ''}
                    </p>
                  </div>
                </div>

                <div className="text-right">
                  {evt.totalCost && (
                    <div className="text-sm font-extrabold text-slate-100">
                      ${evt.totalCost.toFixed(2)}
                    </div>
                  )}
                  {evt.odometer && (
                    <div className="text-xs text-slate-400 font-mono">
                      {evt.odometer.toLocaleString()} mi
                    </div>
                  )}
                </div>
              </div>
            ))}
          </div>
        )}
      </div>
    </div>
  );
};
