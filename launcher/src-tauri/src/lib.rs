//! 1227 Launcher backend: settings, Java detection, mod install / update / repair / rollback
//! and news. The web UI can only call the commands below; none of them accepts a free path
//! or URL from the UI except folders the user picked in the system dialog.

mod install;
mod java;
mod manifest;
mod paths;
mod settings;

use serde::{Deserialize, Serialize};
use settings::{Settings, Target};
use std::path::PathBuf;
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::{Arc, Mutex};

#[derive(Default)]
struct AppState {
    cancel: Arc<AtomicBool>,
    busy: Mutex<bool>,
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct StateView {
    settings: Settings,
    installed: Option<install::Installed>,
    mods_dir: Option<String>,
    app_dir: String,
    launcher_version: String,
}

fn client() -> Result<reqwest::Client, String> {
    reqwest::Client::builder()
        .user_agent(concat!("1227-launcher/", env!("CARGO_PKG_VERSION")))
        .timeout(std::time::Duration::from_secs(20))
        .build()
        .map_err(|e| e.to_string())
}

#[tauri::command]
fn get_state() -> StateView {
    let s = Settings::load();
    StateView {
        mods_dir: s.mods_dir().ok().map(|p| p.to_string_lossy().into_owned()),
        settings: s,
        installed: install::installed(),
        app_dir: paths::app_dir().to_string_lossy().into_owned(),
        launcher_version: env!("CARGO_PKG_VERSION").into(),
    }
}

#[tauri::command]
fn save_settings(settings: Settings) -> Result<(), String> {
    // folders must exist (or be creatable for the own instance); java must be a real file
    if settings.target == Target::Existing {
        let d = settings.existing_mods_dir.as_ref().ok_or("Escolha a pasta mods.")?;
        if !PathBuf::from(d).is_dir() {
            return Err("A pasta mods escolhida não existe.".into());
        }
    }
    if let Some(j) = &settings.java_path {
        if !PathBuf::from(j).is_file() {
            return Err("O Java escolhido não foi encontrado.".into());
        }
    }
    settings.save()
}

#[tauri::command]
async fn detect_java() -> Vec<java::JavaInfo> {
    tauri::async_runtime::spawn_blocking(java::detect).await.unwrap_or_default()
}

#[tauri::command]
async fn pick_folder(title: String) -> Option<String> {
    rfd::AsyncFileDialog::new()
        .set_title(&title)
        .pick_folder()
        .await
        .map(|f| f.path().to_string_lossy().into_owned())
}

#[tauri::command]
async fn pick_java() -> Option<java::JavaInfo> {
    let f = rfd::AsyncFileDialog::new().set_title("Escolha o java.exe (Java 8)").pick_file().await?;
    let p = f.path().to_path_buf();
    tauri::async_runtime::spawn_blocking(move || java::probe(&p)).await.ok().flatten()
}

/// Suggests <.minecraft>/mods when it exists (never writes there on its own).
#[tauri::command]
fn suggest_mods_dir() -> Option<String> {
    let d = paths::dot_minecraft()?.join("mods");
    if d.is_dir() { Some(d.to_string_lossy().into_owned()) } else { None }
}

#[derive(Serialize)]
#[serde(rename_all = "camelCase")]
struct UpdateInfo {
    manifest: manifest::Manifest,
    installed: Option<String>,
    update_available: bool,
}

#[tauri::command]
async fn check_updates() -> Result<UpdateInfo, String> {
    let m = manifest::fetch(&client()?).await?;
    let inst = install::installed().map(|i| i.version);
    let update_available = match &inst {
        None => true,
        Some(v) => manifest::newer(&m.latest, v),
    };
    Ok(UpdateInfo { manifest: m, installed: inst, update_available })
}

#[tauri::command]
async fn install_version(app: tauri::AppHandle, state: tauri::State<'_, AppState>, version: String) -> Result<install::Installed, String> {
    if !manifest::valid_version(&version) {
        return Err("Versão inválida.".into());
    }
    {
        let mut b = state.busy.lock().unwrap();
        if *b {
            return Err("Já existe uma instalação em andamento.".into());
        }
        *b = true;
    }
    state.cancel.store(false, Ordering::SeqCst);
    let result = async {
        let m = manifest::fetch(&client()?).await?;
        let v = m.find(&version).ok_or("Versão não encontrada no manifesto.")?.clone();
        let mods = Settings::load().mods_dir()?;
        install::install(&app, &v, &mods, state.cancel.clone()).await
    }
    .await;
    *state.busy.lock().unwrap() = false;
    result
}

#[tauri::command]
fn cancel_install(state: tauri::State<'_, AppState>) {
    state.cancel.store(true, Ordering::SeqCst);
}

#[tauri::command]
fn rollback() -> Result<install::Installed, String> {
    install::rollback()
}

#[tauri::command]
async fn verify_install() -> Result<bool, String> {
    tauri::async_runtime::spawn_blocking(install::verify).await.map_err(|e| e.to_string())?
}

#[derive(Serialize, Deserialize, Clone)]
struct NewsItem {
    title: String,
    date: String,
    body: String,
}

#[tauri::command]
async fn fetch_news() -> Result<Vec<NewsItem>, String> {
    let resp = client()?.get(manifest::NEWS_URL).send().await.map_err(|e| format!("Sem conexão ({e})."))?;
    if !resp.status().is_success() {
        return Err(format!("Servidor respondeu {}.", resp.status()));
    }
    let mut items: Vec<NewsItem> = resp.json().await.map_err(|e| format!("Notícias ilegíveis: {e}"))?;
    items.truncate(30);
    for n in items.iter_mut() {
        // plain text only, bounded size (the UI never renders HTML from the API)
        n.title.truncate(120);
        n.date.truncate(20);
        n.body.truncate(4000);
    }
    Ok(items)
}

/// Opens one of the launcher's known folders in the file explorer.
#[tauri::command]
fn open_folder(kind: String) -> Result<(), String> {
    let s = Settings::load();
    let p = match kind.as_str() {
        "mods" => s.mods_dir()?,
        "launcher" => paths::app_dir(),
        "backups" => paths::app_dir().join("backups"),
        _ => return Err("Pasta desconhecida.".into()),
    };
    std::fs::create_dir_all(&p).map_err(|e| e.to_string())?;
    let opener = if cfg!(target_os = "windows") { "explorer" } else if cfg!(target_os = "macos") { "open" } else { "xdg-open" };
    std::process::Command::new(opener).arg(&p).spawn().map_err(|e| e.to_string())?;
    Ok(())
}

#[cfg_attr(mobile, tauri::mobile_entry_point)]
pub fn run() {
    tauri::Builder::default()
        .manage(AppState::default())
        .invoke_handler(tauri::generate_handler![
            get_state,
            save_settings,
            detect_java,
            pick_folder,
            pick_java,
            suggest_mods_dir,
            check_updates,
            install_version,
            cancel_install,
            rollback,
            verify_install,
            fetch_news,
            open_folder
        ])
        .run(tauri::generate_context!())
        .expect("error while running the 1227 launcher");
}
