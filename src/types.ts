export type EventType = 
  | 'FUEL' 
  | 'MAINTENANCE' 
  | 'MILEAGE' 
  | 'INSPECTION' 
  | 'REGISTRATION' 
  | 'TIRE_ROTATION';

export type ProcessingStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

export interface Vehicle {
  id: number;
  nickname: string;
  year?: number;
  make?: string;
  model?: string;
  licensePlate?: string;
  vin?: string;
  currentMileage?: number;
  isActive: boolean;
  createdDate: number; // timestamp
}

export interface VehicleEvent {
  id: number;
  vehicleId: number;
  eventType: EventType;
  eventDate: number; // timestamp
  createdDate: number; // timestamp
  confidence?: number;
  verified: boolean;
  notes?: string;
  // Fuel-specific fields
  odometer?: number;
  gallons?: number;
  pricePerGallon?: number;
  totalCost?: number;
  location?: string;
  photoPath?: string;
}

export interface ParsedReceiptData {
  stationName?: string;
  date?: string;
  gallons?: number;
  pricePerGallon?: number;
  totalCost?: number;
  fuelType?: string;
  odometer?: number;
  confidence?: number;
}

export interface ReviewItem {
  id: number;
  photoPath?: string;
  captureDate: number;
  vehicleId?: number;
  eventId?: number;
  reason?: string;
  confidence?: number;
  status: ProcessingStatus;
  createdDate: number;
  ocrText?: string;
  parsedData?: ParsedReceiptData;
}
