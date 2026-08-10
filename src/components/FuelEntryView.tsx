import React, { useState } from 'react';
import { Vehicle, VehicleEvent } from '../types';
import { Fuel, Calendar, Gauge, DollarSign, MapPin, Check, Camera, ShieldCheck } from 'lucide-react';

interface FuelEntryViewProps {
  vehicles: Vehicle[];
  activeVehicleId: number | null;
  onAddEvent: (event: Omit<VehicleEvent, 'id' | 'createdDate'>) => void;
  onUpdateVehicleMileage: (vehicleId: number, mileage: number) => void;
  onSuccessNavigate: () => void;
}

export const FuelEntryView: React.FC<FuelEntryViewProps> = ({
  vehicles,
  activeVehicleId,
  onAddEvent,
  onUpdateVehicleMileage,
  onSuccessNavigate,
}) => {
  const [selectedVehicleId, setSelectedVehicleId] = useState<number>(
    activeVehicleId || (vehicles[0]?.id || 1)
  );

  const selectedVehicle = vehicles.find((v) => v.id === selectedVehicleId) || vehicles[0];

  const [date, setDate] = useState<string>(
    new Date().toISOString().split('T')[0]
  );
  const [odometer, setOdometer] = useState<number | ''>(
    selectedVehicle?.currentMileage ? selectedVehicle.currentMileage + 320 : ''
  );
  const [gallons, setGallons] = useState<number | ''>(12.5);
  const [pricePerGallon, setPricePerGallon] = useState<number | ''>(3.899);
  const [station, setStation] = useState('Chevron');
  const [fuelType, setFuelType] = useState('Regular 87');
  const [notes, setNotes] = useState('');
  const [photoUrl, setPhotoUrl] = useState<string>('');

  // Auto-calculated total cost
  const totalCost = gallons && pricePerGallon ? Number((Number(gallons) * Number(pricePerGallon)).toFixed(2)) : 0;

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedVehicleId || !gallons || !totalCost) return;

    const odoValue = odometer ? Number(odometer) : undefined;

    onAddEvent({
      vehicleId: selectedVehicleId,
      eventType: 'FUEL',
      eventDate: new Date(date).getTime(),
      verified: true,
      odometer: odoValue,
      gallons: Number(gallons),
      pricePerGallon: pricePerGallon ? Number(pricePerGallon) : undefined,
      totalCost,
      location: station,
      notes: notes ? `${fuelType} - ${notes}` : fuelType,
      photoPath: photoUrl || undefined,
    });

    if (odoValue && selectedVehicle && odoValue > (selectedVehicle.currentMileage || 0)) {
      onUpdateVehicleMileage(selectedVehicleId, odoValue);
    }

    onSuccessNavigate();
  };

  return (
    <div id="fuel-entry-view" className="max-w-3xl mx-auto space-y-6">
      <div className="bg-gradient-to-r from-amber-500/10 via-slate-900 to-slate-900 border border-amber-500/20 rounded-2xl p-6">
        <div className="flex items-center space-x-3">
          <div className="w-12 h-12 rounded-xl bg-amber-500/20 text-amber-400 flex items-center justify-center shrink-0">
            <Fuel className="w-6 h-6" />
          </div>
          <div>
            <h1 className="text-2xl font-extrabold text-white tracking-tight">
              Log Fuel Purchase
            </h1>
            <p className="text-sm text-slate-400">
              Record fuel refills manually or auto-calculate cost and mileage metrics.
            </p>
          </div>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-6 shadow-xl">
        {/* Vehicle Selection */}
        <div className="space-y-2">
          <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400">
            Select Vehicle *
          </label>
          <select
            value={selectedVehicleId}
            onChange={(e) => {
              const id = Number(e.target.value);
              setSelectedVehicleId(id);
              const v = vehicles.find((v) => v.id === id);
              if (v?.currentMileage) {
                setOdometer(v.currentMileage + 320);
              }
            }}
            className="w-full px-4 py-3 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500 font-medium"
          >
            {vehicles.map((v) => (
              <option key={v.id} value={v.id}>
                {v.nickname || `${v.year} ${v.make} ${v.model}`} (Odo: {v.currentMileage?.toLocaleString()} mi)
              </option>
            ))}
          </select>
        </div>

        {/* Date & Odometer */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1 flex items-center space-x-1">
              <Calendar className="w-4 h-4 text-blue-400" />
              <span>Date *</span>
            </label>
            <input
              type="date"
              required
              value={date}
              onChange={(e) => setDate(e.target.value)}
              className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1 flex items-center space-x-1">
              <Gauge className="w-4 h-4 text-emerald-400" />
              <span>Current Odometer Reading (mi)</span>
            </label>
            <input
              type="number"
              placeholder="e.g. 34840"
              value={odometer}
              onChange={(e) => setOdometer(e.target.value ? Number(e.target.value) : '')}
              className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500 font-mono"
            />
          </div>
        </div>

        {/* Gallons, Price/Gallon, Computed Total Cost */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Gallons / Volume *
            </label>
            <input
              type="number"
              step="0.001"
              required
              placeholder="12.5"
              value={gallons}
              onChange={(e) => setGallons(e.target.value ? Number(e.target.value) : '')}
              className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500 font-bold"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Price / Gallon ($)
            </label>
            <input
              type="number"
              step="0.001"
              placeholder="3.899"
              value={pricePerGallon}
              onChange={(e) => setPricePerGallon(e.target.value ? Number(e.target.value) : '')}
              className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-emerald-400 mb-1 flex items-center space-x-1">
              <DollarSign className="w-4 h-4" />
              <span>Total Cost ($)</span>
            </label>
            <div className="w-full px-3.5 py-2.5 bg-emerald-500/10 border border-emerald-500/30 rounded-xl text-emerald-400 text-lg font-extrabold flex items-center">
              ${totalCost.toFixed(2)}
            </div>
          </div>
        </div>

        {/* Station & Fuel Type */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1 flex items-center space-x-1">
              <MapPin className="w-4 h-4 text-amber-400" />
              <span>Gas Station Name / Location</span>
            </label>
            <input
              type="text"
              placeholder="e.g. Chevron, Shell #402"
              value={station}
              onChange={(e) => setStation(e.target.value)}
              className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Fuel Type
            </label>
            <select
              value={fuelType}
              onChange={(e) => setFuelType(e.target.value)}
              className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500"
            >
              <option value="Regular 87">Regular 87 Octane</option>
              <option value="Midgrade 89">Midgrade 89 Octane</option>
              <option value="Premium 93">Premium 93 / 91 Octane</option>
              <option value="Diesel">Diesel</option>
              <option value="E85 Flex Fuel">E85 Flex Fuel</option>
            </select>
          </div>
        </div>

        {/* Notes */}
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
            Notes / Details
          </label>
          <textarea
            rows={2}
            placeholder="Add trip notes, discount codes, or pump number..."
            value={notes}
            onChange={(e) => setNotes(e.target.value)}
            className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500"
          />
        </div>

        {/* Submit */}
        <div className="pt-4 border-t border-slate-800 flex items-center justify-between">
          <div className="flex items-center space-x-2 text-xs text-slate-400">
            <ShieldCheck className="w-4 h-4 text-emerald-400" />
            <span>Event will be saved to vehicle timeline</span>
          </div>
          <button
            type="submit"
            className="px-6 py-3 bg-amber-500 hover:bg-amber-400 text-slate-950 font-bold rounded-xl shadow-lg shadow-amber-500/20 transition-all flex items-center space-x-2"
          >
            <Check className="w-5 h-5" />
            <span>Save Fuel Purchase</span>
          </button>
        </div>
      </form>
    </div>
  );
};
