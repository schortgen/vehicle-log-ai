import React, { useState } from 'react';
import { Vehicle } from '../types';
import { Car, Plus, Edit2, Trash2, CheckCircle2, Shield, Gauge, Hash } from 'lucide-react';

interface VehiclesViewProps {
  vehicles: Vehicle[];
  activeVehicleId: number | null;
  setActiveVehicleId: (id: number) => void;
  onAddVehicle: (vehicle: Omit<Vehicle, 'id' | 'createdDate'>) => void;
  onUpdateVehicle: (vehicle: Vehicle) => void;
  onDeleteVehicle: (id: number) => void;
}

export const VehiclesView: React.FC<VehiclesViewProps> = ({
  vehicles,
  activeVehicleId,
  setActiveVehicleId,
  onAddVehicle,
  onUpdateVehicle,
  onDeleteVehicle,
}) => {
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [editingVehicle, setEditingVehicle] = useState<Vehicle | null>(null);

  const [nickname, setNickname] = useState('');
  const [year, setYear] = useState<number | ''>(2024);
  const [make, setMake] = useState('');
  const [model, setModel] = useState('');
  const [licensePlate, setLicensePlate] = useState('');
  const [vin, setVin] = useState('');
  const [currentMileage, setCurrentMileage] = useState<number | ''>(30000);

  const handleOpenAddModal = () => {
    setEditingVehicle(null);
    setNickname('');
    setYear(2024);
    setMake('');
    setModel('');
    setLicensePlate('');
    setVin('');
    setCurrentMileage('');
    setIsModalOpen(true);
  };

  const handleOpenEditModal = (v: Vehicle) => {
    setEditingVehicle(v);
    setNickname(v.nickname || '');
    setYear(v.year || '');
    setMake(v.make || '');
    setModel(v.model || '');
    setLicensePlate(v.licensePlate || '');
    setVin(v.vin || '');
    setCurrentMileage(v.currentMileage || '');
    setIsModalOpen(true);
  };

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (!make || !model) return;

    if (editingVehicle) {
      onUpdateVehicle({
        ...editingVehicle,
        nickname: nickname || `${year} ${make} ${model}`,
        year: year ? Number(year) : undefined,
        make,
        model,
        licensePlate,
        vin,
        currentMileage: currentMileage ? Number(currentMileage) : undefined,
      });
    } else {
      onAddVehicle({
        nickname: nickname || `${year} ${make} ${model}`,
        year: year ? Number(year) : undefined,
        make,
        model,
        licensePlate,
        vin,
        currentMileage: currentMileage ? Number(currentMileage) : undefined,
        isActive: true,
      });
    }

    setIsModalOpen(false);
  };

  return (
    <div id="vehicles-view" className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-2xl font-extrabold text-white tracking-tight flex items-center space-x-3">
            <Car className="w-7 h-7 text-blue-400" />
            <span>Vehicles Management</span>
          </h1>
          <p className="text-sm text-slate-400">
            Add and manage vehicles in your fleet. Select an active vehicle to record logs and AI receipts.
          </p>
        </div>
        <button
          id="add-vehicle-button"
          onClick={handleOpenAddModal}
          className="inline-flex items-center space-x-2 px-4 py-2.5 rounded-xl bg-blue-600 hover:bg-blue-500 text-white font-semibold text-sm shadow-lg shadow-blue-600/30 transition-all shrink-0"
        >
          <Plus className="w-5 h-5" />
          <span>Add Vehicle</span>
        </button>
      </div>

      {/* Vehicles Grid */}
      <div className="grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-6">
        {vehicles.map((v) => {
          const isActive = v.id === activeVehicleId;
          return (
            <div
              key={v.id}
              className={`rounded-2xl border p-6 flex flex-col justify-between transition-all relative ${
                isActive
                  ? 'bg-gradient-to-b from-slate-900 to-slate-800 border-blue-500/80 ring-2 ring-blue-500/30 shadow-xl'
                  : 'bg-slate-900 border-slate-800 hover:border-slate-700'
              }`}
            >
              <div className="space-y-4">
                <div className="flex items-start justify-between">
                  <div className="space-y-1">
                    <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                      {v.year} {v.make}
                    </span>
                    <h3 className="text-xl font-bold text-white">
                      {v.nickname || `${v.make} ${v.model}`}
                    </h3>
                  </div>
                  {isActive ? (
                    <span className="inline-flex items-center space-x-1 text-xs font-bold bg-emerald-500/20 text-emerald-400 border border-emerald-500/30 px-2.5 py-1 rounded-full">
                      <CheckCircle2 className="w-3.5 h-3.5" />
                      <span>ACTIVE</span>
                    </span>
                  ) : (
                    <button
                      onClick={() => setActiveVehicleId(v.id)}
                      className="text-xs font-semibold text-slate-400 hover:text-white px-2.5 py-1 rounded-lg bg-slate-800 border border-slate-700 hover:bg-slate-700 transition-all"
                    >
                      Set Active
                    </button>
                  )}
                </div>

                <div className="space-y-2 pt-2 text-sm text-slate-300">
                  <div className="flex items-center justify-between py-1 border-b border-slate-800">
                    <span className="text-slate-400 flex items-center space-x-1.5 text-xs">
                      <Gauge className="w-4 h-4 text-blue-400" />
                      <span>Current Mileage</span>
                    </span>
                    <span className="font-bold text-white">
                      {v.currentMileage ? `${v.currentMileage.toLocaleString()} mi` : 'Unrecorded'}
                    </span>
                  </div>

                  <div className="flex items-center justify-between py-1 border-b border-slate-800">
                    <span className="text-slate-400 flex items-center space-x-1.5 text-xs">
                      <Shield className="w-4 h-4 text-emerald-400" />
                      <span>License Plate</span>
                    </span>
                    <span className="font-mono text-slate-200">{v.licensePlate || 'N/A'}</span>
                  </div>

                  <div className="flex items-center justify-between py-1">
                    <span className="text-slate-400 flex items-center space-x-1.5 text-xs">
                      <Hash className="w-4 h-4 text-amber-400" />
                      <span>VIN</span>
                    </span>
                    <span className="font-mono text-xs text-slate-300 truncate max-w-[140px]" title={v.vin}>
                      {v.vin || 'N/A'}
                    </span>
                  </div>
                </div>
              </div>

              <div className="pt-6 border-t border-slate-800/80 mt-4 flex items-center justify-end space-x-2">
                <button
                  onClick={() => handleOpenEditModal(v)}
                  className="p-2 rounded-lg bg-slate-800 hover:bg-slate-700 text-slate-300 hover:text-white transition-all text-xs flex items-center space-x-1"
                >
                  <Edit2 className="w-4 h-4" />
                  <span>Edit</span>
                </button>
                {vehicles.length > 1 && (
                  <button
                    onClick={() => onDeleteVehicle(v.id)}
                    className="p-2 rounded-lg bg-red-500/10 hover:bg-red-500/20 text-red-400 transition-all text-xs flex items-center space-x-1"
                  >
                    <Trash2 className="w-4 h-4" />
                    <span>Delete</span>
                  </button>
                )}
              </div>
            </div>
          );
        })}
      </div>

      {/* Add/Edit Modal */}
      {isModalOpen && (
        <div className="fixed inset-0 z-50 bg-slate-950/80 backdrop-blur-sm flex items-center justify-center p-4">
          <div className="bg-slate-900 border border-slate-800 rounded-2xl max-w-lg w-full p-6 space-y-6 shadow-2xl">
            <h2 className="text-xl font-bold text-white">
              {editingVehicle ? 'Edit Vehicle' : 'Add New Vehicle'}
            </h2>

            <form onSubmit={handleSubmit} className="space-y-4">
              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                  Vehicle Nickname (Optional)
                </label>
                <input
                  type="text"
                  placeholder="e.g. Silver Bullet, Work Truck"
                  value={nickname}
                  onChange={(e) => setNickname(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500"
                />
              </div>

              <div className="grid grid-cols-3 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                    Year *
                  </label>
                  <input
                    type="number"
                    required
                    value={year}
                    onChange={(e) => setYear(e.target.value ? Number(e.target.value) : '')}
                    className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                    Make *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="Toyota"
                    value={make}
                    onChange={(e) => setMake(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                    Model *
                  </label>
                  <input
                    type="text"
                    required
                    placeholder="RAV4"
                    value={model}
                    onChange={(e) => setModel(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500"
                  />
                </div>
              </div>

              <div className="grid grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                    License Plate
                  </label>
                  <input
                    type="text"
                    placeholder="8ABC123"
                    value={licensePlate}
                    onChange={(e) => setLicensePlate(e.target.value)}
                    className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                    Current Odometer (mi)
                  </label>
                  <input
                    type="number"
                    placeholder="34500"
                    value={currentMileage}
                    onChange={(e) => setCurrentMileage(e.target.value ? Number(e.target.value) : '')}
                    className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-blue-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider mb-1">
                  VIN (Vehicle Identification Number)
                </label>
                <input
                  type="text"
                  placeholder="17 character VIN"
                  value={vin}
                  onChange={(e) => setVin(e.target.value)}
                  className="w-full px-3.5 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm font-mono focus:outline-none focus:border-blue-500"
                />
              </div>

              <div className="flex items-center justify-end space-x-3 pt-4 border-t border-slate-800">
                <button
                  type="button"
                  onClick={() => setIsModalOpen(false)}
                  className="px-4 py-2 text-sm font-semibold text-slate-400 hover:text-slate-200"
                >
                  Cancel
                </button>
                <button
                  type="submit"
                  className="px-5 py-2 text-sm font-semibold text-white bg-blue-600 hover:bg-blue-500 rounded-xl shadow-lg shadow-blue-600/30"
                >
                  {editingVehicle ? 'Save Changes' : 'Create Vehicle'}
                </button>
              </div>
            </form>
          </div>
        </div>
      )}
    </div>
  );
};
