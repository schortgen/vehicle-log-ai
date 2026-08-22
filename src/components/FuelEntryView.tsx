import React, { useState } from 'react';
import { Vehicle, VehicleEvent, EventType } from '../types';
import { Fuel, Wrench, RotateCcw, Search, FileText, Gauge, Calendar, DollarSign, MapPin, Check, Camera, ShieldCheck, Plus, X } from 'lucide-react';

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

  // Event Type
  const [eventType, setEventType] = useState<EventType>('FUEL');

  const [date, setDate] = useState<string>(
    new Date().toISOString().split('T')[0]
  );
  const [odometer, setOdometer] = useState<number | ''>(
    selectedVehicle?.currentMileage ? selectedVehicle.currentMileage + 320 : ''
  );
  const [tripDistance, setTripDistance] = useState<number | ''>('');
  
  // Fuel fields
  const [gallons, setGallons] = useState<number | ''>(12.5);
  const [pricePerGallon, setPricePerGallon] = useState<number | ''>(3.899);
  const [fuelType, setFuelType] = useState('Regular 87');

  // Service / Maintenance / Other fields
  const [serviceTitle, setServiceTitle] = useState('Synthetic Oil & Filter Change');
  const [manualCost, setManualCost] = useState<number | ''>(45.0);
  const [location, setLocation] = useState('Chevron');
  const [notes, setNotes] = useState('');
  const [photoUrl, setPhotoUrl] = useState<string>('');

  // Auto-calculated fuel total cost vs manual cost
  const fuelTotalCost = gallons && pricePerGallon ? Number((Number(gallons) * Number(pricePerGallon)).toFixed(2)) : 0;
  const effectiveCost = eventType === 'FUEL' ? fuelTotalCost : (manualCost !== '' ? Number(manualCost) : undefined);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!selectedVehicleId) return;
    if (eventType === 'FUEL' && (!gallons || !fuelTotalCost)) return;

    const odoValue = odometer ? Number(odometer) : undefined;
    const tripValue = tripDistance ? Number(tripDistance) : undefined;

    let compiledNotes = notes;
    if (eventType === 'FUEL') {
      compiledNotes = notes ? `${fuelType} - ${notes}` : fuelType;
    } else if (eventType === 'MAINTENANCE') {
      compiledNotes = notes ? `${serviceTitle} - ${notes}` : serviceTitle;
    } else if (eventType === 'INSPECTION' || eventType === 'REGISTRATION') {
      compiledNotes = notes ? `${serviceTitle} - ${notes}` : serviceTitle;
    }

    onAddEvent({
      vehicleId: selectedVehicleId,
      eventType,
      eventDate: new Date(date).getTime(),
      verified: true,
      odometer: odoValue,
      tripDistance: tripValue,
      gallons: eventType === 'FUEL' && gallons ? Number(gallons) : undefined,
      pricePerGallon: eventType === 'FUEL' && pricePerGallon ? Number(pricePerGallon) : undefined,
      totalCost: effectiveCost,
      location: location || undefined,
      notes: compiledNotes || undefined,
      photoPath: photoUrl || undefined,
    });

    if (odoValue && selectedVehicle && odoValue > (selectedVehicle.currentMileage || 0)) {
      onUpdateVehicleMileage(selectedVehicleId, odoValue);
    }

    onSuccessNavigate();
  };

  return (
    <div id="fuel-entry-view" className="max-w-3xl mx-auto space-y-6">
      {/* Header Banner */}
      <div className="bg-gradient-to-r from-blue-500/10 via-slate-900 to-slate-900 border border-blue-500/20 rounded-2xl p-6">
        <div className="flex items-center space-x-3">
          <div className="w-12 h-12 rounded-xl bg-blue-500/20 text-blue-400 flex items-center justify-center shrink-0">
            {eventType === 'FUEL' && <Fuel className="w-6 h-6 text-amber-400" />}
            {eventType === 'MAINTENANCE' && <Wrench className="w-6 h-6 text-blue-400" />}
            {eventType === 'TIRE_ROTATION' && <RotateCcw className="w-6 h-6 text-purple-400" />}
            {eventType === 'INSPECTION' && <Search className="w-6 h-6 text-emerald-400" />}
            {eventType === 'REGISTRATION' && <FileText className="w-6 h-6 text-indigo-400" />}
            {eventType === 'MILEAGE' && <Gauge className="w-6 h-6 text-cyan-400" />}
          </div>
          <div>
            <h1 className="text-2xl font-extrabold text-white tracking-tight">
              {eventType === 'FUEL' && 'Log Fuel Purchase'}
              {eventType === 'MAINTENANCE' && 'Log Maintenance Service'}
              {eventType === 'TIRE_ROTATION' && 'Log Tire Rotation'}
              {eventType === 'INSPECTION' && 'Log Vehicle Inspection'}
              {eventType === 'REGISTRATION' && 'Log Registration & Tags'}
              {eventType === 'MILEAGE' && 'Log Odometer Check'}
            </h1>
            <p className="text-sm text-slate-400">
              Record services, fuel refills, or maintenance history manually into your vehicle timeline.
            </p>
          </div>
        </div>
      </div>

      <form onSubmit={handleSubmit} className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-6 shadow-xl">
        {/* Service / Event Type Selector Chips */}
        <div className="space-y-2">
          <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400">
            Event / Service Type *
          </label>
          <div className="grid grid-cols-2 sm:grid-cols-3 gap-2.5">
            {[
              { type: 'FUEL' as EventType, label: '⛽ Fuel', desc: 'Gas / Diesel Refill' },
              { type: 'MAINTENANCE' as EventType, label: '🔧 Maintenance', desc: 'Oil, Brakes, Battery' },
              { type: 'TIRE_ROTATION' as EventType, label: '🔄 Tire Rotation', desc: 'Tires & Balance' },
              { type: 'INSPECTION' as EventType, label: '🔍 Inspection', desc: 'Safety & Emissions' },
              { type: 'REGISTRATION' as EventType, label: '📄 Registration', desc: 'DMV & Tag Fees' },
              { type: 'MILEAGE' as EventType, label: '📏 Mileage', desc: 'Odometer Log' },
            ].map((item) => (
              <button
                key={item.type}
                type="button"
                onClick={() => {
                  setEventType(item.type);
                  if (item.type === 'FUEL') setLocation('Chevron');
                  else if (item.type === 'MAINTENANCE') setLocation('Jiffy Lube');
                  else if (item.type === 'TIRE_ROTATION') { setLocation('Discount Tire'); setManualCost(0); }
                  else if (item.type === 'INSPECTION') { setLocation('State Inspection Station'); setManualCost(25); }
                  else if (item.type === 'REGISTRATION') { setLocation('DMV Office'); setManualCost(85); }
                }}
                className={`p-3 rounded-xl border text-left transition-all ${
                  eventType === item.type
                    ? 'bg-blue-600/20 border-blue-500 text-white font-bold ring-1 ring-blue-500/50'
                    : 'bg-slate-800/60 border-slate-700/60 text-slate-300 hover:bg-slate-800'
                }`}
              >
                <div className="text-sm font-semibold">{item.label}</div>
                <div className="text-xs text-slate-400 font-normal mt-0.5">{item.desc}</div>
              </button>
            ))}
          </div>
        </div>

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
              <span>Odometer Reading (mi){eventType === 'FUEL' || eventType === 'MILEAGE' ? ' *' : ' (optional)'}</span>
            </label>
            <input
              type="number"
              required={eventType === 'FUEL' || eventType === 'MILEAGE'}
              placeholder="e.g. 34840"
              value={odometer}
              onChange={(e) => setOdometer(e.target.value ? Number(e.target.value) : '')}
              className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500 font-mono"
            />
          </div>
        </div>

        {/* Maintenance / Inspection Specific: Service Description & Presets */}
        {eventType === 'MAINTENANCE' && (
          <div className="space-y-3">
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                Service Description / Parts Replaced *
              </label>
              <input
                type="text"
                required
                placeholder="e.g. Synthetic Oil & Filter Change, Front Brake Pads"
                value={serviceTitle}
                onChange={(e) => setServiceTitle(e.target.value)}
                className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500"
              />
            </div>
            {/* Quick Presets */}
            <div className="flex flex-wrap gap-2">
              {['Oil & Filter Change', 'Front Brake Pads', 'Battery Replacement', 'Cabin & Engine Filters', 'Transmission Fluid', 'Spark Plugs', 'Coolant Flush'].map((preset) => (
                <button
                  key={preset}
                  type="button"
                  onClick={() => setServiceTitle(preset)}
                  className="px-2.5 py-1 bg-slate-800 hover:bg-slate-700 border border-slate-700 rounded-lg text-xs text-slate-300 font-medium"
                >
                  {preset}
                </button>
              ))}
            </div>
          </div>
        )}

        {eventType === 'INSPECTION' && (
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Inspection Details
            </label>
            <input
              type="text"
              placeholder="e.g. Annual State Safety & Emissions Inspection"
              value={serviceTitle}
              onChange={(e) => setServiceTitle(e.target.value)}
              className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500"
            />
          </div>
        )}

        {eventType === 'REGISTRATION' && (
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
              Registration / Renewal Details
            </label>
            <input
              type="text"
              placeholder="e.g. Annual Tag & License Plate Renewal"
              value={serviceTitle}
              onChange={(e) => setServiceTitle(e.target.value)}
              className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500"
            />
          </div>
        )}

        {/* FUEL-SPECIFIC: Gallons, Price/Gallon, Computed Total Cost */}
        {eventType === 'FUEL' ? (
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
                <span>Total Cost ($) *</span>
              </label>
              <div className="w-full px-3.5 py-2.5 bg-emerald-500/10 border border-emerald-500/30 rounded-xl text-emerald-400 text-lg font-extrabold flex items-center">
                ${fuelTotalCost.toFixed(2)}
              </div>
            </div>
          </div>
        ) : (
          /* Non-fuel Total Cost field */
          eventType !== 'MILEAGE' && (
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-emerald-400 mb-1 flex items-center space-x-1">
                  <DollarSign className="w-4 h-4" />
                  <span>Total Cost / Fee ($)</span>
                </label>
                <input
                  type="number"
                  step="0.01"
                  placeholder="0.00"
                  value={manualCost}
                  onChange={(e) => setManualCost(e.target.value ? Number(e.target.value) : '')}
                  className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500 font-bold"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                  Trip Distance (mi) (optional)
                </label>
                <input
                  type="number"
                  step="0.1"
                  placeholder="e.g. 340.5"
                  value={tripDistance}
                  onChange={(e) => setTripDistance(e.target.value ? Number(e.target.value) : '')}
                  className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500"
                />
              </div>
            </div>
          )
        )}

        {/* Location & Fuel Type (if FUEL) */}
        <div className="grid grid-cols-1 sm:grid-cols-2 gap-4">
          <div>
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1 flex items-center space-x-1">
              <MapPin className="w-4 h-4 text-amber-400" />
              <span>
                {eventType === 'FUEL' && 'Gas Station Name / Location'}
                {eventType === 'MAINTENANCE' && 'Service Shop / Garage Name'}
                {eventType === 'TIRE_ROTATION' && 'Tire Shop / Center'}
                {eventType === 'INSPECTION' && 'Inspection Station'}
                {eventType === 'REGISTRATION' && 'DMV / Agency Office'}
                {eventType === 'MILEAGE' && 'Location / Route'}
              </span>
            </label>
            <input
              type="text"
              placeholder={
                eventType === 'FUEL' ? 'e.g. Chevron, Shell #402' :
                eventType === 'MAINTENANCE' ? 'e.g. Jiffy Lube, Dealership, DIY' :
                eventType === 'TIRE_ROTATION' ? 'e.g. Discount Tire, Costco' : 'e.g. Service Center'
              }
              value={location}
              onChange={(e) => setLocation(e.target.value)}
              className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500"
            />
          </div>

          {eventType === 'FUEL' ? (
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                Fuel Grade
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
          ) : (
            <div>
              <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
                Receipt / Invoice Image URL (optional)
              </label>
              <input
                type="text"
                placeholder="https://... or paste photo link"
                value={photoUrl}
                onChange={(e) => setPhotoUrl(e.target.value)}
                className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500"
              />
            </div>
          )}
        </div>

        {/* Notes */}
        <div>
          <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400 mb-1">
            Notes / Details
          </label>
          <textarea
            rows={2}
            placeholder={
              eventType === 'FUEL' ? 'Add notes, discount codes, or pump number...' :
              eventType === 'MAINTENANCE' ? 'Parts used, warranty details, technician notes...' :
              'Additional details or observations...'
            }
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
            className="px-6 py-3 bg-blue-600 hover:bg-blue-500 text-white font-bold rounded-xl shadow-lg shadow-blue-600/20 transition-all flex items-center space-x-2"
          >
            <Check className="w-5 h-5" />
            <span>
              {eventType === 'FUEL' && 'Save Fuel Entry'}
              {eventType === 'MAINTENANCE' && 'Save Maintenance Record'}
              {eventType === 'TIRE_ROTATION' && 'Save Tire Rotation'}
              {eventType === 'INSPECTION' && 'Save Inspection'}
              {eventType === 'REGISTRATION' && 'Save Registration'}
              {eventType === 'MILEAGE' && 'Save Mileage Log'}
            </span>
          </button>
        </div>
      </form>
    </div>
  );
};
