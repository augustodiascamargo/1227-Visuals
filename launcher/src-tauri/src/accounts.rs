//! Local account profiles for the 1227 launcher.
//!
//! Offline profiles are local labels for development/local environments only.
//! They do not mint Minecraft sessions, authenticate with Mojang/Microsoft,
//! or grant access to authenticated game servers.

use crate::paths;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;
use std::time::{SystemTime, UNIX_EPOCH};

#[derive(Debug, Clone, Serialize, Deserialize, PartialEq, Eq)]
#[serde(rename_all = "lowercase")]
pub enum ProfileType {
    Offline,
    Microsoft,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
#[serde(rename_all = "camelCase")]
pub struct AccountProfile {
    pub id: String,
    pub name: String,
    pub profile_type: ProfileType,
    pub created_at: u64,
}

#[derive(Debug, Clone, Serialize, Deserialize, Default)]
#[serde(rename_all = "camelCase")]
pub struct AccountStore {
    pub profiles: Vec<AccountProfile>,
    pub selected_profile_id: Option<String>,
}

fn file() -> PathBuf {
    paths::app_dir().join("accounts.json")
}

fn load() -> AccountStore {
    fs::read_to_string(file())
        .ok()
        .and_then(|contents| serde_json::from_str::<AccountStore>(&contents).ok())
        .unwrap_or_default()
}

fn save(store: &AccountStore) -> Result<(), String> {
    let dir = paths::app_dir();
    fs::create_dir_all(&dir).map_err(|e| format!("Não foi possível criar a pasta de contas: {e}"))?;
    let tmp = dir.join("accounts.json.tmp");
    let contents = serde_json::to_string_pretty(store).map_err(|e| e.to_string())?;
    fs::write(&tmp, contents).map_err(|e| format!("Não foi possível salvar as contas: {e}"))?;
    if file().exists() {
        fs::remove_file(file()).map_err(|e| format!("Não foi possível substituir as contas salvas: {e}"))?;
    }
    fs::rename(&tmp, file()).map_err(|e| format!("Não foi possível finalizar o salvamento: {e}"))
}

fn valid_name(name: &str) -> Result<String, String> {
    let clean = name.trim();
    if !(3..=16).contains(&clean.len()) {
        return Err("O nome precisa ter entre 3 e 16 caracteres.".into());
    }
    if !clean.chars().all(|c| c.is_ascii_alphanumeric() || c == '_') {
        return Err("Use apenas letras sem acento, números e underline (_).".into());
    }
    Ok(clean.to_string())
}

pub fn list() -> AccountStore {
    load()
}

pub fn add_offline(name: String) -> Result<AccountStore, String> {
    let name = valid_name(&name)?;
    let mut store = load();
    if store.profiles.iter().any(|p| p.name.eq_ignore_ascii_case(&name) && p.profile_type == ProfileType::Offline) {
        return Err("Já existe um perfil offline com esse nome.".into());
    }

    let created_at = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|e| e.to_string())?
        .as_secs();
    let nonce = SystemTime::now()
        .duration_since(UNIX_EPOCH)
        .map_err(|e| e.to_string())?
        .as_nanos();
    let profile = AccountProfile {
        id: format!("offline-{nonce:x}"),
        name,
        profile_type: ProfileType::Offline,
        created_at,
    };
    let id = profile.id.clone();
    store.profiles.push(profile);
    if store.selected_profile_id.is_none() {
        store.selected_profile_id = Some(id);
    }
    save(&store)?;
    Ok(store)
}

pub fn select(id: String) -> Result<AccountStore, String> {
    let mut store = load();
    if !store.profiles.iter().any(|p| p.id == id) {
        return Err("Perfil não encontrado.".into());
    }
    store.selected_profile_id = Some(id);
    save(&store)?;
    Ok(store)
}

pub fn remove(id: String) -> Result<AccountStore, String> {
    let mut store = load();
    let before = store.profiles.len();
    store.profiles.retain(|p| p.id != id);
    if store.profiles.len() == before {
        return Err("Perfil não encontrado.".into());
    }
    if store.selected_profile_id.as_deref() == Some(id.as_str()) {
        store.selected_profile_id = store.profiles.first().map(|p| p.id.clone());
    }
    save(&store)?;
    Ok(store)
}

#[cfg(test)]
mod tests {
    use super::valid_name;

    #[test]
    fn accepts_valid_player_names() {
        assert_eq!(valid_name("Player_1227").unwrap(), "Player_1227");
    }

    #[test]
    fn rejects_short_long_and_invalid_names() {
        assert!(valid_name("ab").is_err());
        assert!(valid_name("ThisNameIsTooLong").is_err());
        assert!(valid_name("nome com espaço").is_err());
        assert!(valid_name("jogador!").is_err());
    }
}
