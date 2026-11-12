//! Launcher settings, stored as JSON in the launcher folder.

use crate::paths;
use serde::{Deserialize, Serialize};
use std::fs;
use std::path::PathBuf;

/// Where the 1227 mod is installed.
#[derive(Serialize, Deserialize, Clone, Debug, PartialEq)]
#[serde(rename_all = "kebab-case")]
pub enum Target {
    /// The launcher's own instance folder (default).
    Own,
    /// A Forge 1.8.9 mods folder of an existing installation the user picked.
    Existing,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
#[serde(rename_all = "camelCase", default)]
pub struct Settings {
    pub target: Target,
    pub instance_dir: String,
    pub existing_mods_dir: Option<String>,
    pub java_path: Option<String>,
    pub channel: String,
}

impl Default for Settings {
    fn default() -> Self {
        Settings {
            target: Target::Own,
            instance_dir: paths::default_instance().to_string_lossy().into_owned(),
            existing_mods_dir: None,
            java_path: None,
            channel: "stable".into(),
        }
    }
}

impl Settings {
    fn file() -> PathBuf {
        paths::app_dir().join("settings.json")
    }

    pub fn load() -> Settings {
        fs::read_to_string(Self::file())
            .ok()
            .and_then(|t| serde_json::from_str(&t).ok())
            .unwrap_or_default()
    }

    pub fn save(&self) -> Result<(), String> {
        let dir = paths::app_dir();
        fs::create_dir_all(&dir).map_err(|e| e.to_string())?;
        let tmp = dir.join("settings.json.tmp");
        let text = serde_json::to_string_pretty(self).map_err(|e| e.to_string())?;
        fs::write(&tmp, text).map_err(|e| e.to_string())?;
        fs::rename(&tmp, Self::file()).map_err(|e| e.to_string())
    }

    /// The mods folder the mod goes into, for the current target.
    pub fn mods_dir(&self) -> Result<PathBuf, String> {
        match self.target {
            Target::Own => Ok(PathBuf::from(&self.instance_dir).join("mods")),
            Target::Existing => self
                .existing_mods_dir
                .as_ref()
                .map(PathBuf::from)
                .ok_or_else(|| "Escolha a pasta mods da sua instalação Forge 1.8.9.".to_string()),
        }
    }
}
