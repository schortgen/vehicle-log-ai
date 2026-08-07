import React, { useState } from 'react';
import { Vehicle, VehicleEvent, EventType } from '../types';
import { Clock, Fuel, Wrench, ShieldCheck, Filter, Trash2, Edit, Plus, Calendar, DollarSign, MapPin, Gauge } from 'lucide-react';

interface EventsTimelineViewProps {
  vehicles: Vehicle[];
  events: VehicleEvent[];
  activeVehicleId: number | null;
  onAddEvent: (event: Omit<VehicleEvent, 'id' | 'createdDate'>) => void;
  onUpdateEvent: (event: VehicleEvent) => void;
  onDeleteEvent: (id: number) => void;
}

export const EventsTimelineView: React.FC<EventsTimelineViewProps> = ({
  vehicles,
  events,
  activeVehicleId,
  onAddEvent,
  onUpdateEvent,
  onDeleteEvent,
}) => {
  const [filterVehicleId, setFilterVehicleId] = useState<number | 'ALL'>(
    activeVehicleId || 'ALL'
  );
  const [filterType, setFilterType] = useState<EventType | 'ALL'>('ALL');

  const [isAddModalOpen, setIsAddModalOpen] = useState(false);
  const [editingEvent, setEditingEvent] = useState<VehicleEvent | null>(null);

  // Form states for Add / Edit modal
  const [modalVehicleId, setModalVehicleId] = useState<number>(
    activeVehicleId || (vehicles[0]?.id || 1)
  );
  const [modalType, setModalType] = useState<EventType>('MAINTENANCE');
  const [modalDate, setModalDate] = useState(new Date().toISOString().split('T')[0]);
  const [modalCost, setModalCost] = useState<number | ''>('');
  const [modalOdometer, setModalOdometer] = useState<number | ''>('');
  const [modalLocation, setModalLocation] = useState('');
  const [modalNotes, setModalNotes] = useState('');

  const filteredEvents = events.filter((e) => {
    if (filterVehicleId !== 'ALL' && e.vehicleId !== filterVehicleId) return false;
    if (filterType !== 'ALL' && e.eventType !== filterType) return false;
    return true;
  }).sort((a, b) => b.eventDate - a.eventDate);

  const handleOpenAddModal = () => {
    setEditingEvent(null);
    setModalVehicleId(activeVehicleId || (vehicles[0]?.id || 1));
    setModalType('MAINTENANCE');
    setModalDate(new Date().toISOString().split('T')[0]);
    setModalCost('');
    setModalOdometer('');
    setModalLocation('');
    setModalNotes('');
    setIsAddModalOpen(true);
  };

  const handleOpenEditModal = (evt: VehicleEvent) => {
    setEditingEvent(evt);
    setModalVehicleId(evt.vehicleId);
    setModalType(evt.eventType);
    setModalDate(new Date(evt.eventDate).toISOString().split('T')[0]);
    setModalCost(evt.totalCost || '');
    setModalOdometer(evt.odometer || '');
    setModalLocation(evt.location || '');
    setModalNotes(evt.notes || '');
    setIsAddModalOpen(true);
  };

  const handleSubmitModal = (e: React.FormEvent) => {
    e.preventDefault();
    if (!modalVehicleId) return;

    if (editingEvent) {
      onUpdateEvent({
        ...editingEvent,
        vehicleId: modalVehicleId,
        eventType: modalType,
        eventDate: new Date(modalDate).getTime(),
        totalCost: modalCost ? Number(modalCost) : undefined,
        odometer: modalOdometer ? Number(modalOdometer) : undefined,
        location: modalLocation || undefined,
        notes: modalNotes || undefined,
      });
    } else {
      onAddEvent({
        vehicleId: modalVehicleId,
        eventType: modalType,
        eventDate: new Date(modalDate).getTime(),
        verified: true,
        totalCost: modalCost ? Number(modalCost) : undefined,
        odometer: modalOdometer ? Number(modalOdometer) : undefined,
        location: modalLocation || undefined,
        notes: modalNotes || undefined,
      });
    }

    setIsAddModalOpen(false);
  };

  return (
    <div id="events-timeline-view" className="space-y-6">
      {/* Top Bar */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-extrabold text-white tracking-tight flex items-center space-x-3">
            <Clock className="w-7 h-7 text-blue-400" />
            <span>Vehicle Log Timeline</span>
          </h1>
          <p className="text-sm text-slate-400">
            Comprehensive audit history of fuel purchases, oil changes, service records, and repairs.
          </p>
        </div>

        <button
          id="add-event-button"
          onClick={handleOpenAddModal}
          className="inline-flex items-center space-x-2 px-4 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-semibold text-sm shadow-lg shadow-blue-600/30 transition-all shrink-0"
        >
          <Plus className="w-5 h-5" />
          <span>Add Service Log</span>
        </button>
      </div>

      {/* Filters Bar */}
      <div className="bg-slate-900 border border-slate-800 rounded-xl p-4 flex flex-wrap items-center gap-4 text-sm">
        <div className="flex items-center space-x-2 text-slate-400 font-medium">
          <Filter className="w-4 h-4 text-blue-400" />
          <span>Filters:</span>
        </div>

        {/* Vehicle Filter */}
        <select
          value={filterVehicleId}
          onChange={(e) => setFilterVehicleId(e.target.value === 'ALL' ? 'ALL' : Number(e.target.value))}
          className="px-3 py-1.5 bg-slate-800 border border-slate-700 rounded-lg text-slate-200 text-xs focus:outline-none"
        >
          <option value="ALL">All Vehicles ({vehicles.length})</option>
          {vehicles.map((v) => (
            <option key={v.id} value={v.id}>
              {v.nickname || `${v.year} ${v.make} ${v.model}`}
            </option>
          ))}
        </select>

        {/* Event Type Filter */}
        <select
          value={filterType}
          onChange={(e) => setFilterType(e.target.value as any)}
          className="px-3 py-1.5 bg-slate-800 border border-slate-700 rounded-lg text-slate-200 text-xs focus:outline-none"
        >
          <option value="ALL">All Event Types</option>
          <option value="FUEL">Fuel Purchases</option>
          <option value="MAINTENANCE">Maintenance & Repairs</option>
          <option value="TIRE_ROTATION">Tire Rotation</option>
          <option value="INSPECTION">Inspection</option>
          <option value="REGISTRATION">Registration</option>
          <option value="MILEAGE">Odometer Log</option>
        </select>
      </div>

      {/* Events List */}
      <div className="space-y-4">
        {filteredEvents.length === 0 ? (
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-8 text-center text-slate-400 space-y-2">
            <p className="text-base font-semibold text-slate-300">No events found matching current filter.</p>
            <p className="text-xs">Log a fuel purchase or add a service record to start tracking.</p>
          </div>
        ) : (
          filteredEvents.map((evt) => {
            const vehicle = vehicles.find((v) => v.id === evt.vehicleId);
            return (
              <div
                key={evt.id}
                className="bg-slate-900 border border-slate-800 hover:border-slate-700 rounded-2xl p-5 transition-all shadow-md flex flex-col md:flex-row md:items-center justify-between gap-4"
              >
                <div className="flex items-start space-x-4">
                  <div
                    className={`p-3 rounded-xl shrink-0 ${
                      evt.eventType === 'FUEL'
                        ? 'bg-amber-500/20 text-amber-400'
                        : evt.eventType === 'MAINTENANCE'
                        ? 'bg-blue-500/20 text-blue-400'
                        : evt.eventType === 'TIRE_ROTATION'
                        ? 'bg-purple-500/20 text-purple-400'
                        : 'bg-emerald-500/20 text-emerald-400'
                    }`}
                  >
                    {evt.eventType === 'FUEL' ? <Fuel className="w-6 h-6" /> : <Wrench className="w-6 h-6" />}
                  </div>

                  <div className="space-y-1">
                    <div className="flex flex-wrap items-center gap-2">
                      <span className="font-bold text-base text-white">
                        {evt.eventType === 'FUEL' ? 'Fuel Purchase' : evt.eventType.replace('_', ' ')}
                      </span>
                      {vehicle && (
                        <span className="text-xs font-medium px-2 py-0.5 rounded bg-slate-800 text-slate-300 border border-slate-700">
                          {vehicle.nickname || `${vehicle.make} ${vehicle.model}`}
                        </span>
                      )}
                      {evt.verified && (
                        <span className="inline-flex items-center text-[11px] font-semibold text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20">
                          <ShieldCheck className="w-3.5 h-3.5 mr-1" /> Verified Log
                        </span>
                      )}
                    </div>

                    <div className="flex flex-wrap items-center gap-x-4 gap-y-1 text-xs text-slate-400">
                      <span className="flex items-center space-x-1">
                        <Calendar className="w-3.5 h-3.5 text-blue-400" />
                        <span>{new Date(evt.eventDate).toLocaleDateString()}</span>
                      </span>
                      {evt.location && (
                        <span className="flex items-center space-x-1">
                          <MapPin className="w-3.5 h-3.5 text-amber-400" />
                          <span>{evt.location}</span>
                        </span>
                      )}
                      {evt.odometer && (
                        <span className="flex items-center space-x-1 font-mono">
                          <Gauge className="w-3.5 h-3.5 text-emerald-400" />
                          <span>{evt.odometer.toLocaleString()} mi</span>
                        </span>
                      )}
                    </div>

                    {evt.notes && (
                      <p className="text-xs text-slate-300 pt-1 italic bg-slate-800/40 px-2.5 py-1.5 rounded-lg border border-slate-800">
                        {evt.notes}
                      </p>
                    )}
                  </div>
                </div>

                <div className="flex items-center justify-between md:justify-end gap-4 shrink-0 border-t md:border-t-0 border-slate-800 pt-3 md:pt-0">
                  <div className="text-left md:text-right">
                    {evt.totalCost && (
                      <div className="text-lg font-extrabold text-emerald-400">
                        ${evt.totalCost.toFixed(2)}
                      </div>
                    )}
                    {evt.gallons && (
                      <div className="text-xs text-slate-400">
                        {evt.gallons} gal @ ${evt.pricePerGallon || (evt.totalCost / evt.gallons).toFixed(3)}/gal
                      </div>
                    )}
                  </div>

                  <div className="flex items-center space-x-2">
                    <button
                      onClick={() => handleOpenEditModal(evt)}
                      className="p-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition-all text-xs"
                      title="Edit event"
                    >
                      <Edit className="w-4 h-4" />
                    </button>
                    <button
                      onClick={() => onDeleteEvent(evt.id)}
                      className="p-2 rounded-lg bg-red-500/10 hover:bg-red-500/20 text-red-400 transition-all text-xs"
                      title="Delete event"
                    >
                      <Trash2 className="w-4 h-4" />
                    </button>
                  </div>
                </div>
              </div>
            );
          })
        )}
      </div>

      {/* Add / Edit Modal */}
      {isAddModalOpen && (
        <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-lg w-full p-6 space-y-5 shadow-2xl">
            <h2 className="text-xl font-bold text-white">
              {editingEvent ? 'Edit Logged Event' : 'Add Vehicle Event'}
            </h2>

            <form onSubmit={handleSubmitModal} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                  Vehicle *
                </label>
                <select
                  value={modalVehicleId}
                  onChange={(e) => setModalVehicleId(Number(e.target.value))}
                  className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none"
                >
                  {vehicles.map((v) => (
                    <option key={v.id} value={v.id}>
                      {v.nickname || `${v.year} ${v.make} ${v.model}`}
                    </option>
                  ))}
                </select>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                    Event Type *
                  </label>
                  <select
                    value={modalType}
                    onChange={(e) => setModalType(e.target.value as EventType)}
                    className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none"
                  >
                    <option value="MAINTENANCE">Maintenance & Repair</option>
                    <option value="FUEL">Fuel Purchase</option>
                    <option value="TIRE_ROTATION">Tire Rotation</option>
                    <option value="INSPECTION">Inspection</option>
                    <option value="REGISTRATION">Registration</option>
                  </select>
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                    Date *
                  </label>
                  <input
                    type="date"
                    required
                    value={modalDate}
                    onChange={(e) => setModalDate(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                    Total Cost ($)
                  </label>
                  <input
                    type="number"
                    step="0.01"
                    placeholder="89.99"
                    value={modalCost}
                    onChange={(e) => setModalCost(e.target.value ? Number(e.target.value) : '')}
                    className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none"
                  />
                </div>

                <div>
                  <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                    Odometer Reading (mi)
                  </label>
                  <input
                    type="number"
                    placeholder="34500"
                    value={modalOdometer}
                    onChange={(e) => setModalOdometer(e.target.value ? Number(e.target.value) : '')}
                    className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                  Location / Provider
                </label>
                <input
                  type="text"
                  placeholder="e.g. Toyota Dealership, PepBoys"
                  value={modalLocation}
                  onChange={(e) => setModalLocation(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                  Notes
                </label>
                <textarea
                  rows={2}
                  placeholder="Oil change type, filter replacement, invoice details..."
                  value={modalNotes}
                  onChange={(e) => setModalNotes(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none"
                />
              </div>

              <div className="flex items-center justify-end space-x-3 pt-4 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setIsAddModalOpen(false)}
                  className="px-4 py-2 text-sm font-semibold text-slate-400 hover:text-slate-200"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-500 rounded-xl shadow-lg shadow-blue-600/30"
                >
                  {editingEvent ? 'Save Event' : 'Add Event'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
