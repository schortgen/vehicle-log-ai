import React, { useState, useEffect } from 'react';
import { Vehicle, VehicleEvent, ReviewItem } from './types';
import { INITIAL_VEHICLES, INITIAL_EVENTS, INITIAL_REVIEW_ITEMS } from './data/mockData';
import { Navbar } from './components/Navbar';
import { DashboardView } from './components/DashboardView';
import { VehiclesView } from './components/VehiclesView';
import { FuelEntryView } from './components/FuelEntryView';
import { EventsTimelineView } from './components/EventsTimelineView';
import { ScanReceiptView } from './components/ScanReceiptView';
import { ReviewQueueView } from './components/ReviewQueueView';
import { DiagnosticsView } from './components/DiagnosticsView';

const STORAGE_KEY = 'vehicle_log_ai_data_v2';

export function App() {
  const [currentTab, setCurrentTab] = useState<string>('dashboard');

  // Load from localStorage or default mock data
  const [vehicles, setVehicles] = useState<Vehicle[]>(() => {
    try {
      const saved = localStorage.getItem(`${STORAGE_KEY}_vehicles`);
      return saved ? JSON.parse(saved) : INITIAL_VEHICLES;
    } catch {
      return INITIAL_VEHICLES;
    }
  });

  const [activeVehicleId, setActiveVehicleId] = useState<number | null>(() => {
    return vehicles[0]?.id || null;
  });

  const [events, setEvents] = useState<VehicleEvent[]>(() => {
    try {
      const saved = localStorage.getItem(`${STORAGE_KEY}_events`);
      return saved ? JSON.parse(saved) : INITIAL_EVENTS;
    } catch {
      return INITIAL_EVENTS;
    }
  });

  const [reviewItems, setReviewItems] = useState<ReviewItem[]>(() => {
    try {
      const saved = localStorage.getItem(`${STORAGE_KEY}_reviews`);
      return saved ? JSON.parse(saved) : INITIAL_REVIEW_ITEMS;
    } catch {
      return INITIAL_REVIEW_ITEMS;
    }
  });

  // Save to localStorage on change
  useEffect(() => {
    localStorage.setItem(`${STORAGE_KEY}_vehicles`, JSON.stringify(vehicles));
  }, [vehicles]);

  useEffect(() => {
    localStorage.setItem(`${STORAGE_KEY}_events`, JSON.stringify(events));
  }, [events]);

  useEffect(() => {
    localStorage.setItem(`${STORAGE_KEY}_reviews`, JSON.stringify(reviewItems));
  }, [reviewItems]);

  const activeVehicle = vehicles.find((v) => v.id === activeVehicleId) || vehicles[0];

  // Vehicle Handlers
  const handleAddVehicle = (newVehicle: Omit<Vehicle, 'id' | 'createdDate'>) => {
    const created: Vehicle = {
      ...newVehicle,
      id: Date.now(),
      createdDate: Date.now(),
    };
    setVehicles((prev) => [...prev, created]);
    setActiveVehicleId(created.id);
  };

  const handleUpdateVehicle = (updated: Vehicle) => {
    setVehicles((prev) => prev.map((v) => (v.id === updated.id ? updated : v)));
  };

  const handleDeleteVehicle = (id: number) => {
    setVehicles((prev) => prev.filter((v) => v.id !== id));
    if (activeVehicleId === id) {
      const remaining = vehicles.filter((v) => v.id !== id);
      setActiveVehicleId(remaining[0]?.id || null);
    }
  };

  const handleUpdateVehicleMileage = (vehicleId: number, mileage: number) => {
    setVehicles((prev) =>
      prev.map((v) =>
        v.id === vehicleId && (!v.currentMileage || mileage > v.currentMileage)
          ? { ...v, currentMileage: mileage }
          : v
      )
    );
  };

  // Event Handlers
  const handleAddEvent = (newEvent: Omit<VehicleEvent, 'id' | 'createdDate'>) => {
    const created: VehicleEvent = {
      ...newEvent,
      id: Date.now(),
      createdDate: Date.now(),
    };
    setEvents((prev) => [created, ...prev]);

    // Update vehicle mileage if odometer is provided and higher
    if (newEvent.odometer) {
      handleUpdateVehicleMileage(newEvent.vehicleId, newEvent.odometer);
    }
  };

  const handleUpdateEvent = (updated: VehicleEvent) => {
    setEvents((prev) => prev.map((e) => (e.id === updated.id ? updated : e)));
  };

  const handleDeleteEvent = (id: number) => {
    setEvents((prev) => prev.filter((e) => e.id !== id));
  };

  // Review Queue Handlers
  const handleAddReviewItem = (newItem: Omit<ReviewItem, 'id' | 'createdDate'>) => {
    const created: ReviewItem = {
      ...newItem,
      id: Date.now(),
      createdDate: Date.now(),
    };
    setReviewItems((prev) => [created, ...prev]);
  };

  const handleApproveReviewItem = (
    item: ReviewItem,
    verifiedEvent: Omit<VehicleEvent, 'id' | 'createdDate'>
  ) => {
    // 1. Add verified event to timeline
    handleAddEvent(verifiedEvent);

    // 2. Mark review item as approved
    setReviewItems((prev) =>
      prev.map((r) => (r.id === item.id ? { ...r, status: 'APPROVED' } : r))
    );
  };

  const handleRejectReviewItem = (id: number) => {
    setReviewItems((prev) =>
      prev.map((r) => (r.id === id ? { ...r, status: 'REJECTED' } : r))
    );
  };

  const handleImportBackup = (data: {
    vehicles: Vehicle[];
    events: VehicleEvent[];
    reviewItems: ReviewItem[];
  }) => {
    setVehicles(data.vehicles);
    setEvents(data.events);
    setReviewItems(data.reviewItems || []);
    if (data.vehicles.length > 0) {
      setActiveVehicleId(data.vehicles[0].id);
    }
  };

  const handleResetSampleData = () => {
    setVehicles(INITIAL_VEHICLES);
    setEvents(INITIAL_EVENTS);
    setReviewItems(INITIAL_REVIEW_ITEMS);
    setActiveVehicleId(INITIAL_VEHICLES[0].id);
  };

  const pendingReviewCount = reviewItems.filter((r) => r.status === 'PENDING').length;

  return (
    <div className="min-h-screen bg-slate-950 text-slate-100 flex flex-col font-sans selection:bg-blue-500 selection:text-white">
      <Navbar
        currentTab={currentTab}
        setCurrentTab={setCurrentTab}
        vehicles={vehicles}
        activeVehicleId={activeVehicleId}
        setActiveVehicleId={setActiveVehicleId}
        pendingReviewCount={pendingReviewCount}
      />

      <main className="flex-1 max-w-7xl w-full mx-auto px-4 sm:px-6 lg:px-8 py-8">
        {currentTab === 'dashboard' && (
          <DashboardView
            activeVehicle={activeVehicle}
            vehicles={vehicles}
            events={events}
            reviewItems={reviewItems}
            setCurrentTab={setCurrentTab}
            onOpenAddEventModal={() => setCurrentTab('events')}
          />
        )}

        {currentTab === 'vehicles' && (
          <VehiclesView
            vehicles={vehicles}
            activeVehicleId={activeVehicleId}
            setActiveVehicleId={setActiveVehicleId}
            onAddVehicle={handleAddVehicle}
            onUpdateVehicle={handleUpdateVehicle}
            onDeleteVehicle={handleDeleteVehicle}
          />
        )}

        {currentTab === 'fuel-entry' && (
          <FuelEntryView
            vehicles={vehicles}
            activeVehicleId={activeVehicleId}
            onAddEvent={handleAddEvent}
            onUpdateVehicleMileage={handleUpdateVehicleMileage}
            onSuccessNavigate={() => setCurrentTab('dashboard')}
          />
        )}

        {currentTab === 'events' && (
          <EventsTimelineView
            vehicles={vehicles}
            events={events}
            activeVehicleId={activeVehicleId}
            onAddEvent={handleAddEvent}
            onUpdateEvent={handleUpdateEvent}
            onDeleteEvent={handleDeleteEvent}
          />
        )}

        {currentTab === 'scan' && (
          <ScanReceiptView
            vehicles={vehicles}
            activeVehicleId={activeVehicleId}
            onAddReviewItem={handleAddReviewItem}
            onNavigateToReview={() => setCurrentTab('review')}
          />
        )}

        {currentTab === 'review' && (
          <ReviewQueueView
            reviewItems={reviewItems}
            vehicles={vehicles}
            onApproveReviewItem={handleApproveReviewItem}
            onRejectReviewItem={handleRejectReviewItem}
          />
        )}

        {currentTab === 'diagnostics' && (
          <DiagnosticsView
            vehicles={vehicles}
            events={events}
            reviewItems={reviewItems}
            onImportBackup={handleImportBackup}
            onResetSampleData={handleResetSampleData}
          />
        )}
      </main>

      <footer className="border-t border-slate-900 bg-slate-950 py-6 text-center text-xs text-slate-500">
        <p>Vehicle Log AI • Smart Fuel Tracking & Multimodal Receipt Vision Scanner</p>
      </footer>
    </div>
  );
}

export default App;
