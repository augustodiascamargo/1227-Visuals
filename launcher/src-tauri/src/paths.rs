//! Where the launcher keeps its files, and safe path handling.
//! Nothing outside the launcher's own folder (or a folder the user picked) is ever written.

use std::path::{Path, PathBuf};

/// %APPDATA%\1227-launcher on Windows (or the platform equivalent).
pub fn app_dir() -> PathBuf {
    dirs::data_dir()
        .unwrap_or_else(|| PathBuf::from("."))
        .join("1227-launcher")
}

/// The launcher's own instance (game directory). Never the user's .minecraft.
pub fn default_instance() -> PathBuf {
    app_dir().join("instance")
}

/// The usual .minecraft folder, used only to *suggest* an existing Forge mods folder.
pub fn dot_minecraft() -> Option<PathBuf> {
    if cfg!(target_os = "windows") {
        dirs::data_dir().map(|d| d.join(".minecraft"))
    } else if cfg!(target_os = "macos") {
        dirs::data_dir().map(|d| d.join("minecraft"))
    } else {
        dirs::home_dir().map(|d| d.join(".minecraft"))
    }
}

/// A plain file name: no separators, no "..", no drive letters, printable ASCII only.
pub fn is_plain_file_name(name: &str) -> bool {
    !name.is_empty()
        && name.len() <= 96
        && name != "."
        && name != ".."
        && name
            .chars()
            .all(|c| c.is_ascii_alphanumeric() || c == '.' || c == '-' || c == '_')
}

/// Joins `name` under `base`, refusing anything that is not a plain file name.
pub fn safe_join(base: &Path, name: &str) -> Result<PathBuf, String> {
    if !is_plain_file_name(name) {
        return Err(format!("Nome de arquivo recusado: {name}"));
    }
    Ok(base.join(name))
}

#[cfg(test)]
mod tests {
    use super::*;

    #[test]
    fn plain_names() {
        assert!(is_plain_file_name("1227-1.11.0.jar"));
        assert!(!is_plain_file_name("../evil.jar"));
        assert!(!is_plain_file_name("a/b.jar"));
        assert!(!is_plain_file_name("a\\b.jar"));
        assert!(!is_plain_file_name("C:evil"));
        assert!(!is_plain_file_name(".."));
        assert!(!is_plain_file_name(""));
    }
}
