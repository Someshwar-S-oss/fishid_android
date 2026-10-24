// Typed wrapper over the Kotlin bridge (app/src/main/java/com/example/fishid/bridge/FishIdBridge.kt).
// Shapes mirror assets/data/*.json and history.json (snake_case), so these types are the files' schema.
import { useEffect, useState } from 'react';
import { DeviceEventEmitter, NativeModules, requireNativeComponent, ViewProps } from 'react-native';

export type Lang = 'en' | 'ta';

export type Nutrition = { protein: number; fat: number; moisture: number; ash?: number | null; carbohydrate?: number | null };

export type Fish = {
  id: string;
  scientific_name: string;
  scientific_name_ta?: string | null;
  local_name_ta?: string | null;
  local_name_roman?: string | null;
  images: string[];
  image_uris: string[];
  meristics?: { dorsal_spines: number; dorsal_rays: number; anal_spines: number; anal_rays: number } | null;
  habitat: string[];
  feeding?: string | null;
  vertical?: string | null;
  depth_m?: number[] | null;
  iucn?: string | null;
  nutrition_g?: Nutrition | null;
  price_inr_per_kg?: number | null;
  price_updated?: string | null;
  source?: string | null;
  common_name: string;
  primary_name: string;
  secondary_name?: string | null;
  distribution?: string | null;
  field_marks: string[];
  threshold: number;
};

export type Catalog = { data_version: number; updated: string; entries: Fish[] };

export type Scan = {
  id: string;
  time_ms: number;
  photo: string; // absolute path on the phone
  candidates: { species_id: string; confidence: number }[];
  outcome: 'MATCH' | 'UNSURE' | 'NONE';
  species_id?: string | null;
  picked_by_user?: boolean;
};

export type ScanState = { live: boolean; holding: boolean; identifying: boolean; switchedToTap: boolean; hasFlash: boolean };

export type Versions = { app_version: string; data_version: number; model_version: number | null; high_margin: number };

const M = NativeModules.FishID;
const parse = <T,>(p: Promise<string>): Promise<T> => p.then(s => JSON.parse(s));

export const FishID = {
  catalog: (lang: Lang, query = '') => parse<Catalog>(M.catalog(lang, query)),
  versions: () => parse<Versions>(M.versions()),
  history: () => parse<Scan[]>(M.history()),
  deleteScan: (id: string): Promise<unknown> => M.deleteScan(id),
  clearHistory: (): Promise<unknown> => M.clearHistory(),
  pickCandidate: (scanId: string, speciesId: string): Promise<unknown> => M.pickCandidate(scanId, speciesId),
  saveReport: (scanId: string, answer: string | null, place: string, note: string): Promise<unknown> =>
    M.saveReport(scanId, answer, place, note),
  prefs: (): Promise<Record<string, string>> => M.prefs(),
  setPref: (key: string, value: string) => M.setPref(key, value),
  language: (): Promise<Lang> => M.language(),
  setLanguage: (lang: Lang) => M.setLanguage(lang),
  shutter: () => M.shutter(),
  scanAgain: () => M.scanAgain(),
  setTorch: (on: boolean) => M.setTorch(on),
  dismissTapNotice: () => M.dismissTapNotice(),
  /** Resolves true once a photo was picked; the result then arrives through onScanResult. */
  pickPhoto: (): Promise<boolean> => M.pickPhoto(),
};

export function onScanResult(cb: (s: Scan) => void) {
  const sub = DeviceEventEmitter.addListener('FishID.scanResult', (json: string) => cb(JSON.parse(json)));
  return () => sub.remove();
}

export function useScanState(): [ScanState | null, boolean] {
  const [state, setState] = useState<ScanState | null>(null);
  const [error, setError] = useState(false);
  useEffect(() => {
    const a = DeviceEventEmitter.addListener('FishID.scanState', setState);
    const b = DeviceEventEmitter.addListener('FishID.cameraError', () => setError(true));
    return () => { a.remove(); b.remove(); };
  }, []);
  return [state, error];
}

/** The live viewfinder. Scanning runs while it is mounted. */
export const CameraView = requireNativeComponent<ViewProps>('FishCameraView');

export const fileUri = (path: string) => 'file://' + path;
