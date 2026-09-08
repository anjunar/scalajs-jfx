export interface Design { readonly id: string; readonly name: string; readonly description: string }
export type ColorScheme = "light" | "dark";
export interface PreferenceState { design: string; colorScheme: ColorScheme; storageAvailable: boolean }
export interface Preferences {
  getState(): PreferenceState;
  setDesign(value: string): void;
  setColorScheme(value: ColorScheme): void;
  subscribe(listener: (state: PreferenceState) => void): () => void;
}
export const designs: readonly Design[];
export const defaultDesign: string;
export const storageKeys: Readonly<{ design: string; colorScheme: string }>;
export function serverPreferences(url?: string): PreferenceState;
export function bootstrapScript(legacyKey: string): string;
export function createPreferences(legacyKey: string, url?: string, environment?: Window | null): Preferences;
