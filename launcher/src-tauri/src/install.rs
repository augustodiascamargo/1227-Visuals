//! Installing, verifying, repairing and rolling back the 1227 mod jar.
//!
//! Every install: download to a ".part" file -> check size and SHA-256 -> move the old 1227
//! jar(s) into a timestamped backup folder -> move the new jar into place -> record it.
//! If anything fails before the final move, the mods folder is left exactly as it was.

use crate::manifest::{self, Version};
use crate::paths;
use futures_util::StreamExt;
use serde::{Deserialize, Serialize};
use sha2::{Digest, Sha256};
use std::fs;
use std::io::Read;
use std::path::{Path, PathBuf};
use std::sync::atomic::{AtomicBool, Ordering};
use std::sync::Arc;
use std::time::{SystemTime, UNIX_EPOCH};
use tauri::{AppHandle, Emitter};
use tokio::io::AsyncWriteExt;

#[derive(Serialize, Deserialize, Clone, Debug, Default)]
#[serde(rename_all = "camelCase")]
pub struct Installed {
    pub version: String,
    pub sha256: String,
    pub mods_dir: String,
    /// Backup folder holding what was there before this install (for rollback).
    pub backup: Option<String>,
}

#[derive(Serialize, Clone)]
#[serde(rename_all = "camelCase")]
pub struct Progress {
    pub stage: String,
    pub received: u64,
    pub total: u64,
}

fn record_file() -> PathBuf {
    paths::app_dir().join("installed.json")
}

pub fn installed() -> Option<Installed> {
    fs::read_to_string(record_file()).ok().and_then(|t| serde_json::from_str(&t).ok())
}

fn save_record(r: &Installed) -> Result<(), String> {
    fs::create_dir_all(paths::app_dir()).map_err(|e| e.to_string())?;
    fs::write(record_file(), serde_json::to_string_pretty(r).unwrap()).map_err(|e| e.to_string())
}

pub fn sha256_file(p: &Path) -> Result<String, String> {
    let mut f = fs::File::open(p).map_err(|e| e.to_string())?;
    let mut h = Sha256::new();
    let mut buf = vec![0u8; 64 * 1024];
    loop {
        let n = f.read(&mut buf).map_err(|e| e.to_string())?;
        if n == 0 {
            break;
        }
        h.update(&buf[..n]);
    }
    Ok(hex::encode(h.finalize()))
}

/// The 1227 jars currently in a mods folder (any version).
fn jars_in(dir: &Path) -> Vec<PathBuf> {
    fs::read_dir(dir)
        .map(|rd| {
            rd.flatten()
                .map(|e| e.path())
                .filter(|p| {
                    p.file_name()
                        .and_then(|n| n.to_str())
                        .map(|n| n.starts_with("1227-") && n.ends_with(".jar"))
                        .unwrap_or(false)
                })
                .collect()
        })
        .unwrap_or_default()
}

fn now_stamp() -> u64 {
    SystemTime::now().duration_since(UNIX_EPOCH).map(|d| d.as_secs()).unwrap_or(0)
}

/// Downloads, verifies and installs `v` into `mods_dir`. `cancel` stops it safely.
pub async fn install(app: &AppHandle, v: &Version, mods_dir: &Path, cancel: Arc<AtomicBool>) -> Result<Installed, String> {
    manifest::check_url(&v.file.url)?;
    let target = paths::safe_join(mods_dir, &v.file.name)?;
    fs::create_dir_all(mods_dir).map_err(|e| format!("Não foi possível criar a pasta mods: {e}"))?;
    let part = paths::safe_join(mods_dir, ".1227-download.part")?;

    // redirects (GitHub -> its CDN) are followed only to allowed HTTPS hosts
    let policy = reqwest::redirect::Policy::custom(|attempt| {
        if attempt.previous().len() > 5 {
            attempt.error("too many redirects")
        } else if manifest::check_url(attempt.url().as_str()).is_ok() {
            attempt.follow()
        } else {
            attempt.error("redirect to a host that is not allowed")
        }
    });
    let client = reqwest::Client::builder()
        .user_agent(concat!("1227-launcher/", env!("CARGO_PKG_VERSION")))
        .redirect(policy)
        .build()
        .map_err(|e| e.to_string())?;
    let resp = client.get(&v.file.url).send().await.map_err(|e| format!("Falha no download: {e}"))?;
    if !resp.status().is_success() {
        return Err(format!("O servidor respondeu {}.", resp.status()));
    }
    if let Some(len) = resp.content_length() {
        if len != v.file.size {
            return Err("Tamanho do arquivo diferente do manifesto.".into());
        }
    }
    let total = v.file.size;
    let mut out = tokio::fs::File::create(&part).await.map_err(|e| e.to_string())?;
    let mut hasher = Sha256::new();
    let mut received: u64 = 0;
    let mut stream = resp.bytes_stream();
    let mut last_emit = 0u64;
    while let Some(chunk) = stream.next().await {
        if cancel.load(Ordering::SeqCst) {
            drop(out);
            let _ = fs::remove_file(&part);
            return Err("Download cancelado. Nada foi alterado.".into());
        }
        let chunk = chunk.map_err(|e| {
            let _ = fs::remove_file(&part);
            format!("Conexão interrompida: {e}")
        })?;
        received += chunk.len() as u64;
        if received > total {
            drop(out);
            let _ = fs::remove_file(&part);
            return Err("O arquivo é maior do que o esperado.".into());
        }
        hasher.update(&chunk);
        out.write_all(&chunk).await.map_err(|e| e.to_string())?;
        if received - last_emit > 64 * 1024 || received == total {
            last_emit = received;
            let _ = app.emit("install-progress", Progress { stage: "download".into(), received, total });
        }
    }
    out.flush().await.map_err(|e| e.to_string())?;
    drop(out);

    let digest = hex::encode(hasher.finalize());
    if received != total || !digest.eq_ignore_ascii_case(&v.file.sha256) {
        let _ = fs::remove_file(&part);
        return Err("Verificação SHA-256 falhou: o arquivo baixado está corrompido ou foi alterado. Nada foi instalado.".into());
    }
    let _ = app.emit("install-progress", Progress { stage: "install".into(), received, total });

    // back up whatever 1227 jar(s) are there now
    let old = jars_in(mods_dir);
    let mut backup = None;
    if !old.is_empty() {
        let bdir = paths::app_dir().join("backups").join(now_stamp().to_string());
        fs::create_dir_all(&bdir).map_err(|e| e.to_string())?;
        for j in &old {
            let name = j.file_name().unwrap().to_string_lossy().to_string();
            let dest = bdir.join(&name);
            if fs::rename(j, &dest).is_err() {
                fs::copy(j, &dest).map_err(|e| format!("Backup falhou: {e}"))?;
                fs::remove_file(j).map_err(|e| format!("Feche o jogo antes de atualizar ({e})."))?;
            }
        }
        backup = Some(bdir.to_string_lossy().into_owned());
    }
    if let Err(e) = fs::rename(&part, &target) {
        // restore the backup so the folder is never left without the mod
        if let Some(b) = &backup {
            restore_from(Path::new(b), mods_dir);
        }
        let _ = fs::remove_file(&part);
        return Err(format!("Não foi possível instalar: {e}"));
    }
    let rec = Installed {
        version: v.version.clone(),
        sha256: digest,
        mods_dir: mods_dir.to_string_lossy().into_owned(),
        backup,
    };
    save_record(&rec)?;
    let _ = app.emit("install-progress", Progress { stage: "done".into(), received, total });
    Ok(rec)
}

fn restore_from(bdir: &Path, mods_dir: &Path) {
    if let Ok(rd) = fs::read_dir(bdir) {
        for e in rd.flatten() {
            let _ = fs::copy(e.path(), mods_dir.join(e.file_name()));
        }
    }
}

/// Puts back the jar(s) saved before the last install.
pub fn rollback() -> Result<Installed, String> {
    let rec = installed().ok_or("Nada instalado pelo launcher ainda.")?;
    let b = rec.backup.clone().ok_or("Não há versão anterior para restaurar.")?;
    let mods = PathBuf::from(&rec.mods_dir);
    let bdir = PathBuf::from(&b);
    let mut restored = None;
    if let Ok(rd) = fs::read_dir(&bdir) {
        let files: Vec<_> = rd.flatten().collect();
        if files.is_empty() {
            return Err("O backup está vazio.".into());
        }
        for j in jars_in(&mods) {
            fs::remove_file(&j).map_err(|e| format!("Feche o jogo antes de restaurar ({e})."))?;
        }
        for e in files {
            let name = e.file_name().to_string_lossy().to_string();
            fs::copy(e.path(), mods.join(&name)).map_err(|e| e.to_string())?;
            restored = Some(name);
        }
    }
    let name = restored.ok_or("Backup não encontrado.")?;
    let version = name.trim_start_matches("1227-").trim_end_matches(".jar").to_string();
    let sha = sha256_file(&mods.join(&name))?;
    let rec2 = Installed { version, sha256: sha, mods_dir: rec.mods_dir, backup: None };
    save_record(&rec2)?;
    Ok(rec2)
}

/// Re-hashes the installed jar. Ok(true) = intact.
pub fn verify() -> Result<bool, String> {
    let rec = installed().ok_or("Nada instalado pelo launcher ainda.")?;
    let p = PathBuf::from(&rec.mods_dir).join(format!("1227-{}.jar", rec.version));
    if !p.is_file() {
        return Ok(false);
    }
    Ok(sha256_file(&p)?.eq_ignore_ascii_case(&rec.sha256))
}
