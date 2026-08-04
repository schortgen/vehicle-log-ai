import React, { useState } from 'react';
import { Vehicle, ReviewItem, VehicleEvent } from '../types';
import { ShieldCheck, CheckCircle2, XCircle, Fuel, Gauge, DollarSign, MapPin, Calendar, AlertCircle } from 'lucide-react';

interface ReviewQueueViewProps {
  reviewItems: ReviewItem[];
  vehicles: Vehicle[];
  onApproveReviewItem: (
    item: ReviewItem,
    verifiedEvent: Omit<VehicleEvent, 'id' | 'createdDate'>
  ) => void;
  onRejectReviewItem: (id: number) => void;
}

export const ReviewQueueView: React.FC<ReviewQueueViewProps> = ({
  reviewItems,
  vehicles,
  onApproveReviewItem,
  onRejectReviewItem,
}) => {
  const pendingItems = reviewItems.filter((item) => item.status === 'PENDING');

  const [selectedItemId, setSelectedItemId] = useState<number | null>(
    pendingItems[0]?.id || null
  );

  const selectedItem = pendingItems.find((i) => i.id === selectedItemId) || pendingItems[0];

  // Editable fields for active review candidate
  const [vehicleId, setVehicleId] = useState<number>(
    selectedItem?.vehicleId || (vehicles[0]?.id || 1)
  );
  const [stationName, setStationName] = useState(selectedItem?.parsedData?.stationName || 'Chevron');
  const [dateStr, setDateStr] = useState(
    selectedItem?.parsedData?.date || new Date().toISOString().split('T')[0]
  );
  const [gallons, setGallons] = useState<number | ''>(selectedItem?.parsedData?.gallons || 10.82);
  const [pricePerGallon, setPricePerGallon] = useState<number | ''>(selectedItem?.parsedData?.pricePerGallon || 3.999);
  const [totalCost, setTotalCost] = useState<number | ''>(selectedItem?.parsedData?.totalCost || 43.27);
  const [odometer, setOdometer] = useState<number | ''>(selectedItem?.parsedData?.odometer || 34850);
  const [fuelType, setFuelType] = useState(selectedItem?.parsedData?.fuelType || 'Unleaded Regular 87');

  const handleSelectCandidate = (item: ReviewItem) => {
    setSelectedItemId(item.id);
    setVehicleId(item.vehicleId || (vehicles[0]?.id || 1));
    setStationName(item.parsedData?.stationName || 'Chevron');
    setDateStr(item.parsedData?.date || new Date().toISOString().split('T')[0]);
    setGallons(item.parsedData?.gallons || '');
    setPricePerGallon(item.parsedData?.pricePerGallon || '');
    setTotalCost(item.parsedData?.totalCost || '');
    setOdometer(item.parsedData?.odometer || '');
    setFuelType(item.parsedData?.fuelType || 'Regular');
  };

  const handleApprove = () => {
    if (!selectedItem || !vehicleId || !gallons || !totalCost) return;

    onApproveReviewItem(selectedItem, {
      vehicleId,
      eventType: 'FUEL',
      eventDate: new Date(dateStr).getTime(),
      verified: true,
      gallons: Number(gallons),
      pricePerGallon: pricePerGallon ? Number(pricePerGallon) : undefined,
      totalCost: Number(totalCost),
      odometer: odometer ? Number(odometer) : undefined,
      location: stationName,
      notes: `AI Approved Log - ${fuelType}`,
      photoPath: selectedItem.photoPath,
      confidence: selectedItem.confidence,
    });

    // Reset selection to next pending item if available
    const remaining = pendingItems.filter((i) => i.id !== selectedItem.id);
    if (remaining.length > 0) {
      handleSelectCandidate(remaining[0]);
    } else {
      setSelectedItemId(null);
    }
  };

  return (
    <div id="review-queue-view" className="space-y-6">
      {/* Header */}
      <div className="bg-gradient-to-r from-amber-500/20 via-slate-900 to-slate-900 border border-amber-500/30 rounded-2xl p-6 text-slate-100">
        <div className="flex items-center space-x-3">
          <div className="w-12 h-12 rounded-xl bg-amber-500/20 text-amber-400 flex items-center justify-center shrink-0">
            <ShieldCheck className="w-6 h-6" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <h1 className="text-2xl font-extrabold tracking-tight">AI Review Queue</h1>
              <span className="text-xs font-bold px-2 py-0.5 rounded bg-amber-500/20 text-amber-300 border border-amber-500/30">
                {pendingItems.length} PENDING
              </span>
            </div>
            <p className="text-sm text-slate-400">
              Verify AI-parsed receipt fields before confirming and saving to official vehicle history.
            </p>
          </div>
        </div>
      </div>

      {pendingItems.length === 0 ? (
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-12 text-center text-slate-400 space-y-3">
          <CheckCircle2 className="w-12 h-12 text-emerald-400 mx-auto" />
          <h2 className="text-xl font-bold text-slate-200">Review Queue is Clear!</h2>
          <p className="text-sm max-w-md mx-auto">
            All AI scanned receipts have been verified and added to vehicle history.
          </p>
        </div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-3 gap-6">
          {/* Left Column: List of candidates */}
          <div className="space-y-3">
            <span className="text-xs font-semibold uppercase tracking-wider text-slate-400 block px-1">
              Pending Candidates ({pendingItems.length})
            </span>
            <div className="space-y-2">
              {pendingItems.map((item) => {
                const isSelected = item.id === selectedItem?.id;
                return (
                  <div
                    key={item.id}
                    onClick={() => handleSelectCandidate(item)}
                    className={`p-4 rounded-xl border cursor-pointer transition-all space-y-2 ${
                      isSelected
                        ? 'bg-slate-800 border-amber-500/80 ring-2 ring-amber-500/20 shadow-lg'
                        : 'bg-slate-900 border-slate-800 hover:border-slate-700'
                    }`}
                  >
                    <div className="flex items-center justify-between">
                      <span className="font-bold text-sm text-slate-100">
                        {item.parsedData?.stationName || 'Gas Station'}
                      </span>
                      <span className="text-xs font-bold text-emerald-400">
                        ${item.parsedData?.totalCost || 0}
                      </span>
                    </div>

                    <p className="text-xs text-slate-400 flex items-center justify-between">
                      <span>{item.parsedData?.date || new Date(item.captureDate).toLocaleDateString()}</span>
                      <span className="text-amber-400/90 font-mono">
                        {Math.round((item.confidence || 0.9) * 100)}% Match
                      </span>
                    </p>
                  </div>
                );
              })}
            </div>
          </div>

          {/* Right 2 Columns: Inspection & Verification Form */}
          {selectedItem && (
            <div className="lg:col-span-2 bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-6 shadow-xl">
              <div className="flex items-center justify-between pb-4 border-b border-slate-800">
                <h2 className="text-lg font-bold text-white flex items-center space-x-2">
                  <Fuel className="w-5 h-5 text-amber-400" />
                  <span>Verify AI Extracted Receipt</span>
                </h2>
                <span className="text-xs font-bold bg-amber-500/20 text-amber-300 px-2.5 py-1 rounded-full border border-amber-500/30">
                  Candidate ID #{selectedItem.id}
                </span>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-6">
                {/* Grouped Event Photos preview */}
                <div className="space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider block">
                      Grouped Event Photos
                    </span>
                    <span className="text-[11px] text-emerald-400 bg-emerald-500/10 px-2 py-0.5 rounded border border-emerald-500/20">
                      Auto-Grouped by Date
                    </span>
                  </div>

                  {/* Find items on same date (same YYYY-MM-DD or date string) */}
                  {(() => {
                    const itemDate = selectedItem.parsedData?.date || new Date(selectedItem.captureDate).toISOString().split('T')[0];
                    const sameDateItems = reviewItems.filter((other) => {
                      const otherDate = other.parsedData?.date || new Date(other.captureDate).toISOString().split('T')[0];
                      const ocr = (other.ocrText || '').toLowerCase();
                      const reason = (other.reason || '').toLowerCase();
                      // Exclude non-vehicle photos (flowers, pets, food)
                      const isNonVehicle = ['flower', 'plant', 'garden', 'pet', 'dog', 'cat', 'food', 'selfie'].some(
                        (term) => ocr.includes(term) || reason.includes(term)
                      );
                      return otherDate === itemDate && !isNonVehicle;
                    });

                    return (
                      <div className="space-y-2">
                        <div className="grid grid-cols-2 gap-2">
                          {sameDateItems.map((photoItem) => {
                            const ocrLower = (photoItem.ocrText || photoItem.reason || '').toLowerCase();
                            let badge = 'Receipt';
                            if (ocrLower.includes('odometer') || ocrLower.includes('mile')) badge = 'Odometer';
                            else if (ocrLower.includes('pump') || ocrLower.includes('station')) badge = 'Gas Station';

                            return (
                              <div
                                key={photoItem.id}
                                className="bg-slate-950 border border-slate-800 rounded-xl p-2 text-center space-y-1 relative group"
                              >
                                <img
                                  src={photoItem.photoPath || 'https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?w=600&auto=format&fit=crop&q=80'}
                                  alt={badge}
                                  className="h-28 mx-auto rounded object-contain"
                                />
                                <span className="inline-block text-[10px] font-bold px-2 py-0.5 bg-slate-800 text-amber-300 rounded border border-slate-700">
                                  {badge}
                                </span>
                              </div>
                            );
                          })}
                        </div>
                        <p className="text-[11px] text-slate-400 italic">
                          Photos taken on {itemDate} (odometer, receipt, pump) are grouped together. Unrelated photos (e.g. flowers) are filtered out.
                        </p>
                      </div>
                    );
                  })()}
                </div>

                {/* Form fields */}
                <div className="space-y-4">
                  <div>
                    <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                      Assigned Vehicle *
                    </label>
                    <select
                      value={vehicleId}
                      onChange={(e) => setVehicleId(Number(e.target.value))}
                      className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none"
                    >
                      {vehicles.map((v) => (
                        <option key={v.id} value={v.id}>
                          {v.nickname || `${v.year} ${v.make} ${v.model}`}
                        </option>
                      ))}
                    </select>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                      Station Name
                    </label>
                    <input
                      type="text"
                      value={stationName}
                      onChange={(e) => setStationName(e.target.value)}
                      className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none"
                    />
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                        Date
                      </label>
                      <input
                        type="date"
                        value={dateStr}
                        onChange={(e) => setDateStr(e.target.value)}
                        className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none"
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                        Gallons
                      </label>
                      <input
                        type="number"
                        step="0.001"
                        value={gallons}
                        onChange={(e) => setGallons(e.target.value ? Number(e.target.value) : '')}
                        className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none"
                      />
                    </div>
                  </div>

                  <div className="grid grid-cols-2 gap-3">
                    <div>
                      <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                        Price / Gal ($)
                      </label>
                      <input
                        type="number"
                        step="0.001"
                        value={pricePerGallon}
                        onChange={(e) => setPricePerGallon(e.target.value ? Number(e.target.value) : '')}
                        className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none"
                      />
                    </div>

                    <div>
                      <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                        Total Cost ($)
                      </label>
                      <input
                        type="number"
                        step="0.01"
                        value={totalCost}
                        onChange={(e) => setTotalCost(e.target.value ? Number(e.target.value) : '')}
                        className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-emerald-400 font-bold text-sm focus:outline-none"
                      />
                    </div>
                  </div>

                  <div>
                    <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                      Odometer Reading (mi)
                    </label>
                    <input
                      type="number"
                      value={odometer}
                      onChange={(e) => setOdometer(e.target.value ? Number(e.target.value) : '')}
                      className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm font-mono focus:outline-none"
                    />
                  </div>
                </div>
              </div>

              {/* Action Buttons */}
              <div className="flex items-center justify-between pt-4 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => onRejectReviewItem(selectedItem.id)}
                  className="px-4 py-2.5 rounded-xl bg-red-500/10 hover:bg-red-500/20 text-red-400 font-semibold text-xs transition-all flex items-center space-x-1"
                >
                  <XCircle className="w-4 h-4" />
                  <span>Discard Candidate</span>
                </button>

                <button
                  type="button"
                  onClick={handleApprove}
                  className="px-6 py-2.5 rounded-xl bg-emerald-600 hover:bg-emerald-500 text-white font-bold text-sm shadow-lg shadow-emerald-600/30 transition-all flex items-center space-x-2"
                >
                  <CheckCircle2 className="w-5 h-5" />
                  <span>Approve & Save Log</span>
                </button>
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
