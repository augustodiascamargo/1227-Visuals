//! Version manifest: what versions exist and where their files are.
//! The manifest is data only. It can never run commands or choose paths: file names are
//! checked to be plain "1227-<version>.jar" names and URLs must be HTTPS on allowed hosts.

use serde::{Deserialize, Serialize};

/// The API the launcher asks first. Change it here (it is not a secret).
pub const MANIFEST_URL: &str = "https://api.1227client.workers.dev/v1/manifest";
pub const NEWS_URL: &str = "https://api.1227client.workers.dev/v1/news";

/// Hosts files may be downloaded from (GitHub Releases and its CDN).
const ALLOWED_HOSTS: &[&str] = &[
    "github.com",
    "objects.githubusercontent.com",
    "release-assets.githubusercontent.com",
];

/// Largest file accepted (the mod jar is a few MB).
pub const MAX_FILE_SIZE: u64 = 64 * 1024 * 1024;

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct FileEntry {
    pub name: String,
    pub url: String,
    pub sha256: String,
    pub size: u64,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct Version {
    pub version: String,
    pub minecraft: String,
    pub forge: String,
    #[serde(default)]
    pub date: String,
    #[serde(default)]
    pub notes: Vec<String>,
    pub file: FileEntry,
}

#[derive(Serialize, Deserialize, Clone, Debug)]
pub struct Manifest {
    pub schema: u32,
    pub latest: String,
    pub versions: Vec<Version>,
}

impl Manifest {
    pub fn find(&self, v: &str) -> Option<&Version> {
        self.versions.iter().find(|x| x.version == v)
    }
}

pub fn valid_version(v: &str) -> bool {
    !v.is_empty() && v.len() <= 20 && v.split('.').all(|p| !p.is_empty() && p.chars().all(|c| c.is_ascii_digit()))
}

pub fn check_url(u: &str) -> Result<(), String> {
    let parsed = url::Url::parse(u).map_err(|_| format!("URL inválida: {u}"))?;
    if parsed.scheme() != "https" {
        return Err("Somente downloads HTTPS são aceitos.".into());
    }
    let host = parsed.host_str().unwrap_or("");
    if !ALLOWED_HOSTS.contains(&host) {
        return Err(format!("Host não permitido: {host}"));
    }
    Ok(())
}

/// Checks everything that could be abused before the manifest is used.
pub fn validate(m: &Manifest) -> Result<(), String> {
    if m.schema != 1 {
        return Err(format!("Formato de manifesto não suportado ({}). Atualize o launcher.", m.schema));
    }
    if !valid_version(&m.latest) || m.find(&m.latest).is_none() {
        return Err("Manifesto sem versão mais recente válida.".into());
    }
    for v in &m.versions {
        if !valid_version(&v.version) {
            return Err(format!("Versão inválida no manifesto: {}", v.version));
        }
        if v.minecraft != "1.8.9" {
            return Err(format!("{} não é para Minecraft 1.8.9.", v.version));
        }
        let expected = format!("1227-{}.jar", v.version);
        if v.file.name != expected || !crate::paths::is_plain_file_name(&v.file.name) {
            return Err(format!("Nome de arquivo inesperado: {}", v.file.name));
        }
        check_url(&v.file.url)?;
        if v.file.sha256.len() != 64 || !v.file.sha256.chars().all(|c| c.is_ascii_hexdigit()) {
            return Err(format!("SHA-256 inválido para {}", v.version));
        }
        if v.file.size == 0 || v.file.size > MAX_FILE_SIZE {
            return Err(format!("Tamanho inválido para {}", v.version));
        }
    }
    Ok(())
}

/// Compares dotted numeric versions: "1.10.0" > "1.9.2".
pub fn newer(a: &str, b: &str) -> bool {
    let pa: Vec<u64> = a.split('.').filter_map(|x| x.parse().ok()).collect();
    let pb: Vec<u64> = b.split('.').filter_map(|x| x.parse().ok()).collect();
    pa > pb
}

pub async fn fetch(client: &reqwest::Client) -> Result<Manifest, String> {
    let resp = client
        .get(MANIFEST_URL)
        .send()
        .await
        .map_err(|e| format!("Sem conexão com o servidor de versões ({e})."))?;
    if !resp.status().is_success() {
        return Err(format!("Servidor de versões respondeu {}.", resp.status()));
    }
    let m: Manifest = resp.json().await.map_err(|e| format!("Manifesto ilegível: {e}"))?;
    validate(&m)?;
    Ok(m)
}

#[cfg(test)]
mod tests {
    use super::*;

    fn sample() -> Manifest {
        Manifest {
            schema: 1,
            latest: "1.11.0".into(),
            versions: vec![Version {
                version: "1.11.0".into(),
                minecraft: "1.8.9".into(),
                forge: "11.15.1.2318".into(),
                date: "2026-11-11".into(),
                notes: vec![],
                file: FileEntry {
                    name: "1227-1.11.0.jar".into(),
                    url: "https://github.com/owner/repo/releases/download/v1.11.0/1227-1.11.0.jar".into(),
                    sha256: "a".repeat(64),
                    size: 3_000_000,
                },
            }],
        }
    }

    #[test]
    fn accepts_good() {
        assert!(validate(&sample()).is_ok());
    }

    #[test]
    fn rejects_bad() {
        let mut m = sample();
        m.versions[0].file.url = "http://github.com/x.jar".into();
        assert!(validate(&m).is_err());
        let mut m = sample();
        m.versions[0].file.url = "https://evil.example/x.jar".into();
        assert!(validate(&m).is_err());
        let mut m = sample();
        m.versions[0].file.name = "../1227-1.11.0.jar".into();
        assert!(validate(&m).is_err());
        let mut m = sample();
        m.versions[0].minecraft = "1.20".into();
        assert!(validate(&m).is_err());
    }

    #[test]
    fn ordering() {
        assert!(newer("1.10.0", "1.9.2"));
        assert!(!newer("1.9.0", "1.9.0"));
        assert!(newer("2.0", "1.99.99"));
    }
}
