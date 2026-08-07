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

export interface AuthState {
  isAuthenticated: boolean;
  user: GitHubUser | null;
  token: string | null;
}

export interface GitHubUser {
  login: string;
  id: number;
  avatar_url: string;
  html_url?: string;
  name?: string;
  email?: string;
  company?: string;
  blog?: string;
  location?: string;
  bio?: string;
  public_repos?: number;
  public_gists?: number;
  followers?: number;
  following?: number;
  created_at?: string;
  updated_at?: string;
}

export interface GitHubRepo {
  id: number;
  name: string;
  full_name: string;
  private: boolean;
  fork?: boolean;
  html_url: string;
  url?: string;
  git_url?: string;
  ssh_url?: string;
  clone_url?: string;
  description: string | null;
  stargazers_count: number;
  watchers_count?: number;
  forks_count: number;
  open_issues_count: number;
  size?: number;
  language?: string | null;
  topics?: string[];
  created_at?: string;
  updated_at: string;
  pushed_at?: string;
  default_branch: string;
  owner: {
    login: string;
    avatar_url: string;
    html_url?: string;
  };
}

export interface FileItem {
  name: string;
  path: string;
  sha: string;
  size: number;
  url: string;
  html_url?: string;
  git_url?: string;
  type: 'file' | 'dir';
  download_url?: string | null;
  content?: string;
  encoding?: string;
}

export interface GitHubIssue {
  id: number;
  number: number;
  title: string;
  body: string | null;
  state: 'open' | 'closed';
  user: {
    login: string;
    avatar_url: string;
    html_url?: string;
  };
  created_at: string;
  updated_at: string;
  comments: number;
  html_url: string;
  labels: { id: number; name: string; color: string }[];
}

export interface GitHubPullRequest {
  id: number;
  number: number;
  title: string;
  body: string | null;
  state: 'open' | 'closed' | 'merged';
  user: {
    login: string;
    avatar_url: string;
    html_url?: string;
  };
  created_at: string;
  updated_at: string;
  html_url: string;
  head: { ref: string; label?: string; sha?: string };
  base: { ref: string; label?: string; sha?: string };
}

export interface GitHubCommit {
  sha: string;
  commit: {
    author: {
      name: string;
      email: string;
      date: string;
    };
    message: string;
  };
  html_url: string;
  author?: {
    login: string;
    avatar_url: string;
  };
}

