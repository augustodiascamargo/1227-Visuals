import { invoke } from "@tauri-apps/api/core";
import { listen, type UnlistenFn } from "@tauri-apps/api/event";

export type Target = "own" | "existing";

export interface Settings {
  target: Target;
  instanceDir: string;
  existingModsDir: string | null;
  javaPath: string | null;
  channel: string;
}

export interface Installed {
  version: string;
  sha256: string;
  modsDir: string;
  backup: string | null;
}

export interface StateView {
  settings: Settings;
  installed: Installed | null;
  modsDir: string | null;
  appDir: string;
  launcherVersion: string;
}

export interface JavaInfo {
  path: string;
  version: string;
  major: number;
  is64: boolean;
  ok: boolean;
}

export interface VersionEntry {
  version: string;
  minecraft: string;
  forge: string;
  date: string;
  notes: string[];
  file: { name: string; url: string; sha256: string; size: number };
}

export interface UpdateInfo {
  manifest: { schema: number; latest: string; versions: VersionEntry[] };
  installed: string | null;
  updateAvailable: boolean;
}

export interface Progress {
  stage: "download" | "install" | "done";
  received: number;
  total: number;
}

export interface NewsItem {
  title: string;
  date: string;
  body: string;
}

export const api = {
  state: () => invoke<StateView>("get_state"),
  saveSettings: (settings: Settings) => invoke<void>("save_settings", { settings }),
  detectJava: () => invoke<JavaInfo[]>("detect_java"),
  pickFolder: (title: string) => invoke<string | null>("pick_folder", { title }),
  pickJava: () => invoke<JavaInfo | null>("pick_java"),
  suggestModsDir: () => invoke<string | null>("suggest_mods_dir"),
  checkUpdates: () => invoke<UpdateInfo>("check_updates"),
  install: (version: string) => invoke<Installed>("install_version", { version }),
  cancel: () => invoke<void>("cancel_install"),
  rollback: () => invoke<Installed>("rollback"),
  verify: () => invoke<boolean>("verify_install"),
  news: () => invoke<NewsItem[]>("fetch_news"),
  openFolder: (kind: "mods" | "launcher" | "backups") => invoke<void>("open_folder", { kind }),
  onProgress: (cb: (p: Progress) => void): Promise<UnlistenFn> =>
    listen<Progress>("install-progress", (e) => cb(e.payload)),
};

export function errorText(e: unknown): string {
  return typeof e === "string" ? e : e instanceof Error ? e.message : "Erro inesperado.";
}

export function mb(bytes: number): string {
  return (bytes / 1048576).toFixed(1) + " MB";
}
