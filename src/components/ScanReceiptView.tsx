import React, { useState, useMemo } from 'react';
import { Vehicle, ReviewItem, ParsedReceiptData } from '../types';
import {
  Scan,
  Upload,
  Sparkles,
  CheckCircle2,
  AlertCircle,
  Fuel,
  ArrowRight,
  RefreshCw,
  FileText,
  Calendar,
  CalendarDays,
  Clock,
  Filter,
  X,
  ChevronDown,
} from 'lucide-react';

interface ScanReceiptViewProps {
  vehicles: Vehicle[];
  activeVehicleId: number | null;
  onAddReviewItem: (item: Omit<ReviewItem, 'id' | 'createdDate'>) => void;
  onNavigateToReview: () => void;
}

type DatePreset = 'all' | '7days' | '30days' | 'this_month' | 'custom';

export const ScanReceiptView: React.FC<ScanReceiptViewProps> = ({
  vehicles,
  activeVehicleId,
  onAddReviewItem,
  onNavigateToReview,
}) => {
  const [selectedVehicleId, setSelectedVehicleId] = useState<number>(
    activeVehicleId || (vehicles[0]?.id || 1)
  );

  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [isAnalyzing, setIsAnalyzing] = useState(false);
  const [extractedData, setExtractedData] = useState<ParsedReceiptData | null>(null);
  const [rawOcrText, setRawOcrText] = useState<string>('');
  const [errorMsg, setErrorMsg] = useState<string | null>(null);

  // Date Range Picker State
  const [datePreset, setDatePreset] = useState<DatePreset>('all');
  const [startDate, setStartDate] = useState<string>('');
  const [endDate, setEndDate] = useState<string>('');

  const handlePresetChange = (preset: DatePreset) => {
    setDatePreset(preset);
    const now = new Date();
    const formatIso = (d: Date) => d.toISOString().split('T')[0];

    if (preset === 'all') {
      setStartDate('');
      setEndDate('');
    } else if (preset === '7days') {
      const start = new Date();
      start.setDate(now.getDate() - 7);
      setStartDate(formatIso(start));
      setEndDate(formatIso(now));
    } else if (preset === '30days') {
      const start = new Date();
      start.setDate(now.getDate() - 30);
      setStartDate(formatIso(start));
      setEndDate(formatIso(now));
    } else if (preset === 'this_month') {
      const start = new Date(now.getFullYear(), now.getMonth(), 1);
      setStartDate(formatIso(start));
      setEndDate(formatIso(now));
    } else if (preset === 'custom') {
      if (!startDate) setStartDate(formatIso(new Date(now.getFullYear(), now.getMonth(), 1)));
      if (!endDate) setEndDate(formatIso(now));
    }
  };

  const handleClearDateRange = () => {
    setDatePreset('all');
    setStartDate('');
    setEndDate('');
  };

  // Check if extracted receipt date falls within selected date range
  const isDateInRange = useMemo(() => {
    if (!extractedData?.date || (!startDate && !endDate)) return true;
    const receiptDate = new Date(extractedData.date).getTime();
    if (isNaN(receiptDate)) return true;

    if (startDate) {
      const start = new Date(startDate).getTime();
      if (receiptDate < start) return false;
    }
    if (endDate) {
      const end = new Date(endDate).getTime() + (24 * 60 * 60 * 1000 - 1);
      if (receiptDate > end) return false;
    }
    return true;
  }, [extractedData, startDate, endDate]);

  // Preset sample receipts for quick testing
  const sampleReceipts = [
    {
      title: 'Chevron Fill-Up',
      previewUrl: 'https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?w=600&auto=format&fit=crop&q=80',
      sampleText: 'CHEVRON #2041\nSAN JOSE CA\nDATE: 08/04/2026\nPUMP 02\nREGULAR 87\nGALLONS: 11.850\nPRICE/GAL: $3.899\nTOTAL: $46.20\nODOMETER: 34880',
    },
    {
      title: 'Shell Gas Station',
      previewUrl: 'https://images.unsplash.com/photo-1527018606416-a6745190ce6d?w=600&auto=format&fit=crop&q=80',
      sampleText: 'SHELL OIL #9120\nPALO ALTO CA\n08/01/2026 14:22\nPUMP 06\nV-POWER PREMIUM 93\nGALLONS: 14.200\nPRICE/GAL: $4.359\nTOTAL: $61.90\nODOMETER: 35120',
    },
  ];

  const handleImageUpload = (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    if (!file) return;

    const reader = new FileReader();
    reader.onloadend = () => {
      setImagePreview(reader.result as string);
      analyzeReceipt(reader.result as string, undefined);
    };
    reader.readAsDataURL(file);
  };

  const analyzeReceipt = async (base64Img?: string, textInput?: string) => {
    setIsAnalyzing(true);
    setErrorMsg(null);
    setExtractedData(null);

    try {
      const res = await fetch('/api/ai/scan-receipt', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
          imageBase64: base64Img,
          sampleText: textInput,
          dateRange: startDate || endDate ? { startDate, endDate } : undefined,
        }),
      });

      const json = await res.json();
      if (json.success && json.data) {
        setExtractedData(json.data);
        setRawOcrText(json.data.ocrText || '');
      } else {
        setErrorMsg('Failed to process receipt image.');
      }
    } catch (err: any) {
      console.error(err);
      setErrorMsg('Error connecting to AI OCR service.');
    } finally {
      setIsAnalyzing(false);
    }
  };

  const handleSendToReviewQueue = () => {
    if (!extractedData) return;

    onAddReviewItem({
      photoPath: imagePreview || 'https://images.unsplash.com/photo-1554224155-8d04cb21cd6c?w=600&auto=format&fit=crop&q=80',
      captureDate: Date.now(),
      vehicleId: selectedVehicleId,
      reason: 'AI OCR Extracted Receipt',
      confidence: extractedData.confidence || 0.90,
      status: 'PENDING',
      ocrText: rawOcrText,
      parsedData: extractedData,
    });

    onNavigateToReview();
  };

  return (
    <div id="scan-receipt-view" className="space-y-6">
      {/* Header */}
      <div className="bg-gradient-to-r from-emerald-600/20 via-slate-900 to-slate-900 border border-emerald-500/30 rounded-2xl p-6 text-slate-100">
        <div className="flex items-center space-x-3">
          <div className="w-12 h-12 rounded-xl bg-emerald-500/20 text-emerald-400 flex items-center justify-center shrink-0">
            <Scan className="w-6 h-6" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <h1 className="text-2xl font-extrabold tracking-tight">Gemini AI Receipt OCR Scanner</h1>
              <span className="text-xs font-bold px-2 py-0.5 rounded bg-emerald-500/20 text-emerald-300 border border-emerald-500/30">
                MULTIMODAL AI
              </span>
            </div>
            <p className="text-sm text-slate-400">
              Upload a fuel receipt photo or camera scan. Filter scans by custom date ranges and auto-extract fuel metrics with Gemini.
            </p>
          </div>
        </div>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
        {/* Left Column: Controls, Date Range Picker & Upload */}
        <div className="space-y-6">
          {/* Target Vehicle Selection */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-3">
            <label className="block text-xs font-semibold uppercase tracking-wider text-slate-400">
              Target Vehicle for Scan
            </label>
            <select
              value={selectedVehicleId}
              onChange={(e) => setSelectedVehicleId(Number(e.target.value))}
              className="w-full px-4 py-2.5 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-sm focus:outline-none focus:border-emerald-500"
            >
              {vehicles.map((v) => (
                <option key={v.id} value={v.id}>
                  {v.nickname || `${v.year} ${v.make} ${v.model}`}
                </option>
              ))}
            </select>
          </div>

          {/* Date Range Picker */}
          <div id="photo-scanner-date-range-picker" className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
            <div className="flex items-center justify-between">
              <div className="flex items-center space-x-2 text-emerald-400 font-bold text-sm">
                <CalendarDays className="w-5 h-5 text-emerald-400" />
                <span>Scan Date Range Filter</span>
              </div>
              {(startDate || endDate || datePreset !== 'all') && (
                <button
                  onClick={handleClearDateRange}
                  className="text-xs text-slate-400 hover:text-slate-200 flex items-center space-x-1 bg-slate-800 px-2.5 py-1 rounded-lg border border-slate-700 transition-all hover:bg-slate-700"
                >
                  <X className="w-3.5 h-3.5" />
                  <span>Clear Filter</span>
                </button>
              )}
            </div>

            <p className="text-xs text-slate-400">
              Specify or filter the date range window for photo scanning and auto-verification.
            </p>

            {/* Presets */}
            <div className="flex flex-wrap gap-2">
              {[
                { id: 'all', label: 'All Dates' },
                { id: '7days', label: 'Last 7 Days' },
                { id: '30days', label: 'Last 30 Days' },
                { id: 'this_month', label: 'This Month' },
                { id: 'custom', label: 'Custom' },
              ].map((preset) => (
                <button
                  key={preset.id}
                  onClick={() => handlePresetChange(preset.id as DatePreset)}
                  className={`px-3 py-1.5 text-xs font-semibold rounded-lg border transition-all ${
                    datePreset === preset.id
                      ? 'bg-emerald-600 text-white border-emerald-500 shadow-md shadow-emerald-600/20'
                      : 'bg-slate-800 text-slate-300 border-slate-700 hover:bg-slate-700 hover:text-white'
                  }`}
                >
                  {preset.label}
                </button>
              ))}
            </div>

            {/* Date Pickers */}
            <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
              <div>
                <label className="block text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-1 flex items-center space-x-1">
                  <Calendar className="w-3.5 h-3.5 text-slate-400" />
                  <span>Start Date</span>
                </label>
                <input
                  type="date"
                  value={startDate}
                  onChange={(e) => {
                    setStartDate(e.target.value);
                    setDatePreset('custom');
                  }}
                  className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-xs focus:outline-none focus:border-emerald-500"
                />
              </div>

              <div>
                <label className="block text-[11px] font-semibold text-slate-400 uppercase tracking-wider mb-1 flex items-center space-x-1">
                  <Calendar className="w-3.5 h-3.5 text-slate-400" />
                  <span>End Date</span>
                </label>
                <input
                  type="date"
                  value={endDate}
                  onChange={(e) => {
                    setEndDate(e.target.value);
                    setDatePreset('custom');
                  }}
                  className="w-full px-3 py-2 bg-slate-800 border border-slate-700 rounded-xl text-slate-100 text-xs focus:outline-none focus:border-emerald-500"
                />
              </div>
            </div>

            {/* Active Range Indicator */}
            {(startDate || endDate) && (
              <div className="p-3 bg-emerald-950/40 border border-emerald-500/30 rounded-xl flex items-center justify-between text-xs text-emerald-300">
                <div className="flex items-center space-x-2">
                  <Filter className="w-4 h-4 text-emerald-400 shrink-0" />
                  <span>
                    Active Scan Scope:{' '}
                    <strong className="text-white">
                      {startDate || 'Earliest'} &rarr; {endDate || 'Latest'}
                    </strong>
                  </span>
                </div>
              </div>
            )}
          </div>

          {/* Upload Area */}
          <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-4">
            <h2 className="text-base font-bold text-white flex items-center space-x-2">
              <Upload className="w-5 h-5 text-emerald-400" />
              <span>Upload Receipt Photo</span>
            </h2>

            <div className="border-2 border-dashed border-slate-700 hover:border-emerald-500/60 rounded-2xl p-6 text-center space-y-3 transition-colors bg-slate-800/40">
              {imagePreview ? (
                <div className="space-y-3">
                  <img
                    src={imagePreview}
                    alt="Receipt preview"
                    className="max-h-56 mx-auto rounded-lg object-contain shadow-md"
                  />
                  <div className="flex items-center justify-center space-x-2">
                    <label className="px-3 py-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 text-xs font-medium rounded-lg border border-slate-700 cursor-pointer">
                      Change Photo
                      <input type="file" accept="image/*" onChange={handleImageUpload} className="hidden" />
                    </label>
                  </div>
                </div>
              ) : (
                <div className="space-y-2">
                  <div className="w-12 h-12 rounded-full bg-emerald-500/10 text-emerald-400 flex items-center justify-center mx-auto">
                    <Scan className="w-6 h-6" />
                  </div>
                  <p className="text-sm font-semibold text-slate-200">Drag & drop or click to upload receipt</p>
                  <p className="text-xs text-slate-400">Supports JPG, PNG, WEBP files</p>
                  <label className="inline-block mt-2 px-4 py-2 bg-emerald-600 hover:bg-emerald-500 text-white text-xs font-bold rounded-xl shadow-lg shadow-emerald-600/30 cursor-pointer transition-all">
                    Browse File
                    <input type="file" accept="image/*" onChange={handleImageUpload} className="hidden" />
                  </label>
                </div>
              )}
            </div>

            {/* Quick Sample Receipts */}
            <div className="space-y-2 pt-2">
              <span className="text-xs font-semibold uppercase tracking-wider text-slate-400">
                Or Try Sample Gas Receipts:
              </span>
              <div className="grid grid-cols-2 gap-2">
                {sampleReceipts.map((sample, idx) => (
                  <button
                    key={idx}
                    onClick={() => {
                      setImagePreview(sample.previewUrl);
                      analyzeReceipt(undefined, sample.sampleText);
                    }}
                    className="p-3 bg-slate-800 hover:bg-slate-700/80 border border-slate-700 rounded-xl text-left space-y-1 transition-all group"
                  >
                    <p className="text-xs font-bold text-slate-200 group-hover:text-emerald-400">{sample.title}</p>
                    <p className="text-[11px] text-slate-400">Click to run AI scan</p>
                  </button>
                ))}
              </div>
            </div>
          </div>
        </div>

        {/* Right Column: AI Results Preview */}
        <div className="bg-slate-900 border border-slate-800 rounded-2xl p-6 space-y-6 flex flex-col justify-between">
          <div className="space-y-4">
            <div className="flex items-center justify-between pb-3 border-b border-slate-800">
              <h2 className="text-lg font-bold text-white flex items-center space-x-2">
                <Sparkles className="w-5 h-5 text-emerald-400" />
                <span>AI Extracted Fields</span>
              </h2>
              {isAnalyzing && (
                <span className="text-xs font-semibold text-emerald-400 flex items-center space-x-1 animate-pulse">
                  <RefreshCw className="w-3.5 h-3.5 animate-spin" />
                  <span>Analyzing with Gemini...</span>
                </span>
              )}
            </div>

            {errorMsg && (
              <div className="p-4 bg-red-500/10 border border-red-500/30 rounded-xl text-red-300 text-xs flex items-center space-x-2">
                <AlertCircle className="w-4 h-4 text-red-400 shrink-0" />
                <span>{errorMsg}</span>
              </div>
            )}

            {!extractedData && !isAnalyzing && (
              <div className="text-center py-12 text-slate-500 space-y-2">
                <FileText className="w-12 h-12 mx-auto text-slate-700" />
                <p className="text-sm font-medium">No receipt analyzed yet.</p>
                <p className="text-xs">Upload a receipt photo or select a sample above.</p>
              </div>
            )}

            {extractedData && (
              <div className="space-y-4">
                {/* Date Range Validation Status Badge */}
                {(startDate || endDate) && (
                  <div
                    className={`p-3 rounded-xl border text-xs flex items-center space-x-2 ${
                      isDateInRange
                        ? 'bg-emerald-500/10 border-emerald-500/30 text-emerald-300'
                        : 'bg-amber-500/10 border-amber-500/30 text-amber-300'
                    }`}
                  >
                    {isDateInRange ? (
                      <>
                        <CheckCircle2 className="w-4 h-4 text-emerald-400 shrink-0" />
                        <span>Receipt date matches the selected scan date range.</span>
                      </>
                    ) : (
                      <>
                        <AlertCircle className="w-4 h-4 text-amber-400 shrink-0" />
                        <span>
                          Receipt date ({extractedData.date}) is outside your selected scan window ({startDate || 'Earliest'} to {endDate || 'Latest'}).
                        </span>
                      </>
                    )}
                  </div>
                )}

                <div className="p-4 bg-slate-800/80 border border-slate-700 rounded-xl space-y-3">
                  <div className="flex items-center justify-between text-xs text-slate-400">
                    <span className="font-semibold uppercase tracking-wider text-emerald-400">Extraction Confidence</span>
                    <span className="font-bold text-slate-200">
                      {Math.round((extractedData.confidence || 0.95) * 100)}%
                    </span>
                  </div>

                  <div className="grid grid-cols-2 gap-3 pt-2 text-sm">
                    <div>
                      <span className="text-xs text-slate-400 block">Station / Merchant</span>
                      <span className="font-bold text-white">{extractedData.stationName || 'Chevron'}</span>
                    </div>

                    <div>
                      <span className="text-xs text-slate-400 block">Date</span>
                      <span className="font-bold text-white">{extractedData.date || 'Today'}</span>
                    </div>

                    <div>
                      <span className="text-xs text-slate-400 block">Gallons</span>
                      <span className="font-bold text-white">{extractedData.gallons} gal</span>
                    </div>

                    <div>
                      <span className="text-xs text-slate-400 block">Price / Gal</span>
                      <span className="font-bold text-white">${extractedData.pricePerGallon}</span>
                    </div>

                    <div>
                      <span className="text-xs text-slate-400 block">Total Cost</span>
                      <span className="font-bold text-emerald-400 text-lg">${extractedData.totalCost}</span>
                    </div>

                    <div>
                      <span className="text-xs text-slate-400 block">Odometer</span>
                      <span className="font-bold text-white">{extractedData.odometer ? `${extractedData.odometer} mi` : 'N/A'}</span>
                    </div>
                  </div>
                </div>

                {/* Raw OCR Text */}
                {rawOcrText && (
                  <div className="space-y-1">
                    <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">
                      Extracted OCR Text
                    </span>
                    <pre className="p-3 bg-slate-950 border border-slate-800 rounded-xl text-xs text-slate-300 font-mono whitespace-pre-wrap max-h-36 overflow-y-auto">
                      {rawOcrText}
                    </pre>
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Action Button */}
          {extractedData && (
            <div className="pt-4 border-t border-slate-800">
              <button
                id="send-to-review-queue-button"
                onClick={handleSendToReviewQueue}
                className="w-full py-3 bg-emerald-600 hover:bg-emerald-500 text-white font-bold rounded-xl shadow-lg shadow-emerald-600/30 transition-all flex items-center justify-center space-x-2"
              >
                <span>Add to Review Queue</span>
                <ArrowRight className="w-5 h-5" />
              </button>
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

